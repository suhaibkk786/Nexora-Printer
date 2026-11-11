package nexora.printer.service

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Many POS apps ask the printer "are you there?" before sending: DLE EOT n (10 04 n) and GS r n (1D 72 n). They are answered here
 * ("online, paper ok") and removed, so the app believes it talks to a real printer. Everything else is returned for printing.
 */
object EscPosStatus {
    fun answer(buf: ByteArray, length: Int, output: OutputStream): ByteArray {
        val keep = ByteArrayOutputStream(length)
        var i = 0
        while (i < length) {
            val b = buf[i].toInt() and 0xFF
            if (b == 0x10 && i + 2 < length && buf[i + 1].toInt() == 0x04 && (buf[i + 2].toInt() in 1..4)) {
                runCatching { output.write(0x12); output.flush() }
                i += 3
            } else if (b == 0x1D && i + 2 < length && buf[i + 1].toInt() == 0x72 && (buf[i + 2].toInt() in 1..2)) {
                runCatching { output.write(0x00); output.flush() }
                i += 3
            } else {
                keep.write(b)
                i++
            }
        }
        return keep.toByteArray()
    }
}
