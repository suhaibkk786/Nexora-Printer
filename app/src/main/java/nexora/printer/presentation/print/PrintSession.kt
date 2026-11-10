package nexora.printer.presentation.print

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintOutcome
import nexora.printer.domain.usecase.PrintRequest

sealed interface PrintStatus {
    data object Idle : PrintStatus
    data class Printing(val label: String) : PrintStatus
    data class Success(val message: String) : PrintStatus
    data class ChoosePrinter(val candidates: List<PrinterProfile>) : PrintStatus
    data class Error(val message: String, val printerName: String?, val canRetry: Boolean) : PrintStatus
}

/** Drives one print flow for a screen: print, ask for a printer, fail visibly, retry, cancel. */
class PrintSession(
    private val dispatcher: PrintDispatcher,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow<PrintStatus>(PrintStatus.Idle)
    val status: StateFlow<PrintStatus> = _status.asStateFlow()

    private var action: (suspend (Long?) -> PrintOutcome)? = null
    private var label = "Printing…"
    private var failedJobId: Long? = null
    private var job: Job? = null

    fun start(request: PrintRequest, printerId: Long? = null) {
        label = "Printing ${request.name}…"
        failedJobId = null
        action = { id -> dispatcher.print(request, id) }
        execute(printerId)
    }

    fun startRetryOfJob(jobId: Long) {
        label = "Retrying job…"
        failedJobId = jobId
        action = { id -> dispatcher.retry(jobId, id) }
        execute(null)
    }

    /** Continues after the user picked a printer (or chose another printer after a failure). */
    fun choose(printerId: Long) {
        val jid = failedJobId
        if (jid != null) action = { id -> dispatcher.retry(jid, id) }
        execute(printerId)
    }

    /** Retries the same printer after a failure; this reconnects if the link dropped. */
    fun retry() {
        val jid = failedJobId
        if (jid != null) action = { id -> dispatcher.retry(jid, id) }
        execute(null)
    }

    fun cancel() {
        job?.cancel()
        _status.value = PrintStatus.Idle
    }

    fun dismiss() {
        if (_status.value !is PrintStatus.Printing) _status.value = PrintStatus.Idle
    }

    private fun execute(printerId: Long?) {
        val a = action ?: return
        job?.cancel()
        _status.value = PrintStatus.Printing(label)
        job = scope.launch {
            val outcome = try {
                a(printerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = PrintStatus.Error(e.message ?: "Unexpected error", null, false)
                return@launch
            }
            _status.value = when (outcome) {
                is PrintOutcome.Success -> {
                    failedJobId = null
                    PrintStatus.Success("Sent to ${outcome.printerName}")
                }
                is PrintOutcome.ChoosePrinter -> PrintStatus.ChoosePrinter(outcome.candidates)
                is PrintOutcome.NoPrinter -> PrintStatus.Error(outcome.reason, null, false)
                is PrintOutcome.Failed -> {
                    failedJobId = outcome.jobId
                    PrintStatus.Error(outcome.message, outcome.printer?.name, true)
                }
            }
        }
    }
}
