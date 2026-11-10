package nexora.printer.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import nexora.printer.core.escpos.Align

/**
 * Renders text to a bitmap with Android's text engine, which performs Arabic shaping,
 * bidirectional (mixed Arabic/English) ordering and font fallback. This is the reliable path
 * for scripts that printer code pages cannot render.
 */
object TextRasterizer {
    fun render(
        text: String,
        widthDots: Int,
        textSizePx: Float,
        bold: Boolean,
        align: Align?,
        underline: Boolean = false
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = textSizePx
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            isUnderlineText = underline
        }
        val rtl = text.isNotEmpty() && TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)
        val layoutAlign = when (align) {
            null -> Layout.Alignment.ALIGN_NORMAL
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.LEFT -> if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            Align.RIGHT -> if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthDots)
            .setAlignment(layoutAlign)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .build()
        val bmp = Bitmap.createBitmap(widthDots, maxOf(1, layout.height), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)
        return bmp
    }
}
