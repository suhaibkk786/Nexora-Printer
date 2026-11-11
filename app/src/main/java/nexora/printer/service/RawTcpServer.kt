package nexora.printer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nexora.printer.domain.usecase.PrintDispatcher
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * A virtual network receipt printer (the usual "JetDirect / raw 9100" printer): any POS app that can print to a LAN printer
 * - give it the IP address of this tablet (or 127.0.0.1 when the POS app runs on this same tablet) and port 9100 - sends its
 * ESC/POS bytes here and they are printed on the printer saved in Nexora Printer, whatever that printer's connection is
 * (Bluetooth, USB or Wi-Fi). No change is needed in the other app except choosing "network printer".
 */
class RawTcpServer(private val dispatcher: PrintDispatcher, private val config: ServerConfig, private val log: ReceiveLog) {
    @Volatile private var server: ServerSocket? = null

    suspend fun run() = coroutineScope {
        // this device only, unless the owner allows the other devices of the shop Wi-Fi
        val bind = if (config.lanShared) null else InetAddress.getByName("127.0.0.1")
        val socket = try {
            withContext(Dispatchers.IO) { ServerSocket(ServerConfig.LAN_PORT, 16, bind) }
        } catch (e: IOException) {
            log.add("Network ${ServerConfig.LAN_PORT}", "-", 0, "Could not listen on port ${ServerConfig.LAN_PORT}: ${e.message}. Another app (for example RawBT's network server) may be using it - close it.")
            return@coroutineScope
        }
        server = socket
        try {
            while (isActive) {
                val client = try {
                    withContext(Dispatchers.IO) { socket.accept() }
                } catch (e: IOException) {
                    break
                }
                launch(Dispatchers.IO) { handle(client) }
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    fun stop() {
        runCatching { server?.close() }
    }

    private suspend fun handle(client: Socket) {
        client.use { sock ->
            val input = sock.getInputStream()
            val output = sock.getOutputStream()
            val data = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (data.size() < MAX_BYTES) {
                // waiting for the first bytes may take a while; once a ticket is arriving, a short pause means it is complete
                sock.soTimeout = if (data.size() == 0) FIRST_WAIT_MS else IDLE_MS
                val n = try {
                    input.read(buf)
                } catch (e: SocketTimeoutException) {
                    -1
                } catch (e: IOException) {
                    -1
                }
                if (n < 0) break
                val printable = EscPosStatus.answer(buf, n, output)
                if (printable.isNotEmpty()) data.write(printable)
            }
            val peer = (sock.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress ?: "?"
            val channel = "Network ${ServerConfig.LAN_PORT}"
            if (data.size() > 0) {
                val outcome = dispatcher.printRaw("Network printer (port ${ServerConfig.LAN_PORT})", data.toByteArray())
                log.add(channel, peer, data.size(), log.describe(outcome))
            } else {
                log.add(channel, peer, 0, "Connected, but no ticket was sent (only a status check, or nothing)")
            }
        }
    }

    private companion object {
        const val FIRST_WAIT_MS = 8000
        const val IDLE_MS = 1500
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
