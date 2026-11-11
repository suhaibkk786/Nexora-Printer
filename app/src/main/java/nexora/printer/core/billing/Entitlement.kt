package nexora.printer.core.billing

/** Whether this installation has Nexora Printer Pro: always in a free build, and for subscribers in the Google Play build. */
fun interface Entitlement {
    fun isPro(): Boolean
}

/**
 * Printing is free for everyone. The Nexora logo window that appears while a ticket from a POS prints, and the logo shown when
 * the app opens, are part of the free version; Pro (the subscription) lets the owner switch each of them off.
 */
object PrintWindowPolicy {
    /** [chosen] is the setting ("Show print window" / "Show logo when the app opens"); without Pro it cannot be switched off. */
    fun show(chosen: Boolean, pro: Boolean): Boolean = !pro || chosen
}
