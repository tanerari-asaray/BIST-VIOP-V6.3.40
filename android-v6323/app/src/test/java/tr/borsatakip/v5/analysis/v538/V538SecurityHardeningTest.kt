package tr.borsatakip.v5.analysis.v538

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V538SecurityHardeningTest {
    @Test fun validProviderBoundKeyVerifies() = assertEquals(V538AttestationStatus.VERIFIED, verify(envelope(), bistKey).status)

    @Test fun wrongProviderWithRightSignatureIsRejected() {
        val e = envelope(provider = "VIOP")
        assertEquals("PROVIDER_KEY_BINDING_NOT_TRUSTED", verify(e, bistKey).reason)
    }

    @Test fun wrongGenerationIsRejected() {
        val e = envelope(generation = 2)
        assertEquals("PROVIDER_KEY_BINDING_NOT_TRUSTED", verify(e, bistKey).reason)
    }

    @Test fun keyIdIsPartOfSignedEnvelope() {
        val e = envelope(); val signature = sign(bistKey, e)
        val tampered = e.copy(attestationKeyId = "key-b")
        val r = V538ServerAttestationVerifier.verify(registry, tampered, "key-b", 2, V538ServerAttestationVerifier.ALGORITHM, signature, 100_000, 3_000)
        assertEquals(V538AttestationStatus.REJECTED, r.status)
    }

    @Test fun exactExpiryBoundaryIsRejected() {
        val e = envelope().copy(issuedAt = 90_000, expiresAt = 97_000, serverTime = 95_000)
        assertEquals("SIGNATURE_EXPIRED", verify(e, bistKey, 100_000).reason)
    }

    @Test fun futureIssuedAtIsRejected() {
        val e = envelope().copy(issuedAt = 104_000, expiresAt = 110_000, serverTime = 105_000)
        assertEquals("SIGNATURE_NOT_YET_VALID", verify(e, bistKey, 100_000).reason)
    }

    @Test fun predictableShortEpochIsRejected() {
        assertEquals("SERVER_EPOCH_FORMAT_INVALID", verify(envelope(epoch = "epoch-1"), bistKey).reason)
    }

    @Test fun epochCreatedAfterIssueIsRejected() {
        val e = envelope().copy(serverEpochCreatedAt = 96_000, issuedAt = 95_000)
        assertEquals("EPOCH_CREATED_AFTER_SIGNATURE_ISSUE", verify(e, bistKey).reason)
    }

    @Test fun providerScopedClocksDoNotBleedAcrossProviders() {
        V538TrustedTimeRegistry.resetForTests()
        val a = V538TrustedTimeKey("BIST", "key-a", 1)
        val b = V538TrustedTimeKey("VIOP", "key-b", 2)
        V538TrustedTimeRegistry.markVerified(a, EPOCH_A, 100_000, 10_000)
        V538TrustedTimeRegistry.markVerified(b, EPOCH_B, 107_000, 10_000)
        assertEquals(101_000L, V538TrustedTimeRegistry.now(a, 1, 11_000))
        assertEquals(108_000L, V538TrustedTimeRegistry.now(b, 1, 11_000))
    }

    @Test fun trustedTimeFallsBackAfterMonotonicReset() {
        V538TrustedTimeRegistry.resetForTests()
        val key = V538TrustedTimeKey("BIST", "key-a", 1)
        V538TrustedTimeRegistry.markVerified(key, EPOCH_A, 100_000, 50_000)
        assertEquals(200_000L, V538TrustedTimeRegistry.now(key, 200_000, 1_000))
    }

    @Test fun sameEpochRequiresMonotonicSequence() {
        val first = token()
        val state = V538ReplayProtection.nextState(first, V538ReplayState(), 100_000)
        val replay = first.copy(nonce = "n2", requestId = "r2", snapshotId = "s2")
        assertFalse(V538ReplayProtection.validate(replay, state, 101_000, 3_000).accepted)
    }

    @Test fun newerEpochMayResetSequence() {
        val first = token().copy(serverSequence = 900)
        val state = V538ReplayProtection.nextState(first, V538ReplayState(), 100_000)
        val next = token(epoch = EPOCH_B, epochCreatedAt = 90_000, serverSequence = 1, nonce = "n2", requestId = "r2", snapshotId = "s2", expiresAt = 110_000)
        assertTrue(V538ReplayProtection.validate(next, state, 101_000, 3_000).accepted)
    }

    @Test fun oldEpochAfterTtlIsRejectedByCreatedAtRollback() {
        val a = token()
        var state = V538ReplayProtection.nextState(a, V538ReplayState(), 100_000)
        val b = token(epoch = EPOCH_B, epochCreatedAt = 90_000, serverSequence = 1, nonce = "n2", requestId = "r2", snapshotId = "s2", expiresAt = 110_000)
        state = V538ReplayProtection.nextState(b, state, 101_000)
        val old = token(serverSequence = 999, issuedAt = 1_000_000, serverTime = 1_000_001, expiresAt = 1_010_000, nonce = "n3", requestId = "r3", snapshotId = "s3")
        assertTrue("SERVER_EPOCH_ROLLBACK" in V538ReplayProtection.validate(old, state, 1_001_000, 3_000, 1_000).violations)
    }

    @Test fun replayTokenCarriesOwnTimeEvidence() {
        val future = token(issuedAt = 104_000, serverTime = 105_000, expiresAt = 110_000)
        assertTrue("REPLAY_TOKEN_NOT_YET_VALID" in V538ReplayProtection.validate(future, V538ReplayState(), 100_000, 3_000).violations)
    }

    @Test fun replayExactExpiryBoundaryIsRejected() {
        val exact = token(issuedAt = 90_000, serverTime = 95_000, expiresAt = 97_000)
        assertTrue("REPLAY_TOKEN_EXPIRED" in V538ReplayProtection.validate(exact, V538ReplayState(), 100_000, 3_000).violations)
    }

    @Test fun keyRotationCanKeepTwoProviderScopedKeysDuringOverlap() {
        val rotated = keyPair()
        val raw = listOf(
            keyEntry("BIST", "key-a", 1, bistKey),
            keyEntry("BIST", "key-a2", 2, rotated)
        ).joinToString(";")
        val reg = V538TrustedKeyRegistry.parse(raw)
        assertTrue(reg.get("BIST", "key-a", 1) != null)
        assertTrue(reg.get("BIST", "key-a2", 2) != null)
        assertTrue(reg.get("VIOP", "key-a2", 2) == null)
    }

    @Test fun legacyUnscopedRegistryEntryIsNotConfigured() {
        assertFalse(V538TrustedKeyRegistry.parse("key-a:not-a-provider-scoped-record").isConfigured())
    }

    private val bistKey by lazy { keyPair() }
    private val viopKey by lazy { keyPair() }
    private val registry by lazy {
        V538TrustedKeyRegistry.parse(listOf(
            keyEntry("BIST", "key-a", 1, bistKey),
            keyEntry("VIOP", "key-b", 2, viopKey)
        ).joinToString(";"))
    }

    private fun verify(e: V538AttestationEnvelope, kp: KeyPair, now: Long = 100_000) =
        V538ServerAttestationVerifier.verify(registry, e, e.attestationKeyId, e.attestationKeyGeneration, V538ServerAttestationVerifier.ALGORITHM, sign(kp, e), now, 3_000)

    private fun sign(kp: KeyPair, e: V538AttestationEnvelope): String {
        val s = Signature.getInstance(V538ServerAttestationVerifier.ALGORITHM)
        s.initSign(kp.private); s.update(e.canonical().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun keyEntry(provider: String, keyId: String, generation: Long, kp: KeyPair) =
        "$provider:$keyId:$generation:${Base64.getEncoder().encodeToString(kp.public.encoded)}"

    private fun envelope(provider: String = "BIST", generation: Long = 1, epoch: String = EPOCH_A) = V538AttestationEnvelope(
        "b".repeat(64), "r1", "s1", provider, "key-a", generation, epoch, 80_000,
        100_000, 99_000, 10, "n1", "V5.3.8", 95_000, 105_000
    )

    private fun token(
        epoch: String = EPOCH_A,
        epochCreatedAt: Long = 80_000,
        serverSequence: Long = 10,
        nonce: String = "n1",
        requestId: String = "r1",
        snapshotId: String = "s1",
        issuedAt: Long = 95_000,
        serverTime: Long = 100_000,
        expiresAt: Long = 105_000
    ) = V538ReplayToken("BIST", "key-a", 1, epoch, epochCreatedAt, serverSequence, nonce, requestId, snapshotId, issuedAt, serverTime, expiresAt)

    companion object {
        private const val EPOCH_A = "0123456789abcdef0123456789abcdef"
        private const val EPOCH_B = "abcdef0123456789abcdef0123456789"
    }
}
