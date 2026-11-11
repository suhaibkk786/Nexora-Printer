package nexora.printer.core.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nexora.printer.core.escpos.EscPosPreview
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.service.ServerConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a picture of printed bills in the phone's Gallery (Pictures / Nexora Bills), so the owner can see that a bill was made and what
 * is on it. Settings → "Where bills go": Printer only, Printer and Gallery, or only the Gallery (no printer needed). The last result
 * is shown in Settings.
 */
@Singleton
class BillGallery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: ServerConfig
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val galleryOnly: Boolean get() = config.outputMode == ServerConfig.OutputMode.GALLERY_ONLY

    /** After a bill is sent to the printer: also keep a picture (when "Printer and Gallery" is chosen). Never slows or breaks printing. */
    fun saveCopy(bytes: ByteArray, printer: PrinterProfile) {
        if (config.outputMode != ServerConfig.OutputMode.PRINTER_AND_GALLERY) return
        scope.launch { saveNow(bytes, printer) }
    }

    /** Draws the ticket and saves it. Returns null when saved, else a short reason. */
    suspend fun saveNow(bytes: ByteArray, printer: PrinterProfile): String? = withContext(Dispatchers.IO) {
        try {
            val bmp = EscPosPreview.render(bytes, printer.printableWidth) ?: return@withContext "The ticket had nothing to draw"
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val where = save(bmp, "Bill_$stamp.png")
            bmp.recycle()
            config.galleryDone(where, null)
            null
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            config.galleryDone(null, msg)
            msg
        }
    }

    private fun save(bmp: Bitmap, name: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Nexora Bills")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("The Gallery did not accept the picture")
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("Could not write the picture")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Pictures/Nexora Bills/$name"
        }
        // older Android: the public Pictures folder (needs the storage permission)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nexora Bills").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return "Pictures/Nexora Bills/$name"
    }
}
