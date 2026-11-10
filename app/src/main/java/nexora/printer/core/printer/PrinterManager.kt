package nexora.printer.core.printer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nexora.printer.core.bluetooth.BluetoothTransport
import nexora.printer.core.usb.UsbTransport
import nexora.printer.core.wifi.NetworkEndpoint
import nexora.printer.core.wifi.TcpTransport
import nexora.printer.domain.model.ConnectionType
import nexora.printer.domain.model.PrinterProfile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface PrinterConnector {
    /** Connects on demand, writes [data], and reports failure instead of hiding it. */
    suspend fun send(profile: PrinterProfile, data: ByteArray): Result<Unit>
}

@Singleton
class PrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PrinterConnector {

    private val transports = ConcurrentHashMap<Long, PrinterTransport>()
    private val addresses = ConcurrentHashMap<Long, String>()
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val _states = MutableStateFlow<Map<Long, ConnectionState>>(emptyMap())
    val states: StateFlow<Map<Long, ConnectionState>> = _states.asStateFlow()

    fun stateOf(id: Long): ConnectionState = _states.value[id] ?: ConnectionState.Disconnected

    private fun lock(id: Long) = locks.getOrPut(id) { Mutex() }
    private fun setState(id: Long, s: ConnectionState) = _states.update { it + (id to s) }

    private fun createTransport(p: PrinterProfile): PrinterTransport = when (p.connectionType) {
        ConnectionType.BLUETOOTH -> BluetoothTransport(context, p.address)
        ConnectionType.USB -> UsbTransport(context, p.address)
        ConnectionType.WIFI -> {
            val ep = NetworkEndpoint.parse(p.address)
                ?: throw PrinterException("Invalid network address: ${p.address}")
            TcpTransport(ep.host, ep.port)
        }
    }

    suspend fun connect(p: PrinterProfile): Result<Unit> = lock(p.id).withLock { connectLocked(p) }

    private suspend fun connectLocked(p: PrinterProfile): Result<Unit> {
        setState(p.id, ConnectionState.Connecting)
        transports.remove(p.id)?.close()
        return try {
            val t = createTransport(p)
            t.connect(p.settings.connectionTimeoutMs)
            if (!t.isConnected) throw PrinterException("Connection could not be verified")
            transports[p.id] = t
            addresses[p.id] = p.address
            setState(p.id, ConnectionState.Connected)
            Result.success(Unit)
        } catch (e: CancellationException) {
            setState(p.id, ConnectionState.Disconnected)
            throw e
        } catch (e: Exception) {
            setState(p.id, ConnectionState.Error(e.message ?: "Connection failed"))
            Result.failure(e)
        }
    }

    suspend fun disconnect(id: Long) = lock(id).withLock {
        transports.remove(id)?.close()
        setState(id, ConnectionState.Disconnected)
    }

    override suspend fun send(profile: PrinterProfile, data: ByteArray): Result<Unit> = lock(profile.id).withLock {
        var t = transports[profile.id]
        if (t == null || !t.isConnected) {
            val c = connectLocked(profile)
            if (c.isFailure) return@withLock Result.failure(c.exceptionOrNull()!!)
            t = transports[profile.id]
        }
        val first = writeLocked(profile, t!!, data)
        if (first.isSuccess || !profile.settings.autoReconnect) return@withLock first

        // One automatic reconnect + retry when the link dropped mid-job (auto-reconnect enabled).
        val again = connectLocked(profile)
        if (again.isFailure) return@withLock Result.failure(again.exceptionOrNull()!!)
        writeLocked(profile, transports[profile.id]!!, data)
    }

    private suspend fun writeLocked(p: PrinterProfile, t: PrinterTransport, data: ByteArray): Result<Unit> =
        try {
            t.write(data)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            transports.remove(p.id)?.close()
            setState(p.id, ConnectionState.Error(e.message ?: "Write failed"))
            Result.failure(e)
        }

    /** Called when the OS reports a Bluetooth ACL disconnect or USB detach for [address]. */
    fun onTransportLost(address: String) {
        for ((id, addr) in addresses) {
            if (addr.equals(address, ignoreCase = true) && stateOf(id) == ConnectionState.Connected) {
                transports.remove(id)?.close()
                setState(id, ConnectionState.Error("Connection lost"))
            }
        }
    }
}
