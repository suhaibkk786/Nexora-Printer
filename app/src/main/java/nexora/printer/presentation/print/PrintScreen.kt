package nexora.printer.presentation.print

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nexora.printer.core.escpos.*
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.presentation.preview.*

private val tabs = listOf("Text", "Image", "PDF", "QR", "Barcode")

@Composable
fun PrintScreen(initialTab: Int, onOpenPrinters: () -> Unit, vm: PrintViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, tabs.lastIndex)) }
    val status by vm.session.status.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val pending by vm.pendingShare.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(pending) {
        pending?.let {
            tab = when (it) { is SharedContent.Text -> 0; is SharedContent.Image -> 1; is SharedContent.Pdf -> 2 }
            vm.acceptShared(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    0 -> TextTab(vm)
                    1 -> ImageTab(vm)
                    2 -> PdfTab(vm)
                    3 -> QrTab(vm)
                    else -> BarcodeTab(vm)
                }
                OptionsCard(vm, printers)
            }
        }
    }
    PrintStatusHost(status, vm.session, printers, snackbar, onOpenPrinters)
}

@Composable
private fun OptionsCard(vm: PrintViewModel, printers: List<PrinterProfile>) {
    val c by vm.common.collectAsStateWithLifecycle()
    SectionCard(title = "Print options") {
        DropdownField(
            "Printer", listOf<PrinterProfile?>(null) + printers, printers.firstOrNull { it.id == c.printerId },
            { vm.setPrinter(it?.id) }, { it?.name ?: "Per default-printer mode" }, Modifier.fillMaxWidth()
        )
        Text("Paper width", style = MaterialTheme.typography.labelLarge)
        PaperWidthSelector(c.paper, allowDefault = true) { vm.setPaper(it) }
        StepperRow("Copies", c.copies, 1..99) { vm.setCopies(it) }
    }
}

@Composable
private fun PreviewBitmap(bmp: Bitmap?, emptyText: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        if (bmp == null) Text(emptyText, color = Color.Gray)
        else Image(bmp.asImageBitmap(), contentDescription = "Print preview", contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PrintButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text(label)
    }
}

@Composable
private fun TextTab(vm: PrintViewModel) {
    val t by vm.text.collectAsStateWithLifecycle()
    SectionCard(title = "Text") {
        OutlinedTextField(
            t.text, { v -> vm.updateText { it.copy(text = v) } },
            label = { Text("Text to print (Arabic and mixed text supported)") },
            minLines = 5, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(t.bold, { vm.updateText { it.copy(bold = !it.bold) } }, label = { Text("Bold") })
            FilterChip(t.underline, { vm.updateText { it.copy(underline = !it.underline) } }, label = { Text("Underline") })
            FilterChip(t.forceRaster, { vm.updateText { it.copy(forceRaster = !it.forceRaster) } }, label = { Text("Raster") })
        }
        AlignSelector(t.align) { a -> vm.updateText { it.copy(align = a) } }
        StepperRow("Font size", t.size, 1..4) { s -> vm.updateText { it.copy(size = s) } }
        StepperRow("Line spacing (0 = default)", t.lineSpacing, 0..60) { s -> vm.updateText { it.copy(lineSpacing = s) } }
        Text("Character encoding and code page are set per printer profile.", style = MaterialTheme.typography.bodySmall)
    }
    SectionCard(title = "Preview") {
        val cols = vm.previewColumns(t.size)
        val lines = ReceiptFormatter.wrap(t.text.ifEmpty { "Your text appears here" }, cols)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)
        ) {
            lines.forEach {
                Text(it.ifEmpty { " " }, color = Color.Black, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (t.text.any { TextClassifier.isRtlChar(it) }) {
            Text("Arabic/RTL lines are rendered as an image so shaping and word order are correct.", style = MaterialTheme.typography.bodySmall)
        }
    }
    PrintButton("Print text", t.text.isNotBlank()) { vm.printText() }
}

@Composable
private fun ImageTab(vm: PrintViewModel) {
    val s by vm.image.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::loadImage) }
    SectionCard(title = "Image") {
        OutlinedButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text(if (s.bitmap == null) "Choose image" else s.name) }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        DropdownField("Dithering", DitherMode.entries, s.dither, { d -> vm.updateImage { it.copy(dither = d) } }, { it.label }, Modifier.fillMaxWidth())
        Text("Threshold ${s.threshold}", style = MaterialTheme.typography.labelLarge)
        Slider(s.threshold.toFloat(), { v -> vm.updateImage { it.copy(threshold = v.toInt()) } }, valueRange = 40f..220f)
        Text("Size ${s.scale}% of paper width", style = MaterialTheme.typography.labelLarge)
        Slider(s.scale.toFloat(), { v -> vm.updateImage { it.copy(scale = v.toInt()) } }, valueRange = 20f..100f)
        AlignSelector(s.align) { a -> vm.updateImage { it.copy(align = a) } }
    }
    SectionCard(title = "Preview (as printed: grayscale, dithered)") { PreviewBitmap(s.preview, "Choose an image") }
    PrintButton("Print image", s.bitmap != null) { vm.printImage() }
}

