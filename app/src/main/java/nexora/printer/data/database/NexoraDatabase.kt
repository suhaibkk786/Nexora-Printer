package nexora.printer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PrinterEntity::class, PrinterSettingsEntity::class, PrintJobEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun printerDao(): PrinterDao
    abstract fun printJobDao(): PrintJobDao
}
