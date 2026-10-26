package tr.borsatakip.v5.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tr.borsatakip.v5.analysis.ViopV6Policy
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * User-entered MANUAL transaction journal. Does NOT place brokerage orders.
 * Margin/cost/entry are supplied by the user, NOT verified against a licensed broker feed.
 * Risk limits cover only positions recorded in this local journal.
 */
class ViopManualPositionStore(context: Context) {
    companion object { private val STORAGE_LOCK = Any() }
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("v6_manual_positions_v1", Context.MODE_PRIVATE)

    data class ExitFill(val quantity: Int, val price: Double, val executedAt: Long, val extraFeeTry: Double = 0.0)

    data class Position(
        val id: String, val symbol: String, val side: String, val quantity: Int,
        val entry: Double, val stop: Double, val target: Double,
        val multiplier: Double, val costPerContract: Double, val marginPerContract: Double,
        val openedAt: Long, val closedAt: Long? = null, val exitPrice: Double? = null,
        val brokerExecutionId: String = "", val riskViolationAtEntry: String? = null,
        val exits: List<ExitFill> = emptyList()
    ) {
        val remainingQuantity: Int get() = (quantity - if (exits.isNotEmpty()) exits.sumOf { it.quantity }
            else if (closedAt != null) quantity else 0).coerceAtLeast(0)
        val plannedRiskTry: Double get() = quantity * (kotlin.math.abs(entry - stop) * multiplier + costPerContract)
        val openRiskTry: Double get() = remainingQuantity * (kotlin.math.abs(entry - stop) * multiplier + costPerContract)
        val reservedMarginTry: Double get() = quantity * marginPerContract
        val openMarginTry: Double get() = remainingQuantity * marginPerContract
        val realizedPnlTry: Double?
            get() = if (exits.isNotEmpty()) {
                exits.sumOf { fill ->
                    ((fill.price - entry) * (if (side == "LONG") 1.0 else -1.0)) *
                        fill.quantity * multiplier - fill.quantity * costPerContract - fill.extraFeeTry
                }
            } else exitPrice?.let {
                ((it - entry) * (if (side == "LONG") 1.0 else -1.0)) * quantity * multiplier - costPerContract * quantity
            }
    }
    data class State(val count: Int, val openRiskTry: Double, val marginTry: Double, val dailyLossTry: Double)
    data class Result(val accepted: Boolean, val reason: String, val position: Position? = null)

    @Synchronized fun positions(): List<Position> = synchronized(STORAGE_LOCK) { read() }
    @Synchronized fun openPositions(): List<Position> = synchronized(STORAGE_LOCK) { read().filter { it.closedAt == null } }

    @Synchronized fun state(now: Long = System.currentTimeMillis()): State =
        synchronized(STORAGE_LOCK) { summarize(read(), now) }

    @Synchronized fun record(
        symbol: String, side: String, quantity: Int, entry: Double, stop: Double,
        target: Double, multiplier: Double, costPerContract: Double, marginPerContract: Double,
        now: Long = System.currentTimeMillis(),
        brokerExecutionId: String = ""
    ): Result {
        synchronized(STORAGE_LOCK) {
        val normalizedSide = side.trim().uppercase(java.util.Locale.ROOT)
        val s = symbol.trim().uppercase(java.util.Locale.ROOT)
        val executionId = brokerExecutionId.trim().uppercase(java.util.Locale.ROOT)
        // A completed broker execution is a FACT, not a new plan or a recommendation.
        // Valid monetary inputs are required to avoid recording fictitious zero risk.
        if (executionId.isBlank()) return Result(false, "BROKER_EXECUTION_ID_REQUIRED")
        if (s.isBlank() || normalizedSide !in setOf("LONG", "SHORT") || quantity < 1 ||
            !entry.isFinite() || entry <= 0 || !stop.isFinite() || stop <= 0 ||
            !target.isFinite() || target <= 0 || !multiplier.isFinite() || multiplier <= 0 ||
            !costPerContract.isFinite() || costPerContract < 0 ||
            !marginPerContract.isFinite() || marginPerContract <= 0 || now <= 0
        ) return Result(false, "EXECUTED_TRADE_FIELDS_INVALID")
        val current = try { read() } catch (_: Exception) { return Result(false, "POSITION_STORAGE_CORRUPT") }
        if (current.any { it.brokerExecutionId == executionId })
            return Result(false, "DUPLICATE_BROKER_EXECUTION")
        val snapshot = summarize(current, now)
        val limit = ViopV6Policy.size(
            entry, stop, multiplier, costPerContract, marginPerContract,
            openRiskTry = snapshot.openRiskTry, dailyLossTry = snapshot.dailyLossTry,
            activePositions = snapshot.count, usedMarginTry = snapshot.marginTry,
            direction = normalizedSide
        )
        // Do not hide a real position even if its size exceeds the application's limits.
        // Future PLAN creation remains fail-closed via ViopV6Policy.size().
        val breached = if (quantity > limit.contracts)
            "RISK_LIMIT_EXCEEDED:${limit.reason};allowed=${limit.contracts};executed=${quantity}"
            else null
        val position = Position(UUID.randomUUID().toString(), s, normalizedSide,
            quantity, entry, stop, target, multiplier, costPerContract, marginPerContract, now,
            brokerExecutionId = executionId, riskViolationAtEntry = breached)
        val stored = write(current + position)
        if (stored) try {
            tr.borsatakip.v5.worker.ViopPositionMonitorWorker.enqueueNow(appContext)
        } catch (_: Exception) { /* periodic monitor remains scheduled separately */ }
        return if (stored)
            Result(true, if (breached == null) "EXECUTED_TRADE_RECORDED" else "RISK_BREACH_RECORDED", position)
        else Result(false, "STORAGE_WRITE_FAILED")
        }
    }

