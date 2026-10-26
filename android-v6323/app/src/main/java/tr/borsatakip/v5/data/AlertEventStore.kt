package tr.borsatakip.v5.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.ViopOpportunity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Kalıcı, tekrarı sınırlı fırsat olay geçmişi. Bildirim izninden bağımsız olarak olay kaydı tutar. */
class AlertEventStore(context: Context) {
    private val prefs = context.getSharedPreferences("alert_event_store_v1", Context.MODE_PRIVATE)

    enum class Type { NEW_STRONG_SIGNAL, SCORE_UP, SCORE_DOWN, STOP_NEAR, TARGET1_REACHED, TARGET2_REACHED, DATA_STALE, STOP_BREACH }

    data class Event(val type: Type, val symbol: String, val message: String, val score: Int, val createdAt: Long, val identity: String = "")

    @Synchronized
    fun evaluate(items: List<Opportunity>, now: Long = System.currentTimeMillis()): List<Event> {
        val emitted = mutableListOf<Event>()
        val edit = prefs.edit()
        items.forEach { x ->
            val key = x.symbol.uppercase()
            val previous = prefs.getInt("score:$key", Int.MIN_VALUE)
            if (previous == Int.MIN_VALUE && x.finalSignalScore >= 80) emitted += event(Type.NEW_STRONG_SIGNAL, x, "Yeni güçlü ${x.direction} sinyali", now)
            if (previous != Int.MIN_VALUE) {
                val delta = x.finalSignalScore - previous
                if (delta >= 5) emitted += event(Type.SCORE_UP, x, "Skor $previous → ${x.finalSignalScore}", now)
                if (delta <= -5) emitted += event(Type.SCORE_DOWN, x, "Skor $previous → ${x.finalSignalScore}", now)
            }
            x.riskPlan?.let { p ->
                val stop = p.stop
                if (stop != null) {
                    val riskDistance = abs(p.entry - stop).takeIf { it > 1e-9 }
                    if (riskDistance != null && abs(x.price - stop) <= riskDistance * 0.30) {
                        emitted += event(Type.STOP_NEAR, x, "Stop seviyesine yaklaştı", now)
                    }
                }
                if (p.target2 != null && if (x.direction.equals("LONG", true)) x.price >= p.target2 else x.price <= p.target2) {
                    emitted += event(Type.TARGET2_REACHED, x, "Hedef 2 seviyesi görüldü", now)
                } else if (p.target1 != null && if (x.direction.equals("LONG", true)) x.price >= p.target1 else x.price <= p.target1) {
                    emitted += event(Type.TARGET1_REACHED, x, "Hedef 1 seviyesi görüldü", now)
                }
            }
            if ((x.dataAgeMs ?: 0L) > STALE_EVENT_MS) emitted += event(Type.DATA_STALE, x, "Piyasa verisi eskidi", now)
            edit.putInt("score:$key", x.finalSignalScore)
        }
        edit.apply()
        val accepted = emitted.filter { shouldRecord(it, now) }
        if (accepted.isNotEmpty()) append(accepted)
        return accepted
    }


    @Synchronized
    fun evaluateViop(items: List<ViopOpportunity>, now: Long = System.currentTimeMillis(), origin: String = "WORKER"): List<Event> {
        val emitted = mutableListOf<Event>()
        val edit = prefs.edit()
        items.forEach { x ->
            val key = x.contract.symbol.uppercase()
            // UI viewing a candidate must not consume worker's signal episode.
            val scoreKey = "viop:$origin:score:$key"
            val previous = prefs.getInt(scoreKey, Int.MIN_VALUE)
            val previousDirection = prefs.getString("viop:$origin:direction:$key", "")
            val episodeStarted = previous == Int.MIN_VALUE || previousDirection != x.direction
            if (episodeStarted && x.finalScore >= 80) emitted += Event(Type.NEW_STRONG_SIGNAL, key, "Yeni güçlü ${x.direction} VİOP sinyali", x.finalScore, now)
            if (previous != Int.MIN_VALUE) {
                val delta = x.finalScore - previous
                if (delta >= 5) emitted += Event(Type.SCORE_UP, key, "VİOP skor $previous → ${x.finalScore}", x.finalScore, now)
                if (delta <= -5) emitted += Event(Type.SCORE_DOWN, key, "VİOP skor $previous → ${x.finalScore}", x.finalScore, now)
            }
            // DO NOT monitor temporary signal risk plans as if they were user's live positions.
            // Stop/target warnings are based exclusively on the manually confirmed ledger.
            if (x.dataAgeMs > STALE_EVENT_MS) emitted += Event(Type.DATA_STALE, key, "VİOP piyasa verisi eskidi", x.finalScore, now)
            edit.putInt(scoreKey, x.finalScore)
            edit.putString("viop:$origin:direction:$key", x.direction)
        }
        edit.apply()
        val accepted = emitted.filter { shouldRecord(it, now) }
        if (accepted.isNotEmpty()) append(accepted)
        return accepted
    }

