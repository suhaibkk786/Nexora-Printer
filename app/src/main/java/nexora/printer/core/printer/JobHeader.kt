package nexora.printer.core.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import nexora.printer.R
import nexora.printer.core.escpos.Align
import nexora.printer.core.escpos.DitherMode
import nexora.printer.core.escpos.EscPosBuilder
import nexora.printer.core.escpos.RasterImage
import nexora.printer.core.utils.BitmapOps
import nexora.printer.domain.model.PrinterProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Bytes printed at the top of a job (logo and/or date-time). Empty when both are off. */
fun interface JobHeader {
    fun build(profile: PrinterProfile, logo: Boolean, time: Boolean): ByteArray
}

@Singleton
class JobHeaderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : JobHeader {

    private val cache = HashMap<Int, RasterImage>()

    override fun build(profile: PrinterProfile, logo: Boolean, time: Boolean): ByteArray {
        if (!logo && !time) return ByteArray(0)
        val b = EscPosBuilder()
        b.initialize()
        if (profile.settings.leftMargin > 0 || profile.settings.rightMargin > 0) {
            b.leftMargin(profile.settings.leftMargin)
            b.printAreaWidth(profile.usableDots)
        }
        b.align(Align.CENTER)
        if (logo) {
            runCatching { logoRaster(profile.usableDots) }.getOrNull()?.let { b.raster(it); b.lf() }
        }
        if (time) {
            b.textLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        }
        b.lf().align(Align.LEFT)
        return b.build()
    }

    /** The logo is coloured; thermal paper is black/white, so every visible pixel becomes solid black. */
    @Synchronized
    private fun logoRaster(usableDots: Int): RasterImage = cache.getOrPut(usableDots) {
        val src = BitmapFactory.decodeResource(context.resources, R.drawable.nexora_logo)
            ?: throw IllegalStateException("Logo missing")
        val w = (usableDots * 0.4).toInt().coerceIn(96, 220)
        val h = (src.height.toLong() * w / src.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = if ((px[i] ushr 24) > 60) Color.BLACK else Color.WHITE
        val mono = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val out = BitmapOps.toRaster(mono, usableDots, DitherMode.THRESHOLD, 128, Align.CENTER, keepSize = true)
        mono.recycle()
        if (scaled !== src) scaled.recycle()
        src.recycle()
        out
    }
}
