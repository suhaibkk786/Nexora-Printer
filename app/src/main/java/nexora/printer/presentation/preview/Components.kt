package nexora.printer.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nexora.printer.core.escpos.Align
import nexora.printer.core.printer.ConnectionState
import nexora.printer.domain.model.ConnectionType
import nexora.printer.domain.model.PaperWidth
import nexora.printer.domain.model.PrinterProfile

fun ConnectionType.icon(): ImageVector = when (this) {
    ConnectionType.BLUETOOTH -> Icons.Default.Bluetooth
    ConnectionType.USB -> Icons.Default.Usb
    ConnectionType.WIFI -> Icons.Default.Wifi
}

@Composable
fun StatusChip(state: ConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        ConnectionState.Connected -> "Connected" to Color(0xFF2E9E5B)
        ConnectionState.Connecting -> "Connecting…" to Color(0xFFE0A100)
        ConnectionState.Disconnected -> "Disconnected" to MaterialTheme.colorScheme.outline
        is ConnectionState.Error -> "Error" to MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${labelOf(selected)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelOf(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
fun AlignSelector(selected: Align, onSelect: (Align) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Align.entries.forEach { a ->
            FilterChip(
                selected = a == selected,
                onClick = { onSelect(a) },
                label = { Text(a.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
fun PaperWidthSelector(selected: PaperWidth?, allowDefault: Boolean, onSelect: (PaperWidth?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowDefault) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Printer default") })
        }
        PaperWidth.entries.forEach { w ->
            FilterChip(selected = w == selected, onClick = { onSelect(w) }, label = { Text("${w.mm} mm") })
        }
    }
}

@Composable
fun NumberField(label: String, value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw -> onValue(raw.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun PrinterPickerDialog(
    title: String,
    printers: List<PrinterProfile>,
    onPick: (PrinterProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (printers.isEmpty()) Text("No saved printers.")
                printers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(p.connectionType.icon(), contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${p.connectionType.label} • ${p.paperWidth.mm} mm" + if (p.isDefault) " • Default" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
