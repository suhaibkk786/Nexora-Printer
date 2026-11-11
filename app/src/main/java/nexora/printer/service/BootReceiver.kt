package nexora.printer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the silent-printing service again after the tablet restarts (when it is switched on in Settings). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences(ServerConfig.FILE, Context.MODE_PRIVATE).getBoolean(ServerConfig.KEY_ENABLED, true)
        if (enabled) runCatching { PrintServerService.start(context) }
    }
}
