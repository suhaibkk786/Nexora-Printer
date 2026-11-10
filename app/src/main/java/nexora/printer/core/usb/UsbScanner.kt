package nexora.printer.core.usb

import android.content.Context
import android.hardware.usb.UsbManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Lists attached USB devices with an honest compatibility verdict from their interfaces. */
    fun devices(): List<UsbDeviceInfo> {
        val m = context.getSystemService(UsbManager::class.java) ?: return emptyList()
        return m.deviceList.values.map { UsbPrinterSupport.describe(it) }
            .sortedBy { it.support.ordinal }
    }
}
