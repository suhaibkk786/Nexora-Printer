package nexora.printer.core.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nexora.printer.core.printer.PrinterException
import nexora.printer.core.printer.PrinterTransport
import kotlin.coroutines.resume

class UsbTransport(
    private val context: Context,
    private val address: String
) : PrinterTransport {

    private var connection: UsbDeviceConnection? = null
    private var claimed: UsbInterface? = null
    private var endpoint: UsbEndpoint? = null
    @Volatile private var healthy = false

    override val isConnected: Boolean get() = healthy && connection != null

    override suspend fun connect(timeoutMs: Int) {
        close()
        val manager = context.getSystemService(UsbManager::class.java)
            ?: throw PrinterException("USB host mode is not supported on this device")
        val (vid, pid) = UsbPrinterSupport.parseAddress(address)
            ?: throw PrinterException("Invalid USB printer address: $address")
        val device = manager.deviceList.values.firstOrNull { it.vendorId == vid && it.productId == pid }
            ?: throw PrinterException("USB printer is not attached. Check the OTG cable and power.")

        if (!manager.hasPermission(device)) {
            val granted = requestPermission(manager, device, maxOf(timeoutMs.toLong(), 20_000L))
            if (!granted) throw PrinterException("USB permission was denied or timed out")
        }
        val target = UsbPrinterSupport.findTarget(device)
            ?: throw PrinterException("This USB device has no printer or bulk-OUT interface, so it cannot print ESC/POS")

        withContext(Dispatchers.IO) {
            val conn = manager.openDevice(device) ?: throw PrinterException("Could not open the USB device")
            if (!conn.claimInterface(target.usbInterface, true)) {
                conn.close()
                throw PrinterException("Could not claim the USB printer interface (in use by another app?)")
            }
            connection = conn
            claimed = target.usbInterface
            endpoint = target.bulkOut
            healthy = true
        }
    }

    private suspend fun requestPermission(manager: UsbManager, device: UsbDevice, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        if (intent.action != ACTION_PERMISSION) return
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    }
                }
                ContextCompat.registerReceiver(
                    context, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED
                )
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                val pi = PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), PendingIntent.FLAG_MUTABLE
                )
                manager.requestPermission(device, pi)
            }
        } ?: false

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val conn = connection
        val ep = endpoint
        if (conn == null || ep == null) throw PrinterException("Printer is not connected")
        var off = 0
        while (off < data.size) {
            ensureActive()
            val n = minOf(CHUNK, data.size - off)
            val sent = conn.bulkTransfer(ep, data, off, n, WRITE_TIMEOUT_MS)
            if (sent < 0) {
                healthy = false
                throw PrinterException("USB transfer failed (printer unplugged, off, or out of paper?)")
            }
            off += sent
        }
    }

    override fun close() {
        healthy = false
        runCatching { claimed?.let { connection?.releaseInterface(it) } }
        runCatching { connection?.close() }
        connection = null
        claimed = null
        endpoint = null
    }

    private companion object {
        const val ACTION_PERMISSION = "nexora.printer.USB_PERMISSION"
        const val CHUNK = 16 * 1024
        const val WRITE_TIMEOUT_MS = 5000
    }
}
