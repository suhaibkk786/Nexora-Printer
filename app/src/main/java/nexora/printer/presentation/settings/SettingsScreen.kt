package nexora.printer.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nexora.printer.BuildConfig
import nexora.printer.core.billing.BillingManager
import nexora.printer.core.billing.LicenseInfo
import nexora.printer.domain.model.*
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrinterRepository
import nexora.printer.service.PrintServerService
import nexora.printer.service.ServerConfig
import nexora.printer.presentation.preview.*
import nexora.printer.presentation.printers.PrinterProfileEditorDialog
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val printers: PrinterRepository,
    private val billing: BillingManager,
    private val serverConfig: ServerConfig
) : ViewModel() {
    val serverOn = MutableStateFlow(serverConfig.enabled)
    val allowedOrigins = MutableStateFlow(serverConfig.allowedOrigins)
    fun setServerOn(on: Boolean, context: android.content.Context) {
        serverConfig.enabled = on
        serverOn.value = on
        if (on) runCatching { PrintServerService.start(context) } else PrintServerService.stop(context)
    }
    fun setAllowedOrigins(text: String) { serverConfig.allowedOrigins = text; allowedOrigins.value = text }
    val subscriptionStatus: StateFlow<BillingManager.Status> = billing.status
    val isPro: Boolean get() = billing.isPro()
    val licensed: StateFlow<LicenseInfo?> = billing.license
    val preferences: StateFlow<AppPreferences> = prefs.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())
    val printerList: StateFlow<List<PrinterProfile>> = printers.observePrinters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMode(m: DefaultPrinterMode) = launch { prefs.setMode(m) }
    fun setTheme(t: ThemeMode) = launch { prefs.setTheme(t) }
    fun setAutoConnect(v: Boolean) = launch { prefs.setAutoConnect(v) }
    fun setAutoReconnect(v: Boolean) = launch { prefs.setAutoReconnect(v) }
    fun setCopies(v: Int) = launch { prefs.setDefaultCopies(v) }
    fun setPrintLogo(v: Boolean) = launch { prefs.setPrintLogo(v) }
    fun setPrintTime(v: Boolean) = launch { prefs.setPrintTime(v) }
    fun setShowPrintWindow(v: Boolean) = launch { prefs.setShowPrintWindow(v) }
    fun setShowStartLogo(v: Boolean) = launch { prefs.setShowStartLogo(v) }
    fun setPaper(w: PaperWidth) = launch { prefs.setDefaultPaperWidth(w) }
    fun setPriority(o: List<ConnectionType>) = launch { prefs.setConnectionPriority(o) }
    fun setDefault(id: Long) = launch { printers.setDefault(id) }
    fun saveProfile(p: PrinterProfile) = launch { printers.updateProfile(p) }
    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}

