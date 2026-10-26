package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.TechnicalSnapshot

enum class TrendUiState {
    STRONG_BULLISH,
    BULLISH,
    NEUTRAL,
    BEARISH,
    STRONG_BEARISH,
    INSUFFICIENT_DATA,
    REJECTED
}

data class TrendUiStyle(
    val state: TrendUiState,
    val label: String,
    val arrow: String,
    val red: Int,
    val green: Int,
    val blue: Int
) {
    /** Qualitative technical strength, never the ranking score or trade confidence. */
    val strengthLevel: Int get() = when (state) {
        TrendUiState.STRONG_BULLISH, TrendUiState.STRONG_BEARISH -> 2
        TrendUiState.BULLISH, TrendUiState.BEARISH -> 1
        else -> 0
    }

    val color: Int get() = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    val rowFillAlpha: Int get() = when (strengthLevel) { 2 -> 32; 1 -> 16; else -> 0 }
    val borderAlpha: Int get() = when (strengthLevel) { 2 -> 235; 1 -> 150; else -> 45 }
    val borderWidthDp: Float get() = if (strengthLevel == 2) 1.5f else 1f
    val sparklineFillAlpha: Int get() = when (strengthLevel) { 2 -> 58; 1 -> 32; else -> 12 }
    val sparklineWidthDp: Float get() = when (strengthLevel) { 2 -> 1.8f; 1 -> 1.3f; else -> 1f }
}

/**
 * Maps the selected analysis-timeframe technical snapshot to one consistent trend state.
 * Daily price change is deliberately NOT used: card colour must represent technical trend,
 * not a one-day price movement. Signal direction also remains a separate concept.
 */
object TrendUiPolicy {
    fun resolve(opportunity: Opportunity): TrendUiStyle = resolve(
        price = opportunity.price,
        technical = opportunity.technical,
        decisionState = opportunity.decisionState
    )

    fun resolve(
        price: Double,
        technical: TechnicalSnapshot,
        decisionState: DecisionState
    ): TrendUiStyle {
        if (decisionState == DecisionState.REJECTED) return style(TrendUiState.REJECTED)
        if (decisionState == DecisionState.INSUFFICIENT_DATA) return style(TrendUiState.INSUFFICIENT_DATA)
        if (!price.isFinite() || price <= 0.0) return style(TrendUiState.INSUFFICIENT_DATA)

        val ema20 = technical.ema20?.takeIf { it.isFinite() && it > 0.0 }
        val ema50 = technical.ema50?.takeIf { it.isFinite() && it > 0.0 }
        val ema200 = technical.ema200?.takeIf { it.isFinite() && it > 0.0 }
        if (ema20 == null || ema50 == null) return style(TrendUiState.INSUFFICIENT_DATA)

        // Invalid optional evidence must not amplify an otherwise ordinary trend.
        val rsi = technical.rsi14?.takeIf { it.isFinite() && it in 0.0..100.0 }
        val macd = technical.macd?.takeIf { it.isFinite() }
        val macdSignal = technical.macdSignal?.takeIf { it.isFinite() }
        val macdBull = macd != null && macdSignal != null && macd > macdSignal
        val macdBear = macd != null && macdSignal != null && macd < macdSignal
        val rsiBull = rsi != null && rsi >= 52.0
        val rsiBear = rsi != null && rsi <= 48.0

        val bullish = price > ema20 && ema20 > ema50
        val bearish = price < ema20 && ema20 < ema50
        val strongBullish = bullish && ema200 != null && ema50 > ema200 && macdBull && rsiBull
        val strongBearish = bearish && ema200 != null && ema50 < ema200 && macdBear && rsiBear

        return style(
            when {
                strongBullish -> TrendUiState.STRONG_BULLISH
                strongBearish -> TrendUiState.STRONG_BEARISH
                bullish -> TrendUiState.BULLISH
                bearish -> TrendUiState.BEARISH
                else -> TrendUiState.NEUTRAL
            }
        )
    }

    private fun style(state: TrendUiState): TrendUiStyle = when (state) {
        TrendUiState.STRONG_BULLISH -> TrendUiStyle(state, "GÜÇLÜ YÜKSELİŞ", "↑", 0, 255, 157)
        TrendUiState.BULLISH -> TrendUiStyle(state, "YÜKSELİŞ", "↑", 32, 212, 139)
        TrendUiState.NEUTRAL -> TrendUiStyle(state, "YATAY", "→", 175, 194, 212)
        TrendUiState.BEARISH -> TrendUiStyle(state, "DÜŞÜŞ", "↓", 255, 100, 116)
        TrendUiState.STRONG_BEARISH -> TrendUiStyle(state, "GÜÇLÜ DÜŞÜŞ", "↓", 255, 145, 155)
        TrendUiState.INSUFFICIENT_DATA -> TrendUiStyle(state, "YETERSİZ VERİ", "—", 175, 194, 212)
        TrendUiState.REJECTED -> TrendUiStyle(state, "REDDEDİLDİ", "—", 175, 194, 212)
    }
}
