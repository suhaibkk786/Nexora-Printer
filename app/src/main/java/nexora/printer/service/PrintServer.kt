package nexora.printer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nexora.printer.core.printer.RawPrintLink
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintOutcome
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * A tiny web server on this device only (127.0.0.1): the POS page sends the ticket with `fetch('http://127.0.0.1:40214/print')`
 * and it prints at once, without opening the app (the way RawBT's local server works).
 *
 *   POST /print   body: the ESC/POS bytes as base64 text (or a rawbt:base64,... link, or the raw bytes)
 *   GET  /ping    answers {"ok":true} so the POS can tell the service is running
 */
class PrintServer(private val dispatcher: PrintDispatcher, private val config: ServerConfig) {
    @Volatile private var server: ServerSocket? = null

    /** Serves until [stop] is called or the coroutine is cancelled. */
    suspend fun run() = coroutineScope {
        val socket = withContext(Dispatchers.IO) { ServerSocket(ServerConfig.PORT, 8, InetAddress.getByName("127.0.0.1")) }
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
            sock.soTimeout = 5000
            val out = sock.getOutputStream()
            val req = try {
                MiniHttp.readRequest(BufferedInputStream(sock.getInputStream()))
            } catch (e: IOException) {
                null
            }
            if (req == null) {
                out.write(MiniHttp.response(400, """{"ok":false,"error":"bad request"}""", null))
                return
            }
            val origin = req.headers["origin"]
            if (!config.originAllowed(origin)) {
                out.write(MiniHttp.response(403, """{"ok":false,"error":"this web address is not allowed to print"}""", null))
                return
            }
            val answer = when {
                req.method == "OPTIONS" -> MiniHttp.response(204, "", origin)
                req.method == "GET" && req.path.startsWith("/ping") -> MiniHttp.response(200, """{"ok":true,"app":"Nexora Printer"}""", origin)
                req.method == "POST" && req.path.startsWith("/print") -> print(req.body, origin)
                else -> MiniHttp.response(404, """{"ok":false,"error":"not found"}""", origin)
            }
            out.write(answer)
            out.flush()
        }
    }

    private suspend fun print(body: ByteArray, origin: String?): ByteArray {
        val bytes = RawPrintLink.decodeBody(body)
            ?: return MiniHttp.response(400, """{"ok":false,"error":"empty ticket"}""", origin)
        return when (val outcome = dispatcher.printRaw("POS ticket", bytes)) {
            is PrintOutcome.Success -> MiniHttp.response(200, """{"ok":true,"printer":${json(outcome.printerName)}}""", origin)
            is PrintOutcome.Failed -> MiniHttp.response(500, """{"ok":false,"error":${json(outcome.message)}}""", origin)
            is PrintOutcome.NoPrinter -> MiniHttp.response(500, """{"ok":false,"error":${json(outcome.reason)}}""", origin)
            is PrintOutcome.ChoosePrinter -> MiniHttp.response(500, """{"ok":false,"error":"choose a default printer in Nexora Printer"}""", origin)
        }
    }

    private fun json(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""
}
