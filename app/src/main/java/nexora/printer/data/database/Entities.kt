package nexora.printer.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val connectionType: String,
    val paperWidth: Int,
    val printableWidth: Int,
    val isDefault: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "printer_settings",
    foreignKeys = [ForeignKey(
        entity = PrinterEntity::class,
        parentColumns = ["id"],
        childColumns = ["printerId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PrinterSettingsEntity(
    @PrimaryKey val printerId: Long,
    val encoding: String,
    val codePage: Int,
    val density: Int,
    val leftMargin: Int,
    val rightMargin: Int,
    val autoCut: Boolean,
    val feedLines: Int,
    val ditheringMode: String,
    val defaultAlignment: String,
    val connectionTimeoutMs: Int,
    val autoReconnect: Boolean
)

/** History survives printer deletion, so printerId is intentionally not a foreign key. */
@Entity(tableName = "print_jobs", indices = [Index("timestamp")])
data class PrintJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val printerId: Long?,
    val name: String,
    val printerName: String,
    val connectionType: String?,
    val jobType: String,
    val timestamp: Long,
    val status: String,
    val errorMessage: String?,
    val payload: ByteArray? = null
)

data class PrinterWithSettings(
    @Embedded val printer: PrinterEntity,
    @Relation(parentColumn = "id", entityColumn = "printerId") val settings: PrinterSettingsEntity?
)
