package nexora.printer.presentation.diagnostics

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nexora.printer.core.escpos.*
import nexora.printer.core.printer.ImageOptions
import nexora.printer.core.printer.JobRenderer
import nexora.printer.core.printer.PrinterManager
import nexora.printer.core.utils.TestPatterns
import nexora.printer.domain.model.JobType
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintRequest
import nexora.printer.presentation.preview.DropdownField
import nexora.printer.presentation.preview.SectionCard
import nexora.printer.presentation.print.PrintSession
import nexora.printer.presentation.print.PrintStatus
import nexora.printer.presentation.print.PrintStatusHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    printerRepo: PrinterRepository,
    prefs: PreferencesRepository,
    private val manager: PrinterManager,
    private val renderer: JobRenderer,
    dispatcher: PrintDispatcher
) : ViewModel() {
    val session = PrintSession(dispatcher, viewModelScope)
    val printers: StateFlow<List<PrinterProfile>> = printerRepo.observePrinters()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val defaultId = prefs.preferences.map { it.defaultPrinterId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _selected = MutableStateFlow<Long?>(null)
    val selected: StateFlow<PrinterProfile?> = combine(printers, _selected, defaultId) { l, s, d ->
        l.firstOrNull { it.id == (s ?: d) } ?: l.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log = _log.asStateFlow()

    init {
        viewModelScope.launch {
            session.status.collect {
                when (it) {
                    is PrintStatus.Success -> log("OK: ${it.message}. Data was written to the printer; check the paper to confirm output.")
                    is PrintStatus.Error -> log("FAILED: ${it.message}")
                    else -> Unit
                }
            }
        }
    }

    private fun log(line: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { listOf("$t  $line") + it }
    }

    fun select(p: PrinterProfile) { _selected.value = p.id }

    fun connectionTest() {
        val p = selected.value ?: return log("No printer selected.")
        viewModelScope.launch {
            log("Connecting to ${p.name} (${p.connectionType.label})…")
            val start = System.currentTimeMillis()
            val r = manager.connect(p)
            log(if (r.isSuccess) "Connected in ${System.currentTimeMillis() - start} ms." else "Connection failed: ${r.exceptionOrNull()?.message}")
        }
    }

    private fun run(name: String, render: (PrinterProfile) -> ByteArray) {
        val p = selected.value ?: return log("No printer selected.")
        log("Sending $name to ${p.name}…")
        session.start(PrintRequest(name, JobType.TEST) { render(it) }, p.id)
    }

    fun escPosTest() = run("ESC/POS self-test") { renderer.renderEscPosSelfTest(it) }
    fun receiptTest() = run("Test receipt") { renderer.renderTestReceipt(it) }
    fun qrNative() = run("QR test (native)") { renderer.renderQr(it, "NEXORA-QR-NATIVE", 6, QrErrorCorrection.M, true) }
    fun qrRaster() = run("QR test (raster)") { renderer.renderQr(it, "NEXORA-QR-RASTER", 6, QrErrorCorrection.M, false) }
    fun barcodeNative() = run("Barcode test (native)") { renderer.renderBarcode(it, BarcodeType.CODE128, "NEXORA2026", 80, 3, true) }
    fun barcodeRaster() = run("Barcode test (raster)") { renderer.renderBarcode(it, BarcodeType.CODE128, "NEXORA2026", 80, 3, false) }
    fun imageTest() = run("Image test") { p ->
        val bmp: Bitmap = TestPatterns.imageTest(p.usableDots)
        try { renderer.renderImage(p, bmp, ImageOptions(p.settings.ditheringMode)) } finally { bmp.recycle() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, onOpenPrinters: () -> Unit, vm: DiagnosticsViewModel = hiltViewModel()) {
    val printers by vm.printers.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val status by vm.session.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(title = "Printer under test") {
                if (printers.isEmpty()) {
                    Text("No printers saved.")
                    Button(onClick = onOpenPrinters) { Text("Add a printer") }
                } else {
                    DropdownField("Printer", printers, selected ?: printers.first(), { vm.select(it) }, { it.name }, Modifier.fillMaxWidth())
                }
            }
            SectionCard(title = "Tests") {
                val on = selected != null
                Button(onClick = vm::connectionTest, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("Connection test") }
                OutlinedButton(onClick = vm::escPosTest, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("ESC/POS test") }
                OutlinedButton(onClick = vm::receiptTest, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("Test receipt (incl. Arabic)") }
                OutlinedButton(onClick = vm::qrNative, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("QR test – native command") }
                OutlinedButton(onClick = vm::qrRaster, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("QR test – raster") }
                OutlinedButton(onClick = vm::barcodeNative, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("Barcode test – native command") }
                OutlinedButton(onClick = vm::barcodeRaster, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("Barcode test – raster") }
                OutlinedButton(onClick = vm::imageTest, enabled = on, modifier = Modifier.fillMaxWidth()) { Text("Image test") }
                Text(
                    "“OK” means the bytes were written to the connection. A printer cannot report paper output over a raw link, so confirm on the paper.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SectionCard(title = "Log") {
                if (log.isEmpty()) Text("No tests run yet.", style = MaterialTheme.typography.bodySmall)
                log.take(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            SectionCard(title = "Troubleshooting") {
                Text(
                    "• Nothing prints / garbled text: check the code page in the printer profile; try the raster option.\n" +
                        "• Bluetooth won’t connect: pair in Android settings first, switch the printer on, close other apps using it.\n" +
                        "• BLE-only printers are not supported (no Classic SPP).\n" +
                        "• USB: use an OTG cable; accept the permission prompt; vendor-specific devices may not work.\n" +
                        "• Wi-Fi: check the IP and port (usually 9100) and that both devices share a network.\n" +
                        "• Image too light or dark: adjust threshold or dithering; raise density if your printer supports it.\n" +
                        "• Native QR/barcode prints nothing: your printer may lack the command; use the raster variant.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    PrintStatusHost(status, vm.session, printers, snackbar, onOpenPrinters)
}
