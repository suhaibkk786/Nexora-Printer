package nexora.printer.core.printer

import java.util.Base64

/**
 * Print links sent by a point-of-sale web page: `rawbt:base64,<ESC/POS bytes in base64>` (the same link RawBT understands)
 * or `nexoraprint:base64,<...>`. The bytes are sent to the printer exactly as they are — the POS has already laid out the ticket.
 */
object RawPrintLink {
    val SCHEMES = listOf("rawbt", "nexoraprint")

    /** The decoded printer bytes, or null when the link is not a base64 print link (or is empty / damaged). */
    fun decode(link: String?): ByteArray? {
        val text = link?.trim() ?: return null
        val colon = text.indexOf(':')
        if (colon <= 0 || text.substring(0, colon).lowercase() !in SCHEMES) return null
        val rest = text.substring(colon + 1)
        val marker = "base64,"
        val start = rest.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        // a page may percent-encode '+', '/' and '='; whitespace can creep in when a link is wrapped
        val payload = percentDecode(rest.substring(start + marker.length)).filterNot { it.isWhitespace() }
        if (payload.isEmpty()) return null
        return try {
            Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            try { Base64.getUrlDecoder().decode(payload) } catch (e2: IllegalArgumentException) { null }
        }?.takeIf { it.isNotEmpty() }
    }

    /**
     * The printer bytes in a request body sent by a POS page: a `rawbt:base64,...` link, plain base64 text, or the raw bytes.
     * Null when the body is empty.
     */
    fun decodeBody(body: ByteArray): ByteArray? {
        if (body.isEmpty()) return null
        val text = String(body, Charsets.ISO_8859_1).trim()
        decode(text)?.let { return it }
        // plain base64 text: letters, digits, + / = - _ and line breaks only (the bytes of a raw ticket contain other characters)
        val base64Chars = "+/=_-"
        if (text.isNotEmpty() && text.all { (it.isLetterOrDigit() && it.code < 128) || it in base64Chars || it == '\r' || it == '\n' }) {
            val decoded = try { Base64.getMimeDecoder().decode(text) } catch (e: IllegalArgumentException) { null }
            if (decoded != null && decoded.isNotEmpty()) return decoded
        }
        return body
    }

    private fun percentDecode(s: String): String {
        if (!s.contains('%')) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) { out.append(v.toChar()); i += 3; continue }
            }
            out.append(c); i++
        }
        return out.toString()
    }
}
