package nexora.printer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nexora.printer.data.database.*
import nexora.printer.data.repository.toDomain
import nexora.printer.data.repository.toEntity
import nexora.printer.data.repository.toSettingsEntity
import nexora.printer.domain.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a device/emulator: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var db: NexoraDatabase

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, NexoraDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test fun packageNameIsNexoraPrinter() {
        assertEquals("nexora.printer", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

    @Test fun printerAndSettingsRoundTrip() = runBlocking {
        val p = PrinterProfile(0, "Kitchen", ConnectionType.WIFI, "192.168.1.9:9100", PaperWidth.MM80, 576, false, 1L,
            PrinterSettings(encoding = "windows-1256", codePage = 50, density = 4, leftMargin = 8, autoCut = false))
        val id = db.printerDao().insertPrinter(p.toEntity())
        db.printerDao().upsertSettings(p.toSettingsEntity(id))
        val loaded = db.printerDao().getWithSettings(id)!!.toDomain()
        assertEquals("Kitchen", loaded.name)
        assertEquals(ConnectionType.WIFI, loaded.connectionType)
        assertEquals(50, loaded.settings.codePage)
        assertFalse(loaded.settings.autoCut)
    }

    @Test fun deletingPrinterCascadesSettings() = runBlocking {
        val p = PrinterProfile(0, "A", ConnectionType.USB, "1:2", PaperWidth.MM58, 384, false, 1L)
        val id = db.printerDao().insertPrinter(p.toEntity())
        db.printerDao().upsertSettings(p.toSettingsEntity(id))
        db.printerDao().delete(id)
        assertTrue(db.printerDao().observeAll().first().isEmpty())
    }

    @Test fun failedJobKeepsPayloadButListOmitsIt() = runBlocking {
        val jobId = db.printJobDao().insert(PrintJobEntity(printerId = 1, name = "j", printerName = "P", connectionType = "USB",
            jobType = "TEXT", timestamp = 1, status = "PENDING", errorMessage = null))
        db.printJobDao().update(jobId, "FAILED", "offline", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), db.printJobDao().getPayload(jobId))
        val listed = db.printJobDao().observeAll().first().single()
        assertEquals("FAILED", listed.status)
        assertNull(listed.payload)
    }
}
