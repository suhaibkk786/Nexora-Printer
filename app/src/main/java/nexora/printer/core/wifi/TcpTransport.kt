package nexora.printer.core.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nexora.printer.core.printer.PrinterException
import nexora.printer.core.printer.PrinterTransport
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Raw TCP (JetDirect-style, default port 9100) transport. No network scanning is performed. */
class TcpTransport(
    private val host: String,
    private val port: Int = NetworkEndpoint.DEFAULT_PORT
) : PrinterTransport {

    private var socket: Socket? = null
    @Volatile private var healthy = false

    override val isConnected: Boolean
        get() = healthy && socket?.let { it.isConnected && !it.isClosed } == true

    override suspend fun connect(timeoutMs: Int) = withContext(Dispatchers.IO) {
        close()
        val s = Socket()
        try {
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timeoutMs)
            socket = s
            healthy = true
        } catch (e: IOException) {
            runCatching { s.close() }
            throw PrinterException("Could not reach $host:$port (${e.message ?: "network error"})", e)
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val s = socket ?: throw PrinterException("Printer is not connected")
        try {
            val os = s.getOutputStream()
            var off = 0
            while (off < data.size) {
                ensureActive()
                val n = minOf(CHUNK, data.size - off)
                os.write(data, off, n)
                off += n
            }
            os.flush()
        } catch (e: IOException) {
            healthy = false
            throw PrinterException("Network connection lost while sending: ${e.message}", e)
        }
    }

    override fun close() {
        healthy = false
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        const val CHUNK = 16 * 1024
    }
}
