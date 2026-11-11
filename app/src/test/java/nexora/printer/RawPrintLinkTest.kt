package nexora.printer

import nexora.printer.core.printer.RawPrintLink
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class RawPrintLinkTest {
    private val ticket = byteArrayOf(0x1B, 0x40, 'H'.code.toByte(), 'i'.code.toByte(), 0x0A, 0x1D, 0x56, 0x00)
    private val b64 = Base64.getEncoder().encodeToString(ticket)

    @Test fun decodesRawbtLink() = assertArrayEquals(ticket, RawPrintLink.decode("rawbt:base64,$b64"))
    @Test fun decodesNexoraLink() = assertArrayEquals(ticket, RawPrintLink.decode("nexoraprint:base64,$b64"))
    @Test fun schemeIsNotCaseSensitive() = assertArrayEquals(ticket, RawPrintLink.decode("RawBT:BASE64,$b64"))
    @Test fun percentEncodedBase64IsDecoded() {
        val link = "rawbt:base64," + b64.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")
        assertArrayEquals(ticket, RawPrintLink.decode(link))
    }
    @Test fun wrappedLinkWithWhitespaceStillDecodes() = assertArrayEquals(ticket, RawPrintLink.decode("rawbt:base64,${b64.take(4)}
${b64.drop(4)}"))
    @Test fun otherSchemesAreIgnored() = assertNull(RawPrintLink.decode("https://example.com/base64,$b64"))
    @Test fun emptyOrDamagedIsRejected() {
        assertNull(RawPrintLink.decode(null))
        assertNull(RawPrintLink.decode("rawbt:base64,"))
        assertNull(RawPrintLink.decode("rawbt:base64,@@@@"))
        assertNull(RawPrintLink.decode("rawbt:text,hello"))
    }
}
