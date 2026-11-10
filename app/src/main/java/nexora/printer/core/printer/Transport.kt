package nexora.printer.core.printer

import java.io.IOException

class PrinterException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** A byte pipe to one printer. A transport only reports connected after a real connection succeeded. */
interface PrinterTransport {
    val isConnected: Boolean
    suspend fun connect(timeoutMs: Int)
    suspend fun write(data: ByteArray)
    fun close()
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}
