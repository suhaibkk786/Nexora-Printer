package nexora.printer.presentation.print

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.presentation.preview.PrinterPickerDialog

/**
 * Renders the visible side of a [PrintSession]: progress with cancel, ask-every-time picker,
 * a clear failure dialog (reconnect/retry, choose another printer) and a success snackbar.
 */
@Composable
fun PrintStatusHost(
    status: PrintStatus,
    session: PrintSession,
    printers: List<PrinterProfile>,
    snackbar: SnackbarHostState,
    onManagePrinters: () -> Unit
) {
    var pickAnother by remember { mutableStateOf(false) }

    when (status) {
        PrintStatus.Idle -> Unit
        is PrintStatus.Printing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Printing") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(status.label)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { session.cancel() }) { Text("Cancel") } }
        )
        is PrintStatus.Success -> LaunchedEffect(status) {
            snackbar.showSnackbar(status.message)
            session.dismiss()
        }
        is PrintStatus.ChoosePrinter -> PrinterPickerDialog(
            title = "Choose a printer",
            printers = status.candidates,
            onPick = { session.choose(it.id) },
            onDismiss = { session.dismiss() }
        )
        is PrintStatus.Error -> {
            if (pickAnother) {
                PrinterPickerDialog(
                    title = "Print on another printer",
                    printers = printers,
                    onPick = { pickAnother = false; session.choose(it.id) },
                    onDismiss = { pickAnother = false }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { session.dismiss() },
                    title = { Text(if (status.printerName != null) "Could not print on ${status.printerName}" else "Cannot print") },
                    text = {
                        Text(
                            status.message + if (status.canRetry) {
                                "\n\nYour job is saved. Nothing was sent to any other printer."
                            } else ""
                        )
                    },
                    confirmButton = {
                        if (status.canRetry) {
                            TextButton(onClick = { session.retry() }) { Text("Reconnect & retry") }
                        } else {
                            TextButton(onClick = { session.dismiss(); onManagePrinters() }) { Text("Printers") }
                        }
                    },
                    dismissButton = {
                        Row {
                            if (status.canRetry) {
                                TextButton(onClick = { pickAnother = true }) { Text("Other printer") }
                            }
                            TextButton(onClick = { session.dismiss() }) { Text("Dismiss") }
                        }
                    }
                )
            }
        }
    }
}
