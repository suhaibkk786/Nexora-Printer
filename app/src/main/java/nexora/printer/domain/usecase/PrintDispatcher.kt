package nexora.printer.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nexora.printer.core.printer.JobHeader
import nexora.printer.core.printer.PrinterConnector
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrintJobRepository
import nexora.printer.domain.repository.PrinterRepository
import javax.inject.Inject
import javax.inject.Singleton

class PrintRequest(
    val name: String,
    val type: JobType,
    val copies: Int = 1,
    /** Builds the ESC/POS bytes for one copy, for the printer the job is routed to. */
    val render: suspend (PrinterProfile) -> ByteArray,
) {
    /** Ready-made printer bytes (e.g. from a POS): sent as they are, without the logo / time header. */
    var raw: Boolean = false
}

sealed interface PrintOutcome {
    data class Success(val jobId: Long, val printerName: String) : PrintOutcome
    data class ChoosePrinter(val candidates: List<PrinterProfile>) : PrintOutcome
    data class NoPrinter(val reason: String) : PrintOutcome
    data class Failed(val jobId: Long, val printer: PrinterProfile?, val message: String) : PrintOutcome
}

@Singleton
class PrintDispatcher @Inject constructor(
    private val printers: PrinterRepository,
    private val prefs: PreferencesRepository,
    private val jobs: PrintJobRepository,
    private val connector: PrinterConnector,
    private val header: JobHeader,
    /** Keeps a picture of the bills in the phone's Gallery (Settings → where bills go). */
    private val gallery: nexora.printer.core.utils.BillGallery? = null
) {

    suspend fun print(req: PrintRequest, explicitPrinterId: Long? = null): PrintOutcome {
        val all = printers.observePrinters().first()
        val p = prefs.preferences.first()

        // "Gallery only": the bill is not printed, it is saved as a picture (a printer is still needed once, for the paper size)
        if (gallery?.galleryOnly == true) {
            val target = explicitPrinterId?.let { id -> all.firstOrNull { it.id == id } }
                ?: all.firstOrNull { it.id == p.defaultPrinterId } ?: all.firstOrNull()
                ?: return PrintOutcome.NoPrinter("Add a printer once: its paper size is used for the picture.")
            val bytes = if (req.raw) req.render(target) else header.build(target, p.printLogo, p.printTime) + req.render(target)
            val err = gallery.saveNow(bytes, target)
            return if (err == null) PrintOutcome.Success(0, "Gallery") else PrintOutcome.Failed(0, target, "Could not save to the Gallery: $err")
        }

        val printer: PrinterProfile = if (explicitPrinterId != null) {
            all.firstOrNull { it.id == explicitPrinterId }
                ?: return PrintOutcome.NoPrinter("The selected printer no longer exists.")
        } else {
            when (val r = PrinterResolver.resolve(p.mode, p.defaultPrinterId, p.lastUsedPrinterId, p.connectionPriority, all)) {
                is Resolution.Use -> r.printer
                is Resolution.AskUser -> return PrintOutcome.ChoosePrinter(r.candidates)
                Resolution.NoPrinters -> return PrintOutcome.NoPrinter("No printers saved yet. Add a printer first.")
            }
        }

        val jobId = jobs.create(
            PrintJob(0, printer.id, req.name, printer.name, printer.connectionType, req.type,
                System.currentTimeMillis(), JobStatus.PENDING, null)
        )
        return run(jobId, printer, p.autoReconnect, req.copies.coerceIn(1, 99)) {
            // a ticket from another app prints exactly as that app made it
            val bytes = if (req.raw) req.render(printer) else header.build(printer, p.printLogo, p.printTime) + req.render(printer)
            gallery?.saveCopy(bytes, printer)       // "Printer and Gallery": a picture of the bill is kept too
            bytes
        }
    }

    /**
     * A ticket made by another system (a POS sends ESC/POS bytes). It goes to the default printer - never to a picker, because
     * nobody is at the screen: with "Ask every time" the default printer (or the first one) is used.
     */
    suspend fun printRaw(name: String, bytes: ByteArray): PrintOutcome {
        fun request() = PrintRequest(name, JobType.RAW, 1) { bytes }.also { it.raw = true }
        val outcome = print(request())
        if (outcome !is PrintOutcome.ChoosePrinter) return outcome
        val defaultId = prefs.preferences.first().defaultPrinterId
        val target = outcome.candidates.firstOrNull { it.id == defaultId } ?: outcome.candidates.firstOrNull()
            ?: return PrintOutcome.NoPrinter("No printers saved yet. Add a printer first.")
        return print(request(), target.id)
    }

    /**
     * Re-sends the preserved bytes of a failed/cancelled job. By default to the same printer;
     * a different printer is used only when the caller explicitly passes its id.
     */
    suspend fun retry(jobId: Long, printerId: Long? = null): PrintOutcome {
        val job = jobs.get(jobId) ?: return PrintOutcome.NoPrinter("Job not found.")
        val payload = jobs.getPayload(jobId)
            ?: return PrintOutcome.NoPrinter("This job cannot be retried: its data was not preserved.")
        val all = printers.observePrinters().first()
        val target = printerId ?: job.printerId
        val printer = all.firstOrNull { it.id == target }
            ?: return if (printerId == null) PrintOutcome.ChoosePrinter(all)
            else PrintOutcome.NoPrinter("The selected printer no longer exists.")
        val p = prefs.preferences.first()

        val effectiveJobId = if (printer.id == job.printerId) {
            jobs.update(jobId, JobStatus.PENDING, null, payload)
            jobId
        } else {
            jobs.create(
                PrintJob(0, printer.id, job.name, printer.name, printer.connectionType, job.type,
                    System.currentTimeMillis(), JobStatus.PENDING, null)
            )
        }
        return run(effectiveJobId, printer, p.autoReconnect, 1) { payload }
    }

    private suspend fun run(
        jobId: Long,
        printer: PrinterProfile,
        globalAutoReconnect: Boolean,
        copies: Int,
        bytes: suspend () -> ByteArray
    ): PrintOutcome {
        try {
            val single = withContext(Dispatchers.Default) { bytes() }
            val data = if (copies == 1) single else ByteArray(single.size * copies).also { out ->
                repeat(copies) { single.copyInto(out, it * single.size) }
            }
            val target = printer.copy(
                settings = printer.settings.copy(autoReconnect = printer.settings.autoReconnect && globalAutoReconnect)
            )
            val result = connector.send(target, data)
            return if (result.isSuccess) {
                jobs.update(jobId, JobStatus.SUCCESS, null, null)
                prefs.setLastUsedPrinterId(printer.id)
                PrintOutcome.Success(jobId, printer.name)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown printing error"
                jobs.update(jobId, JobStatus.FAILED, msg, data.takeIf { it.size <= MAX_RETRY_PAYLOAD })
                PrintOutcome.Failed(jobId, printer, msg)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { jobs.update(jobId, JobStatus.CANCELLED, "Cancelled", null) }
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Could not prepare print data"
            jobs.update(jobId, JobStatus.FAILED, msg, null)
            return PrintOutcome.Failed(jobId, printer, msg)
        }
    }

    companion object {
        /** Larger payloads are not stored (SQLite cursor window limit); such jobs cannot be retried. */
        const val MAX_RETRY_PAYLOAD = 1_500_000
    }
}
