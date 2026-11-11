package nexora.printer.core.billing

/** Whether this installation has Nexora Printer Pro: always in a free build, and for subscribers in the Google Play build. */
fun interface Entitlement {
    fun isPro(): Boolean
}

/**
 * Printing is free for everyone. The Nexora logo window that appears while a ticket from a POS prints is part of the free
 * version; Pro (the subscription) lets the owner switch it off.
 */
object PrintWindowPolicy {
    /** [chosen] is the "Show print window" setting; without Pro it cannot be switched off. */
    fun show(chosen: Boolean, pro: Boolean): Boolean = !pro || chosen
}
