package nexora.printer.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.*
import nexora.printer.core.printer.PrinterException
import nexora.printer.core.printer.PrinterTransport
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic (RFCOMM / SPP) transport. BLE-only printers are not supported here
 * because BLE needs a printer-specific GATT protocol.
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(
    private val context: Context,
    private val address: String
) : PrinterTransport {

    private var socket: BluetoothSocket? = null
    @Volatile private var healthy = false

    override val isConnected: Boolean get() = healthy && socket?.isConnected == true

    override suspend fun connect(timeoutMs: Int) = withContext(Dispatchers.IO) {
        if (!BluetoothPermissions.hasConnect(context)) {
            throw PrinterException("Bluetooth permission has not been granted")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw PrinterException("This device has no Bluetooth adapter")
        if (!adapter.isEnabled) throw PrinterException("Bluetooth is turned off")
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw PrinterException("Invalid Bluetooth address: $address", e)
        }
        close()
        runCatching { adapter.cancelDiscovery() }

        var lastError: Exception? = null
        for (secure in listOf(true, false)) {
            val s = try {
                if (secure) device.createRfcommSocketToServiceRecord(SPP_UUID)
                else device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: IOException) {
                lastError = e
                continue
            }
            try {
                connectWithTimeout(s, timeoutMs)
                socket = s
                healthy = true
                return@withContext
            } catch (e: Exception) {
                runCatching { s.close() }
                lastError = e
                if (e is CancellationException) throw e
            }
        }
        throw PrinterException(
            "Could not connect to the printer (${lastError?.message ?: "unknown error"}). " +
                "Make sure it is switched on, in range, paired, and not connected to another device.",
            lastError
        )
    }

    private suspend fun connectWithTimeout(s: BluetoothSocket, timeoutMs: Int) = coroutineScope {
        var timedOut = false
        val watchdog = launch {
            delay(timeoutMs.toLong())
            timedOut = true
            runCatching { s.close() }   // closing unblocks the pending connect()
        }
        try {
            runInterruptible { s.connect() }
        } catch (e: IOException) {
            if (timedOut) throw PrinterException("Connection timed out after ${timeoutMs / 1000}s", e)
            throw e
        } finally {
            watchdog.cancel()
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val s = socket ?: throw PrinterException("Printer is not connected")
        try {
            val os = s.outputStream
            var off = 0
            while (off < data.size) {
                ensureActive()
                val n = minOf(CHUNK, data.size - off)
                os.write(data, off, n)
                off += n
                if (off < data.size) delay(CHUNK_DELAY_MS)
            }
            os.flush()
        } catch (e: IOException) {
            healthy = false
            throw PrinterException("Connection lost while sending: ${e.message}", e)
        }
    }

    override fun close() {
        healthy = false
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHUNK = 512
        private const val CHUNK_DELAY_MS = 8L
    }
}
