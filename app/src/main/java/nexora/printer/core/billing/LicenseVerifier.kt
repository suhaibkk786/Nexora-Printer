package nexora.printer.core.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** What a license code says: who it is for and until when (null = never expires). */
data class LicenseInfo(val label: String, val untilYyyyMmDd: Int?)

/**
 * Pro without a Google Play subscription: a license code `NXP1.<label|lastDay>.<signature>` made by the owner with
 * `tools/make_license.py`. The signature (ECDSA P-256) is checked against the public key built into the app, so a code
 * cannot be made without the owner's private key, which never leaves the owner's PC.
 */
object LicenseVerifier {
    /** The public half of the owner's signing key (safe to publish). */
    const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAES20ZkcuZEwGmTXrWingANWW+5qJAfEdB7JrRIVbKF2UQqd7fT3zlsUlOpcwQnkcfFj1FK7jaTNkjBb0kN5TROQ=="

    /** The license inside [code] when its signature is right and it has not expired on [today] (yyyyMMdd), else null. */
    fun verify(code: String?, today: Int, publicKeyB64: String = PUBLIC_KEY): LicenseInfo? {
        val parts = code?.trim()?.split('.') ?: return null
        if (parts.size != 3 || parts[0] != "NXP1") return null
        return try {
            val payload = Base64.getUrlDecoder().decode(parts[1])
            val signature = Base64.getUrlDecoder().decode(parts[2])
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)))
            val ok = Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(payload)
                verify(signature)
            }
            if (!ok) return null
            val text = String(payload, Charsets.UTF_8)
            val bar = text.lastIndexOf('|')
            if (bar < 0) return null
            val until = text.substring(bar + 1).toIntOrNull() ?: return null
            if (until != 0 && today > until) return null
            LicenseInfo(text.substring(0, bar), if (until == 0) null else until)
        } catch (e: Exception) {
            null
        }
    }
}
