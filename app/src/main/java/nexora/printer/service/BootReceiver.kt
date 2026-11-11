package nexora.printer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the silent-printing service again after the tablet restarts (when it is switched on in Settings). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val sp = context.getSharedPreferences(ServerConfig.FILE, Context.MODE_PRIVATE)
        val on = sp.getBoolean(ServerConfig.KEY_ENABLED, true) || sp.getBoolean(ServerConfig.KEY_LAN, true) || sp.getBoolean(ServerConfig.KEY_BT, false)
        if (on) runCatching { PrintServerService.start(context) }
    }
}
