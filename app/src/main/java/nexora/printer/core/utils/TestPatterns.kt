package nexora.printer.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

object TestPatterns {
    /** Gradient + line-pairs + checkerboard strip: shows grey levels, dithering and dot alignment. */
    fun imageTest(widthDots: Int): Bitmap {
        val w = widthDots.coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, 220, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), 90f, p)
        p.shader = null

        p.color = Color.BLACK
        var x = 0
        while (x < w) {
            c.drawRect(x.toFloat(), 100f, (x + 1).toFloat(), 130f, p)
            x += 4
        }
        val cell = 8
        for (row in 0 until 3) for (col in 0 until w / cell) {
            if ((row + col) % 2 == 0) {
                c.drawRect((col * cell).toFloat(), (140 + row * cell).toFloat(), ((col + 1) * cell).toFloat(), (148 + row * cell).toFloat(), p)
            }
        }
        p.textSize = 28f
        c.drawText("NEXORA IMAGE TEST", 8f, 205f, p)
        return bmp
    }
}
