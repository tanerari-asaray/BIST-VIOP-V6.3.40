package tr.borsatakip.v5.analysis.v536

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V536ProductionHardeningTest {
    @Test fun oneMinuteFutureToleranceIsThreeSeconds() {
        assertTrue(V536ClockIntegrity.validateSnapshot(100_000, 103_000, 1).accepted)
        assertFalse(V536ClockIntegrity.validateSnapshot(100_000, 103_001, 1).accepted)
    }

    @Test fun threeMinuteFutureToleranceIsFiveSeconds() {
        assertTrue(V536ClockIntegrity.validateSnapshot(100_000, 105_000, 3).accepted)
        assertFalse(V536ClockIntegrity.validateSnapshot(100_000, 105_001, 3).accepted)
    }

    @Test fun longerTimeframesUseWiderButBoundedTolerance() {
        assertEquals(7_500L, V536ClockPolicy.forTimeframe(15).futureToleranceMs)
        assertEquals(10_000L, V536ClockPolicy.forTimeframe(60).futureToleranceMs)
        assertEquals(15_000L, V536ClockPolicy.forTimeframe(61).futureToleranceMs)
    }

    @Test fun validEcdsaSignatureIsVerified() {
        val kp = keyPair()
        val env = envelope()
        val registry = registry("k1", kp)
        val result = V536ServerAttestationVerifier.verify(registry, env, "k1", V536ServerAttestationVerifier.ALGORITHM, sign(kp, env))
        assertEquals(V536AttestationStatus.VERIFIED, result.status)
    }

    @Test fun keyRotationAllowsSecondTrustedKey() {
        val a = keyPair(); val b = keyPair(); val env = envelope()
        val raw = "a:${Base64.getEncoder().encodeToString(a.public.encoded)};b:${Base64.getEncoder().encodeToString(b.public.encoded)}"
        val result = V536ServerAttestationVerifier.verify(V536TrustedKeyRegistry.parse(raw), env, "b", V536ServerAttestationVerifier.ALGORITHM, sign(b, env))
        assertEquals(V536AttestationStatus.VERIFIED, result.status)
    }

    @Test fun wrongKeyIsRejected() {
        val a = keyPair(); val b = keyPair(); val env = envelope()
        val raw = "a:${Base64.getEncoder().encodeToString(a.public.encoded)};b:${Base64.getEncoder().encodeToString(b.public.encoded)}"
        val result = V536ServerAttestationVerifier.verify(V536TrustedKeyRegistry.parse(raw), env, "b", V536ServerAttestationVerifier.ALGORITHM, sign(a, env))
        assertEquals(V536AttestationStatus.REJECTED, result.status)
    }

    @Test fun noTrustedKeyIsNotConfiguredNotVerified() {
        val kp = keyPair(); val env = envelope()
        val result = V536ServerAttestationVerifier.verify(V536TrustedKeyRegistry.parse(""), env, "k1", V536ServerAttestationVerifier.ALGORITHM, sign(kp, env))
        assertEquals(V536AttestationStatus.NOT_CONFIGURED, result.status)
    }

    @Test fun expiredAttestationIsRejected() {
        val kp = keyPair(); val env = envelope().copy(serverTime = 106_000)
        val result = V536ServerAttestationVerifier.verify(registry("k1", kp), env, "k1", V536ServerAttestationVerifier.ALGORITHM, sign(kp, env))
        assertEquals(V536AttestationStatus.REJECTED, result.status)
    }

    @Test fun sameSequenceIsReplay() {
        val first = token()
        val state = V536ReplayProtection.nextState(first, V536ReplayState())
        assertFalse(V536ReplayProtection.validate(first.copy(nonce="n2", requestId="r2", snapshotId="s2"), state, 100_000).accepted)
    }

    @Test fun sameNonceIsReplay() {
        val first = token(); val state = V536ReplayProtection.nextState(first, V536ReplayState())
        assertFalse(V536ReplayProtection.validate(first.copy(serverSequence=11, requestId="r2", snapshotId="s2"), state, 100_000).accepted)
    }

    @Test fun sameRequestIdIsReplay() {
        val first = token(); val state = V536ReplayProtection.nextState(first, V536ReplayState())
        assertFalse(V536ReplayProtection.validate(first.copy(serverSequence=11, nonce="n2", snapshotId="s2"), state, 100_000).accepted)
    }

    @Test fun sameSnapshotIdIsReplay() {
        val first = token(); val state = V536ReplayProtection.nextState(first, V536ReplayState())
        assertFalse(V536ReplayProtection.validate(first.copy(serverSequence=11, nonce="n2", requestId="r2"), state, 100_000).accepted)
    }

    @Test fun strictlyNewReplayTokenIsAccepted() {
        val first = token(); val state = V536ReplayProtection.nextState(first, V536ReplayState())
        assertTrue(V536ReplayProtection.validate(first.copy(serverSequence=11, nonce="n2", requestId="r2", snapshotId="s2"), state, 100_000).accepted)
    }

    @Test fun observabilityDoesNotNeedMarketPayload() {
        val o = V536RemoteObservability()
        o.record(V536RemoteEvent(V536RemoteEventType.REQUEST_STARTED, 1, "START", "V5.3.6"))
        o.record(V536RemoteEvent(V536RemoteEventType.REQUEST_STARTED, 2, "START", "V5.3.6"))
        assertEquals(2L, o.count(V536RemoteEventType.REQUEST_STARTED))
        assertEquals(2L, o.lastEvent()?.atMillis)
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun registry(id: String, kp: KeyPair) = V536TrustedKeyRegistry.parse("$id:${Base64.getEncoder().encodeToString(kp.public.encoded)}")
    private fun envelope() = V536AttestationEnvelope("b".repeat(64), "r1", "s1", "p1", 100_000, 99_000, 10, "n1", "V5.3.6", 95_000, 105_000)
    private fun sign(kp: KeyPair, env: V536AttestationEnvelope): String {
        val s = Signature.getInstance(V536ServerAttestationVerifier.ALGORITHM)
        s.initSign(kp.private); s.update(env.canonical().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }
    private fun token() = V536ReplayToken("p1", 10, "n1", "r1", "s1", 105_000)
}
