package tr.borsatakip.v5.ui

import tr.borsatakip.v5.analysis.OhlcvResampler
import tr.borsatakip.v5.model.Candle

/** Request identity prevents a cancelled or slow response from restoring the wrong period. */
class ChartDisplayState {
    enum class Phase { LOADING, RECORDED, LOADED, ERROR }
    var generation = 0L; private set
    var phase = Phase.ERROR; private set
    var candles: List<Candle> = emptyList(); private set

    fun begin(): Long { generation++; phase = Phase.LOADING; candles = emptyList(); return generation }
    fun complete(request: Long, raw: List<Candle>, recorded: Boolean = false): Boolean {
        if (request != generation) return false
        val clean = OhlcvResampler.sanitize(raw)
        candles = if (clean.any { it.timestamp > System.currentTimeMillis() + 60_000 }) emptyList() else clean
        phase = if (candles.isEmpty()) Phase.ERROR else if (recorded) Phase.RECORDED else Phase.LOADED
        return true
    }
    fun fail(request: Long): Boolean {
        if (request != generation) return false
        candles = emptyList(); phase = Phase.ERROR; return true
    }
    // A downloaded series alone proves neither real-time freshness nor a closed last bar.
    val tradingSignalAllowed: Boolean get() = false
}
