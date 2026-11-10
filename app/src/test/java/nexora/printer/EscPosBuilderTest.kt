package nexora.printer

import nexora.printer.core.escpos.*
import org.junit.Assert.*
import org.junit.Test

class EscPosBuilderTest {
    private fun ints(b: ByteArray) = b.map { it.toInt() and 0xFF }

    @Test fun initializeAlignBoldUnderline() {
        val out = EscPosBuilder().initialize().align(Align.CENTER).bold(true).underline(1).build()
        assertEquals(listOf(0x1B, 0x40, 0x1B, 0x61, 1, 0x1B, 0x45, 1, 0x1B, 0x2D, 1), ints(out))
    }

    @Test fun fontSizeEncodesWidthAndHeight() {
        assertEquals(listOf(0x1D, 0x21, 0x11), ints(EscPosBuilder().fontSize(2, 2).build()))
        assertEquals(listOf(0x1D, 0x21, 0x00), ints(EscPosBuilder().fontSize(1, 1).build()))
        assertEquals(listOf(0x1D, 0x21, 0x77), ints(EscPosBuilder().fontSize(99, 99).build()))
    }

    @Test fun cutModes() {
        assertEquals(listOf(0x1D, 0x56, 0), ints(EscPosBuilder().cut(CutMode.FULL).build()))
        assertEquals(listOf(0x1D, 0x56, 1), ints(EscPosBuilder().cut(CutMode.PARTIAL).build()))
    }

    @Test fun textUsesConfiguredCharset() {
        val out = EscPosBuilder(charset("IBM437")).text("é").build()
        assertEquals(listOf(0x82), ints(out))
    }

    @Test fun drawerPulse() {
        assertEquals(listOf(0x1B, 0x70, 0, 25, 250), ints(EscPosBuilder().drawerPulse().build()))
    }

    @Test fun marginsAreLittleEndian() {
        assertEquals(listOf(0x1D, 0x4C, 0x2C, 0x01), ints(EscPosBuilder().leftMargin(300).build()))
        assertEquals(listOf(0x1D, 0x57, 0x80, 0x01), ints(EscPosBuilder().printAreaWidth(384).build()))
    }

    @Test fun densityZeroSendsNothing() {
        assertEquals(0, EscPosBuilder().density(0).build().size)
        assertEquals(5, EscPosBuilder().density(3).build().size)
    }

    @Test fun rasterHeaderAndBanding() {
        val img = RasterImage(16, 300, ByteArray(2 * 300))
        val out = ints(EscPosBuilder().raster(img, bandHeight = 128).build())
        // 3 bands: 128 + 128 + 44 rows
        assertEquals(listOf(0x1D, 0x76, 0x30, 0x00, 2, 0, 128, 0), out.take(8))
        val second = 8 + 2 * 128
        assertEquals(listOf(0x1D, 0x76, 0x30, 0x00, 2, 0, 128, 0), out.subList(second, second + 8))
        val third = second + 8 + 2 * 128
        assertEquals(listOf(0x1D, 0x76, 0x30, 0x00, 2, 0, 44, 0), out.subList(third, third + 8))
        assertEquals(third + 8 + 2 * 44, out.size)
    }

    @Test fun qrNativeCommandSequence() {
        val out = ints(EscPosBuilder().qrNative("HI", 4, QrErrorCorrection.H).build())
        val expected = listOf(
            0x1D, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0,
            0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x43, 4,
            0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x45, 51,
            0x1D, 0x28, 0x6B, 5, 0, 0x31, 0x50, 0x30, 'H'.code, 'I'.code,
            0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30
        )
        assertEquals(expected, out)
    }

    @Test fun qrRejectsEmptyContent() {
        assertThrows(IllegalArgumentException::class.java) { EscPosBuilder().qrNative("") }
    }

    @Test fun barcodeCode128UsesSetBPrefix() {
        val out = ints(EscPosBuilder().barcodeNative(BarcodeType.CODE128, "AB", 60, 2, HriPosition.NONE).build())
        assertEquals(listOf(0x1D, 0x48, 0, 0x1D, 0x68, 60, 0x1D, 0x77, 2, 0x1D, 0x6B, 73, 4, '{'.code, 'B'.code, 'A'.code, 'B'.code), out)
    }

    @Test fun barcodeEan13() {
        val out = ints(EscPosBuilder().barcodeNative(BarcodeType.EAN13, "123456789012").build())
        assertEquals(listOf(0x1D, 0x6B, 67, 12), out.subList(out.size - 12 - 4, out.size - 12))
    }

    @Test fun invalidBarcodeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { EscPosBuilder().barcodeNative(BarcodeType.EAN13, "12AB") }
    }
}