    /** Complete closure is represented by a final execution, never by deleting the plan. */
    @Synchronized fun close(id: String, exitPrice: Double, now: Long = System.currentTimeMillis()): Result {
        val existing = synchronized(STORAGE_LOCK) {
            try { read().firstOrNull { it.id == id && it.remainingQuantity > 0 } }
            catch (_: Exception) { null }
        } ?: return Result(false, "OPEN_POSITION_MISSING_OR_LEDGER_CORRUPT")
        return closePartial(id, existing.remainingQuantity, exitPrice, now)
    }

    @Synchronized fun closePartial(id: String, exitQuantity: Int, exitPrice: Double,
        executedAt: Long = System.currentTimeMillis(), extraFeeTry: Double = 0.0): Result {
        synchronized(STORAGE_LOCK) {
            if (exitQuantity < 1 || !exitPrice.isFinite() || exitPrice <= 0.0 ||
                !extraFeeTry.isFinite() || extraFeeTry < 0.0) return Result(false,"EXIT_FIELDS_INVALID")
            val current = try { read() } catch (_: Exception) { return Result(false,"POSITION_STORAGE_CORRUPT") }
            val old = current.firstOrNull { it.id == id && it.remainingQuantity > 0 }
                ?: return Result(false,"OPEN_POSITION_NOT_FOUND")
            if (executedAt < old.openedAt) return Result(false,"EXIT_BEFORE_ENTRY")
            if (exitQuantity > old.remainingQuantity) return Result(false,"EXIT_EXCEEDS_OPEN_QUANTITY")
            val remaining = old.remainingQuantity - exitQuantity
            val updated = old.copy(
                exits = old.exits + ExitFill(exitQuantity,exitPrice,executedAt,extraFeeTry),
                closedAt = if (remaining == 0) executedAt else null,
                exitPrice = if (remaining == 0) exitPrice else null)
            val stored = write(current.map { if (it.id == id) updated else it })
            if (stored) try { tr.borsatakip.v5.worker.ViopPositionMonitorWorker.enqueueNow(appContext) }
                catch (_: Exception) { }
            return if (stored) Result(true,if (remaining == 0) "MANUAL_JOURNAL_CLOSED" else "PARTIAL_EXIT_RECORDED",updated)
            else Result(false,"STORAGE_WRITE_FAILED")
        }
    }

    private fun summarize(items: List<Position>, now: Long): State {
        val zone = ZoneId.of("Europe/Istanbul")
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val active = items.filter { it.remainingQuantity > 0 }
        val losses = items.sumOf { p ->
            if (p.exits.isNotEmpty()) {
                p.exits.filter {
                    Instant.ofEpochMilli(it.executedAt).atZone(zone).toLocalDate() == day
                }.sumOf { exit ->
                    val pnl = (exit.price - p.entry) * (if (p.side == "LONG") 1 else -1) *
                        exit.quantity * p.multiplier - exit.quantity * p.costPerContract - exit.extraFeeTry
                    pnl.coerceAtMost(0.0)
                }
            } else if (p.closedAt != null && Instant.ofEpochMilli(p.closedAt)
                .atZone(zone).toLocalDate() == day) (p.realizedPnlTry ?: 0.0).coerceAtMost(0.0)
            else 0.0
        }
        return State(active.size, active.sumOf { it.openRiskTry },
            active.sumOf { it.openMarginTry }, -losses)
    }

