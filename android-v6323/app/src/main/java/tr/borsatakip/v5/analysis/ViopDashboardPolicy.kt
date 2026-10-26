package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.ViopOpportunity

/** Rows and counts must describe the same instant, including quote and contract expiry. */
object ViopDashboardPolicy {
    data class Snapshot(val items: List<ViopOpportunity>, val longs: Int, val shorts: Int, val assessedAtMs: Long)
    fun snapshot(candidates: List<ViopOpportunity>, nowMs: Long): Snapshot {
        val items = ViopV6Policy.shortlist(candidates, nowMs)
        return Snapshot(items, items.count { it.direction.equals("LONG", true) },
            items.count { it.direction.equals("SHORT", true) }, nowMs)
    }
}
