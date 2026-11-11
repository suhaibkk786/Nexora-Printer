package nexora.printer.service

import java.io.ByteArrayOutputStream
import java.io.InputStream

class HttpRequest(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

/** Just enough HTTP for the silent-printing service: one request per connection, a body up to [MAX_BODY] bytes. */
object MiniHttp {
    const val MAX_BODY = 2_000_000
    private const val MAX_HEAD = 16 * 1024

    /** The request on [input], or null when it is not valid HTTP or the body is too large. */
    fun readRequest(input: InputStream): HttpRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val first = lines.firstOrNull()?.split(' ') ?: return null
        if (first.size < 2) return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var done = 0
        while (done < length) {
            val n = input.read(body, done, length - done)
            if (n < 0) return null
            done += n
        }
        return HttpRequest(first[0].uppercase(), first[1], headers, body)
    }

    /** Reads up to the blank line that ends the headers. */
    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var match = 0                      // how much of "\r\n\r\n" has just been seen
        val end = byteArrayOf(13, 10, 13, 10)
        while (buf.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            match = if (b == end[match].toInt()) match + 1 else if (b == 13) 1 else 0
            if (match == 4) return String(buf.toByteArray(), Charsets.ISO_8859_1).removeSuffix("\r\n\r\n")
        }
        return null
    }

    /** A complete HTTP response. CORS headers let the POS web page read the answer; the private-network header answers Chrome's preflight. */
    fun response(status: Int, text: String, origin: String?): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 403 -> "Forbidden"
            404 -> "Not Found"; 413 -> "Payload Too Large"; else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: ${origin ?: "*"}\r\n" +
            "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type\r\n" +
            "Access-Control-Allow-Private-Network: true\r\n" +
            "Vary: Origin\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.ISO_8859_1) + body
    }
}
