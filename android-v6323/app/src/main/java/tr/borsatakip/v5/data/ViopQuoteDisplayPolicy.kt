package tr.borsatakip.v5.data

import tr.borsatakip.v5.model.ViopQuote

data class ViopQuoteDisplay(
    val changePct: Double?,
    val changeDirection: Int,
    val isLive: Boolean
)

/** Display-only evidence, evaluated at the dashboard's shared assessment time. */
object ViopQuoteDisplayPolicy {
    fun from(quote: ViopQuote, assessedAtMs: Long): ViopQuoteDisplay {
        val change = quote.dailyChangePct?.takeIf(Double::isFinite)
        val live = quote.price.isFinite() && quote.price > 0.0 &&
            quote.realtime && quote.delaySeconds == 0 && quote.currentSessionIncluded &&
            quote.exchangeTimestamp > 0L && assessedAtMs >= quote.exchangeTimestamp &&
            assessedAtMs - quote.exchangeTimestamp <= 60_000L
        return ViopQuoteDisplay(
            changePct = change,
            changeDirection = when { change == null -> 0; change > 0.0 -> 1; change < 0.0 -> -1; else -> 0 },
            isLive = live
        )
    }
}
