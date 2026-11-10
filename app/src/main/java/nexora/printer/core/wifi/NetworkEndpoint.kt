package nexora.printer.core.wifi

data class NetworkEndpoint(val host: String, val port: Int = DEFAULT_PORT) {
    fun toAddress() = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 9100

        /** Accepts "host" or "host:port". Returns null when invalid. */
        fun parse(address: String): NetworkEndpoint? {
            val a = address.trim()
            if (a.isEmpty() || a.any { it.isWhitespace() }) return null
            val idx = a.lastIndexOf(':')
            if (idx < 0) return NetworkEndpoint(a)
            val host = a.substring(0, idx)
            val port = a.substring(idx + 1).toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return NetworkEndpoint(host, port)
        }
    }
}
