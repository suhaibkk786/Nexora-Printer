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
class BluetoothPrintServer(private val context: Context, private val dispatcher: PrintDispatcher) {
    @Volatile private var server: BluetoothServerSocket? = null

    suspend fun run() = coroutineScope {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return@coroutineScope
        while (isActive) {
            if (!adapter.isEnabled || !BluetoothPermissions.hasConnect(context)) { delay(5_000); continue }
            val listener = try {
                adapter.listenUsingRfcommWithServiceRecord("Nexora Printer", SPP_UUID)
            } catch (e: IOException) {
                delay(5_000); continue
            } catch (e: SecurityException) {
                delay(5_000); continue
            }
            server = listener
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
            if (data.size() > 0) dispatcher.printRaw("Nearby device (Bluetooth)", data.toByteArray())
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val FIRST_WAIT_MS = 60_000L        // a paired app may keep the connection open for a long time before the first ticket
        const val IDLE_MS = 1_500L
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
