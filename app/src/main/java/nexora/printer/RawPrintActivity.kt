package nexora.printer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nexora.printer.core.billing.Entitlement
import nexora.printer.core.billing.PrintWindowPolicy
import nexora.printer.core.printer.RawPrintLink
import nexora.printer.domain.model.ThemeMode
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintOutcome
import nexora.printer.presentation.theme.NexoraTheme
import javax.inject.Inject

/** What the print window shows. */
sealed interface TicketUi {
    data object Printing : TicketUi
    data class Done(val printer: String) : TicketUi
    data class Failed(val message: String) : TicketUi
}

/**
 * Receives print links from a point-of-sale page (`rawbt:base64,...` or `nexoraprint:base64,...`) and prints the ticket
 * exactly as the POS made it on the default printer. Like RawBT it can show a small window with the Nexora logo while it
 * prints. That window is part of the free version; with Nexora Printer Pro it can be switched off (Settings -> Show print window)
 * and the ticket then prints silently with only a short message.
 */
@AndroidEntryPoint
class RawPrintActivity : ComponentActivity() {
    @Inject lateinit var dispatcher: PrintDispatcher
    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var entitlement: Entitlement

    private var ui: TicketUi by mutableStateOf<TicketUi>(TicketUi.Printing)
    private var showWindow: Boolean by mutableStateOf(false)
    private var ticket: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bytes = RawPrintLink.decode(intent?.dataString)
        if (bytes == null) {
            toast(getString(R.string.raw_print_bad_link))
            finish()
            return
        }
        ticket = bytes
        setContent {
            NexoraTheme(ThemeMode.SYSTEM) {
                if (showWindow) {
                    TicketWindow(
                        state = ui,
                        onRetry = { print() },
                        onOpenApp = { startActivity(Intent(this@RawPrintActivity, MainActivity::class.java)); finish() },
                        onClose = { finish() }
                    )
                }
            }
        }
        lifecycleScope.launch {
            // without Pro the logo window always shows; Pro can switch it off in Settings
            showWindow = PrintWindowPolicy.show(prefs.preferences.first().showPrintWindow, entitlement.isPro())
            print()
        }
    }

    private fun print() {
        val data = ticket ?: return
        lifecycleScope.launch {
            ui = TicketUi.Printing
            when (val outcome = dispatcher.printRaw("POS ticket", data)) {
                is PrintOutcome.Success -> {
                    ui = TicketUi.Done(outcome.printerName)
                    if (showWindow) delay(1100) else toast(getString(R.string.raw_print_done, outcome.printerName))
                    finish()
                }
                is PrintOutcome.Failed -> fail(getString(R.string.raw_print_failed, outcome.message))
                is PrintOutcome.NoPrinter -> fail(outcome.reason)
                is PrintOutcome.ChoosePrinter -> fail(getString(R.string.raw_print_choose))
            }
        }
    }

    private suspend fun fail(message: String) {
        ui = TicketUi.Failed(message)
        if (!showWindow) {
            toast(message)
            finish()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}

@androidx.compose.runtime.Composable
private fun TicketWindow(state: TicketUi, onRetry: () -> Unit, onOpenApp: () -> Unit, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(enabled = state !is TicketUi.Printing) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(32.dp).widthIn(max = 320.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(painterResource(R.drawable.nexora_logo), contentDescription = "Nexora Printer", modifier = Modifier.size(72.dp))
                Text("Nexora Printer", style = MaterialTheme.typography.titleMedium)
                when (state) {
                    TicketUi.Printing -> {
                        CircularProgressIndicator()
                        Text("Printing…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is TicketUi.Done ->
                        Text("Printed on ${state.printer}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    is TicketUi.Failed -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = onRetry) { Text("Retry") }
                            TextButton(onClick = onOpenApp) { Text("Open app") }
                            TextButton(onClick = onClose) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}
