package nexora.printer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nexoraDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexora_prefs")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : PreferencesRepository {

    private val store = context.nexoraDataStore

    private object Keys {
        val DEFAULT_ID = longPreferencesKey("default_printer_id")
        val MODE = stringPreferencesKey("default_printer_mode")
        val LAST_USED = longPreferencesKey("last_used_printer_id")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val THEME = stringPreferencesKey("theme")
        val COPIES = intPreferencesKey("default_copies")
        val PAPER = intPreferencesKey("default_paper_mm")
        val PRIORITY = stringPreferencesKey("connection_priority")
        val PRINT_LOGO = booleanPreferencesKey("print_logo")
        val PRINT_LOGO_POS = booleanPreferencesKey("print_logo_pos")
        val PRINT_TIME = booleanPreferencesKey("print_time")
    }

    override val preferences: Flow<AppPreferences> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val defaults = AppPreferences()
            AppPreferences(
                defaultPrinterId = p[Keys.DEFAULT_ID],
                mode = p[Keys.MODE]?.let { n -> DefaultPrinterMode.entries.firstOrNull { it.name == n } }
                    ?: defaults.mode,
                lastUsedPrinterId = p[Keys.LAST_USED],
                autoConnect = p[Keys.AUTO_CONNECT] ?: defaults.autoConnect,
                autoReconnect = p[Keys.AUTO_RECONNECT] ?: defaults.autoReconnect,
                theme = p[Keys.THEME]?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: defaults.theme,
                defaultCopies = p[Keys.COPIES] ?: defaults.defaultCopies,
                defaultPaperWidth = p[Keys.PAPER]?.let { PaperWidth.fromMm(it) } ?: defaults.defaultPaperWidth,
                printLogo = p[Keys.PRINT_LOGO] ?: defaults.printLogo,
                printTime = p[Keys.PRINT_TIME] ?: defaults.printTime,
                printLogoOnPosTickets = p[Keys.PRINT_LOGO_POS] ?: defaults.printLogoOnPosTickets,
                connectionPriority = parsePriority(p[Keys.PRIORITY]) ?: defaults.connectionPriority
            )
        }

    private fun parsePriority(csv: String?): List<ConnectionType>? {
        if (csv.isNullOrBlank()) return null
        val parsed = csv.split(',').mapNotNull { n -> ConnectionType.entries.firstOrNull { it.name == n } }
        return parsed.takeIf { it.isNotEmpty() }
    }

    override suspend fun setDefaultPrinterId(id: Long?) {
        store.edit { if (id == null) it.remove(Keys.DEFAULT_ID) else it[Keys.DEFAULT_ID] = id }
    }

    override suspend fun setMode(mode: DefaultPrinterMode) {
        store.edit { it[Keys.MODE] = mode.name }
    }

    override suspend fun setLastUsedPrinterId(id: Long?) {
        store.edit { if (id == null) it.remove(Keys.LAST_USED) else it[Keys.LAST_USED] = id }
    }

    override suspend fun setAutoConnect(enabled: Boolean) {
        store.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        store.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setDefaultCopies(copies: Int) {
        store.edit { it[Keys.COPIES] = copies.coerceIn(1, 99) }
    }

    override suspend fun setDefaultPaperWidth(width: PaperWidth) {
        store.edit { it[Keys.PAPER] = width.mm }
    }

    override suspend fun setPrintLogoOnPosTickets(enabled: Boolean) {
        store.edit { it[Keys.PRINT_LOGO_POS] = enabled }
    }

    override suspend fun setPrintLogo(enabled: Boolean) {
        store.edit { it[Keys.PRINT_LOGO] = enabled }
    }

    override suspend fun setPrintTime(enabled: Boolean) {
        store.edit { it[Keys.PRINT_TIME] = enabled }
    }

    override suspend fun setConnectionPriority(order: List<ConnectionType>) {
        store.edit { it[Keys.PRIORITY] = order.joinToString(",") { t -> t.name } }
    }
}
