package nexora.printer.core.escpos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Turns the ESC/POS bytes of a ticket into a picture of the paper, the way the printer prints it: text with alignment, bold and
 * double size, raster logos / QR images, feeds and cuts. It is used to keep a copy of every bill in the Gallery, so the owner can see
 * what was printed (and that printing worked) without a printer. Barcodes and printer-made QR codes are shown as a grey placeholder box.
 */
object EscPosPreview {
    private sealed interface Row
    private class TextRow(val text: String, val align: Int, val bold: Boolean, val w: Int, val h: Int, val underline: Boolean) : Row
    private class ImageRow(val bmp: Bitmap, val align: Int) : Row
    private class Gap(val dots: Int) : Row
    private object CutRow : Row
    private class BoxRow(val label: String, val align: Int) : Row

    /** [dots] = paper width in dots (384 = 58 mm, 576 = 80 mm). Returns null when there is nothing to draw. */
    fun render(data: ByteArray, dots: Int): Bitmap? {
        val width = dots.coerceIn(192, 832)
        val rows = decode(data, width)
        if (rows.isEmpty()) return null
        val cols = if (width >= 560) 48 else if (width >= 440) 42 else 32     // characters per line, font A
        val cell = width.toFloat() / cols
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.BLACK }
        // choose a text size whose character cell is exactly the printer's cell
        paint.textSize = 100f
        val unit = paint.measureText("0") / 100f
        val baseSize = cell / unit
        val lineH = cell * 2.4f

