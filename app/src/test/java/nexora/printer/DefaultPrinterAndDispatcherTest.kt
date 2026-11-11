package nexora.printer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import nexora.printer.core.printer.JobHeader
import nexora.printer.core.printer.PrinterConnector
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrintJobRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

private fun printer(id: Long, type: ConnectionType = ConnectionType.BLUETOOTH, created: Long = id) =
    PrinterProfile(id, "P$id", type, "addr$id", PaperWidth.MM58, 384, false, created)

class PrinterResolverTest {
    private val list = listOf(printer(1), printer(2, ConnectionType.USB), printer(3, ConnectionType.WIFI))

    @Test fun alwaysDefaultUsesDefault() {
        val r = PrinterResolver.resolve(DefaultPrinterMode.ALWAYS_DEFAULT, 2, 3, emptyList(), list)
        assertEquals(2L, (r as Resolution.Use).printer.id)
    }

    @Test fun alwaysDefaultAsksWhenDefaultMissingInsteadOfPickingAnother() {
        val r = PrinterResolver.resolve(DefaultPrinterMode.ALWAYS_DEFAULT, 99, null, emptyList(), list)
        assertTrue(r is Resolution.AskUser)
    }

    @Test fun lastUsedPrefersLastThenDefault() {
        assertEquals(3L, (PrinterResolver.resolve(DefaultPrinterMode.LAST_USED, 1, 3, emptyList(), list) as Resolution.Use).printer.id)
        assertEquals(1L, (PrinterResolver.resolve(DefaultPrinterMode.LAST_USED, 1, null, emptyList(), list) as Resolution.Use).printer.id)
        assertTrue(PrinterResolver.resolve(DefaultPrinterMode.LAST_USED, null, null, emptyList(), list) is Resolution.AskUser)
    }

    @Test fun askEveryTimeAlwaysAsks() {
        assertTrue(PrinterResolver.resolve(DefaultPrinterMode.ASK_EVERY_TIME, 1, 1, emptyList(), list) is Resolution.AskUser)
    }

    @Test fun connectionPriorityOrdersByType() {
        val r = PrinterResolver.resolve(DefaultPrinterMode.CONNECTION_PRIORITY, 1, null,
            listOf(ConnectionType.WIFI, ConnectionType.USB, ConnectionType.BLUETOOTH), list)
        assertEquals(3L, (r as Resolution.Use).printer.id)
    }

    @Test fun noPrinters() {
        assertEquals(Resolution.NoPrinters, PrinterResolver.resolve(DefaultPrinterMode.ALWAYS_DEFAULT, null, null, emptyList(), emptyList()))
    }
}

private class FakePrinters(list: List<PrinterProfile>) : PrinterRepository {
    val flow = MutableStateFlow(list)
    override fun observePrinters(): Flow<List<PrinterProfile>> = flow
    override suspend fun getPrinter(id: Long) = flow.value.firstOrNull { it.id == id }
    override suspend fun addPrinter(profile: PrinterProfile) = 0L
    override suspend fun updateProfile(profile: PrinterProfile) {}
    override suspend fun rename(id: Long, name: String) {}
    override suspend fun delete(id: Long) {}
    override suspend fun setDefault(id: Long) {}
}

private class FakePrefs(initial: AppPreferences) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<AppPreferences> = state
    override suspend fun setDefaultPrinterId(id: Long?) { state.value = state.value.copy(defaultPrinterId = id) }
    override suspend fun setMode(mode: DefaultPrinterMode) { state.value = state.value.copy(mode = mode) }
    override suspend fun setLastUsedPrinterId(id: Long?) { state.value = state.value.copy(lastUsedPrinterId = id) }
    override suspend fun setAutoConnect(enabled: Boolean) {}
    override suspend fun setAutoReconnect(enabled: Boolean) {}
    override suspend fun setTheme(theme: ThemeMode) {}
    override suspend fun setDefaultCopies(copies: Int) {}
    override suspend fun setDefaultPaperWidth(width: PaperWidth) {}
    override suspend fun setPrintLogo(enabled: Boolean) { state.value = state.value.copy(printLogo = enabled) }
    override suspend fun setPrintTime(enabled: Boolean) { state.value = state.value.copy(printTime = enabled) }
    override suspend fun setPrintLogoOnPosTickets(enabled: Boolean) { state.value = state.value.copy(printLogoOnPosTickets = enabled) }
    override suspend fun setConnectionPriority(order: List<ConnectionType>) {}
}

