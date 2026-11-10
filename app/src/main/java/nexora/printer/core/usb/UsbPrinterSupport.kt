package nexora.printer.core.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

enum class UsbSupport(val label: String) {
    PRINTER_CLASS("USB printer class"),
    VENDOR_SPECIFIC("Vendor-specific (may work)"),
    UNSUPPORTED("No printable interface")
}

data class UsbDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val support: UsbSupport
) {
    val address: String get() = UsbPrinterSupport.address(vendorId, productId)
}

object UsbPrinterSupport {
    class Target(val usbInterface: UsbInterface, val bulkOut: UsbEndpoint, val support: UsbSupport)

    fun address(vendorId: Int, productId: Int) = "$vendorId:$productId"

    fun parseAddress(address: String): Pair<Int, Int>? {
        val parts = address.split(':')
        if (parts.size != 2) return null
        val v = parts[0].toIntOrNull() ?: return null
        val p = parts[1].toIntOrNull() ?: return null
        return v to p
    }

    /** Looks for a printer-class interface with a bulk OUT endpoint, then a vendor-specific one. */
    fun findTarget(device: UsbDevice): Target? {
        for (wanted in listOf(UsbConstants.USB_CLASS_PRINTER, UsbConstants.USB_CLASS_VENDOR_SPEC)) {
            for (i in 0 until device.interfaceCount) {
                val itf = device.getInterface(i)
                if (itf.interfaceClass != wanted) continue
                for (j in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                        val s = if (wanted == UsbConstants.USB_CLASS_PRINTER) UsbSupport.PRINTER_CLASS else UsbSupport.VENDOR_SPECIFIC
                        return Target(itf, ep, s)
                    }
                }
            }
        }
        return null
    }

    fun describe(device: UsbDevice) = UsbDeviceInfo(
        name = device.productName ?: device.deviceName,
        vendorId = device.vendorId,
        productId = device.productId,
        support = findTarget(device)?.support ?: UsbSupport.UNSUPPORTED
    )
}
