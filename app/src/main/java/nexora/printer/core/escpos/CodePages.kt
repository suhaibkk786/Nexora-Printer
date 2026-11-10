package nexora.printer.core.escpos

import java.nio.charset.Charset

/**
 * Common ESC/POS code page presets. The [id] is what ESC t sends; the [charsetName] is the
 * Java charset used to encode text. Printers differ in the ids they map, so profiles allow
 * overriding both independently.
 */
enum class EscPosCodePage(val id: Int, val charsetName: String, val label: String) {
    PC437(0, "IBM437", "PC437 (USA)"),
    PC850(2, "IBM850", "PC850 (Multilingual)"),
    PC860(3, "IBM860", "PC860 (Portuguese)"),
    PC863(4, "IBM863", "PC863 (Canadian-French)"),
    PC865(5, "IBM865", "PC865 (Nordic)"),
    WPC1252(16, "windows-1252", "WPC1252 (Latin)"),
    PC866(17, "IBM866", "PC866 (Cyrillic)"),
    PC852(18, "IBM852", "PC852 (Latin 2)"),
    PC858(19, "IBM00858", "PC858 (Euro)"),
    PC864(22, "IBM864", "PC864 (Arabic)"),
    WPC1256(50, "windows-1256", "WPC1256 (Arabic, some models)");

    val charset: Charset
        get() = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.ISO_8859_1)
}
