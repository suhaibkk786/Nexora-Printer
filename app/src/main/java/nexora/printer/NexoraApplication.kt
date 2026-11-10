package nexora.printer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import nexora.printer.core.printer.ConnectionCoordinator
import javax.inject.Inject

@HiltAndroidApp
class NexoraApplication : Application() {
    @Inject lateinit var coordinator: ConnectionCoordinator

    override fun onCreate() {
        super.onCreate()
        coordinator.start()
    }
}
