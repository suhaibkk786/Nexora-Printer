package nexora.printer

import nexora.printer.core.escpos.*
import nexora.printer.core.utils.PageRange
import nexora.printer.core.wifi.NetworkEndpoint
import org.junit.Assert.*
import org.junit.Test

class ImagingAndFormattingTest {
    @Test fun thresholdPacksBitsMsbFirst() {
        // 8 pixels: dark,light,dark,light,... -> 10101010
        val lum = IntArray(8) { if (it % 2 == 0) 0 else 255 }
        val r = Dithering.toRaster(lum, 8, 1, DitherMode.THRESHOLD, 128)
        assertEquals(0xAA, r.bits[0].toInt() and 0xFF)
    }

    @Test fun rasterPadsPartialBytes() {
        val r = Dithering.toRaster(IntArray(10) { 0 }, 10, 1, DitherMode.THRESHOLD)
        assertEquals(2, r.rowBytes)
        assertEquals(0xFF, r.bits[0].toInt() and 0xFF)
        assertEquals(0xC0, r.bits[1].toInt() and 0xFF)
    }

    @Test fun floydSteinbergMidGrayIsMixed() {
        val n = 16 * 16
        val r = Dithering.toRaster(IntArray(n) { 128 }, 16, 16, DitherMode.FLOYD_STEINBERG, 129)
        var black = 0
        for (y in 0 until 16) for (x in 0 until 16) if (r.isBlack(x, y)) black++
        assertTrue("expected roughly half black, got $black", black in 90..166)
    }

    @Test fun floydSteinbergExtremesStayPure() {
        val white = Dithering.toRaster(IntArray(64) { 255 }, 8, 8, DitherMode.FLOYD_STEINBERG)
        val black = Dithering.toRaster(IntArray(64) { 0 }, 8, 8, DitherMode.FLOYD_STEINBERG)
        assertTrue(white.bits.all { it.toInt() == 0 })
        assertTrue(black.bits.all { (it.toInt() and 0xFF) == 0xFF })
    }

    @Test fun luminanceCompositesTransparencyOnWhite() {
        assertEquals(255, Dithering.luminance(0x00000000))
        assertEquals(0, Dithering.luminance(0xFF000000.toInt()))
    }

    @Test fun charsPerLine() {
        assertEquals(32, ReceiptFormatter.charsPerLine(384))
        assertEquals(48, ReceiptFormatter.charsPerLine(576))
        assertEquals(16, ReceiptFormatter.charsPerLine(384, 2))
    }

    @Test fun wrapKeepsWordsAndSplitsLongOnes() {
        assertEquals(listOf("hello", "world"), ReceiptFormatter.wrap("hello world", 8))
        assertEquals(listOf("abcd", "efgh", "i"), ReceiptFormatter.wrap("abcdefghi", 4))
        assertEquals(listOf("a", "", "b"), ReceiptFormatter.wrap("a\n\nb", 10))
    }

    @Test fun twoColumnsFillsWidth() {
        val line = ReceiptFormatter.twoColumns("TOTAL", "10.50", 32)
        assertEquals(32, line.length)
        assertTrue(line.startsWith("TOTAL") && line.endsWith("10.50"))
        assertEquals(20, ReceiptFormatter.twoColumns("a very long item name", "9.99", 20).length)
    }

    @Test fun arabicAndUnencodableTextGoToRaster() {
        val cs = charset("IBM437")
        assertTrue(TextClassifier.needsRaster("مرحبا", cs))
        assertTrue(TextClassifier.needsRaster("Total المجموع 5", cs))
        assertTrue(TextClassifier.needsRaster("日本語", cs))
        assertFalse(TextClassifier.needsRaster("Plain ASCII 123", cs))
        assertFalse(TextClassifier.needsRaster("café", cs))
    }

    @Test fun barcodeValidation() {
        assertNull(BarcodeValidator.validate(BarcodeType.EAN13, "123456789012"))
        assertNotNull(BarcodeValidator.validate(BarcodeType.EAN13, "1234"))
        assertNull(BarcodeValidator.validate(BarcodeType.EAN8, "1234567"))
        assertNull(BarcodeValidator.validate(BarcodeType.UPC_A, "12345678901"))
        assertNull(BarcodeValidator.validate(BarcodeType.CODE39, "ABC-123"))
        assertNotNull(BarcodeValidator.validate(BarcodeType.CODE39, "abc"))
        assertNull(BarcodeValidator.validate(BarcodeType.CODE128, "Hello 123"))
        assertNotNull(BarcodeValidator.validate(BarcodeType.CODE128, "héllo"))
    }

    @Test fun pageRanges() {
        assertEquals(listOf(0, 1, 2, 4), PageRange.parse("1-3,5", 10))
        assertEquals((0 until 5).toList(), PageRange.parse("", 5))
        assertEquals(listOf(3, 4), PageRange.parse("4-", 5))
        assertNull(PageRange.parse("0", 5))
        assertNull(PageRange.parse("6", 5))
        assertNull(PageRange.parse("3-1", 5))
        assertNull(PageRange.parse("a", 5))
    }

    @Test fun networkEndpointParsing() {
        assertEquals(NetworkEndpoint("192.168.1.50", 9100), NetworkEndpoint.parse("192.168.1.50"))
        assertEquals(NetworkEndpoint("10.0.0.2", 9101), NetworkEndpoint.parse("10.0.0.2:9101"))
        assertNull(NetworkEndpoint.parse("10.0.0.2:99999"))
        assertNull(NetworkEndpoint.parse("bad host"))
        assertNull(NetworkEndpoint.parse(""))
    }
}
