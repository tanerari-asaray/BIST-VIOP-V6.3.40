package tr.borsatakip.v5.analysis.v533

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v531.ScoringConfig

class V533SnapshotInvariantTest {
    @Test fun validSnapshotAccepted() = accept(validSnapshot())
    @Test fun missingProviderRejected() = reject(rehash(validSnapshot().copy(provider = "")))
    @Test fun missingProviderIdRejected() = reject(rehash(validSnapshot().copy(providerId = "")))
    @Test fun missingProviderVersionRejected() = reject(rehash(validSnapshot().copy(providerVersion = "")))
    @Test fun missingSourceTypeRejected() = reject(rehash(validSnapshot().copy(sourceType = "")))
    @Test fun missingSnapshotIdRejected() = reject(rehash(validSnapshot().copy(snapshotId = "")))
    @Test fun missingRequestIdRejected() = reject(rehash(validSnapshot().copy(requestId = "")))
    @Test fun freshGreaterThanTrackedRejected() = reject(rehash(validSnapshot().copy(freshSymbols = 101)))
    @Test fun readyGreaterThanFreshRejected() = reject(rehash(validSnapshot().copy(readySymbols = 91, freshSymbols = 90)))
    @Test fun verifiedUniverseZeroRejected() = reject(rehash(validSnapshot().copy(universeVerified = true, universeSymbols = 0)))
    @Test fun unverifiedUniverseNonzeroRejected() = reject(rehash(validSnapshot().copy(universeVerified = false, universeSymbols = 100)))
    @Test fun trackedGreaterThanUniverseRejected() = reject(rehash(validSnapshot().copy(universeVerified = true, universeSymbols = 50)))
    @Test fun freshCoverageMismatchRejected() = reject(rehash(validSnapshot().copy(freshCoveragePct = 12.0)))
    @Test fun readyCoverageMismatchRejected() = reject(rehash(validSnapshot().copy(readyCoveragePct = 99.0)))
    @Test fun invalidSnapshotHashRejected() = reject(validSnapshot().copy(snapshotHash = "bad"))
    @Test fun duplicateSymbolsRejected() { val s = validSnapshot(); val refs = refs() + refs().first(); reject(rehash(s, refs), refs) }
    @Test fun badInputHashRejected() { val s = validSnapshot(); val refs = refs().toMutableList(); refs[0] = refs[0].copy(calculationInputHash = "bad"); reject(rehash(s, refs), refs) }
    @Test fun badOutputHashRejected() { val s = validSnapshot(); val refs = refs().toMutableList(); refs[0] = refs[0].copy(calculationOutputHash = "bad"); reject(rehash(s, refs), refs) }
    @Test fun engineVersionMismatchRejected() = reject(rehash(validSnapshot().copy(calculationEngineVersion = "V5.3.2")))
    @Test fun nonRemoteModeRejected() = reject(rehash(validSnapshot().copy(engineMode = "LOCAL")))
    @Test fun generatedAfterServerRejected() = reject(rehash(validSnapshot().copy(generatedAt = validSnapshot().serverTime + 20_000L)))
    @Test fun timeframeOutOfRangeRejected() = reject(rehash(validSnapshot().copy(analysisTimeframeMinutes = 240)))
    @Test fun cadenceOutOfRangeRejected() = reject(rehash(validSnapshot().copy(scanCadenceMinutes = 0)))
    @Test fun resultItemCountMismatchRejected() = reject(rehash(validSnapshot().copy(resultItemCount = 80)))
    @Test fun resultItemCountGreaterThanReadyRejected() = reject(rehash(validSnapshot().copy(resultItemCount = 81)))
    @Test fun itemOrderDoesNotChangeHash() { val s = validSnapshot(); val reversed = refs().reversed(); assertTrue(V533AuditHash.snapshotHash(s, refs()) == V533AuditHash.snapshotHash(s, reversed)) }
    @Test fun itemHashMutationChangesSnapshotHash() { val s = validSnapshot(); val a = V533AuditHash.snapshotHash(s, refs()); val r = refs().toMutableList(); r[0] = r[0].copy(calculationOutputHash = V533AuditHash.sha256Hex("changed")); val b = V533AuditHash.snapshotHash(s, r); assertTrue(a != b) }

    private fun refs() = listOf(
        V533SnapshotItemRef("THYAO", V533AuditHash.sha256Hex("in1"), V533AuditHash.sha256Hex("out1")),
        V533SnapshotItemRef("ASELS", V533AuditHash.sha256Hex("in2"), V533AuditHash.sha256Hex("out2"))
    )

    private fun validSnapshot(): V533SnapshotMetadata {
        val base = V533SnapshotMetadata(
            provider = "Provider A",
            providerId = "provider-a",
            providerVersion = "2026.09",
            sourceType = "LICENSED_MARKET_DATA",
            snapshotId = "snap-001",
            requestId = "req-001",
            generatedAt = 1_800_000_000_000L,
            serverTime = 1_800_000_005_000L,
            trackedSymbols = 100,
            freshSymbols = 90,
            readySymbols = 80,
            resultItemCount = 2,
            universeVerified = true,
            universeSymbols = 600,
            freshCoveragePct = 90.0,
            readyCoveragePct = 80.0,
            minReadyBars = 220,
            engineVersion = 1L,
            calculationEngineVersion = ScoringConfig.ENGINE_VERSION,
            engineMode = "REMOTE",
            analysisTimeframeMinutes = 5,
            scanCadenceMinutes = 15,
            scanMode = "MANUAL",
            snapshotHash = ""
        )
        return rehash(base)
    }

    private fun rehash(s: V533SnapshotMetadata, refs: List<V533SnapshotItemRef> = refs()) = s.copy(snapshotHash = V533AuditHash.snapshotHash(s, refs))
    private fun accept(s: V533SnapshotMetadata, refs: List<V533SnapshotItemRef> = refs()) = assertTrue(V533SnapshotInvariant.evaluate(s, refs).violations.joinToString(), V533SnapshotInvariant.evaluate(s, refs).accepted)
    private fun reject(s: V533SnapshotMetadata, refs: List<V533SnapshotItemRef> = refs()) = assertFalse(V533SnapshotInvariant.evaluate(s, refs).accepted)
}
