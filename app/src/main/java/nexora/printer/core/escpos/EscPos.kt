package nexora.printer.core.escpos

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class Align(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }

enum class CutMode { FULL, PARTIAL }

enum class QrErrorCorrection(val code: Int) { L(48), M(49), Q(50), H(51) }

enum class BarcodeType(val label: String, val m: Int) {
    UPC_A("UPC-A", 65),
    EAN13("EAN-13", 67),
    EAN8("EAN-8", 68),
    CODE39("Code 39", 69),
    CODE128("Code 128", 73)
}

enum class HriPosition(val code: Int) { NONE(0), ABOVE(1), BELOW(2), BOTH(3) }

/** 1-bit-per-pixel image, MSB first, row stride = (width + 7) / 8, bit 1 = black dot. */
class RasterImage(val width: Int, val height: Int, val bits: ByteArray) {
    val rowBytes: Int get() = (width + 7) / 8

    init {
        require(width > 0 && height > 0) { "Empty image" }
        require(bits.size == rowBytes * height) { "Bit buffer size does not match dimensions" }
    }

    fun isBlack(x: Int, y: Int): Boolean =
        ((bits[y * rowBytes + (x shr 3)].toInt() shr (7 - (x and 7))) and 1) == 1
}

/**
 * Byte-level ESC/POS command builder. Pure Kotlin so it can be unit tested on the JVM.
 * Not every printer supports every command; callers choose what to emit per printer profile.
 */
class EscPosBuilder(private val charset: Charset = Charsets.ISO_8859_1) {
    private val out = ByteArrayOutputStream()

    fun raw(vararg bytes: Int) = apply { bytes.forEach { out.write(it and 0xFF) } }
    fun raw(bytes: ByteArray) = apply { out.write(bytes, 0, bytes.size) }

    fun initialize() = raw(0x1B, 0x40)
    fun align(a: Align) = raw(0x1B, 0x61, a.code)
    fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
    fun underline(mode: Int) = raw(0x1B, 0x2D, mode.coerceIn(0, 2))

    /** Character size multiplier, 1..8 for width and height (GS ! n). */
    fun fontSize(width: Int, height: Int) =
        raw(0x1D, 0x21, ((width.coerceIn(1, 8) - 1) shl 4) or (height.coerceIn(1, 8) - 1))

    /** Select font A (false) or font B (true). */
    fun font(b: Boolean) = raw(0x1B, 0x4D, if (b) 1 else 0)

    /** null restores the default spacing (ESC 2). */
    fun lineSpacing(dots: Int?) =
        if (dots == null) raw(0x1B, 0x32) else raw(0x1B, 0x33, dots.coerceIn(0, 255))

    fun codePage(n: Int) = raw(0x1B, 0x74, n.coerceIn(0, 255))

    fun text(s: String) = raw(s.toByteArray(charset))
    fun textLine(s: String) = text(s).lf()
    fun lf(n: Int = 1) = apply { repeat(n) { out.write(0x0A) } }

    fun feedLines(n: Int) = if (n > 0) raw(0x1B, 0x64, n.coerceIn(0, 255)) else this
    fun feedDots(n: Int) = raw(0x1B, 0x4A, n.coerceIn(0, 255))

    /** Function A cut (GS V 0/1), the most widely supported variant. */
    fun cut(mode: CutMode) = raw(0x1D, 0x56, if (mode == CutMode.FULL) 0 else 1)

    fun drawerPulse(pin: Int = 0) = raw(0x1B, 0x70, pin and 1, 25, 250)

    /**
     * Best-effort density: ESC 7 (heating dots, heating time, interval).
     * Level 0 sends nothing and leaves the printer default. Not supported by every model.
     */
    fun density(level: Int) =
        if (level in 1..8) raw(0x1B, 0x37, 7, 50 + level * 15, 2) else this

    fun leftMargin(dots: Int) = raw(0x1D, 0x4C, dots and 0xFF, (dots shr 8) and 0xFF)
    fun printAreaWidth(dots: Int) = raw(0x1D, 0x57, dots and 0xFF, (dots shr 8) and 0xFF)

    /** GS v 0 raster image, emitted in bands so small printer buffers are not overrun. */
    fun raster(image: RasterImage, bandHeight: Int = 128) = apply {
        val rb = image.rowBytes
        var y = 0
        while (y < image.height) {
            val h = minOf(bandHeight, image.height - y)
            raw(0x1D, 0x76, 0x30, 0x00, rb and 0xFF, (rb shr 8) and 0xFF, h and 0xFF, (h shr 8) and 0xFF)
            out.write(image.bits, y * rb, h * rb)
            y += h
        }
    }

    /** Native QR (GS ( k), model 2. */
    fun qrNative(content: String, moduleSize: Int = 6, ec: QrErrorCorrection = QrErrorCorrection.M) = apply {
        val data = content.toByteArray(Charsets.UTF_8)
        require(data.isNotEmpty() && data.size <= 7000) { "QR content must be 1..7000 bytes" }
        raw(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)
        raw(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleSize.coerceIn(1, 16))
        raw(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, ec.code)
        val len = data.size + 3
        raw(0x1D, 0x28, 0x6B, len and 0xFF, (len shr 8) and 0xFF, 0x31, 0x50, 0x30)
        raw(data)
        raw(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)
    }

    /** Native barcode (GS k, function B). */
    fun barcodeNative(
        type: BarcodeType,
        data: String,
        height: Int = 80,
        width: Int = 3,
        hri: HriPosition = HriPosition.BELOW
    ) = apply {
        BarcodeValidator.validate(type, data)?.let { throw IllegalArgumentException(it) }
        raw(0x1D, 0x48, hri.code)
        raw(0x1D, 0x68, height.coerceIn(1, 255))
        raw(0x1D, 0x77, width.coerceIn(2, 6))
        val payload = (if (type == BarcodeType.CODE128) "{B$data" else data).toByteArray(Charsets.US_ASCII)
        raw(0x1D, 0x6B, type.m, payload.size)
        raw(payload)
    }

    fun build(): ByteArray = out.toByteArray()
}
