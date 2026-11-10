package nexora.printer.core.utils

object PageRange {
    /**
     * Parses "1-3,5,8-" style input (1-based) into sorted, distinct 0-based indices.
     * Blank input selects all pages. Returns null when the input is invalid.
     */
    fun parse(input: String, pageCount: Int): List<Int>? {
        if (pageCount <= 0) return null
        if (input.isBlank()) return (0 until pageCount).toList()
        val result = sortedSetOf<Int>()
        for (part in input.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) return null
            if ('-' in p) {
                val bits = p.split('-')
                if (bits.size != 2) return null
                val from = bits[0].trim().toIntOrNull() ?: return null
                val to = if (bits[1].isBlank()) pageCount else (bits[1].trim().toIntOrNull() ?: return null)
                if (from < 1 || to > pageCount || from > to) return null
                for (i in from..to) result += i - 1
            } else {
                val n = p.toIntOrNull() ?: return null
                if (n < 1 || n > pageCount) return null
                result += n - 1
            }
        }
        return result.toList()
    }
}