    @Synchronized
    fun evaluateManualViop(
        positions: List<ViopManualPositionStore.Position>,
        quotes: Map<String, tr.borsatakip.v5.model.ViopQuote>,
        now: Long = System.currentTimeMillis()
    ): List<Event> {
        val emitted = mutableListOf<Event>()
        positions.filter { it.closedAt == null }.forEach { pos ->
            val q = quotes[pos.symbol]
            val age = if (q == null) Long.MAX_VALUE else now - q.exchangeTimestamp
            val fresh = q != null && q.realtime && q.delaySeconds == 0 &&
                q.symbol.equals(pos.symbol, true) && q.price.isFinite() && q.price > 0.0 && q.exchangeTimestamp > 0L && age in 0L..60000L
            if (!fresh) {
                emitted += Event(Type.DATA_STALE, pos.symbol,
                    "VİOP manuel pozisyonu izlenemiyor: güncel doğrulanmış fiyat yok", 0, now, pos.id)
            } else {
                val price = q!!.price
                val distance = kotlin.math.abs(pos.entry - pos.stop)
                val nearStop = if (pos.side == "LONG") price <= pos.stop + distance * 0.30
                               else price >= pos.stop - distance * 0.30
                val hitTarget = if (pos.side == "LONG") price >= pos.target else price <= pos.target
                val crossedStop = if (pos.side == "LONG") price <= pos.stop else price >= pos.stop
                if (crossedStop) emitted += Event(Type.STOP_BREACH, pos.symbol,
                    "MANUEL pozisyon: STOP SEVİYESİ AŞILDI • aracı kurumdan kontrol edin", 0, now, pos.id)
                else if (nearStop) emitted += Event(Type.STOP_NEAR, pos.symbol,
                    "MANUEL pozisyon: stop seviyesine yaklaşıldı; aracı kurumdan kontrol edin", 0, now, pos.id)
                if (hitTarget) emitted += Event(Type.TARGET1_REACHED, pos.symbol,
                    "MANUEL pozisyon: hedef fiyat görüldü; aracı kurumdan kontrol edin", 0, now, pos.id)
            }
        }
        val accepted = emitted.filter { shouldRecord(it,now) }
        if (accepted.isNotEmpty()) append(accepted)
        return accepted
    }

    @Synchronized
    fun recentEvents(limit: Int = 100): List<Event> {
        val a = readArray()
        return (a.length() - 1 downTo 0).mapNotNull { i ->
            if (a.length() - 1 - i >= limit.coerceAtLeast(1)) return@mapNotNull null
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val type = runCatching { Type.valueOf(o.optString("type")) }.getOrNull() ?: return@mapNotNull null
            Event(
                type = type,
                symbol = o.optString("symbol"),
                message = o.optString("message"),
                score = o.optInt("score"),
                createdAt = o.optLong("createdAt", 0L),
                identity = o.optString("identity")
            )
        }
    }

    @Synchronized
    fun unreadCount(): Int {
        val lastReadAt = prefs.getLong("last_read_at", 0L)
        return recentEvents(MAX_EVENTS).count { it.createdAt > lastReadAt }
    }

    @Synchronized
    fun markAllRead(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_read_at", now.coerceAtLeast(0L)).apply()
    }

    @Synchronized
    fun recentLines(limit: Int = 40): List<String> {
        val a = readArray()
        val df = SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR"))
        return (a.length() - 1 downTo 0).mapNotNull { i ->
            if (a.length() - 1 - i >= limit) return@mapNotNull null
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val at = o.optLong("createdAt", 0L)
            "${if (at > 0) df.format(Date(at)) else "?"} • ${o.optString("symbol")} • ${o.optString("type")}\n${o.optString("message")} • Skor ${o.optInt("score")}/100"
        }
    }

    private fun event(type: Type, x: Opportunity, message: String, now: Long) = Event(type, x.symbol, message, x.finalSignalScore, now)

    private fun shouldRecord(e: Event, now: Long): Boolean {
        val key = "event:${e.type.name}:${e.identity.ifBlank { e.symbol.uppercase() }}"
        val last = prefs.getLong(key, 0L)
        if (now - last < EVENT_COOLDOWN_MS) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }

    private fun append(events: List<Event>) {
        val a = readArray()
        events.forEach { e -> a.put(JSONObject().apply {
            put("type", e.type.name); put("symbol", e.symbol); put("message", e.message); put("score", e.score); put("createdAt", e.createdAt); put("identity", e.identity)
        }) }
        val out = JSONArray()
        val start = (a.length() - MAX_EVENTS).coerceAtLeast(0)
        for (i in start until a.length()) out.put(a.get(i))
        prefs.edit().putString("events", out.toString()).apply()
    }

    private fun readArray(): JSONArray = runCatching { JSONArray(prefs.getString("events", "[]") ?: "[]") }.getOrDefault(JSONArray())

    companion object {
        private const val MAX_EVENTS = 200
        private const val EVENT_COOLDOWN_MS = 60L * 60L * 1000L
        private const val STALE_EVENT_MS = 5L * 60L * 1000L
    }
}
