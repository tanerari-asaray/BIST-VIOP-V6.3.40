package tr.borsatakip.v5.analysis.v537

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V537SecurityFixTest {
    @Test fun validAttestationAtTrustedNowIsVerified() {
        val kp = keyPair(); val env = envelope()
        val r = verify(kp, env, trustedNow = 100_000)
        assertEquals(V537AttestationStatus.VERIFIED, r.status)
    }

    @Test fun expiredButCorrectlySignedEnvelopeIsRejectedAgainstTrustedNow() {
        val kp = keyPair(); val env = envelope().copy(issuedAt = 80_000, expiresAt = 90_000, serverTime = 85_000)
        val r = verify(kp, env, trustedNow = 100_000)
        assertEquals("SIGNATURE_EXPIRED", r.reason)
    }

    @Test fun futureIssuedAtIsRejectedAgainstTrustedNow() {
        val kp = keyPair(); val env = envelope().copy(issuedAt = 104_001, expiresAt = 110_000, serverTime = 105_000)
        val r = verify(kp, env, trustedNow = 100_000, skew = 3_000)
        assertEquals("SIGNATURE_NOT_YET_VALID", r.reason)
    }

    @Test fun staleServerTimeIsRejectedEvenWithCorrectSignature() {
        val kp = keyPair(); val env = envelope().copy(issuedAt = 1_000, expiresAt = 121_000, serverTime = 1_000)
        val r = verify(kp, env, trustedNow = 130_001, skew = 3_000)
        assertEquals(V537AttestationStatus.REJECTED, r.status)
    }

    @Test fun modifiedEpochBreaksSignature() {
        val kp = keyPair(); val env = envelope(); val sig = sign(kp, env)
        val tampered = env.copy(serverEpoch = "epoch-B")
        val r = V537ServerAttestationVerifier.verify(registry(kp), tampered, "k1", V537ServerAttestationVerifier.ALGORITHM, sig, 100_000, 3_000)
        assertEquals("SIGNATURE_INVALID", r.reason)
    }


    @Test fun modifiedSnapshotHashBreaksSignature() = assertTamperRejected { it.copy(snapshotHash = "c".repeat(64)) }
    @Test fun modifiedRequestIdBreaksSignature() = assertTamperRejected { it.copy(requestId = "r2") }
    @Test fun modifiedSnapshotIdBreaksSignature() = assertTamperRejected { it.copy(snapshotId = "s2") }
    @Test fun modifiedSequenceBreaksSignature() = assertTamperRejected { it.copy(serverSequence = 11) }
    @Test fun modifiedNonceBreaksSignature() = assertTamperRejected { it.copy(nonce = "n2") }
    @Test fun modifiedEngineVersionBreaksSignature() = assertTamperRejected { it.copy(calculationEngineVersion = "V5.3.6") }

    @Test fun rotatedOutOldKeyIsRejected() {
        val old = keyPair(); val current = keyPair(); val env = envelope()
        val currentOnly = V537TrustedKeyRegistry.parse("new:${Base64.getEncoder().encodeToString(current.public.encoded)}")
        val r = V537ServerAttestationVerifier.verify(currentOnly, env, "old", V537ServerAttestationVerifier.ALGORITHM, sign(old, env), 100_000, 3_000)
        assertEquals("UNKNOWN_KEY_ID", r.reason)
    }

    @Test fun replayExpiryUsesTrustedNowNotPacketServerTime() {
        val token = token().copy(expiresAt = 90_000)
        val report = V537ReplayProtection.validate(token, V537ReplayState(), trustedNowMillis = 100_000, allowedClockSkewMs = 3_000)
        assertFalse(report.accepted)
        assertTrue("REPLAY_TOKEN_EXPIRED" in report.violations)
    }

    @Test fun sameEpochRequiresMonotonicSequence() {
        val first = token()
        val state = V537ReplayProtection.nextState(first, V537ReplayState(), acceptedAt = 100_000)
        val second = first.copy(serverSequence = 10, nonce = "n2", requestId = "r2", snapshotId = "s2")
        assertFalse(V537ReplayProtection.validate(second, state, 101_000, 3_000).accepted)
    }

    @Test fun newEpochMayResetSequence() {
        val first = token().copy(serverSequence = 900)
        val state = V537ReplayProtection.nextState(first, V537ReplayState(), acceptedAt = 100_000)
        val restarted = first.copy(serverEpoch = "epoch-B", serverSequence = 1, nonce = "n2", requestId = "r2", snapshotId = "s2", expiresAt = 110_000)
        assertTrue(V537ReplayProtection.validate(restarted, state, 101_000, 3_000).accepted)
    }

    @Test fun retiredEpochCannotBeReplayedWithinWindow() {
        val a = token().copy(serverEpoch = "epoch-A")
        var state = V537ReplayProtection.nextState(a, V537ReplayState(), 100_000)
        val b = a.copy(serverEpoch = "epoch-B", serverSequence = 1, nonce = "n2", requestId = "r2", snapshotId = "s2")
        state = V537ReplayProtection.nextState(b, state, 101_000)
        val replayA = a.copy(serverSequence = 999, nonce = "n3", requestId = "r3", snapshotId = "s3")
        val report = V537ReplayProtection.validate(replayA, state, 102_000, 3_000)
        assertFalse(report.accepted)
        assertTrue("SERVER_EPOCH_REPLAY" in report.violations)
    }

    @Test fun nonceReplayIsRejectedWithinTtl() {
        val first = token()
        val state = V537ReplayProtection.nextState(first, V537ReplayState(), 100_000)
        val second = first.copy(serverSequence = 11, requestId = "r2", snapshotId = "s2", expiresAt = 120_000)
        assertFalse(V537ReplayProtection.validate(second, state, 101_000, 3_000).accepted)
    }

    @Test fun expiredReplayEntriesArePrunedByTimestampNotSetOrder() {
        val window = 10_000L
        val t1 = token().copy(nonce = "old", requestId = "oldR", snapshotId = "oldS")
        var state = V537ReplayProtection.nextState(t1, V537ReplayState(), 10_000, window)
        val t2 = token().copy(serverSequence = 11, nonce = "new", requestId = "newR", snapshotId = "newS", expiresAt = 40_000)
        state = V537ReplayProtection.nextState(t2, state, 25_001, window)
        assertTrue(state.nonces.none { it.fingerprint == V537ReplayProtection.fingerprint("old") })
        assertTrue(state.nonces.any { it.fingerprint == V537ReplayProtection.fingerprint("new") })
    }

    @Test fun fingerprintIsDeterministicAndOpaque() {
        val a = V537ReplayProtection.fingerprint("secret-nonce")
        val b = V537ReplayProtection.fingerprint("secret-nonce")
        assertEquals(a, b)
        assertNotEquals("secret-nonce", a)
        assertTrue(a.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun trustedTimeUsesElapsedRealtimeAfterAnchor() {
        V537TrustedTimeAnchor.resetForTests()
        assertEquals(100_000L, V537TrustedTimeAnchor.now(100_000, 10_000))
        V537TrustedTimeAnchor.markVerified(101_000, 11_000)
        assertEquals(106_000L, V537TrustedTimeAnchor.now(1L, 16_000))
    }

    @Test fun trustedTimeFallsBackAfterElapsedRealtimeReset() {
        V537TrustedTimeAnchor.resetForTests()
        V537TrustedTimeAnchor.markVerified(100_000, 50_000)
        assertEquals(200_000L, V537TrustedTimeAnchor.now(200_000, 1_000))
    }

    @Test fun malformedKeyIsNotConfigured() {
        assertFalse(V537TrustedKeyRegistry.parse("k1:not-base64").isConfigured())
    }

    @Test fun malformedSignatureIsRejected() {
        val kp = keyPair(); val env = envelope()
        val r = V537ServerAttestationVerifier.verify(registry(kp), env, "k1", V537ServerAttestationVerifier.ALGORITHM, "%%%", 100_000, 3_000)
        assertEquals("SIGNATURE_BASE64_INVALID", r.reason)
    }


    private fun assertTamperRejected(change: (V537AttestationEnvelope) -> V537AttestationEnvelope) {
        val kp = keyPair(); val original = envelope(); val sig = sign(kp, original)
        val r = V537ServerAttestationVerifier.verify(registry(kp), change(original), "k1", V537ServerAttestationVerifier.ALGORITHM, sig, 100_000, 3_000)
        assertEquals("SIGNATURE_INVALID", r.reason)
    }

    private fun verify(kp: KeyPair, env: V537AttestationEnvelope, trustedNow: Long, skew: Long = 3_000) =
        V537ServerAttestationVerifier.verify(registry(kp), env, "k1", V537ServerAttestationVerifier.ALGORITHM, sign(kp, env), trustedNow, skew)

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun registry(kp: KeyPair) = V537TrustedKeyRegistry.parse("k1:${Base64.getEncoder().encodeToString(kp.public.encoded)}")
    private fun sign(kp: KeyPair, env: V537AttestationEnvelope): String {
        val s = Signature.getInstance(V537ServerAttestationVerifier.ALGORITHM)
        s.initSign(kp.private)
        s.update(env.canonical().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }
    private fun envelope() = V537AttestationEnvelope(
        snapshotHash = "b".repeat(64), requestId = "r1", snapshotId = "s1", providerId = "p1",
        serverEpoch = "epoch-A", serverTime = 100_000, generatedAt = 99_000, serverSequence = 10,
        nonce = "n1", calculationEngineVersion = "V5.3.7", issuedAt = 95_000, expiresAt = 105_000
    )
    private fun token() = V537ReplayToken("p1", "epoch-A", 10, "n1", "r1", "s1", 105_000)
}