    /** Strict and non-destructive: malformed storage must never be treated as zero risk. */
    private fun read(): List<Position> = try {
        val original = prefs.getString("positions", "[]") ?: throw IllegalStateException("NULL_LEDGER")
        val json = JSONArray(original)
        val ids = HashSet<String>()
        val brokerIds = HashSet<String>()
        (0 until json.length()).map { i ->
            val o = json.optJSONObject(i) ?: throw IllegalStateException("INVALID_RECORD_AT_$i")
            val id = o.getString("id").trim()
            val symbol = o.getString("symbol").trim()
            val side = o.getString("side")
            val qtyString = o.get("quantity").toString()
            val qty = qtyString.toIntOrNull() ?: throw IllegalStateException("INVALID_QUANTITY_$i")
            val opened = o.getLong("openedAt")
            val closed = if (o.isNull("closedAt")) null else o.getLong("closedAt")
            val exit = if (o.isNull("exitPrice")) null else o.getDouble("exitPrice")
            val broker = o.optString("brokerExecutionId").trim().ifEmpty { "LEGACY:$id" }
            val riskFlag = if (o.isNull("riskViolationAtEntry")) null else o.optString("riskViolationAtEntry").takeIf { it.isNotBlank() }
            val exitArray = o.optJSONArray("exits") ?: run {
                if (o.has("exits") && !o.isNull("exits")) throw IllegalStateException("INVALID_EXITS_" + i)
                JSONArray()
            }
            val fills = (0 until exitArray.length()).map { n ->
                val e = exitArray.optJSONObject(n) ?: throw IllegalStateException("INVALID_EXIT_" + i)
                ExitFill(e.getInt("quantity"),e.getDouble("price"),e.getLong("executedAt"),
                    e.optDouble("extraFeeTry", 0.0))
            }
            val p = Position(id,symbol,side,qty,o.getDouble("entry"),
                o.getDouble("stop"),o.getDouble("target"),o.getDouble("multiplier"),
                o.getDouble("cost"),o.getDouble("margin"),opened,closed,exit,broker,riskFlag,fills)
            if (id.isBlank() || !ids.add(id) || !brokerIds.add(broker) ||
                symbol.isBlank() || side !in setOf("LONG","SHORT") || qty < 1 ||
                !p.entry.isFinite() || p.entry <= 0 || !p.stop.isFinite() || p.stop <= 0 ||
                !p.target.isFinite() || p.target <= 0 ||
                !p.multiplier.isFinite() || p.multiplier <= 0 ||
                !p.costPerContract.isFinite() || p.costPerContract < 0 ||
                !p.marginPerContract.isFinite() || p.marginPerContract <= 0 ||
                opened <= 0 || (closed != null && (closed < opened || exit == null)) ||
                (closed == null && exit != null) ||
                (exit != null && (!exit.isFinite() || exit <= 0)) ||
                fills.any { it.quantity < 1 || !it.price.isFinite() || it.price <= 0 ||
                    it.executedAt < opened || (closed != null && it.executedAt > closed) ||
                    !it.extraFeeTry.isFinite() || it.extraFeeTry < 0 } ||
                fills.sumOf { it.quantity } > qty ||
                (fills.isNotEmpty() && ((fills.sumOf { it.quantity } == qty) != (closed != null)))) {
                throw IllegalStateException("CORRUPT_POSITION_$i")
            }
            p
        }
    } catch (error: Exception) {
        // Leave the original prefs bytes untouched for user-controlled recovery.
        throw IllegalStateException("POSITION_STORAGE_CORRUPT", error)
    }

    private fun write(items: List<Position>): Boolean {
        val data = JSONArray()
        items.forEach { x ->
            data.put(JSONObject().apply {
                put("id", x.id); put("symbol", x.symbol); put("side", x.side)
                put("quantity", x.quantity); put("entry", x.entry); put("stop", x.stop)
                put("target", x.target); put("multiplier", x.multiplier)
                put("cost", x.costPerContract); put("margin", x.marginPerContract)
                put("openedAt", x.openedAt); put("closedAt", x.closedAt); put("exitPrice", x.exitPrice)
                put("brokerExecutionId", x.brokerExecutionId)
                put("riskViolationAtEntry", x.riskViolationAtEntry)
                put("exits", JSONArray().apply {
                    x.exits.forEach { exit ->
                        put(JSONObject().apply {
                            put("quantity",exit.quantity); put("price",exit.price)
                            put("executedAt",exit.executedAt)
                            put("extraFeeTry",exit.extraFeeTry)
                        })
                    }
                })
            })
        }
        return prefs.edit().putString("positions", data.toString()).commit()
    }
}
