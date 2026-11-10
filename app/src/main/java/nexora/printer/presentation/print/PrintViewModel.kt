package nexora.printer.presentation.print

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nexora.printer.core.escpos.*
import nexora.printer.core.printer.ImageOptions
import nexora.printer.core.printer.JobRenderer
import nexora.printer.core.printer.TextOptions
import nexora.printer.core.utils.*
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintRequest
import javax.inject.Inject

data class CommonState(val copies: Int = 1, val printerId: Long? = null, val paper: PaperWidth? = null)

data class TextState(
    val text: String = "",
    val bold: Boolean = false,
    val underline: Boolean = false,
    val align: Align = Align.LEFT,
    val size: Int = 1,
    val lineSpacing: Int = 0,
    val forceRaster: Boolean = false
)

data class ImageState(
    val bitmap: Bitmap? = null,
    val name: String = "",
    val dither: DitherMode = DitherMode.FLOYD_STEINBERG,
    val threshold: Int = 128,
    val align: Align = Align.CENTER,
    val scale: Int = 100,
    val preview: Bitmap? = null,
    val error: String? = null
)

data class PdfState(
    val uri: Uri? = null,
    val name: String = "",
    val pageCount: Int = 0,
    val pages: String = "",
    val scale: Int = 100,
    val dither: DitherMode = DitherMode.THRESHOLD,
    val cutEachPage: Boolean = false,
    val preview: Bitmap? = null,
    val progress: Float? = null,
    val error: String? = null
)

data class QrUiState(
    val content: String = "",
    val size: Int = 6,
    val ec: QrErrorCorrection = QrErrorCorrection.M,
    val native: Boolean = true,
    val preview: Bitmap? = null,
    val error: String? = null
)

data class BarcodeUiState(
    val type: BarcodeType = BarcodeType.CODE128,
    val data: String = "",
    val height: Int = 80,
    val width: Int = 3,
    val native: Boolean = true,
    val preview: Bitmap? = null,
    val error: String? = null
)

