package nexora.printer.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nexora.printer.domain.usecase.PrintOutcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What reached this tablet from other apps (a POS web page, a network or Bluetooth "printer" connection) and what became of it.
 * Shown in Settings → "Received from other apps", so the owner can tell at once whether a POS app is sending anything and why a
 * ticket did not print.
 */
@Singleton
class ReceiveLog @Inject constructor(@ApplicationContext context: Context) {
    data class Entry(val at: Long, val channel: String, val peer: String, val bytes: Int, val result: String)

    private val sp = context.getSharedPreferences("nexora_receive_log", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Whether the Bluetooth "nearby device" printer is listening, or why it is not. */
    val btStatus = MutableStateFlow("Off")

    @Synchronized
    fun add(channel: String, peer: String, bytes: Int, result: String) {
        val list = (listOf(Entry(System.currentTimeMillis(), channel, peer, bytes, result)) + _entries.value).take(MAX)
        _entries.value = list
        sp.edit().putString(KEY, list.joinToString("\n") { e -> listOf(e.at.toString(), e.channel, e.peer, e.bytes.toString(), e.result).joinToString("|") { clean(it) } }).apply()
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        sp.edit().remove(KEY).apply()
    }

    fun describe(o: PrintOutcome): String = when (o) {
        is PrintOutcome.Success -> "Printed on ${o.printerName}"
        is PrintOutcome.Failed -> "Could not print: ${o.message}"
        is PrintOutcome.NoPrinter -> o.reason
        is PrintOutcome.ChoosePrinter -> "Choose a default printer in Nexora Printer"
    }

    private fun clean(s: String) = s.replace("|", " ").replace("\n", " ")

    private fun load(): List<Entry> =
        (sp.getString(KEY, null) ?: "").lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("|", limit = 5)
            if (p.size < 5) null else Entry(p[0].toLongOrNull() ?: 0L, p[1], p[2], p[3].toIntOrNull() ?: 0, p[4])
        }

    private companion object {
        const val KEY = "log"
        const val MAX = 25
    }
}
