package nexora.printer.core.printer

import android.graphics.Bitmap
import nexora.printer.core.escpos.*
import nexora.printer.core.utils.BitmapOps
import nexora.printer.core.utils.CodeImages
import nexora.printer.core.utils.TextRasterizer
import nexora.printer.domain.model.PrinterProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class TextOptions(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val align: Align = Align.LEFT,
    val size: Int = 1,
    val lineSpacing: Int? = null,
    val forceRaster: Boolean = false
)

data class ImageOptions(
    val dither: DitherMode = DitherMode.FLOYD_STEINBERG,
    val threshold: Int = 128,
    val align: Align = Align.CENTER,
    val scalePercent: Int = 100
)

/** Turns documents into ESC/POS bytes for a specific printer profile. */
@Singleton
class JobRenderer @Inject constructor() {

    private fun begin(p: PrinterProfile): EscPosBuilder {
        val s = p.settings
        return EscPosBuilder(p.charset).apply {
            initialize()
            density(s.density)
            codePage(s.codePage)
            if (s.leftMargin > 0 || s.rightMargin > 0) {
                leftMargin(s.leftMargin)
                printAreaWidth(p.usableDots)
            }
            align(s.defaultAlignment)
        }
    }

    private fun finish(b: EscPosBuilder, p: PrinterProfile): ByteArray {
        b.align(Align.LEFT).bold(false).underline(0).fontSize(1, 1).lineSpacing(null)
        b.feedLines(p.settings.feedLines)
        if (p.settings.autoCut) b.cut(CutMode.PARTIAL)
        return b.build()
    }

    // ---------------------------------------------------------------- text

