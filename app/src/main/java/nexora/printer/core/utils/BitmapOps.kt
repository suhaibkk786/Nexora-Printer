package nexora.printer.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import nexora.printer.core.escpos.Align
import nexora.printer.core.escpos.DitherMode
import nexora.printer.core.escpos.Dithering
import nexora.printer.core.escpos.RasterImage

object BitmapOps {
    /**
     * Scales [src] into a printer-width canvas and converts it to 1-bit raster.
     * [scalePercent] shrinks the image inside the paper width; [keepSize] leaves images that already
     * fit untouched (used for QR codes, barcodes and rendered text, which must stay crisp).
     */
    fun toRaster(
        src: Bitmap,
        widthDots: Int,
        mode: DitherMode,
        threshold: Int = 128,
        align: Align = Align.CENTER,
        scalePercent: Int = 100,
        keepSize: Boolean = false
    ): RasterImage {
        val canvasW = ((widthDots / 8) * 8).coerceAtLeast(8)
        val targetW = if (keepSize && src.width <= canvasW) src.width
        else (canvasW * scalePercent.coerceIn(10, 100) / 100).coerceIn(8, canvasW)
        val targetH = (src.height.toLong() * targetW / src.width).toInt().coerceAtLeast(1)

        val scaled = if (targetW == src.width && targetH == src.height) src
        else Bitmap.createScaledBitmap(src, targetW, targetH, true)

        val canvasBmp = Bitmap.createBitmap(canvasW, targetH, Bitmap.Config.ARGB_8888)
        Canvas(canvasBmp).apply {
            drawColor(Color.WHITE)
            val left = when (align) {
                Align.LEFT -> 0
                Align.CENTER -> (canvasW - targetW) / 2
                Align.RIGHT -> canvasW - targetW
            }
            drawBitmap(scaled, left.toFloat(), 0f, null)
        }
        if (scaled !== src) scaled.recycle()

        val px = IntArray(canvasW * targetH)
        canvasBmp.getPixels(px, 0, canvasW, 0, 0, canvasW, targetH)
        canvasBmp.recycle()
        val lum = IntArray(px.size) { Dithering.luminance(px[it]) }
        return Dithering.toRaster(lum, canvasW, targetH, mode, threshold)
    }

    fun rasterToBitmap(r: RasterImage): Bitmap {
        val px = IntArray(r.width * r.height)
        for (y in 0 until r.height) for (x in 0 until r.width) {
            px[y * r.width + x] = if (r.isBlack(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, r.width, r.height, Bitmap.Config.ARGB_8888)
    }
}
