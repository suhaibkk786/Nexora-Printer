package nexora.printer.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import nexora.printer.core.escpos.BarcodeType
import nexora.printer.core.escpos.QrErrorCorrection

/** Software (ZXing) QR/barcode rendering: the raster fallback for printers without native support. */
object CodeImages {
    fun qr(content: String, modulePx: Int, ec: QrErrorCorrection, maxWidth: Int): Bitmap {
        val level = when (ec) {
            QrErrorCorrection.L -> ErrorCorrectionLevel.L
            QrErrorCorrection.M -> ErrorCorrectionLevel.M
            QrErrorCorrection.Q -> ErrorCorrectionLevel.Q
            QrErrorCorrection.H -> ErrorCorrectionLevel.H
        }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to level,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val scale = modulePx.coerceAtLeast(1).coerceAtMost(maxOf(1, maxWidth / m.width))
        return toBitmap(m, scale)
    }

    fun barcode(type: BarcodeType, data: String, widthDots: Int, heightDots: Int, showText: Boolean): Bitmap {
        val format = when (type) {
            BarcodeType.CODE128 -> BarcodeFormat.CODE_128
            BarcodeType.CODE39 -> BarcodeFormat.CODE_39
            BarcodeType.EAN13 -> BarcodeFormat.EAN_13
            BarcodeType.EAN8 -> BarcodeFormat.EAN_8
            BarcodeType.UPC_A -> BarcodeFormat.UPC_A
        }
        val m = MultiFormatWriter().encode(data, format, widthDots, heightDots)
        val bars = toBitmap(m, 1)
        if (!showText) return bars

        val textH = 30
        val out = Bitmap.createBitmap(bars.width, bars.height + textH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(bars, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        c.drawText(data, bars.width / 2f, bars.height + 24f, paint)
        bars.recycle()
        return out
    }

    private fun toBitmap(m: BitMatrix, scale: Int): Bitmap {
        val w = m.width * scale
        val h = m.height * scale
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (m.get(x / scale, y / scale)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
