package nexora.printer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Transaction
    @Query("SELECT * FROM printers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PrinterWithSettings>>

    @Transaction
    @Query("SELECT * FROM printers WHERE id = :id")
    suspend fun getWithSettings(id: Long): PrinterWithSettings?

    @Insert
    suspend fun insertPrinter(printer: PrinterEntity): Long

    @Update
    suspend fun updatePrinter(printer: PrinterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: PrinterSettingsEntity)

    @Query("UPDATE printers SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM printers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE printers SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE printers SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: Long)
}

@Dao
interface PrintJobDao {
    /** Payload is excluded from the list query to stay clear of the SQLite cursor window limit. */
    @Query(
        "SELECT id, printerId, name, printerName, connectionType, jobType, timestamp, status, errorMessage, " +
            "NULL AS payload FROM print_jobs ORDER BY timestamp DESC LIMIT 300"
    )
    fun observeAll(): Flow<List<PrintJobEntity>>

    @Insert
    suspend fun insert(job: PrintJobEntity): Long

    @Query("UPDATE print_jobs SET status = :status, errorMessage = :error, payload = :payload WHERE id = :id")
    suspend fun update(id: Long, status: String, error: String?, payload: ByteArray?)

    @Query(
        "SELECT id, printerId, name, printerName, connectionType, jobType, timestamp, status, errorMessage, " +
            "NULL AS payload FROM print_jobs WHERE id = :id"
    )
    suspend fun get(id: Long): PrintJobEntity?

    @Query("SELECT payload FROM print_jobs WHERE id = :id")
    suspend fun getPayload(id: Long): ByteArray?

    @Query("DELETE FROM print_jobs")
    suspend fun clear()
}
