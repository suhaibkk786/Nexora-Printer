package nexora.printer.core.escpos

import java.nio.charset.Charset

object TextClassifier {
    fun isRtlChar(c: Char): Boolean {
        val code = c.code
        return code in 0x0590..0x08FF || code in 0xFB1D..0xFDFF || code in 0xFE70..0xFEFF
    }

    fun containsRtl(s: String) = s.any { isRtlChar(it) }

    /**
     * Arabic/Hebrew need shaping and bidi reordering that thermal printer code pages cannot do,
     * and any character the printer charset cannot encode would print as '?'. Both go to raster.
     */
    fun needsRaster(line: String, charset: Charset): Boolean =
        containsRtl(line) || !charset.newEncoder().canEncode(line)
}
