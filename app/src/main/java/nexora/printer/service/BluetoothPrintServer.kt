package nexora.printer.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nexora.printer.core.bluetooth.BluetoothPermissions
import nexora.printer.domain.usecase.PrintDispatcher
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * A virtual Bluetooth printer ("print from a nearby device"): another phone, tablet or POS app pairs with this tablet in its Bluetooth
 * settings, sees it as a printer (serial port, SPP), and sends its ESC/POS ticket; it is printed on the printer saved in Nexora Printer.
 * It works with POS apps and print utilities that print to a Bluetooth printer, the way RawBT's virtual printer does.
 */
@SuppressLint("MissingPermission")
class BluetoothPrintServer(private val context: Context, private val dispatcher: PrintDispatcher, private val log: ReceiveLog) {
    @Volatile private var server: BluetoothServerSocket? = null

    suspend fun run() = coroutineScope {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { log.btStatus.value = "This device has no Bluetooth"; return@coroutineScope }
        while (isActive) {
            if (!BluetoothPermissions.hasConnect(context)) { log.btStatus.value = "Waiting: Bluetooth permission is not granted"; delay(5_000); continue }
            if (!adapter.isEnabled) { log.btStatus.value = "Waiting: Bluetooth is turned off"; delay(5_000); continue }
            val listener = try {
                adapter.listenUsingRfcommWithServiceRecord("Nexora Printer", SPP_UUID)
            } catch (e: IOException) {
                log.btStatus.value = "Could not start: ${e.message}"; delay(5_000); continue
            } catch (e: SecurityException) {
                log.btStatus.value = "Could not start: ${e.message}"; delay(5_000); continue
            }
            server = listener
            log.btStatus.value = "Ready: other devices can pair and print"
            try {
                while (isActive) {
                    val client = try {
                        withContext(Dispatchers.IO) { listener.accept() }
                    } catch (e: IOException) {
                        break
                    }
                    launch(Dispatchers.IO) { handle(client) }
                }
            } finally {
                runCatching { listener.close() }
            }
            delay(2_000)
        }
    }

    fun stop() {
        runCatching { server?.close() }
    }

    private suspend fun handle(socket: BluetoothSocket) {
        socket.use { s ->
            val input = s.inputStream
            val output = s.outputStream
            val data = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var lastData = System.currentTimeMillis()
            // a Bluetooth socket has no read timeout: poll for bytes, and treat a short pause after a ticket as its end
            while (data.size() < MAX_BYTES) {
                val ready = try { input.available() } catch (e: IOException) { -1 }
                if (ready < 0) break
                if (ready > 0) {
                    val n = try { input.read(buf, 0, minOf(buf.size, ready)) } catch (e: IOException) { -1 }
                    if (n < 0) break
                    val printable = EscPosStatus.answer(buf, n, output)
                    if (printable.isNotEmpty()) data.write(printable)
                    lastData = System.currentTimeMillis()
                } else {
                    val idle = System.currentTimeMillis() - lastData
                    if (data.size() > 0 && idle > IDLE_MS) break
                    if (data.size() == 0 && idle > FIRST_WAIT_MS) break
                    delay(40)
                }
            }
            val peer = runCatching { s.remoteDevice?.let { (it.name ?: "device") + " " + it.address } }.getOrNull() ?: "?"
            if (data.size() > 0) {
                val outcome = dispatcher.printRaw("Nearby device (Bluetooth)", data.toByteArray())
                log.add("Bluetooth", peer, data.size(), log.describe(outcome))
            } else {
                log.add("Bluetooth", peer, 0, "Connected, but no ticket was sent (only a status check, or nothing)")
            }
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val FIRST_WAIT_MS = 60_000L        // a paired app may keep the connection open for a long time before the first ticket
        const val IDLE_MS = 1_500L
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
