package nexora.printer

import nexora.printer.core.printer.RawPrintLink
import nexora.printer.service.MiniHttp
import nexora.printer.service.ServerConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class SilentPrintingTest {
    private fun request(text: String) = MiniHttp.readRequest(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)))

    @Test fun readsAPostWithBody() {
        val r = request("POST /print HTTP/1.1\r\nHost: 127.0.0.1\r\nOrigin: https://pos.shop.com\r\nContent-Length: 5\r\n\r\nhello")!!
        assertEquals("POST", r.method)
        assertEquals("/print", r.path)
        assertEquals("https://pos.shop.com", r.headers["origin"])
        assertEquals("hello", String(r.body))
    }

    @Test fun readsAGetWithoutBody() {
        val r = request("GET /ping HTTP/1.1\r\nHost: x\r\n\r\n")!!
        assertEquals("GET", r.method)
        assertEquals(0, r.body.size)
    }

    @Test fun rejectsGarbageAndHugeBodies() {
        assertNull(request("hello"))
        assertNull(request("POST /print HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n"))
        assertNull(request("POST /print HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort"))
    }

    @Test fun responseHasCorsAndLength() {
        val text = String(MiniHttp.response(200, "{\"ok\":true}", "https://pos.shop.com"), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("HTTP/1.1 200 OK"))
        assertTrue(text.contains("Access-Control-Allow-Origin: https://pos.shop.com"))
        assertTrue(text.contains("Access-Control-Allow-Private-Network: true"))
        assertTrue(text.contains("Content-Length: 11"))
        assertTrue(text.endsWith("{\"ok\":true}"))
    }

    @Test fun anyAddressIsAllowedUntilTheListIsSet() {
        assertTrue(ServerConfig.matches("", "https://evil.example"))
        assertTrue(ServerConfig.matches("", null))
    }

    @Test fun onlyListedAddressesMayPrint() {
        assertTrue(ServerConfig.matches("pos.shop.com", "https://pos.shop.com"))
        assertTrue(ServerConfig.matches("https://pos.shop.com/", "https://pos.shop.com"))
        assertTrue(ServerConfig.matches("a.com, pos.shop.com", "https://POS.shop.com"))
        assertFalse(ServerConfig.matches("pos.shop.com", "https://evil.example"))
        assertFalse(ServerConfig.matches("pos.shop.com", null))
    }

    @Test fun bodyAsBase64RawbtLinkOrRawBytes() {
        val ticket = byteArrayOf(0x1B, 0x40, 'H'.code.toByte(), 0x0A, 0x1D, 0x56, 0x00)
        val b64 = Base64.getEncoder().encodeToString(ticket)
        assertArrayEquals(ticket, RawPrintLink.decodeBody(b64.toByteArray()))
        assertArrayEquals(ticket, RawPrintLink.decodeBody("rawbt:base64,$b64".toByteArray()))
        assertArrayEquals(ticket, RawPrintLink.decodeBody(ticket))
        assertNull(RawPrintLink.decodeBody(ByteArray(0)))
    }
}
