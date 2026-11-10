package nexora.printer.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import nexora.printer.data.database.NexoraDatabase
import nexora.printer.data.database.PrinterDao
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrinterRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterRepositoryImpl @Inject constructor(
    private val db: NexoraDatabase,
    private val dao: PrinterDao,
    private val prefs: PreferencesRepository
) : PrinterRepository {

    /** DataStore holds the authoritative default printer id; isDefault is derived from it. */
    override fun observePrinters(): Flow<List<PrinterProfile>> =
        combine(dao.observeAll(), prefs.preferences) { list, p ->
            list.map { it.toDomain().copy(isDefault = it.printer.id == p.defaultPrinterId) }
        }

    override suspend fun getPrinter(id: Long): PrinterProfile? =
        observePrinters().first().firstOrNull { it.id == id }

    override suspend fun addPrinter(profile: PrinterProfile): Long {
        val id = db.withTransaction {
            val newId = dao.insertPrinter(profile.toEntity().copy(id = 0, isDefault = false))
            dao.upsertSettings(profile.toSettingsEntity(newId))
            newId
        }
        if (prefs.preferences.first().defaultPrinterId == null) setDefault(id)
        return id
    }

    override suspend fun updateProfile(profile: PrinterProfile) {
        db.withTransaction {
            dao.updatePrinter(profile.toEntity())
            dao.upsertSettings(profile.toSettingsEntity())
        }
    }

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim().ifBlank { "Printer" })

    override suspend fun delete(id: Long) {
        dao.delete(id)
        val p = prefs.preferences.first()
        if (p.defaultPrinterId == id) prefs.setDefaultPrinterId(null)
        if (p.lastUsedPrinterId == id) prefs.setLastUsedPrinterId(null)
    }

    override suspend fun setDefault(id: Long) {
        db.withTransaction {
            dao.clearDefault()
            dao.markDefault(id)
        }
        prefs.setDefaultPrinterId(id)
    }
}
