package nexora.printer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nexora.printer.MainActivity
import nexora.printer.domain.usecase.PrintDispatcher
import javax.inject.Inject

/** Keeps the silent-printing server running in the background (a foreground service, so Android does not stop it). */
@AndroidEntryPoint
class PrintServerService : Service() {
    @Inject lateinit var dispatcher: PrintDispatcher
    @Inject lateinit var config: ServerConfig
    @Inject lateinit var log: ReceiveLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: PrintServer? = null
    private var lan: RawTcpServer? = null
    private var bt: BluetoothPrintServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        if (server == null && config.enabled) {
            val s = PrintServer(dispatcher, config, log)
            server = s
            scope.launch { runCatching { s.run() } }
        }
        if (lan == null && config.lanEnabled) {
            val s = RawTcpServer(dispatcher, config, log)
            lan = s
            scope.launch { runCatching { s.run() } }
        }
        if (bt == null && config.btEnabled) {
            val s = BluetoothPrintServer(applicationContext, dispatcher, log)
            bt = s
            scope.launch { runCatching { s.run() } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        lan?.stop()
        lan = null
        bt?.stop()
        bt = null
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Silent printing", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Nexora Printer is ready")
            .setContentText("Tickets from your POS web page and from other POS apps (network printer, port ${ServerConfig.LAN_PORT}) print without opening the app")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(ID, notification)
    }

    companion object {
        private const val CHANNEL = "silent_printing"
        private const val ID = 4021

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PrintServerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrintServerService::class.java))
        }
    }
}
