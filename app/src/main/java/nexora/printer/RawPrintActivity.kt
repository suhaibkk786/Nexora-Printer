package nexora.printer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nexora.printer.core.printer.RawPrintLink
import nexora.printer.domain.usecase.PrintDispatcher
import nexora.printer.domain.usecase.PrintOutcome
import javax.inject.Inject

/**
 * Receives print links from a point-of-sale page (`rawbt:base64,...` or `nexoraprint:base64,...`), prints the ticket on the
 * default printer without showing a screen, and closes. Works like RawBT, so the POS needs no change.
 */
@AndroidEntryPoint
class RawPrintActivity : ComponentActivity() {
    @Inject lateinit var dispatcher: PrintDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bytes = RawPrintLink.decode(intent?.dataString)
        if (bytes == null) {
            toast(getString(R.string.raw_print_bad_link))
            finish()
            return
        }
        lifecycleScope.launch {
            val outcome = dispatcher.printRaw("POS ticket", bytes)
            toast(
                when (outcome) {
                    is PrintOutcome.Success -> getString(R.string.raw_print_done, outcome.printerName)
                    is PrintOutcome.Failed -> getString(R.string.raw_print_failed, outcome.message)
                    is PrintOutcome.NoPrinter -> outcome.reason
                    is PrintOutcome.ChoosePrinter -> getString(R.string.raw_print_choose)
                }
            )
            finish()
        }
    }

    private fun toast(message: String) = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
}
