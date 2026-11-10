package nexora.printer.presentation.printers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nexora.printer.core.bluetooth.BluetoothDeviceInfo
import nexora.printer.core.bluetooth.BluetoothPermissions
import nexora.printer.core.printer.ConnectionState
import nexora.printer.core.usb.UsbDeviceInfo
import nexora.printer.core.usb.UsbSupport
import nexora.printer.core.wifi.NetworkEndpoint
import nexora.printer.domain.model.ConnectionType
import nexora.printer.domain.model.PaperWidth
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.presentation.preview.*
import nexora.printer.presentation.print.PrintStatusHost

private data class Draft(val name: String, val type: ConnectionType, val address: String)

@Composable
fun PrintersScreen(vm: PrintersViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val status by vm.session.status.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var rename by remember { mutableStateOf<PrinterProfile?>(null) }
    var configure by remember { mutableStateOf<PrinterProfile?>(null) }
    var forget by remember { mutableStateOf<PrinterProfile?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add printer") })
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Printers", style = MaterialTheme.typography.headlineSmall) }
            if (state.printers.isEmpty()) {
                item { Text("No printers saved. Tap “Add printer” to pair a Bluetooth printer, attach a USB printer, or enter a network address.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.printers, key = { it.id }) { p ->
                PrinterCard(
                    p, state.connection[p.id] ?: ConnectionState.Disconnected,
                    onConnect = { vm.connect(p) }, onDisconnect = { vm.disconnect(p) },
                    onDefault = { vm.setDefault(p) }, onTest = { vm.test(p) },
                    onRename = { rename = p }, onConfigure = { configure = p }, onForget = { forget = p }
                )
            }
        }
    }

    if (showAdd) {
        AddPrinterDialog(vm, onPick = { draft = it; showAdd = false }, onDismiss = { vm.stopScan(); showAdd = false })
    }
    draft?.let { dr ->
        var name by remember(dr) { mutableStateOf(dr.name) }
        var paper by remember(dr) { mutableStateOf(state.defaultPaper) }
        AlertDialog(
            onDismissRequest = { draft = null },
            title = { Text("Save printer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Text("${dr.type.label} • ${dr.address}", style = MaterialTheme.typography.bodySmall)
                    PaperWidthSelector(paper, false) { if (it != null) paper = it }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addPrinter(name, dr.type, dr.address, paper); draft = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { draft = null }) { Text("Cancel") } }
        )
    }
    rename?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Rename printer") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(p, name); rename = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } }
        )
    }
    configure?.let { p -> PrinterProfileEditorDialog(p, onSave = { vm.saveProfile(it); configure = null }, onDismiss = { configure = null }) }
    forget?.let { p ->
        AlertDialog(
            onDismissRequest = { forget = null },
            title = { Text("Forget ${p.name}?") },
            text = { Text("The printer profile will be removed from this app. Print history is kept.") },
            confirmButton = { TextButton(onClick = { vm.forget(p); forget = null }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { forget = null }) { Text("Cancel") } }
        )
    }
    PrintStatusHost(status, vm.session, state.printers, snackbar, onManagePrinters = {})
}

@Composable
private fun PrinterCard(
    p: PrinterProfile, conn: ConnectionState,
    onConnect: () -> Unit, onDisconnect: () -> Unit, onDefault: () -> Unit, onTest: () -> Unit,
    onRename: () -> Unit, onConfigure: () -> Unit, onForget: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(p.connectionType.icon(), contentDescription = p.connectionType.label)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    if (p.isDefault) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Star, contentDescription = "Default", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)) }
                }
                Text("${p.connectionType.label} • ${p.paperWidth.mm} mm • ${p.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(conn)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Rename") }, { menu = false; onRename() })
                    DropdownMenuItem({ Text("Configure profile") }, { menu = false; onConfigure() })
                    DropdownMenuItem({ Text("Forget") }, { menu = false; onForget() })
                }
            }
        }
        (conn as? ConnectionState.Error)?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (conn == ConnectionState.Connected) OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            else Button(onClick = onConnect, enabled = conn != ConnectionState.Connecting) { Text("Connect") }
            FilledTonalButton(onClick = onTest) { Text("Test") }
            if (!p.isDefault) OutlinedButton(onClick = onDefault) { Text("Set default") }
        }
    }
}

