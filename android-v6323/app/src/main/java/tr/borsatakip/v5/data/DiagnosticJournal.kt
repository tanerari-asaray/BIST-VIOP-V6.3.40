package tr.borsatakip.v5.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import tr.borsatakip.v5.scan.ScanState
import tr.borsatakip.v5.scan.ScanStatus
import java.util.UUID

/** Bounded, offline-only, evidence journal. No URLs, quotes, keys or exception messages. */
class DiagnosticJournal(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("diagnostic_journal_v3", Context.MODE_PRIVATE)

    data class Event(
        val at: Long,
        val runId: String,
        val subsystem: String,
        val phase: String,
        val code: String,
        val durationMs: Long?,
        val count: Int?
    ) {
        fun json() = JSONObject().apply {
            put("atEpochMs", at)
            put("runId", runId)
            put("subsystem", subsystem)
            put("phase", phase)
            put("code", code)
            put("durationMs", durationMs ?: JSONObject.NULL)
            put("affectedCount", count ?: JSONObject.NULL)
        }
    }

    fun record(subsystem: String, phase: String, code: String, runId: String = "", durationMs: Long? = null, count: Int? = null) {
        val safe = Regex("[A-Z][A-Z0-9_]{0,63}")
        // Only controlled enum-like values and optional UUIDs; no upstream error message can leak.
        val event = Event(
            System.currentTimeMillis(),
            runId.takeIf { it.length <= 40 && it.matches(Regex("[A-Za-z0-9-]+")) } ?: "",
            subsystem.takeIf { safe.matches(it) } ?: "OTHER",
            phase.takeIf { safe.matches(it) } ?: "OTHER",
            code.takeIf { safe.matches(it) } ?: "UNCLASSIFIED",
            durationMs?.takeIf { it >= 0 && it < 604800000L },
            count?.takeIf { it >= 0 }
        )
        synchronized(lock) {
            val events = readUnsafe().takeLast(MAX_EVENTS - 1).toMutableList()
            events += event
            preferences.edit().putString(KEY, JSONArray().apply { events.forEach { put(it.json()) } }.toString()).commit()
        }
    }

    fun recent(): List<Event> = synchronized(lock) { readUnsafe() }

    private fun readUnsafe(): List<Event> = runCatching {
        val array = JSONArray(preferences.getString(KEY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            Event(
                obj.optLong("atEpochMs"), obj.optString("runId"), obj.optString("subsystem"),
                obj.optString("phase"), obj.optString("code"),
                if (obj.isNull("durationMs")) null else obj.optLong("durationMs"),
                if (obj.isNull("affectedCount")) null else obj.optInt("affectedCount")
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "recent_evidence"
        private const val MAX_EVENTS = 150
        private val lock = Any()
    }
}

/** Tracks transitions with monotonic elapsed time, not mutable wall clock. */
class ScanPhaseTracker(context: Context, private val origin: String) {
    private val journal = DiagnosticJournal(context)
    private val clockStart = SystemClock.elapsedRealtime()
    private var lastTick = clockStart
    private var lastPhase: String? = null
    private var stopped = false
    private var runId: String = UUID.randomUUID().toString()

    private var begun = false

    fun observe(state: ScanState) {
        if (stopped) return
        state.scanRun?.scanRunId?.let { runId = it }
        if (state.errorMessage == "SCAN_ALREADY_RUNNING") {
            journal.record("SCAN", "CONCURRENCY", "SCAN_ALREADY_RUNNING", runId)
            stopped = true
            return
        }
        if (!begun) {
            begun = true
            journal.record("SCAN", "START", "SCAN_STARTED_${origin}", runId)
        }
        val current = state.phase.name
        if (current != lastPhase) {
            val now = SystemClock.elapsedRealtime()
            lastPhase?.let { journal.record("SCAN", it, "PHASE_FINISHED", runId, (now - lastTick).coerceAtLeast(0)) }
            journal.record("SCAN", current, "PHASE_ENTERED", runId)
            lastPhase = current
            lastTick = now
        }
        when (state.status) {
            ScanStatus.COMPLETED -> close(
                if (state.scanRun?.status?.name == "COMPLETE") "SCAN_COMPLETE" else "SCAN_PARTIAL",
                state.successful, state.integrityRejected, state.noData, state.errors
            )
            ScanStatus.ERROR -> close("SCAN_FAILED", 0, 0, state.noData, state.errors)
            ScanStatus.CANCELLED -> close("SCAN_CANCELLED")
            else -> Unit
        }
    }

    fun close(code: String = "SCAN_INTERRUPTED", resultCount: Int = 0, blocked: Int = 0, noData: Int = 0, errors: Int = 0) {
        if (stopped) return
        stopped = true
        val now = SystemClock.elapsedRealtime()
        lastPhase?.let { journal.record("SCAN", it, "PHASE_FINISHED", runId, (now - lastTick).coerceAtLeast(0)) }
        journal.record("SCAN", "END", code, runId, (now - clockStart).coerceAtLeast(0), resultCount)
        if (blocked > 0) journal.record("TRADE_GATE", "BLOCK", "EXPECTED_OR_REQUIRES_REVIEW", runId, count = blocked)
        if (noData > 0) journal.record("PROVIDER", "NO_DATA", "PROVIDER_NO_DATA", runId, count = noData)
        if (errors > 0) journal.record("SCAN", "ERROR", "ANALYSIS_OR_PROVIDER_FAILURE", runId, count = errors)
    }
}

/** Platform process termination records; not a live crash or ANR assertion. */
object PreviousProcessDiagnostics {
    fun read(context: Context): JSONArray = JSONArray().apply {
        if (Build.VERSION.SDK_INT < 30) return@apply
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@apply
        val exits = runCatching { manager.getHistoricalProcessExitReasons(null, 0, 5).toList() }.getOrDefault(emptyList())
        exits.forEach { event ->
            put(JSONObject().apply {
                put("atEpochMs", event.timestamp)
                put("reasonCode", event.reason)
                put("importance", event.importance)
                put("status", event.status)
                // Never include process descriptions, trace contents or personally identifiable data.
                put("source", "ANDROID_APPLICATION_EXIT_INFO")
            })
        }
    }
}