private class FakeJobs : PrintJobRepository {
    val jobs = mutableMapOf<Long, PrintJob>()
    val payloads = mutableMapOf<Long, ByteArray?>()
    private var next = 1L
    override fun observeJobs(): Flow<List<PrintJob>> = MutableStateFlow(jobs.values.toList()).map { it }
    override suspend fun create(job: PrintJob): Long { val id = next++; jobs[id] = job.copy(id = id); return id }
    override suspend fun update(id: Long, status: JobStatus, error: String?, payload: ByteArray?) {
        jobs[id] = jobs.getValue(id).copy(status = status, errorMessage = error); payloads[id] = payload
    }
    override suspend fun get(id: Long) = jobs[id]
    override suspend fun getPayload(id: Long) = payloads[id]
    override suspend fun clear() { jobs.clear() }
}

private val NO_HEADER = JobHeader { _, _, _ -> ByteArray(0) }

private class FakeConnector(var fail: Boolean = false) : PrinterConnector {
    val sent = mutableListOf<Pair<Long, ByteArray>>()
    override suspend fun send(profile: PrinterProfile, data: ByteArray): Result<Unit> =
        if (fail) Result.failure(java.io.IOException("printer offline"))
        else { sent += profile.id to data; Result.success(Unit) }
}

class PrintDispatcherTest {
    private val printers = FakePrinters(listOf(printer(1), printer(2)))
    private fun dispatcher(prefs: FakePrefs, jobs: FakeJobs, conn: FakeConnector) = PrintDispatcher(printers, prefs, jobs, conn, NO_HEADER)
    private fun req(copies: Int = 1) = PrintRequest("job", JobType.TEXT, copies) { byteArrayOf(1, 2, 3) }

    @Test fun printsToDefaultAndRemembersLastUsed() = runTest {
        val prefs = FakePrefs(AppPreferences(defaultPrinterId = 1))
        val jobs = FakeJobs(); val conn = FakeConnector()
        val out = dispatcher(prefs, jobs, conn).print(req())
        assertTrue(out is PrintOutcome.Success)
        assertEquals(listOf(1L), conn.sent.map { it.first })
        assertEquals(JobStatus.SUCCESS, jobs.jobs.values.single().status)
        assertEquals(1L, prefs.state.value.lastUsedPrinterId)
    }

    @Test fun copiesAreRepeated() = runTest {
        val conn = FakeConnector()
        dispatcher(FakePrefs(AppPreferences(defaultPrinterId = 1)), FakeJobs(), conn).print(req(3))
        assertEquals(9, conn.sent.single().second.size)
    }

    @Test fun failureIsReportedPreservedAndNeverRoutedElsewhere() = runTest {
        val prefs = FakePrefs(AppPreferences(defaultPrinterId = 1))
        val jobs = FakeJobs(); val conn = FakeConnector(fail = true)
        val out = dispatcher(prefs, jobs, conn).print(req())
        assertTrue(out is PrintOutcome.Failed)
        assertTrue(conn.sent.isEmpty())
        val job = jobs.jobs.values.single()
        assertEquals(JobStatus.FAILED, job.status)
        assertEquals("printer offline", job.errorMessage)
        assertNotNull(jobs.payloads[job.id])
        assertNull(prefs.state.value.lastUsedPrinterId)
    }

    @Test fun retrySendsPreservedBytesToSamePrinter() = runTest {
        val prefs = FakePrefs(AppPreferences(defaultPrinterId = 1))
        val jobs = FakeJobs(); val conn = FakeConnector(fail = true)
        val d = dispatcher(prefs, jobs, conn)
        val failed = d.print(req()) as PrintOutcome.Failed
        conn.fail = false
        val out = d.retry(failed.jobId)
        assertTrue(out is PrintOutcome.Success)
        assertEquals(1L, conn.sent.single().first)
        assertArrayEquals(byteArrayOf(1, 2, 3), conn.sent.single().second)
        assertEquals(JobStatus.SUCCESS, jobs.jobs.getValue(failed.jobId).status)
    }