@Composable
fun SettingsScreen(onOpenDiagnostics: () -> Unit, onOpenSubscription: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val subscription by vm.subscriptionStatus.collectAsStateWithLifecycle()
    val licensed by vm.licensed.collectAsStateWithLifecycle()
    val printers by vm.printerList.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val default = printers.firstOrNull { it.id == p.defaultPrinterId }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            SectionCard(title = "Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(p.theme == m, { vm.setTheme(m) }, label = { Text(m.label) })
                    }
                }
            }

            SectionCard(title = "Default printer") {
                DropdownField("Default", printers, default ?: printers.firstOrNull(), { pr -> pr?.let { x -> vm.setDefault(x.id) } }, { it?.name ?: "None" }, Modifier.fillMaxWidth())
                Text("Selection mode", style = MaterialTheme.typography.labelLarge)
                DefaultPrinterMode.entries.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(p.mode == m, { vm.setMode(m) })
                        Column {
                            Text(m.label, style = MaterialTheme.typography.bodyLarge)
                            Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (p.mode == DefaultPrinterMode.CONNECTION_PRIORITY) {
                    Text("Connection priority (top = preferred)", style = MaterialTheme.typography.labelLarge)
                    p.connectionPriority.forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${t.label}", Modifier.weight(1f))
                            IconButton(onClick = { vm.setPriority(p.connectionPriority.swap(i, i - 1)) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
                            IconButton(onClick = { vm.setPriority(p.connectionPriority.swap(i, i + 1)) }, enabled = i < p.connectionPriority.lastIndex) { Icon(Icons.Default.ArrowDownward, "Move down") }
                        }
                    }
                }
                SwitchRow("Auto-connect default printer", p.autoConnect, vm::setAutoConnect, "Connects when the app starts or the default changes")
                SwitchRow("Auto-reconnect", p.autoReconnect, vm::setAutoReconnect, "Retries when the connection drops")
            }

            SectionCard(title = "Defaults") {
                Text("Paper width for new printers", style = MaterialTheme.typography.labelLarge)
                PaperWidthSelector(p.defaultPaperWidth, false) { if (it != null) vm.setPaper(it) }
                StepperRow("Default copies", p.defaultCopies, 1..99) { vm.setCopies(it) }
            }

            SectionCard(title = "Print header") {
                SwitchRow("Print logo", p.printLogo, vm::setPrintLogo, "Prints the Nexora X logo at the top of every job")
                SwitchRow("Print date & time", p.printTime, vm::setPrintTime, "Prints the time the job was sent, at the top")
                Text(
                    if (!p.printLogo && !p.printTime) "Both off: jobs print directly with nothing added."
                    else "Applies to jobs printed from this app, shared files and print dialogs. POS tickets print exactly as the POS made them.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "Logo & print window") {
                val pro = (subscription == BillingManager.Status.ACTIVE || licensed != null) && vm.isPro
                SwitchRow(
                    "Show print window", p.showPrintWindow || !pro, vm::setShowPrintWindow,
                    if (pro) "Shows a small window with the Nexora logo while a ticket from a POS or another app prints. Off: it prints silently."
                    else "A small window with the Nexora logo shows while a POS ticket prints. Subscribe to Nexora Printer Pro to remove it.",
                    enabled = pro
                )
                if (!pro) TextButton(onClick = onOpenSubscription) { Text("Remove the logos \u2013 Nexora Printer Pro") }
                SwitchRow(
                    "Show logo when the app opens", p.showStartLogo || !pro, vm::setShowStartLogo,
                    if (pro) "Shows the Nexora logo for a moment when the app opens. Off: the app opens straight to the home screen."
                    else "The Nexora logo shows for a moment when the app opens. Subscribe to Nexora Printer Pro to remove it.",
                    enabled = pro
                )
            }

            val serverOn by vm.serverOn.collectAsStateWithLifecycle()
            val origins by vm.allowedOrigins.collectAsStateWithLifecycle()
            val appContext = androidx.compose.ui.platform.LocalContext.current
            SectionCard(title = "Silent printing from a POS") {
                SwitchRow(
                    "Print without opening the app", serverOn, { vm.setServerOn(it, appContext) },
                    "A small background service on this tablet receives the ticket from your POS page and prints it straight away, " +
                        "like RawBT's local server. A notification stays while it runs."
                )
                Text("Address used by the POS: http://127.0.0.1:${ServerConfig.PORT}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = origins, onValueChange = { vm.setAllowedOrigins(it) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Allowed POS address (optional)") }, placeholder = { Text("pos.yourshop.com") },
                    supportingText = { Text("Only web pages from this address may print. Empty: any web page opened on this tablet can print.") }
                )
            }

            SectionCard(title = "Subscription") {
                Text(
                    when {
                        !BuildConfig.REQUIRE_SUBSCRIPTION -> "This build is free: all features are unlocked."
                        licensed != null -> "Nexora Printer Pro by license: ${licensed?.label}. You can remove the Nexora logos above."
                        subscription == BillingManager.Status.ACTIVE -> "Nexora Printer Pro: active. You can remove the Nexora logos above."
                        else -> "Printing is free. Nexora Printer Pro removes the Nexora logo window shown while POS tickets print, and the logo shown when the app opens."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onOpenSubscription) { Text("Plans & subscription") }
            }

            SectionCard(title = "Printer profile") {
                Text(
                    if (default == null) "Set a default printer to edit its density, margins, code page, auto-cut, feed lines, dithering and timeout."
                    else "Editing: ${default.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { editing = true }, enabled = default != null) { Text("Edit profile") }
            }

            SectionCard(title = "Tools") {
                OutlinedButton(onClick = onOpenDiagnostics) { Text("Open diagnostics") }
            }

            val uriHandler = LocalUriHandler.current
            SectionCard(title = "About") {
                Text("Nexora Printer 1.0.0", style = MaterialTheme.typography.bodyLarge)
                Text("Developed by Nexora IT Technologies", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { runCatching { uriHandler.openUri("https://www.nexoraittechnologies.com") } }) {
                    Text("www.nexoraittechnologies.com")
                }
            }
        }
    }
    if (editing && default != null) {
        PrinterProfileEditorDialog(default, onSave = { vm.saveProfile(it); editing = false }, onDismiss = { editing = false })
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