        // measure
        var total = 24f
        val heights = rows.map { r ->
            when (r) {
                is TextRow -> lineH * r.h
                is ImageRow -> r.bmp.height.toFloat()
                is Gap -> r.dots.toFloat()
                is BoxRow -> cell * 4
                CutRow -> 30f
            }
        }
        total += heights.sum() + 24f
        val bmp = Bitmap.createBitmap(width, total.toInt().coerceIn(40, 20000), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = 12f
        rows.forEachIndexed { i, r ->
            when (r) {
                is TextRow -> {
                    paint.textSize = baseSize * r.w
                    paint.isFakeBoldText = r.bold
                    paint.isUnderlineText = r.underline
                    val textW = paint.measureText(r.text)
                    val x = when (r.align) { 1 -> (width - textW) / 2f; 2 -> width - textW; else -> 0f }
                    canvas.save()
                    // double height = the same glyph stretched vertically
                    canvas.scale(1f, r.h.toFloat() / r.w.toFloat(), 0f, y)
                    canvas.drawText(r.text, x.coerceAtLeast(0f), y + (lineH * r.w / r.h) * 0.78f, paint)
                    canvas.restore()
                }
                is ImageRow -> {
                    val x = when (r.align) { 1 -> (width - r.bmp.width) / 2f; 2 -> (width - r.bmp.width).toFloat(); else -> 0f }
                    canvas.drawBitmap(r.bmp, x.coerceAtLeast(0f), y, null)
                }
                is BoxRow -> {
                    val box = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
                    val w = cell * 14; val x = when (r.align) { 1 -> (width - w) / 2f; 2 -> width - w; else -> 0f }
                    canvas.drawRect(x, y + 4, x + w, y + cell * 4 - 4, box)
                    paint.textSize = baseSize; paint.isFakeBoldText = false; paint.isUnderlineText = false
                    paint.color = Color.GRAY; canvas.drawText(r.label, x + 8, y + cell * 2.4f, paint); paint.color = Color.BLACK
                }
                CutRow -> {
                    val dash = Paint().apply { color = Color.GRAY; strokeWidth = 2f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f) }
                    canvas.drawLine(0f, y + 15, width.toFloat(), y + 15, dash)
                }
                is Gap -> {}
            }
            y += heights[i]
        }
        return bmp
    }

    // ───────────────────────────── decoding ─────────────────────────────

    private fun decode(d: ByteArray, width: Int): List<Row> {
        val rows = ArrayList<Row>()
        val line = java.io.ByteArrayOutputStream()
        var align = 0; var bold = false; var w = 1; var h = 1; var underline = false; var codePage = 0
        var i = 0
        fun u(k: Int) = if (k < d.size) d[k].toInt() and 0xFF else 0

        fun flush(force: Boolean) {
            val bytes = line.toByteArray(); line.reset()
            if (bytes.isEmpty() && !force) return
            rows.add(TextRow(decodeText(bytes, codePage), align, bold, w, h, underline))
        }

        while (i < d.size) {
            val b = u(i)
            when (b) {
                0x0A -> { flush(true); i++ }
                0x0D, 0x00 -> i++
                0x09 -> { line.write(' '.code); line.write(' '.code); line.write(' '.code); line.write(' '.code); i++ }
                0x0C -> { flush(false); rows.add(Gap(40)); i++ }
                0x1B -> {                                              // ESC
                    flush(false)
                    when (u(i + 1)) {
                        0x40 -> { align = 0; bold = false; w = 1; h = 1; underline = false; codePage = 0; i += 2 }
                        0x61 -> { align = u(i + 2).let { if (it > 2) it - 48 else it }.coerceIn(0, 2); i += 3 }
                        0x45, 0x47 -> { bold = (u(i + 2) and 1) == 1; i += 3 }
                        0x2D -> { underline = (u(i + 2) and 3) != 0; i += 3 }
                        0x21 -> { val n = u(i + 2); bold = (n and 8) != 0; h = if ((n and 16) != 0) 2 else 1; w = if ((n and 32) != 0) 2 else 1; underline = (n and 128) != 0; i += 3 }
                        0x74 -> { codePage = u(i + 2); i += 3 }
                        0x4D, 0x32, 0x3D, 0x52, 0x56, 0x72, 0x7B, 0x20, 0x33, 0x4A -> {
                            if (u(i + 1) == 0x4A) rows.add(Gap(u(i + 2)))
                            i += if (u(i + 1) == 0x32) 2 else 3
                        }
                        0x64 -> { rows.add(Gap(u(i + 2) * 30)); i += 3 }
                        0x70 -> i += 5
                        0x24, 0x5C -> i += 4
                        0x2A -> {                                       // ESC * m nL nH data: column bit image
                            val m = u(i + 2); val n = u(i + 3) + 256 * u(i + 4)
                            val per = if (m >= 32) 3 else 1
                            val colsN = n.coerceIn(1, width)
                            val tall = per * 8
                            val bmp = Bitmap.createBitmap(colsN, tall, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            for (cx in 0 until colsN) for (by in 0 until per) {
                                val v = u(i + 5 + cx * per + by)
                                for (bit in 0 until 8) if ((v shr (7 - bit)) and 1 == 1) bmp.setPixel(cx, by * 8 + bit, Color.BLACK)
                            }
                            rows.add(ImageRow(bmp, align)); i += 5 + n * per
                        }
                        else -> i += 2
                    }
                }
                0x1D -> {                                              // GS
                    flush(false)
                    when (u(i + 1)) {
                        0x21 -> { val n = u(i + 2); w = ((n shr 4) and 7) + 1; h = (n and 7) + 1; i += 3 }
                        0x56 -> { rows.add(CutRow); i += if (u(i + 2) == 65 || u(i + 2) == 66) 4 else 3 }
                        0x76 -> {                                       // GS v 0 m xL xH yL yH data: raster image
                            val xb = u(i + 4) + 256 * u(i + 5); val yd = u(i + 6) + 256 * u(i + 7)
                            val wpx = (xb * 8).coerceIn(1, 1600); val hpx = yd.coerceIn(1, 6000)
                            if (xb > 0 && yd > 0 && i + 8 + xb * yd <= d.size + 8) {
                                val bmp = Bitmap.createBitmap(wpx, hpx, Bitmap.Config.ARGB_8888)
                                val pixels = IntArray(wpx * hpx) { Color.WHITE }
                                for (row in 0 until hpx) for (cb in 0 until xb) {
                                    val v = u(i + 8 + row * xb + cb)
                                    if (v != 0) for (bit in 0 until 8) if ((v shr (7 - bit)) and 1 == 1) pixels[row * wpx + cb * 8 + bit] = Color.BLACK
                                }
                                bmp.setPixels(pixels, 0, wpx, 0, 0, wpx, hpx)
                                rows.add(ImageRow(if (wpx > width) Bitmap.createScaledBitmap(bmp, width, (hpx * width / wpx).coerceAtLeast(1), true) else bmp, align))
                            }
                            i += 8 + xb * yd
                        }
                        0x6B -> {                                       // GS k: barcode
                            val m = u(i + 2)
                            if (m <= 6) { var k = i + 3; while (k < d.size && u(k) != 0) k++; i = k + 1 }
                            else { i += 4 + u(i + 3) }
                            rows.add(BoxRow("||| barcode |||", align))
                        }
                        0x28 -> {                                       // GS ( k ...: QR / 2D codes made by the printer
                            val len = u(i + 3) + 256 * u(i + 4)
                            if (u(i + 2) == 0x6B && u(i + 6) == 81) rows.add(BoxRow("[ QR code ]", align))
                            i += 5 + len
                        }
                        0x48, 0x68, 0x77, 0x66, 0x42, 0x61, 0x72, 0x49, 0x44 -> i += 3
                        0x50, 0x4C, 0x57 -> i += 4
                        else -> i += 2
                    }
                }
                0x1C -> { flush(false); i += if (u(i + 1) == 0x53 || u(i + 1) == 0x57) 4 else 3 }          // FS
                0x10 -> i += if (u(i + 1) == 0x04) 3 else 2                                                // DLE EOT n: status request
                in 0x01..0x08, in 0x0E..0x1F -> i++
                else -> { line.write(b); i++ }
            }
        }
        flush(false)
        return rows
    }

    private fun decodeText(bytes: ByteArray, codePage: Int): String {
        // many POS apps send UTF-8 text; otherwise the printer's selected code page
        runCatching {
            val dec = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString()
        }.onFailure { if (it !is CharacterCodingException) return String(bytes, Charsets.ISO_8859_1) }
        val name = when (codePage) { 0 -> "IBM437"; 2 -> "IBM850"; 3 -> "IBM860"; 4 -> "IBM863"; 5 -> "IBM865"; 16 -> "windows-1252"; 17 -> "IBM866"; 19 -> "IBM858"; 28 -> "windows-1256"; else -> "ISO-8859-1" }
        val cs = runCatching { Charset.forName(name) }.getOrDefault(Charsets.ISO_8859_1)
        return String(bytes, cs)
    }
}
