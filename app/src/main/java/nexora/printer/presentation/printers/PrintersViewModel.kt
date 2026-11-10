package nexora.printer.presentation.printers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nexora.printer.core.bluetooth.BluetoothDeviceInfo
import nexora.printer.core.bluetooth.BluetoothPermissions
import nexora.printer.core.bluetooth.BluetoothScanner
import nexora.printer.core.printer.ConnectionState
import nexora.printer.core.printer.JobRenderer
import nexora.printer.core.printer.PrinterManager
import nexora.printer.core.usb.UsbDeviceInfo
import nexora.printer.core.usb.UsbScanner
import nexora.printer.core.wifi.NetworkEndpoint
import nexora.printer.core.wifi.TcpTransport
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintRequest
import nexora.printer.presentation.print.PrintSession
import javax.inject.Inject

data class PrintersState(
    val printers: List<PrinterProfile> = emptyList(),
    val connection: Map<Long, ConnectionState> = emptyMap(),
    val defaultPaper: PaperWidth = PaperWidth.MM58
)

@HiltViewModel
class PrintersViewModel @Inject constructor(
    private val repo: PrinterRepository,
    prefs: PreferencesRepository,
    private val manager: PrinterManager,
    private val btScanner: BluetoothScanner,
    private val usbScanner: UsbScanner,
    private val renderer: JobRenderer,
    dispatcher: PrintDispatcher,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val session = PrintSession(dispatcher, viewModelScope)

    val state: StateFlow<PrintersState> = combine(repo.observePrinters(), manager.states, prefs.preferences) { l, s, p ->
        PrintersState(l, s, p.defaultPaperWidth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintersState())

    private val _paired = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val paired = _paired.asStateFlow()
    private val _found = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val found = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private val _usb = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val usb = _usb.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var scanJob: Job? = null

    fun clearMessage() { _message.value = null }

    fun hasBluetoothPermission() = BluetoothPermissions.hasAll(context)

    fun loadPaired() {
        if (!hasBluetoothPermission()) { _message.value = "Bluetooth permission is required."; return }
        _paired.value = btScanner.pairedDevices()
    }

    fun scan() {
        if (!hasBluetoothPermission()) { _message.value = "Bluetooth permission is required."; return }
        scanJob?.cancel()
        _found.value = emptyList()
        scanJob = viewModelScope.launch {
            _scanning.value = true
            try {
                btScanner.discover().collect { d ->
                    _found.update { l -> if (l.any { it.address == d.address }) l else l + d }
                }
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() { scanJob?.cancel() }

    fun refreshUsb() { _usb.value = usbScanner.devices() }

    fun addPrinter(name: String, type: ConnectionType, address: String, paper: PaperWidth) {
        viewModelScope.launch {
            repo.addPrinter(
                PrinterProfile(0, name.trim().ifBlank { "Printer" }, type, address, paper, paper.defaultDots, false, System.currentTimeMillis())
            )
        }
    }

    fun probeWifi(host: String, port: Int) {
        viewModelScope.launch {
            _message.value = "Testing $host:$port…"
            val t = TcpTransport(host, port)
            _message.value = try {
                t.connect(5_000)
                if (t.isConnected) "Connection to $host:$port succeeded." else "Could not verify the connection."
            } catch (e: Exception) {
                "Connection failed: ${e.message}"
            } finally {
                t.close()
            }
        }
    }

    fun connect(p: PrinterProfile) { viewModelScope.launch { manager.connect(p) } }
    fun disconnect(p: PrinterProfile) { viewModelScope.launch { manager.disconnect(p.id) } }
    fun setDefault(p: PrinterProfile) { viewModelScope.launch { repo.setDefault(p.id) } }
    fun rename(p: PrinterProfile, name: String) { viewModelScope.launch { repo.rename(p.id, name) } }
    fun saveProfile(p: PrinterProfile) { viewModelScope.launch { repo.updateProfile(p) } }
    fun forget(p: PrinterProfile) {
        viewModelScope.launch {
            manager.disconnect(p.id)
            repo.delete(p.id)
        }
    }

    fun test(p: PrinterProfile) {
        session.start(PrintRequest("Test: ${p.name}", JobType.TEST) { renderer.renderTestReceipt(it) }, p.id)
    }

    fun validWifi(host: String, port: Int) = NetworkEndpoint.parse("$host:$port") != null
}
