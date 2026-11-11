package nexora.printer.presentation.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import nexora.printer.BuildConfig
import nexora.printer.R
import nexora.printer.core.billing.BillingManager
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(val billing: BillingManager) : ViewModel()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The paywall: what Nexora Printer Pro is, the plans from Google Play (price, period, free trial), restore and manage. */
@Composable
fun SubscriptionScreen(onBack: () -> Unit, vm: SubscriptionViewModel = hiltViewModel()) {
    val status by vm.billing.status.collectAsStateWithLifecycle()
    val plans by vm.billing.plans.collectAsStateWithLifecycle()
    val message by vm.billing.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.nexora_logo), contentDescription = "Nexora Printer", modifier = Modifier.size(88.dp))
            Text("Nexora Printer Pro", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Printing is free. Pro removes the Nexora logo: the window shown while a POS ticket prints, and the logo shown when the app opens.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "No Nexora logo window while POS tickets print",
                    "No Nexora logo when the app opens",
                    "Tickets print silently and instantly, straight to your printer",
                    "Switch either logo back on any time in Settings",
                    "Supports the development of Nexora Printer"
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }

            when {
                !BuildConfig.REQUIRE_SUBSCRIPTION ->
                    Text("This build is free: all features are unlocked.", style = MaterialTheme.typography.titleMedium)

                status == BillingManager.Status.ACTIVE -> {
                    Text("You are subscribed. Thank you!", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { runCatching { uriHandler.openUri(vm.billing.manageUrl) } }) {
                        Text("Manage or cancel subscription")
                    }
                }

                else -> {
                    if (plans.isEmpty()) {
                        Text(
                            if (status == BillingManager.Status.UNAVAILABLE) "Plans could not be loaded. Check your connection and Google Play."
                            else "Loading plans…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    plans.forEach { plan ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                Text("${plan.price} / ${plan.per}", style = MaterialTheme.typography.headlineSmall)
                                plan.trial?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                                Button(
                                    onClick = { context.findActivity()?.let { vm.billing.subscribe(it, plan) } },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (plan.trial != null) "Start free trial" else "Subscribe") }
                            }
                        }
                    }
                    TextButton(onClick = { vm.billing.refresh() }) { Text("Restore purchases") }
                }
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text(
                "The subscription renews automatically until you cancel it in Google Play → Payments & subscriptions " +
                    "→ Subscriptions. Payment is charged to your Google Play account.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Row2(
                onTerms = { runCatching { uriHandler.openUri("https://www.nexoraittechnologies.com/terms") } },
                onPrivacy = { runCatching { uriHandler.openUri("https://www.nexoraittechnologies.com/privacy") } },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun Row2(onTerms: () -> Unit, onPrivacy: () -> Unit, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onTerms) { Text("Terms") }
        TextButton(onClick = onPrivacy) { Text("Privacy") }
        TextButton(onClick = onBack) { Text("Close") }
    }
}
