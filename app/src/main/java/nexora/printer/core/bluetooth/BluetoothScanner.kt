package nexora.printer.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import nexora.printer.core.printer.PrinterException
import javax.inject.Inject
import javax.inject.Singleton

data class BluetoothDeviceInfo(
    val name: String?,
    val address: String,
    val bonded: Boolean,
    /** False for BLE-only devices: they do not expose Classic SPP, so they cannot be used here. */
    val supportsClassic: Boolean,
    val likelyPrinter: Boolean
)

@SuppressLint("MissingPermission")
@Singleton
class BluetoothScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun pairedDevices(): List<BluetoothDeviceInfo> {
        val a = adapter() ?: return emptyList()
        if (!BluetoothPermissions.hasConnect(context)) return emptyList()
        return try {
            a.bondedDevices.orEmpty().map { it.toInfo() }.sortedWith(
                compareByDescending<BluetoothDeviceInfo> { it.likelyPrinter }.thenBy { it.name ?: it.address }
            )
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun discover(): Flow<BluetoothDeviceInfo> = callbackFlow {
        val a = adapter()
        if (a == null || !a.isEnabled || !BluetoothPermissions.hasAll(context)) {
            close(PrinterException("Bluetooth is off, unavailable, or permission is missing"))
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                        trySend(d.toInfo())
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        if (!a.startDiscovery()) close(PrinterException("Could not start Bluetooth discovery"))
        awaitClose {
            runCatching { a.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun BluetoothDevice.toInfo() = BluetoothDeviceInfo(
        name = name,
        address = address,
        bonded = bondState == BluetoothDevice.BOND_BONDED,
        supportsClassic = type != BluetoothDevice.DEVICE_TYPE_LE,
        likelyPrinter = bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING
    )
}
