package tr.borsatakip.v5.scan

import tr.borsatakip.v5.data.ProviderReadinessSnapshot
import tr.borsatakip.v5.data.ProviderState
import tr.borsatakip.v5.data.FullUniverseScanSnapshotStore

/** Independent, fail-closed audit. A diagnostic is never a trading authorization. */
data class IndependentAuditFinding(val code: String, val severity: String, val evidence: String)
data class IndependentAuditResult(
    val liveSignalEligible: Boolean,
    val coveragePercent: Double?,
    val scanAgeMs: Long?,
    val missingSymbols: Int?,
    val findings: List<IndependentAuditFinding>
)

object IndependentScanAudit {
    private const val MAX_SCAN_AGE_MS = 5 * 60_000L
    private const val MAX_PROVIDER_PROBE_AGE_MS = 5 * 60_000L

    fun evaluate(provider: ProviderReadinessSnapshot, scan: ScanState?, now: Long, delayed: FullUniverseScanSnapshotStore.Snapshot? = null): IndependentAuditResult {
        val findings = mutableListOf<IndependentAuditFinding>()
        fun issue(code: String, severity: String, evidence: String) {
            findings += IndependentAuditFinding(code, severity, evidence)
        }
        val probeAge = provider.testedAt.takeIf { it in 1..now }?.let { now - it }
        if (provider.state != ProviderState.PROVIDER_READY || probeAge == null || probeAge > MAX_PROVIDER_PROBE_AGE_MS) {
            issue("PROVIDER_NOT_FRESHLY_VERIFIED", "P0", "state=${provider.state}; probeAgeMs=${probeAge ?: "UNKNOWN"}")
        }
        val completedAt = scan?.scanRun?.scanCompletedAt ?: 0L
        val age = completedAt.takeIf { it in 1..now }?.let { now - it }
        if (scan == null) {
            if (delayed == null) issue("SCAN_NOT_AVAILABLE", "P0", "No retained analysis scan or delayed universe observation")
            else {
                issue("REALTIME_SCAN_NOT_AVAILABLE", "P0", "Legacy real-time analysis scan unavailable; delayed observation is separate")
                issue("DELAYED_OBSERVATION_NOT_TRADE_ELIGIBLE", "P0",
                    "mode=${delayed.mode}; status=${delayed.status}; BIST attempted=${delayed.triedBist}/${delayed.discoveredBist}; observed=${delayed.successfulBist}; not a live trading signal")
            }
        }
        else {
            if (scan.scanMode != BistScanMode.REALTIME_ONLY) issue("DELAYED_MODE", "P0", scan.scanMode.name)
            if (scan.scanRun?.status?.name != "COMPLETE") issue("SCAN_PARTIAL_OR_UNKNOWN", "P1", "runStatus=${scan.scanRun?.status}")
            if (age == null || age > MAX_SCAN_AGE_MS) issue("SCAN_RESULT_STALE", "P0", "ageMs=${age ?: "UNKNOWN"}")
            if (scan.integrityRejected > 0) {
                if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS)
                    issue("DELAYED_RESULTS_NOT_TRADE_ELIGIBLE", "P0", "count=${scan.integrityRejected}; expected fail-closed for delayed source")
                else issue("INTEGRITY_REJECTED", "P0", "count=${scan.integrityRejected}")
            }
            if (scan.total <= 0 || scan.processed !in 0..scan.total || scan.successful !in 0..scan.total ||
                scan.results.size > scan.total || scan.integrityRejected !in 0..scan.total)
                issue("COUNTER_RANGE_INVALID", "P1", "total=${scan.total}; processed=${scan.processed}")
            if (scan.universeSymbols.isNotEmpty()) {
                val universe = scan.universeSymbols.map { it.trim().uppercase(java.util.Locale.ROOT) }.toSet()
                val fetched = scan.scannedSymbols.map { it.trim().uppercase(java.util.Locale.ROOT) }.toSet()
                if (!universe.containsAll(fetched)) issue("FETCHED_OUTSIDE_UNIVERSE", "P1", "unexpected=${(fetched - universe).size}")
                val missing = universe - fetched
                val diagnosed = scan.missingSymbolDiagnostics.map { it.symbol.trim().uppercase(java.util.Locale.ROOT) }.toSet()
                if (missing != diagnosed) issue("MISSING_DIAGNOSTIC_MISMATCH", "P1", "missing=${missing.size}; diagnosed=${diagnosed.size}")
                if (missing.isNotEmpty()) issue("COVERAGE_INCOMPLETE", "P1", "missing=${missing.size}")
            } else issue("UNIVERSE_EVIDENCE_MISSING", "P1", "No retained symbol universe")
            if (scan.unclassifiedMissingSymbols.isNotEmpty()) issue("UNKNOWN_MISSING_ROOT_CAUSE", "P1", "count=${scan.unclassifiedMissingSymbols.size}")
        }
        val coverage = scan?.let { current ->
            if (current.total > 0) 100.0 * current.scannedSymbols.distinct().size / current.total else null
        }
        val missing = scan?.universeSymbols?.takeIf { it.isNotEmpty() }?.let { universe ->
            universe.map { it.uppercase(java.util.Locale.ROOT) }.toSet().minus(
                scan.scannedSymbols.map { it.uppercase(java.util.Locale.ROOT) }.toSet()
            ).size
        }
        // Eligibility is restricted to this audit's observable gates; never claims order readiness.
        return IndependentAuditResult(findings.isEmpty(), coverage, age, missing, findings)
    }
}
