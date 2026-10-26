package tr.borsatakip.v5.data

/** Distinguishes proven overlapping intervals from missing/truncated evidence. */
object DiagnosticOverlapAudit {
    data class Interval(val runId: String, val start: Long, val end: Long)
    data class Overlap(val firstRunId: String, val secondRunId: String, val overlapMs: Long)

    fun intervals(events: List<DiagnosticJournal.Event>): List<Interval> = events
        .asSequence()
        .filter { it.subsystem == "SCAN" && it.code.startsWith("SCAN_STARTED_") && it.runId.isNotBlank() }
        .distinctBy { it.runId }
        .mapNotNull { start ->
            val end = events.firstOrNull { it.subsystem == "SCAN" && it.phase == "END" &&
                it.runId == start.runId && it.at >= start.at } ?: return@mapNotNull null
            Interval(start.runId, start.at, end.at)
        }.toList()

    fun overlaps(events: List<DiagnosticJournal.Event>): List<Overlap> {
        val completed = intervals(events)
        return completed.indices.flatMap { i ->
            (i + 1 until completed.size).mapNotNull { j ->
                val a = completed[i]; val b = completed[j]
                val ms = minOf(a.end, b.end) - maxOf(a.start, b.start)
                if (ms > 0) Overlap(a.runId, b.runId, ms) else null
            }
        }
    }
}
