package nexora.printer.core.escpos

object ReceiptFormatter {
    /** Font A is 12 dots wide, font B 9 dots wide, multiplied by the size multiplier. */
    fun charsPerLine(printableDots: Int, sizeMultiplier: Int = 1, fontB: Boolean = false): Int {
        val cell = (if (fontB) 9 else 12) * sizeMultiplier.coerceAtLeast(1)
        return (printableDots / cell).coerceAtLeast(1)
    }

    fun wrap(text: String, width: Int): List<String> {
        val w = width.coerceAtLeast(1)
        val out = mutableListOf<String>()
        for (para in text.replace("\r", "").split('\n')) {
            if (para.isEmpty()) {
                out += ""
                continue
            }
            var line = StringBuilder()
            for (word in para.split(' ')) {
                var rest = word
                while (rest.length > w) {
                    if (line.isNotEmpty()) {
                        out += line.toString()
                        line = StringBuilder()
                    }
                    out += rest.take(w)
                    rest = rest.drop(w)
                }
                val needed = if (line.isEmpty()) rest.length else line.length + 1 + rest.length
                if (needed > w) {
                    out += line.toString()
                    line = StringBuilder(rest)
                } else {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(rest)
                }
            }
            out += line.toString()
        }
        return out
    }

    fun twoColumns(left: String, right: String, width: Int): String {
        val l = if (left.length + right.length + 1 > width) left.take((width - right.length - 1).coerceAtLeast(0)) else left
        val gap = (width - l.length - right.length).coerceAtLeast(1)
        return l + " ".repeat(gap) + right
    }

    fun center(text: String, width: Int): String {
        val t = text.take(width)
        return " ".repeat((width - t.length) / 2) + t
    }

    fun divider(width: Int, ch: Char = '-') = ch.toString().repeat(width.coerceAtLeast(1))
}
