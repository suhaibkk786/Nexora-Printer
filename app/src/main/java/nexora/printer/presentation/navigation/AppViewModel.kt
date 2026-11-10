package nexora.printer.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nexora.printer.domain.model.ThemeMode
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.presentation.print.SharedContent
import nexora.printer.presentation.print.SharedContentHolder
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    prefs: PreferencesRepository,
    holder: SharedContentHolder
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = prefs.preferences.map { it.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val pendingShare: StateFlow<SharedContent?> = holder.pending
}
