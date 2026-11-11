package nexora.printer.core.billing

/** Whether this installation may print: always in a free build, and for subscribers in the Google Play build. */
fun interface Entitlement {
    fun canPrint(): Boolean
}
