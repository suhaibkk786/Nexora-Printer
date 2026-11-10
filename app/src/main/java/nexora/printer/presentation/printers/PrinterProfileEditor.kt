package nexora.printer.presentation.printers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nexora.printer.core.escpos.DitherMode
import nexora.printer.core.escpos.EscPosCodePage
import nexora.printer.domain.model.PrinterProfile
import nexora.printer.presentation.preview.*

@Composable
fun PrinterProfileEditorDialog(profile: PrinterProfile, onSave: (PrinterProfile) -> Unit, onDismiss: () -> Unit) {
    var d by remember(profile.id) { mutableStateOf(profile) }
    val s = d.settings
    val page = EscPosCodePage.entries.firstOrNull { it.id == s.codePage && it.charsetName == s.encoding } ?: EscPosCodePage.PC437

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Printer profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Paper width", style = MaterialTheme.typography.labelLarge)
                PaperWidthSelector(d.paperWidth, allowDefault = false) { w -> if (w != null) d = d.withPaper(w) }
                NumberField("Printable dots (58mm≈384, 80mm≈576)", d.printableWidth, { d = d.copy(printableWidth = it.coerceIn(64, 1024)) }, Modifier.fillMaxWidth())
                DropdownField("Code page", EscPosCodePage.entries, page, {
                    d = d.copy(settings = s.copy(codePage = it.id, encoding = it.charsetName))
                }, { it.label }, Modifier.fillMaxWidth())
                Text("Arabic, Urdu and unsupported characters always use the raster fallback.", style = MaterialTheme.typography.bodySmall)
                StepperRow("Print density (0 = printer default)", s.density, 0..8) { d = d.copy(settings = s.copy(density = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Left margin", s.leftMargin, { d = d.copy(settings = s.copy(leftMargin = it.coerceIn(0, 200))) }, Modifier.weight(1f))
                    NumberField("Right margin", s.rightMargin, { d = d.copy(settings = s.copy(rightMargin = it.coerceIn(0, 200))) }, Modifier.weight(1f))
                }
                Text("Default alignment", style = MaterialTheme.typography.labelLarge)
                AlignSelector(s.defaultAlignment) { d = d.copy(settings = s.copy(defaultAlignment = it)) }
                SwitchRow("Auto cut", s.autoCut, { d = d.copy(settings = s.copy(autoCut = it)) })
                StepperRow("Feed lines after print", s.feedLines, 0..20) { d = d.copy(settings = s.copy(feedLines = it)) }
                DropdownField("Image dithering", DitherMode.entries, s.ditheringMode, { d = d.copy(settings = s.copy(ditheringMode = it)) }, { it.label }, Modifier.fillMaxWidth())
                StepperRow("Connection timeout (s)", s.connectionTimeoutMs / 1000, 3..60) { d = d.copy(settings = s.copy(connectionTimeoutMs = it * 1000)) }
                SwitchRow("Auto reconnect", s.autoReconnect, { d = d.copy(settings = s.copy(autoReconnect = it)) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(d.copy(name = d.name.trim().ifBlank { profile.name })) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
