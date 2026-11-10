package nexora.printer.domain.usecase

import nexora.printer.domain.model.ConnectionType
import nexora.printer.domain.model.DefaultPrinterMode
import nexora.printer.domain.model.PrinterProfile

sealed interface Resolution {
    data class Use(val printer: PrinterProfile) : Resolution
    data class AskUser(val candidates: List<PrinterProfile>) : Resolution
    data object NoPrinters : Resolution
}

/**
 * Decides which printer a job goes to. Never substitutes another printer silently:
 * if the preferred printer cannot be determined, the user is asked.
 * Connection failures are handled later, at send time, and are reported - not rerouted.
 */
object PrinterResolver {
    fun resolve(
        mode: DefaultPrinterMode,
        defaultId: Long?,
        lastUsedId: Long?,
        priority: List<ConnectionType>,
        printers: List<PrinterProfile>
    ): Resolution {
        if (printers.isEmpty()) return Resolution.NoPrinters
        val default = printers.firstOrNull { it.id == defaultId }
        return when (mode) {
            DefaultPrinterMode.ALWAYS_DEFAULT ->
                default?.let { Resolution.Use(it) } ?: Resolution.AskUser(printers)

            DefaultPrinterMode.LAST_USED ->
                (printers.firstOrNull { it.id == lastUsedId } ?: default)
                    ?.let { Resolution.Use(it) } ?: Resolution.AskUser(printers)

            DefaultPrinterMode.ASK_EVERY_TIME -> Resolution.AskUser(printers)

            DefaultPrinterMode.CONNECTION_PRIORITY -> {
                fun rank(t: ConnectionType) = priority.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it }
                val ordered = printers.sortedWith(
                    compareBy<PrinterProfile>(
                        { rank(it.connectionType) },
                        { if (it.id == defaultId) 0 else 1 },
                        { it.createdAt }
                    )
                )
                Resolution.Use(ordered.first())
            }
        }
    }
}
