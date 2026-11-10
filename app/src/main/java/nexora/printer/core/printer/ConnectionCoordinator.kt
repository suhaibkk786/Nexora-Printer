package nexora.printer.core.printer

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import nexora.printer.core.usb.UsbPrinterSupport
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrinterRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Auto-connects the default printer, watches for dropped links and retries when enabled. */
@Singleton
class ConnectionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val printers: PrinterRepository,
    private val prefs: PreferencesRepository,
    private val manager: PrinterManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val retryJobs = ConcurrentHashMap<Long, Job>()
    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            combine(prefs.preferences, printers.observePrinters()) { p, list ->
                if (p.autoConnect) list.firstOrNull { it.id == p.defaultPrinterId } else null
            }.distinctUntilChangedBy { it?.id }
                .collect { printer ->
                    if (printer != null && manager.stateOf(printer.id) != ConnectionState.Connected) {
                        manager.connect(printer)
                    }
                }
        }

        scope.launch {
            manager.states.collect { states ->
                val p = prefs.preferences.first()
                if (!p.autoReconnect) return@collect
                val list = printers.observePrinters().first()
                for ((id, state) in states) {
                    if (state !is ConnectionState.Error) continue
                    val profile = list.firstOrNull { it.id == id } ?: continue
                    if (!profile.settings.autoReconnect) continue
                    if (retryJobs[id]?.isActive == true) continue
                    retryJobs[id] = scope.launch { reconnect(profile) }
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        d?.address?.let { manager.onTransportLost(it) }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val d = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        d?.let { manager.onTransportLost(UsbPrinterSupport.address(it.vendorId, it.productId)) }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private suspend fun reconnect(p: PrinterProfile) {
        repeat(3) { attempt ->
            delay(3_000L * (attempt + 1))
            if (manager.connect(p).isSuccess) return
        }
    }
}
