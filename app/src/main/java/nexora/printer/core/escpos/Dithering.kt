package nexora.printer.core.escpos

enum class DitherMode(val label: String) {
    THRESHOLD("Threshold"),
    FLOYD_STEINBERG("Floyd-Steinberg")
}

object Dithering {
    /** Luminance 0..255 of an ARGB pixel composited on white. */
    fun luminance(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val lum = 0.299 * r + 0.587 * g + 0.114 * b
        return ((lum * a + 255.0 * (255 - a)) / 255.0).toInt().coerceIn(0, 255)
    }

    /** [lum] is a row-major luminance buffer. Pixels darker than [threshold] become black dots. */
    fun toRaster(lum: IntArray, width: Int, height: Int, mode: DitherMode, threshold: Int = 128): RasterImage {
        require(lum.size == width * height) { "Luminance buffer size mismatch" }
        val rb = (width + 7) / 8
        val bits = ByteArray(rb * height)

        fun setBlack(x: Int, y: Int) {
            val i = y * rb + (x shr 3)
            bits[i] = (bits[i].toInt() or (0x80 shr (x and 7))).toByte()
        }

        when (mode) {
            DitherMode.THRESHOLD -> {
                for (y in 0 until height) for (x in 0 until width) {
                    if (lum[y * width + x] < threshold) setBlack(x, y)
                }
            }
            DitherMode.FLOYD_STEINBERG -> {
                val buf = lum.copyOf()
                for (y in 0 until height) for (x in 0 until width) {
                    val i = y * width + x
                    val old = buf[i]
                    val new = if (old < threshold) 0 else 255
                    if (new == 0) setBlack(x, y)
                    val err = old - new
                    if (x + 1 < width) buf[i + 1] += err * 7 / 16
                    if (y + 1 < height) {
                        if (x > 0) buf[i + width - 1] += err * 3 / 16
                        buf[i + width] += err * 5 / 16
                        if (x + 1 < width) buf[i + width + 1] += err / 16
                    }
                }
            }
        }
        return RasterImage(width, height, bits)
    }
}
