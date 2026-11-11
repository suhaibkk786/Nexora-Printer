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

    /** POS web addresses allowed to print, comma separated (empty = any web page on this device). */
    var allowedOrigins: String
        get() = sp.getString(KEY_ORIGINS, "") ?: ""
        set(v) { sp.edit().putString(KEY_ORIGINS, v.trim()).apply() }

    fun originAllowed(origin: String?): Boolean = matches(allowedOrigins, origin)

    companion object {
        const val FILE = "nexora_service"
        const val KEY_ENABLED = "enabled"
        const val KEY_ORIGINS = "origins"
        const val PORT = 40214

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
