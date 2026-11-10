package nexora.printer.presentation.print

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SharedContent {
    data class Text(val text: String) : SharedContent
    data class Image(val uri: Uri) : SharedContent
    data class Pdf(val uri: Uri) : SharedContent
}

/** Holds content received through the Android share sheet until the Print screen consumes it. */
@Singleton
class SharedContentHolder @Inject constructor() {
    private val _pending = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()
    fun offer(content: SharedContent) { _pending.value = content }
    fun consume() { _pending.value = null }
}
