package nexora.printer

import nexora.printer.core.billing.LicenseVerifier
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class LicenseVerifierTest {
    private fun pair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private fun pub(kp: KeyPair) = Base64.getEncoder().encodeToString(kp.public.encoded)
    private fun code(kp: KeyPair, payload: String): String {
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(kp.private); update(payload.toByteArray()); sign() }
        return "NXP1." + enc.encodeToString(payload.toByteArray()) + "." + enc.encodeToString(sig)
    }

    @Test fun validCodeThatNeverExpires() {
        val kp = pair()
        val info = LicenseVerifier.verify(code(kp, "Suhaib - own use|0"), 20261111, pub(kp))
        assertEquals("Suhaib - own use", info?.label)
        assertNull(info?.untilYyyyMmDd)
    }

    @Test fun validUntilItsLastDay() {
        val kp = pair()
        val c = code(kp, "Demo shop|20270331")
        assertNotNull(LicenseVerifier.verify(c, 20270331, pub(kp)))
        assertNull(LicenseVerifier.verify(c, 20270401, pub(kp)))
    }

    @Test fun codeFromAnotherKeyIsRejected() {
        val mine = pair(); val other = pair()
        assertNull(LicenseVerifier.verify(code(other, "Forged|0"), 20261111, pub(mine)))
    }

    @Test fun changedTextIsRejected() {
        val kp = pair()
        val parts = code(kp, "Demo shop|20261201").split('.')
        val forged = "NXP1." + enc.encodeToString("Demo shop|0".toByteArray()) + "." + parts[2]
        assertNull(LicenseVerifier.verify(forged, 20261111, pub(kp)))
    }

    @Test fun garbageIsRejected() {
        val kp = pair()
        assertNull(LicenseVerifier.verify(null, 20261111, pub(kp)))
        assertNull(LicenseVerifier.verify("", 20261111, pub(kp)))
        assertNull(LicenseVerifier.verify("hello", 20261111, pub(kp)))
        assertNull(LicenseVerifier.verify("NXP1.abc.def", 20261111, pub(kp)))
        assertNull(LicenseVerifier.verify("NXP2." + "a.b", 20261111, pub(kp)))
    }
}