@Composable
private fun PdfTab(vm: PrintViewModel) {
    val s by vm.pdf.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::loadPdf) }
    val pages = vm.selectedPages()
    SectionCard(title = "PDF") {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(if (s.uri == null) "Choose PDF" else s.name) }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (s.pageCount > 0) {
            Text("${s.pageCount} pages")
            OutlinedTextField(
                s.pages, { v -> vm.updatePdf { it.copy(pages = v) } },
                label = { Text("Pages (blank = all, e.g. 1-3,5)") }, singleLine = true,
                isError = pages == null, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Text("Scale ${s.scale}%", style = MaterialTheme.typography.labelLarge)
            Slider(s.scale.toFloat(), { v -> vm.updatePdf { it.copy(scale = v.toInt()) } }, valueRange = 30f..100f)
            DropdownField("Dithering", DitherMode.entries, s.dither, { d -> vm.updatePdf { it.copy(dither = d) } }, { it.label }, Modifier.fillMaxWidth())
            SwitchRow("Cut after each page", s.cutEachPage, { c -> vm.updatePdf { it.copy(cutEachPage = c) } })
        }
    }
    SectionCard(title = "Preview (first selected page)") { PreviewBitmap(s.preview, "Choose a PDF") }
    s.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
    PrintButton("Print PDF", s.uri != null && pages != null && s.progress == null) { vm.printPdf() }
}

@Composable
private fun QrTab(vm: PrintViewModel) {
    val s by vm.qr.collectAsStateWithLifecycle()
    SectionCard(title = "QR code") {
        OutlinedTextField(s.content, { v -> vm.updateQr { it.copy(content = v) } }, label = { Text("Content") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        StepperRow("Module size", s.size, 1..16) { v -> vm.updateQr { it.copy(size = v) } }
        DropdownField("Error correction", QrErrorCorrection.entries, s.ec, { e -> vm.updateQr { it.copy(ec = e) } }, { it.name }, Modifier.fillMaxWidth())
        SwitchRow("Native printer command", s.native, { n -> vm.updateQr { it.copy(native = n) } }, "Off = raster image (works on every printer)")
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    SectionCard(title = "Preview") { PreviewBitmap(s.preview, "Enter content") }
    PrintButton("Print QR code", s.content.isNotBlank() && s.error == null) { vm.printQr() }
}

@Composable
private fun BarcodeTab(vm: PrintViewModel) {
    val s by vm.barcode.collectAsStateWithLifecycle()
    SectionCard(title = "Barcode") {
        DropdownField("Type", BarcodeType.entries, s.type, { t -> vm.updateBarcode { it.copy(type = t) } }, { it.label }, Modifier.fillMaxWidth())
        OutlinedTextField(s.data, { v -> vm.updateBarcode { it.copy(data = v) } }, label = { Text("Data") }, singleLine = true, isError = s.error != null, modifier = Modifier.fillMaxWidth())
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        StepperRow("Height (dots)", s.height / 10, 3..25) { v -> vm.updateBarcode { it.copy(height = v * 10) } }
        StepperRow("Bar width (native)", s.width, 2..6) { v -> vm.updateBarcode { it.copy(width = v) } }
        SwitchRow("Native printer command", s.native, { n -> vm.updateBarcode { it.copy(native = n) } }, "Off = raster image (works on every printer)")
    }
    SectionCard(title = "Preview") { PreviewBitmap(s.preview, "Enter valid data") }
    PrintButton("Print barcode", s.data.isNotEmpty() && s.error == null) { vm.printBarcode() }
}
