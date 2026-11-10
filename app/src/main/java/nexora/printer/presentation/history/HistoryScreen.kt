package nexora.printer.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nexora.printer.domain.model.PrintJob
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.domain.repository.PrintJobRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.presentation.dashboard.JobRow
import nexora.printer.presentation.print.PrintSession
import nexora.printer.presentation.print.PrintStatusHost
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val jobs: PrintJobRepository,
    printers: PrinterRepository,
    dispatcher: PrintDispatcher
) : ViewModel() {
    val session = PrintSession(dispatcher, viewModelScope)
    val items: StateFlow<List<PrintJob>> = jobs.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val printers: StateFlow<List<PrinterProfile>> = printers.observePrinters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(job: PrintJob) = session.startRetryOfJob(job.id)
    fun clear() { viewModelScope.launch { jobs.clear() } }
}

@Composable
fun HistoryScreen(onOpenPrinters: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val jobs by vm.items.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val status by vm.session.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Print history", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { confirmClear = true }, enabled = jobs.isNotEmpty()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                }
            }
            if (jobs.isEmpty()) {
                Text("Nothing printed yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jobs, key = { it.id }) { JobRow(it, onRetry = vm::retry) }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Failed jobs kept for retry will be deleted too.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    PrintStatusHost(status, vm.session, printers, snackbar, onOpenPrinters)
}
