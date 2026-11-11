package nexora.printer.domain.repository

import kotlinx.coroutines.flow.Flow
import nexora.printer.domain.model.*

interface PrinterRepository {
    fun observePrinters(): Flow<List<PrinterProfile>>
    suspend fun getPrinter(id: Long): PrinterProfile?
    /** Saves a new printer; the first printer saved automatically becomes the default. */
    suspend fun addPrinter(profile: PrinterProfile): Long
    suspend fun updateProfile(profile: PrinterProfile)
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun setDefault(id: Long)
}

interface PrintJobRepository {
    fun observeJobs(): Flow<List<PrintJob>>
    suspend fun create(job: PrintJob): Long
    suspend fun update(id: Long, status: JobStatus, error: String?, payload: ByteArray?)
    suspend fun get(id: Long): PrintJob?
    suspend fun getPayload(id: Long): ByteArray?
    suspend fun clear()
}

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun setDefaultPrinterId(id: Long?)
    suspend fun setMode(mode: DefaultPrinterMode)
    suspend fun setLastUsedPrinterId(id: Long?)
    suspend fun setAutoConnect(enabled: Boolean)
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setDefaultCopies(copies: Int)
    suspend fun setDefaultPaperWidth(width: PaperWidth)
    suspend fun setPrintLogo(enabled: Boolean)
    suspend fun setPrintTime(enabled: Boolean)
    suspend fun setShowPrintWindow(enabled: Boolean)
    suspend fun setShowStartLogo(enabled: Boolean)
    suspend fun setConnectionPriority(order: List<ConnectionType>)
}
