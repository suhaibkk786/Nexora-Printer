package nexora.printer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import nexora.printer.presentation.navigation.NexoraApp
import nexora.printer.service.PrintServerService
import nexora.printer.service.ServerConfig
import nexora.printer.presentation.print.SharedContent
import nexora.printer.presentation.print.SharedContentHolder
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sharedHolder: SharedContentHolder
    @Inject lateinit var serverConfig: ServerConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        // silent printing: the background service runs while this is switched on in Settings
        if (serverConfig.anyOn) runCatching { PrintServerService.start(this) }
        setContent { NexoraApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Share / "Open with" integration for text, images and PDF files. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val type = intent.type ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (type.startsWith("text/")) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedHolder.offer(SharedContent.Text(it)) }
                } else {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
                    sharedHolder.offer(
                        if (type == "application/pdf") SharedContent.Pdf(uri) else SharedContent.Image(uri)
                    )
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (type == "application/pdf") sharedHolder.offer(SharedContent.Pdf(uri))
            }
        }
    }
}
