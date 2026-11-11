package nexora.printer.service

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import nexora.printer.core.escpos.Align
import nexora.printer.core.escpos.DitherMode
import nexora.printer.core.escpos.RasterImage
import nexora.printer.core.printer.JobRenderer
import nexora.printer.core.utils.BitmapOps
import nexora.printer.core.utils.PdfDocumentSource
import nexora.printer.domain.model.JobType
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintOutcome
import nexora.printer.domain.usecase.PrintRequest
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Exposes saved printers to the Android print framework (system print dialog, browsers, document apps).
 * Jobs arrive as PDF, are rendered to raster, and go through the same dispatcher as in-app printing.
 */
@AndroidEntryPoint
class NexoraPrintService : PrintService() {
    @Inject lateinit var printerRepo: PrinterRepository
    @Inject lateinit var dispatcher: PrintDispatcher
    @Inject lateinit var renderer: JobRenderer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = ConcurrentHashMap<String, Job>()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = object : PrinterDiscoverySession() {
        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) { publish() }
        override fun onStopPrinterDiscovery() {}
        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) { publish() }
        override fun onStartPrinterStateTracking(printerId: PrinterId) { publish() }
        override fun onStopPrinterStateTracking(printerId: PrinterId) {}
        override fun onDestroy() {}

        private fun publish() {
            scope.launch {
                val list = printerRepo.observePrinters().first()
                addPrinters(list.map { info(it) })
            }
        }
    }

    private fun info(p: PrinterProfile): PrinterInfo {
        val id = generatePrinterId(p.id.toString())
        val media = PrintAttributes.MediaSize("NEXORA_${p.paperWidth.mm}", "${p.paperWidth.mm} mm roll", (p.paperWidth.mm / 25.4 * 1000).toInt(), 11_000)
        val caps = PrinterCapabilitiesInfo.Builder(id)
            .addMediaSize(media, true)
            .addResolution(PrintAttributes.Resolution("203dpi", "203 dpi", 203, 203), true)
            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        return PrinterInfo.Builder(id, p.name, PrinterInfo.STATUS_IDLE).setCapabilities(caps).build()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        running.remove(printJob.id.toString())?.cancel()
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        val key = printJob.id.toString()
        running[key] = scope.launch {
            try {
                printJob.start()
                val printerId = printJob.info.printerId?.localId?.toLongOrNull()
                val tmp = withContext(Dispatchers.IO) { copyDocument(printJob) }
                if (tmp == null) { printJob.fail("No document data"); return@launch }
                try {
                    val name = printJob.info.label ?: "Document"
                    val request = PrintRequest(name, JobType.PDF, printJob.info.copies.coerceAtLeast(1)) { p ->
                        val rasters = mutableListOf<RasterImage>()
                        val src = PdfDocumentSource {
                            android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                        val n = src.pageCount()
                        src.forEachPage((0 until n).toList(), p.usableDots) { _, _, bmp ->
                            rasters += BitmapOps.toRaster(bmp, p.usableDots, DitherMode.THRESHOLD, 128, Align.LEFT, keepSize = true)
                        }
                        renderer.renderRasters(p, rasters, cutBetween = false)
                    }
                    when (val outcome = dispatcher.print(request, printerId)) {
                        is PrintOutcome.Success -> printJob.complete()
                        is PrintOutcome.Failed -> printJob.fail(outcome.message)
                        is PrintOutcome.NoPrinter -> printJob.fail(outcome.reason)
                        is PrintOutcome.ChoosePrinter -> printJob.fail("Select a Nexora printer in the print dialog.")
                    }
                } finally {
                    tmp.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printJob.fail(e.message ?: "Print failed")
            } finally {
                running.remove(key)
            }
        }
    }

    private fun copyDocument(job: PrintJob): File? {
        val fd = job.document.data ?: return null
        val out = File.createTempFile("job", ".pdf", cacheDir)
        FileInputStream(fd.fileDescriptor).use { input -> out.outputStream().use { input.copyTo(it) } }
        fd.close()
        return out
    }
}
