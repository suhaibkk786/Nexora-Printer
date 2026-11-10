package nexora.printer.core.escpos

object BarcodeValidator {
    /** Returns an error message, or null when [data] is valid for [type]. */
    fun validate(type: BarcodeType, data: String): String? {
        if (data.isEmpty()) return "Enter barcode data"
        val digits = data.all { it in '0'..'9' }
        return when (type) {
            BarcodeType.EAN13 ->
                if (digits && data.length in 12..13) null else "EAN-13 needs 12 or 13 digits"
            BarcodeType.EAN8 ->
                if (digits && data.length in 7..8) null else "EAN-8 needs 7 or 8 digits"
            BarcodeType.UPC_A ->
                if (digits && data.length in 11..12) null else "UPC-A needs 11 or 12 digits"
            BarcodeType.CODE39 ->
                if (data.all { it in '0'..'9' || it in 'A'..'Z' || it in " -.\$/+%" }) null
                else "Code 39 allows 0-9, A-Z, space and - . \$ / + %"
            BarcodeType.CODE128 ->
                if (data.length <= 80 && data.all { it.code in 32..126 }) null
                else "Code 128 (set B) allows printable ASCII, max 80 characters"
        }
    }
}
