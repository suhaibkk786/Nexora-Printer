package nexora.printer

import nexora.printer.service.EscPosStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class EscPosStatusTest {
    @Test
    fun statusQueriesAreAnsweredAndNotPrinted() {
        val out = ByteArrayOutputStream()
        // "are you there?" (DLE EOT 1), a line of text, paper status (GS r 1)
        val input = byteArrayOf(0x10, 0x04, 0x01, 'H'.code.toByte(), 'i'.code.toByte(), 0x1D, 0x72, 0x01, 0x0A)
        val printable = EscPosStatus.answer(input, input.size, out)
        assertArrayEquals(byteArrayOf('H'.code.toByte(), 'i'.code.toByte(), 0x0A), printable)
        assertArrayEquals(byteArrayOf(0x12, 0x00), out.toByteArray())
    }

    @Test
    fun ordinaryTicketsPassUnchanged() {
        val out = ByteArrayOutputStream()
        val input = byteArrayOf(0x1B, 0x40, 'A'.code.toByte(), 0x0A, 0x1D, 0x56, 0x00)
        val printable = EscPosStatus.answer(input, input.size, out)
        assertArrayEquals(input, printable)
        assertEquals(0, out.size())
    }
}