    fun renderText(p: PrinterProfile, text: String, o: TextOptions): ByteArray {
        val b = begin(p)
        val charset = p.charset
        val cpl = ReceiptFormatter.charsPerLine(p.usableDots, o.size)
        b.align(o.align).bold(o.bold).underline(if (o.underline) 1 else 0).fontSize(o.size, o.size)
        b.lineSpacing(o.lineSpacing)

        val pending = mutableListOf<String>()
        fun flushRaster() {
            if (pending.isEmpty()) return
            val bmp = TextRasterizer.render(
                pending.joinToString("\n"), p.usableDots, 24f * o.size, o.bold, o.align, o.underline
            )
            b.raster(BitmapOps.toRaster(bmp, p.usableDots, DitherMode.THRESHOLD, 140, Align.LEFT, keepSize = true))
            bmp.recycle()
            pending.clear()
        }

        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if (o.forceRaster || TextClassifier.needsRaster(line, charset)) {
                pending += line
            } else {
                flushRaster()
                ReceiptFormatter.wrap(line, cpl).forEach { b.textLine(it) }
            }
        }
        flushRaster()
        return finish(b, p)
    }

    // ------------------------------------------------------------- receipts

    fun renderTestReceipt(p: PrinterProfile): ByteArray {
        val b = begin(p)
        val cpl = ReceiptFormatter.charsPerLine(p.usableDots)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        b.align(Align.CENTER).bold(true).fontSize(2, 2).textLine("NEXORA").fontSize(1, 1)
        b.textLine("PRINTER TEST RECEIPT").bold(false).lf()
        b.align(Align.LEFT)
        b.textLine(ReceiptFormatter.divider(cpl))
        b.textLine(ReceiptFormatter.twoColumns("Printer", p.name, cpl))
        b.textLine(ReceiptFormatter.twoColumns("Connection", p.connectionType.label, cpl))
        b.textLine(ReceiptFormatter.twoColumns("Paper", "${p.paperWidth.mm}mm / ${p.printableWidth} dots", cpl))
        b.textLine(ReceiptFormatter.twoColumns("Encoding", p.settings.encoding, cpl))
        b.textLine(ReceiptFormatter.twoColumns("Date", date, cpl))
        b.textLine(ReceiptFormatter.divider(cpl))
        b.textLine(ReceiptFormatter.twoColumns("Espresso x2", "7.00", cpl))
        b.textLine(ReceiptFormatter.twoColumns("Croissant", "3.50", cpl))
        b.textLine(ReceiptFormatter.divider(cpl, '='))
        b.bold(true).textLine(ReceiptFormatter.twoColumns("TOTAL", "10.50", cpl)).bold(false)
        b.lf()

        // Arabic and mixed-direction lines go through the raster path (shaping + bidi).
        val arabic = TextRasterizer.render(
            "مرحباً بكم في نكسورا\nTotal المجموع: 10.50", p.usableDots, 26f, false, Align.CENTER
        )
        b.raster(BitmapOps.toRaster(arabic, p.usableDots, DitherMode.THRESHOLD, 140, Align.LEFT, keepSize = true))
        arabic.recycle()
        b.lf()

        val qr = CodeImages.qr("NEXORA-PRINTER-TEST", 5, QrErrorCorrection.M, p.usableDots)
        b.raster(BitmapOps.toRaster(qr, p.usableDots, DitherMode.THRESHOLD, 128, Align.CENTER, keepSize = true))
        qr.recycle()
        b.align(Align.CENTER).textLine("Thank you").align(Align.LEFT)
        return finish(b, p)
    }

    /** Prints styles, alignments and the printer's glyph table for the selected code page. */
    fun renderEscPosSelfTest(p: PrinterProfile): ByteArray {
        val b = begin(p)
        val cpl = ReceiptFormatter.charsPerLine(p.usableDots)
        b.textLine("ESC/POS self-test").textLine(ReceiptFormatter.divider(cpl))
        b.textLine("Normal text")
        b.bold(true).textLine("Bold text").bold(false)
        b.underline(1).textLine("Underlined").underline(0)
        b.fontSize(2, 1).textLine("Wide").fontSize(1, 2).textLine("Tall").fontSize(2, 2).textLine("Big").fontSize(1, 1)
        b.align(Align.CENTER).textLine("Centered").align(Align.RIGHT).textLine("Right").align(Align.LEFT)
        b.textLine(ReceiptFormatter.divider(cpl))
        b.textLine("Code page ${p.settings.codePage} (${p.settings.encoding}) 0x80-0xFF:")
        val glyphs = StringBuilder()
        for (code in 0x80..0xFF) {
            glyphs.append(String(byteArrayOf(code.toByte()), p.charset))
            if ((code - 0x7F) % 16 == 0) glyphs.append('\n')
        }
        b.textLine(glyphs.toString())
        return finish(b, p)
    }

    // ------------------------------------------------------------ QR / barcode

    fun renderQr(
        p: PrinterProfile, content: String, moduleSize: Int, ec: QrErrorCorrection, native: Boolean
    ): ByteArray {
        val b = begin(p)
        b.align(Align.CENTER)
        if (native) {
            b.qrNative(content, moduleSize, ec)
        } else {
            val bmp = CodeImages.qr(content, moduleSize, ec, p.usableDots)
            b.raster(BitmapOps.toRaster(bmp, p.usableDots, DitherMode.THRESHOLD, 128, Align.CENTER, keepSize = true))
            bmp.recycle()
        }
        b.lf()
        return finish(b, p)
    }

    fun renderBarcode(
        p: PrinterProfile, type: BarcodeType, data: String, height: Int, width: Int, native: Boolean
    ): ByteArray {
        val b = begin(p)
        b.align(Align.CENTER)
        if (native) {
            b.barcodeNative(type, data, height, width, HriPosition.BELOW)
        } else {
            BarcodeValidator.validate(type, data)?.let { throw IllegalArgumentException(it) }
            val bmp = CodeImages.barcode(type, data, (p.usableDots * 0.9).toInt(), height, showText = true)
            b.raster(BitmapOps.toRaster(bmp, p.usableDots, DitherMode.THRESHOLD, 128, Align.CENTER, keepSize = true))
            bmp.recycle()
        }
        b.lf()
        return finish(b, p)
    }

    // ------------------------------------------------------- images / documents

    fun toRaster(p: PrinterProfile, bmp: Bitmap, o: ImageOptions): RasterImage =
        BitmapOps.toRaster(bmp, p.usableDots, o.dither, o.threshold, o.align, o.scalePercent)

    fun renderImage(p: PrinterProfile, bmp: Bitmap, o: ImageOptions): ByteArray =
        renderRasters(p, listOf(toRaster(p, bmp, o)), cutBetween = false)

    /** Prints pre-converted rasters (image or PDF pages). Optionally cuts after every page. */
    fun renderRasters(p: PrinterProfile, pages: List<RasterImage>, cutBetween: Boolean): ByteArray {
        val b = begin(p)
        pages.forEachIndexed { i, r ->
            b.raster(r)
            if (cutBetween && i < pages.lastIndex) {
                b.feedLines(p.settings.feedLines)
                if (p.settings.autoCut) b.cut(CutMode.PARTIAL)
            }
        }
        return finish(b, p)
    }
}
