package nexora.printer.domain.model

import nexora.printer.core.escpos.Align
import nexora.printer.core.escpos.DitherMode
import java.nio.charset.Charset

enum class ConnectionType(val label: String) {
    BLUETOOTH("Bluetooth"),
    USB("USB"),
    WIFI("Wi-Fi / LAN")
}

enum class PaperWidth(val mm: Int, val defaultDots: Int) {
    MM58(58, 384),
    MM80(80, 576);

    companion object {
        fun fromMm(mm: Int) = entries.firstOrNull { it.mm == mm } ?: MM58
    }
}

enum class DefaultPrinterMode(val label: String, val description: String) {
    ALWAYS_DEFAULT("Always use default", "Always print to the printer marked as default."),
    LAST_USED("Last used printer", "Print to the printer you most recently used."),
    ASK_EVERY_TIME("Ask every time", "Choose a printer before each print job."),
    CONNECTION_PRIORITY("Connection priority", "Prefer printers by connection type, in the order you set.")
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class JobType(val label: String) {
    TEXT("Text"), IMAGE("Image"), PDF("PDF"), QR("QR code"), BARCODE("Barcode"), TEST("Test")
}

enum class JobStatus { PENDING, SUCCESS, FAILED, CANCELLED }

data class PrinterSettings(
    val encoding: String = "IBM437",
    val codePage: Int = 0,
    val density: Int = 0,
    val leftMargin: Int = 0,
    val rightMargin: Int = 0,
    val autoCut: Boolean = true,
    val feedLines: Int = 3,
    val ditheringMode: DitherMode = DitherMode.FLOYD_STEINBERG,
    val defaultAlignment: Align = Align.LEFT,
    val connectionTimeoutMs: Int = 10_000,
    val autoReconnect: Boolean = true
)

data class PrinterProfile(
    val id: Long,
    val name: String,
    val connectionType: ConnectionType,
    /** Bluetooth MAC, USB "vendorId:productId" (decimal) or Wi-Fi "host:port". */
    val address: String,
    val paperWidth: PaperWidth,
    val printableWidth: Int,
    val isDefault: Boolean,
    val createdAt: Long,
    val settings: PrinterSettings = PrinterSettings()
) {
    /** Dots usable for raster output and text layout, rounded down to a multiple of 8. */
    val usableDots: Int
        get() = ((printableWidth - settings.leftMargin - settings.rightMargin).coerceAtLeast(64) / 8) * 8

    val charset: Charset
        get() = runCatching { Charset.forName(settings.encoding) }.getOrDefault(Charsets.ISO_8859_1)

    fun withPaper(p: PaperWidth) = copy(paperWidth = p, printableWidth = p.defaultDots)
}

data class PrintJob(
    val id: Long,
    val printerId: Long?,
    val name: String,
    val printerName: String,
    val connectionType: ConnectionType?,
    val type: JobType,
    val timestamp: Long,
    val status: JobStatus,
    val errorMessage: String?
)

data class AppPreferences(
    val defaultPrinterId: Long? = null,
    val mode: DefaultPrinterMode = DefaultPrinterMode.ALWAYS_DEFAULT,
    val lastUsedPrinterId: Long? = null,
    val autoConnect: Boolean = true,
    val autoReconnect: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultCopies: Int = 1,
    val defaultPaperWidth: PaperWidth = PaperWidth.MM58,
    val connectionPriority: List<ConnectionType> =
        listOf(ConnectionType.USB, ConnectionType.WIFI, ConnectionType.BLUETOOTH)
)
