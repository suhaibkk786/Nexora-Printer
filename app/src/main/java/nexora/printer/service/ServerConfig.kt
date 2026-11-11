package nexora.printer.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Settings of the silent-printing service (kept in SharedPreferences so the boot receiver can read them without a database). */
@Singleton
class ServerConfig @Inject constructor(@ApplicationContext context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Print POS tickets in the background, without opening the app. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) { sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    /** Virtual network printer (raw port 9100): other POS apps print to this tablet like to a LAN receipt printer. */
    var lanEnabled: Boolean
        get() = sp.getBoolean(KEY_LAN, true)
        set(v) { sp.edit().putBoolean(KEY_LAN, v).apply() }

    /** false: only apps on this tablet (127.0.0.1); true: also other devices on the shop Wi-Fi. */
    var lanShared: Boolean
        get() = sp.getBoolean(KEY_LAN_SHARED, false)
        set(v) { sp.edit().putBoolean(KEY_LAN_SHARED, v).apply() }

    /** Virtual Bluetooth printer: another phone / POS app pairs with this tablet and prints to it ("nearby device"). */
    var btEnabled: Boolean
        get() = sp.getBoolean(KEY_BT, false)
        set(v) { sp.edit().putBoolean(KEY_BT, v).apply() }

    /** The background service is needed when any way of receiving tickets is on. */
    val anyOn: Boolean get() = enabled || lanEnabled || btEnabled

    /** Where a bill goes: the printer, the printer and a copy in the Gallery, or only the Gallery (no printer needed). */
    enum class OutputMode { PRINTER, PRINTER_AND_GALLERY, GALLERY_ONLY }

    var outputMode: OutputMode
        get() = runCatching { OutputMode.valueOf(sp.getString(KEY_OUTPUT, null) ?: "PRINTER") }.getOrDefault(OutputMode.PRINTER)
        set(v) { sp.edit().putString(KEY_OUTPUT, v.name).apply() }

    /** What the Gallery saving did last, shown in Settings so the owner can tell whether it works. */
    val galleryCount: Int get() = sp.getInt(KEY_G_COUNT, 0)
    val galleryLastAt: Long get() = sp.getLong(KEY_G_AT, 0L)
    val galleryLastPath: String? get() = sp.getString(KEY_G_PATH, null)
    val galleryError: String? get() = sp.getString(KEY_G_ERROR, null)

    fun galleryDone(path: String?, error: String?) {
        val e = sp.edit()
        if (path != null) { e.putInt(KEY_G_COUNT, galleryCount + 1).putLong(KEY_G_AT, System.currentTimeMillis()).putString(KEY_G_PATH, path).remove(KEY_G_ERROR) }
        else e.putString(KEY_G_ERROR, error ?: "unknown error").putLong(KEY_G_AT, System.currentTimeMillis())
        e.apply()
    }

    /** POS web addresses allowed to print, comma separated (empty = any web page on this device). */
    var allowedOrigins: String
        get() = sp.getString(KEY_ORIGINS, "") ?: ""
        set(v) { sp.edit().putString(KEY_ORIGINS, v.trim()).apply() }

    fun originAllowed(origin: String?): Boolean = matches(allowedOrigins, origin)

    companion object {
        const val FILE = "nexora_service"
        const val KEY_ENABLED = "enabled"
        const val KEY_ORIGINS = "origins"
        const val KEY_LAN = "lan"
        const val KEY_LAN_SHARED = "lan_shared"
        const val KEY_BT = "bt"
        const val KEY_OUTPUT = "output_mode"
        const val KEY_G_COUNT = "gallery_count"
        const val KEY_G_AT = "gallery_at"
        const val KEY_G_PATH = "gallery_path"
        const val KEY_G_ERROR = "gallery_error"
        const val PORT = 40214
        /** The standard raw printing port of network receipt printers. */
        const val LAN_PORT = 9100

        /** [entries] are addresses such as `pos.shop.com` or `https://pos.shop.com`; the web page's Origin must match one. */
        fun matches(entries: String, origin: String?): Boolean {
            val list = entries.split(',', ' ', '\n', ';').map { it.trim().trimEnd('/').lowercase() }.filter { it.isNotEmpty() }
            if (list.isEmpty()) return true
            if (origin == null) return false
            val o = origin.trim().trimEnd('/').lowercase()
            val oNoScheme = o.substringAfter("://")
            return list.any { it == o || it.substringAfter("://") == oNoScheme }
        }
    }
}
