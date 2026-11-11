package nexora.printer.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nexora.printer.R
import nexora.printer.core.printer.ConnectionState
import nexora.printer.core.printer.JobRenderer
import nexora.printer.core.printer.PrinterManager
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrintJobRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintRequest
import nexora.printer.presentation.preview.*
import nexora.printer.presentation.print.PrintSession
import nexora.printer.presentation.print.PrintStatusHost
import nexora.printer.presentation.theme.Navy
import nexora.printer.presentation.theme.Teal
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class DashboardState(
    val printers: List<PrinterProfile> = emptyList(),
    val defaultPrinter: PrinterProfile? = null,
    val mode: DefaultPrinterMode = DefaultPrinterMode.ALWAYS_DEFAULT,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val recentJobs: List<PrintJob> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val printers: PrinterRepository,
    prefs: PreferencesRepository,
    jobs: PrintJobRepository,
    private val manager: PrinterManager,
    dispatcher: PrintDispatcher,
    private val renderer: JobRenderer
) : ViewModel() {
    val session = PrintSession(dispatcher, viewModelScope)

    val state: StateFlow<DashboardState> = combine(
        printers.observePrinters(), prefs.preferences, manager.states, jobs.observeJobs()
    ) { list, p, states, recent ->
        val def = list.firstOrNull { it.id == p.defaultPrinterId }
        DashboardState(
            printers = list,
            defaultPrinter = def,
            mode = p.mode,
            connection = def?.let { states[it.id] } ?: ConnectionState.Disconnected,
            recentJobs = recent.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun connect(p: PrinterProfile) { viewModelScope.launch { manager.connect(p) } }
    fun disconnect(p: PrinterProfile) { viewModelScope.launch { manager.disconnect(p.id) } }
    fun setDefault(id: Long) { viewModelScope.launch { printers.setDefault(id) } }

    fun testReceipt() {
        session.start(PrintRequest("Test receipt", JobType.TEST) { renderer.renderTestReceipt(it) })
    }
}

@Composable
fun DashboardScreen(
    onOpenPrint: (Int) -> Unit,
    onOpenPrinters: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val status by vm.session.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pickDefault by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Header() }
            item { DefaultPrinterCard(state, vm, onOpenPrinters, onChange = { pickDefault = true }) }
            item {
                SectionCard(title = "Quick actions") {
                    val actions = listOf(
                        Triple("Text", Icons.Default.TextFields, 0),
                        Triple("Image", Icons.Default.Image, 1),
                        Triple("PDF", Icons.Default.PictureAsPdf, 2),
                        Triple("QR code", Icons.Default.QrCode, 3),
                        Triple("Barcode", Icons.Default.ViewWeek, 4)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.take(3).forEach { (l, i, t) -> QuickAction(l, i, Modifier.weight(1f)) { onOpenPrint(t) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.drop(3).forEach { (l, i, t) -> QuickAction(l, i, Modifier.weight(1f)) { onOpenPrint(t) } }
                        QuickAction("Test printer", Icons.Default.Receipt, Modifier.weight(1f)) { vm.testReceipt() }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent print jobs", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenHistory) { Text("See all") }
                }
            }
            if (state.recentJobs.isEmpty()) {
                item { Text("No print jobs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.recentJobs, key = { it.id }) { JobRow(it, onRetry = null) }
            }
            item {
                Text(
                    "Developed by Nexora IT Technologies\nwww.nexoraittechnologies.com",
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (pickDefault) {
        PrinterPickerDialog("Default printer", state.printers, onPick = { vm.setDefault(it.id); pickDefault = false }, onDismiss = { pickDefault = false })
    }
    PrintStatusHost(status, vm.session, state.printers, snackbar, onOpenPrinters)
}

@Composable
private fun Header() {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Navy, Teal)), RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color.White, CircleShape).padding(10.dp)) {
                Image(painterResource(R.drawable.nexora_logo), contentDescription = "Nexora logo", modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Nexora Printer", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Bluetooth • USB • Wi-Fi thermal printing", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun DefaultPrinterCard(state: DashboardState, vm: DashboardViewModel, onAdd: () -> Unit, onChange: () -> Unit) {
    SectionCard(title = "Default printer") {
        val p = state.defaultPrinter
        if (p == null) {
            Text(
                if (state.printers.isEmpty()) "No printer set up yet." else "No default printer selected.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = if (state.printers.isEmpty()) onAdd else onChange) {
                Text(if (state.printers.isEmpty()) "Add a printer" else "Choose default")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(p.connectionType.icon(), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                    Text("${p.connectionType.label} • ${p.paperWidth.mm} mm • ${state.mode.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(state.connection)
            }
            (state.connection as? ConnectionState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.connection == ConnectionState.Connected) {
                    OutlinedButton(onClick = { vm.disconnect(p) }) { Text("Disconnect") }
                } else {
                    Button(onClick = { vm.connect(p) }, enabled = state.connection != ConnectionState.Connecting) { Text("Connect") }
                }
                OutlinedButton(onClick = onChange) { Text("Change") }
                FilledTonalButton(onClick = { vm.testReceipt() }) { Text("Test receipt") }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun JobRow(job: PrintJob, onRetry: ((PrintJob) -> Unit)?) {
    val (icon, tint) = when (job.status) {
        JobStatus.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF2E9E5B)
        JobStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        JobStatus.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.outline
        JobStatus.PENDING -> Icons.Default.Schedule to Color(0xFFE0A100)
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = job.status.name, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    "${job.type.label} • ${job.printerName} • ${job.connectionType?.label ?: "-"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(job.timestamp)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.status == JobStatus.FAILED && job.errorMessage != null) {
                    Text(job.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
            if (onRetry != null && (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED)) {
                IconButton(onClick = { onRetry(job) }) { Icon(Icons.Default.Replay, contentDescription = "Retry") }
            }
        }
    }
}