@Composable
private fun AddPrinterDialog(vm: PrintersViewModel, onPick: (Draft) -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val paired by vm.paired.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val usb by vm.usb.collectAsStateWithLifecycle()
    var hasPerm by remember { mutableStateOf(BluetoothPermissions.hasAll(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = BluetoothPermissions.hasAll(context)
        if (hasPerm) vm.loadPaired()
    }
    LaunchedEffect(tab) {
        if (tab == 0 && hasPerm) vm.loadPaired()
        if (tab == 1) vm.refreshUsb()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add printer") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    listOf("Bluetooth", "USB", "Wi-Fi").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(min = 200.dp, max = 360.dp)) {
                    when (tab) {
                        0 -> BluetoothTab(hasPerm, paired, found, scanning,
                            onGrant = { permLauncher.launch(BluetoothPermissions.required()) },
                            onScan = { vm.scan() }, onStop = { vm.stopScan() },
                            onPick = { onPick(Draft(it.name ?: it.address, ConnectionType.BLUETOOTH, it.address)) })
                        1 -> UsbTab(usb, onRefresh = { vm.refreshUsb() },
                            onPick = { onPick(Draft(it.name, ConnectionType.USB, it.address)) })
                        else -> WifiTab(vm, onPick)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun BluetoothTab(
    hasPerm: Boolean, paired: List<BluetoothDeviceInfo>, found: List<BluetoothDeviceInfo>, scanning: Boolean,
    onGrant: () -> Unit, onScan: () -> Unit, onStop: () -> Unit, onPick: (BluetoothDeviceInfo) -> Unit
) {
    if (!hasPerm) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bluetooth permission is needed to list and connect to printers.")
            Button(onClick = onGrant) { Text("Grant permission") }
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { Text("Paired devices", style = MaterialTheme.typography.labelLarge) }
        if (paired.isEmpty()) item { Text("None. Pair your printer in Android Bluetooth settings first, or scan below.", style = MaterialTheme.typography.bodySmall) }
        items(paired, key = { "p" + it.address }) { DeviceRow(it, onPick) }
        item {
            Spacer(Modifier.height(8.dp))
            if (scanning) OutlinedButton(onClick = onStop) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Stop scanning") }
            else OutlinedButton(onClick = onScan) { Text("Scan nearby") }
        }
        items(found.filter { f -> paired.none { it.address == f.address } }, key = { "f" + it.address }) { DeviceRow(it, onPick) }
    }
}

@Composable
private fun DeviceRow(d: BluetoothDeviceInfo, onPick: (BluetoothDeviceInfo) -> Unit) {
    val usable = d.supportsClassic
    Column(Modifier.fillMaxWidth().clickable(enabled = usable) { onPick(d) }.padding(vertical = 8.dp)) {
        Text(d.name ?: "Unnamed device", style = MaterialTheme.typography.bodyLarge)
        Text(
            d.address + when {
                !usable -> " • BLE only: not supported"
                d.likelyPrinter -> " • Printer"
                !d.bonded -> " • Not paired"
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (usable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun UsbTab(devices: List<UsbDeviceInfo>, onRefresh: () -> Unit, onPick: (UsbDeviceInfo) -> Unit) {
    LazyColumn {
        item { OutlinedButton(onClick = onRefresh) { Text("Refresh") } }
        if (devices.isEmpty()) item { Text("No USB devices attached. Connect the printer with an OTG cable.", style = MaterialTheme.typography.bodySmall) }
        items(devices, key = { it.address }) { d ->
            val usable = d.support != UsbSupport.UNSUPPORTED
            Column(Modifier.fillMaxWidth().clickable(enabled = usable) { onPick(d) }.padding(vertical = 8.dp)) {
                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                Text("VID ${d.vendorId} / PID ${d.productId} • ${d.support.label}", style = MaterialTheme.typography.bodySmall,
                    color = if (usable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WifiTab(vm: PrintersViewModel, onPick: (Draft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableIntStateOf(NetworkEndpoint.DEFAULT_PORT) }
    val valid = host.isNotBlank() && vm.validWifi(host.trim(), port)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(host, { host = it }, label = { Text("IP address or host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField("Port (raw printing: 9100)", port, { port = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.probeWifi(host.trim(), port) }, enabled = valid) { Text("Test connection") }
            Button(onClick = { onPick(Draft(name.ifBlank { host.trim() }, ConnectionType.WIFI, "${host.trim()}:$port")) }, enabled = valid) { Text("Next") }
        }
    }
}
