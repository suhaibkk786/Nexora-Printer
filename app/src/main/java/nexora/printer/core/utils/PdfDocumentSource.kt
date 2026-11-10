package nexora.printer.core.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Renders PDF pages with Android's PdfRenderer. A fresh descriptor is opened per operation. */
class PdfDocumentSource(private val open: () -> ParcelFileDescriptor) {

    suspend fun pageCount(): Int = withContext(Dispatchers.IO) {
        open().use { fd -> PdfRenderer(fd).use { it.pageCount } }
    }

    /**
     * Renders [indices] at [widthPx] (height keeps the page aspect ratio) and hands each bitmap to
     * [block], which must finish with it synchronously; the bitmap is recycled afterwards so memory
     * stays flat even for long documents. Cancellation is checked between pages.
     */
    suspend fun forEachPage(
        indices: List<Int>,
        widthPx: Int,
        block: (position: Int, pageIndex: Int, bitmap: Bitmap) -> Unit
    ) = withContext(Dispatchers.IO) {
        open().use { fd ->
            PdfRenderer(fd).use { renderer ->
                indices.forEachIndexed { position, index ->
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use { page ->
                        val h = (page.height.toLong() * widthPx / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        try {
                            block(position, index, bmp)
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
            }
        }
    }
}