@HiltViewModel
class PrintViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    printerRepo: PrinterRepository,
    prefs: PreferencesRepository,
    dispatcher: PrintDispatcher,
    private val renderer: JobRenderer,
    private val holder: SharedContentHolder
) : ViewModel() {

    val session = PrintSession(dispatcher, viewModelScope)

    val printers: StateFlow<List<PrinterProfile>> = printerRepo.observePrinters()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val defaultId: StateFlow<Long?> = prefs.preferences.map { it.defaultPrinterId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pendingShare: StateFlow<SharedContent?> = holder.pending

    private val _common = MutableStateFlow(CommonState())
    val common = _common.asStateFlow()
    private val _text = MutableStateFlow(TextState())
    val text = _text.asStateFlow()
    private val _image = MutableStateFlow(ImageState())
    val image = _image.asStateFlow()
    private val _pdf = MutableStateFlow(PdfState())
    val pdf = _pdf.asStateFlow()
    private val _qr = MutableStateFlow(QrUiState())
    val qr = _qr.asStateFlow()
    private val _barcode = MutableStateFlow(BarcodeUiState())
    val barcode = _barcode.asStateFlow()

    private var imageJob: Job? = null
    private var pdfJob: Job? = null
    private var qrJob: Job? = null
    private var barcodeJob: Job? = null

    init {
        viewModelScope.launch { _common.update { it.copy(copies = prefs.preferences.first().defaultCopies) } }
    }

    // ------------------------------------------------------------ common

    fun setCopies(n: Int) = _common.update { it.copy(copies = n.coerceIn(1, 99)) }
    fun setPrinter(id: Long?) { _common.update { it.copy(printerId = id) }; refreshAllPreviews() }
    fun setPaper(p: PaperWidth?) { _common.update { it.copy(paper = p) }; refreshAllPreviews() }

    private fun selectedProfile(): PrinterProfile? {
        val id = _common.value.printerId ?: defaultId.value
        return printers.value.firstOrNull { it.id == id }
    }

    /** Dots available for previews and rendering, honouring the paper override. */
    fun previewDots(): Int =
        ((_common.value.paper?.defaultDots ?: selectedProfile()?.usableDots ?: PaperWidth.MM58.defaultDots) / 8) * 8

    /** Chars per line for the text preview. */
    fun previewColumns(size: Int): Int = ReceiptFormatter.charsPerLine(previewDots(), size)

    private fun effective(p: PrinterProfile): PrinterProfile = _common.value.paper?.let { p.withPaper(it) } ?: p

    private fun refreshAllPreviews() {
        refreshImagePreview(); refreshPdfPreview(); refreshQrPreview(); refreshBarcodePreview()
    }

    // ------------------------------------------------------------ share

    fun acceptShared(content: SharedContent) {
        when (content) {
            is SharedContent.Text -> _text.update { it.copy(text = content.text) }
            is SharedContent.Image -> loadImage(content.uri)
            is SharedContent.Pdf -> loadPdf(content.uri)
        }
        holder.consume()
    }

    // -------------------------------------------------------------- text

    fun updateText(f: (TextState) -> TextState) = _text.update(f)

    fun printText() {
        val t = _text.value
        if (t.text.isBlank()) return
        val c = _common.value
        session.start(PrintRequest("Text: ${t.text.trim().take(28)}", JobType.TEXT, c.copies) { p ->
            renderer.renderText(
                effective(p), t.text,
                TextOptions(t.bold, t.underline, t.align, t.size, t.lineSpacing.takeIf { it > 0 }, t.forceRaster)
            )
        }, c.printerId)
    }

    // ------------------------------------------------------------- image

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { ImageLoader.load(context, uri) }.getOrNull() }
            if (bmp == null) {
                _image.update { it.copy(error = "Could not read that image") }
            } else {
                _image.update { it.copy(bitmap = bmp, name = displayName(uri) ?: "Image", error = null) }
                refreshImagePreview()
            }
        }
    }

    fun updateImage(f: (ImageState) -> ImageState) {
        _image.update(f)
        refreshImagePreview()
    }

    private fun imageOptions(s: ImageState) = ImageOptions(s.dither, s.threshold, s.align, s.scale)

    private fun refreshImagePreview() {
        val s = _image.value
        val bmp = s.bitmap ?: return
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                runCatching {
                    val dots = previewDots()
                    BitmapOps.rasterToBitmap(
                        BitmapOps.toRaster(bmp, dots, s.dither, s.threshold, s.align, s.scale)
                    )
                }.getOrNull()
            }
            _image.update { it.copy(preview = preview) }
        }
    }

    fun printImage() {
        val s = _image.value
        val bmp = s.bitmap ?: return
        val c = _common.value
        session.start(PrintRequest("Image: ${s.name}", JobType.IMAGE, c.copies) { p ->
            renderer.renderImage(effective(p), bmp, imageOptions(s))
        }, c.printerId)
    }

    // --------------------------------------------------------------- PDF

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _pdf.update { it.copy(error = null, progress = null) }
            val count = runCatching { pdfSource(uri).pageCount() }.getOrElse {
                _pdf.update { s -> s.copy(error = "Could not open this PDF (${it.message})") }
                return@launch
            }
            _pdf.update { it.copy(uri = uri, name = displayName(uri) ?: "Document.pdf", pageCount = count, pages = "") }
            refreshPdfPreview()
        }
    }

    fun updatePdf(f: (PdfState) -> PdfState) {
        _pdf.update(f)
        refreshPdfPreview()
    }

    private fun pdfSource(uri: Uri) = PdfDocumentSource {
        context.contentResolver.openFileDescriptor(uri, "r") ?: throw java.io.IOException("Cannot open PDF")
    }

    fun selectedPages(): List<Int>? = PageRange.parse(_pdf.value.pages, _pdf.value.pageCount)

    private fun refreshPdfPreview() {
        val s = _pdf.value
        val uri = s.uri ?: return
        val first = PageRange.parse(s.pages, s.pageCount)?.firstOrNull() ?: return
        pdfJob?.cancel()
        pdfJob = viewModelScope.launch {
            val dots = previewDots()
            var preview: Bitmap? = null
            runCatching {
                pdfSource(uri).forEachPage(listOf(first), dots * s.scale / 100) { _, _, bmp ->
                    preview = BitmapOps.rasterToBitmap(BitmapOps.toRaster(bmp, dots, s.dither, 128, Align.LEFT, keepSize = true))
                }
            }
            _pdf.update { it.copy(preview = preview) }
        }
    }

    fun printPdf() {
        val s = _pdf.value
        val uri = s.uri ?: return
        val pages = selectedPages() ?: return
        val c = _common.value
        _pdf.update { it.copy(progress = 0f) }
        session.start(PrintRequest("PDF: ${s.name}", JobType.PDF, c.copies) { p ->
            val ep = effective(p)
            val rasters = mutableListOf<RasterImage>()
            try {
                pdfSource(uri).forEachPage(pages, ep.usableDots * s.scale / 100) { pos, _, bmp ->
                    rasters += BitmapOps.toRaster(bmp, ep.usableDots, s.dither, 128, Align.LEFT, keepSize = true)
                    _pdf.update { it.copy(progress = (pos + 1f) / pages.size) }
                }
                renderer.renderRasters(ep, rasters, s.cutEachPage)
            } finally {
                _pdf.update { it.copy(progress = null) }
            }
        }, c.printerId)
    }

    // ---------------------------------------------------------------- QR

    fun updateQr(f: (QrUiState) -> QrUiState) {
        _qr.update(f)
        refreshQrPreview()
    }

    private fun refreshQrPreview() {
        val s = _qr.value
        qrJob?.cancel()
        if (s.content.isBlank()) { _qr.update { it.copy(preview = null, error = null) }; return }
        qrJob = viewModelScope.launch {
            val dots = previewDots()
            val r = withContext(Dispatchers.Default) { runCatching { CodeImages.qr(s.content, s.size, s.ec, dots) } }
            _qr.update { it.copy(preview = r.getOrNull(), error = r.exceptionOrNull()?.message) }
        }
    }

    fun printQr() {
        val s = _qr.value
        if (s.content.isBlank()) return
        val c = _common.value
        session.start(PrintRequest("QR: ${s.content.take(28)}", JobType.QR, c.copies) { p ->
            renderer.renderQr(effective(p), s.content, s.size, s.ec, s.native)
        }, c.printerId)
    }

    // ----------------------------------------------------------- barcode

    fun updateBarcode(f: (BarcodeUiState) -> BarcodeUiState) {
        _barcode.update(f)
        refreshBarcodePreview()
    }

    private fun refreshBarcodePreview() {
        val s = _barcode.value
        barcodeJob?.cancel()
        if (s.data.isEmpty()) { _barcode.update { it.copy(preview = null, error = null) }; return }
        val validation = BarcodeValidator.validate(s.type, s.data)
        if (validation != null) { _barcode.update { it.copy(preview = null, error = validation) }; return }
        barcodeJob = viewModelScope.launch {
            val dots = previewDots()
            val r = withContext(Dispatchers.Default) {
                runCatching { CodeImages.barcode(s.type, s.data, (dots * 0.9).toInt(), s.height, true) }
            }
            _barcode.update { it.copy(preview = r.getOrNull(), error = r.exceptionOrNull()?.message) }
        }
    }

    fun printBarcode() {
        val s = _barcode.value
        if (s.error != null || s.data.isEmpty()) return
        val c = _common.value
        session.start(PrintRequest("${s.type.label}: ${s.data.take(24)}", JobType.BARCODE, c.copies) { p ->
            renderer.renderBarcode(effective(p), s.type, s.data, s.height, s.width, s.native)
        }, c.printerId)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
}