    @Test fun retryOnAnotherPrinterOnlyWhenExplicit() = runTest {
        val jobs = FakeJobs(); val conn = FakeConnector(fail = true)
        val d = dispatcher(FakePrefs(AppPreferences(defaultPrinterId = 1)), jobs, conn)
        val failed = d.print(req()) as PrintOutcome.Failed
        conn.fail = false
        d.retry(failed.jobId, printerId = 2)
        assertEquals(2L, conn.sent.single().first)
        assertEquals(JobStatus.FAILED, jobs.jobs.getValue(failed.jobId).status)
    }

    @Test fun askEveryTimeReturnsCandidatesWithoutCreatingAJob() = runTest {
        val jobs = FakeJobs()
        val out = dispatcher(FakePrefs(AppPreferences(mode = DefaultPrinterMode.ASK_EVERY_TIME)), jobs, FakeConnector()).print(req())
        assertTrue(out is PrintOutcome.ChoosePrinter)
        assertTrue(jobs.jobs.isEmpty())
    }

    @Test fun explicitPrinterOverridesMode() = runTest {
        val conn = FakeConnector()
        dispatcher(FakePrefs(AppPreferences(mode = DefaultPrinterMode.ASK_EVERY_TIME)), FakeJobs(), conn).print(req(), explicitPrinterId = 2)
        assertEquals(2L, conn.sent.single().first)
    }

    @Test fun headerIsPrependedOnlyWhenEnabled() = runTest {
        val header = JobHeader { _, logo, time -> if (logo || time) byteArrayOf(9, 9) else ByteArray(0) }
        val off = FakeConnector()
        PrintDispatcher(printers, FakePrefs(AppPreferences(defaultPrinterId = 1)), FakeJobs(), off, header).print(req())
        assertArrayEquals(byteArrayOf(1, 2, 3), off.sent.single().second)
        val on = FakeConnector()
        PrintDispatcher(printers, FakePrefs(AppPreferences(defaultPrinterId = 1, printLogo = true)), FakeJobs(), on, header).print(req())
        assertArrayEquals(byteArrayOf(9, 9, 1, 2, 3), on.sent.single().second)
    }

    @Test fun posTicketsGetTheLogoOnlyWhenThatSettingIsOn() = runTest {
        val header = JobHeader { _, logo, time -> if (logo || time) byteArrayOf(9, 9) else ByteArray(0) }
        val ticket = byteArrayOf(0x1B, 0x40, 5)
        val off = FakeConnector()
        // the general logo setting does not touch POS tickets
        PrintDispatcher(printers, FakePrefs(AppPreferences(defaultPrinterId = 1, printLogo = true, printTime = true)), FakeJobs(), off, header).printRaw("pos", ticket)
        assertArrayEquals(ticket, off.sent.single().second)
        val on = FakeConnector()
        PrintDispatcher(printers, FakePrefs(AppPreferences(defaultPrinterId = 1, printLogoOnPosTickets = true)), FakeJobs(), on, header).printRaw("pos", ticket)
        assertArrayEquals(byteArrayOf(9, 9) + ticket, on.sent.single().second)
    }

    @Test fun posTicketGoesToDefaultPrinterEvenWhenAskingEveryTime() = runTest {
        val conn = FakeConnector()
        PrintDispatcher(printers, FakePrefs(AppPreferences(mode = DefaultPrinterMode.ASK_EVERY_TIME, defaultPrinterId = 2)), FakeJobs(), conn, NO_HEADER).printRaw("pos", byteArrayOf(1))
        assertEquals(2L, conn.sent.single().first)
    }

    @Test fun noPrintersSavedGivesClearOutcome() = runTest {
        val d = PrintDispatcher(FakePrinters(emptyList()), FakePrefs(AppPreferences()), FakeJobs(), FakeConnector(), NO_HEADER)
        assertTrue(d.print(req()) is PrintOutcome.NoPrinter)
    }

    @Test fun renderFailureIsRecordedAsFailedJob() = runTest {
        val jobs = FakeJobs()
        val bad = PrintRequest("bad", JobType.QR) { throw IllegalArgumentException("bad content") }
        val out = dispatcher(FakePrefs(AppPreferences(defaultPrinterId = 1)), jobs, FakeConnector()).print(bad)
        assertTrue(out is PrintOutcome.Failed)
        assertEquals(JobStatus.FAILED, jobs.jobs.values.single().status)
    }
}
