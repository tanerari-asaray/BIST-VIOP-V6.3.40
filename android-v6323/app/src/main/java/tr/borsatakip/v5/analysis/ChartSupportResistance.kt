package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.Candle

/** Real observed levels, for chart display only — never a LONG/SHORT instruction. */
object ChartSupportResistance {
    enum class Evidence { CONFIRMED_PIVOT, ROLE_REVERSAL, RECENT_RANGE }
    data class Level(val price: Double, val evidence: Evidence)
    data class Levels(val support: Level?, val resistance: Level?, val bandHalfWidth: Double)

    /** Caller passes only closed OHLCV bars. No forward-looking bar may be used. */
    fun fromConfirmed(confirmed: List<Candle>): Levels {
        val bars = OhlcvResampler.sanitize(confirmed)
        if (bars.size < 12) return Levels(null, null, 0.0)
        val price = bars.last().close
        if (!price.isFinite() || price <= 0.0) return Levels(null, null, 0.0)
        val pivots = ExtendedStockEvidence.ladder(bars, price)
        val changed = ExtendedStockEvidence.pivotLevels(bars).filter { it.roleChanged }.map { it.price }.toSet()
        fun pivot(value: Double) = Level(value, if (value in changed) Evidence.ROLE_REVERSAL else Evidence.CONFIRMED_PIVOT)
        val history = bars.dropLast(1).takeLast(40)
        if (history.size < 10) return Levels(null, null, 0.0)
        // Only observed OHLCV boundaries, and distinguish them from confirmed swing pivots.
        val floor = history.minOf { it.low }.takeIf { it.isFinite() && it > 0.0 && it < price }
        val ceiling = history.maxOf { it.high }.takeIf { it.isFinite() && it > price }
        val support = pivots.supports.firstOrNull { it.isFinite() && it > 0.0 && it < price }?.let { pivot(it) }
            ?: floor?.let { Level(it, Evidence.RECENT_RANGE) }
        val resistance = pivots.resistances.firstOrNull { it.isFinite() && it > price }?.let { pivot(it) }
            ?: ceiling?.let { Level(it, Evidence.RECENT_RANGE) }
        // Width is derived from observed candle ranges; it is illustrative, not a price target.
        val averageRange = bars.takeLast(14).map { (it.high - it.low).coerceAtLeast(0.0) }.average()
        val halfWidth = (averageRange * 0.15).coerceIn(price * 0.0005, price * 0.005)
        return Levels(support, resistance, halfWidth)
    }
}
