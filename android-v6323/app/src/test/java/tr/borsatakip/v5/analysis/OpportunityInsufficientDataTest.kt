package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.Stock

class OpportunityInsufficientDataTest {
    @Test fun fewerThan220BarsReturnsExplicitInsufficientDecision() {
        val now = System.currentTimeMillis()
        val candles = (0 until 100).map { i ->
            val close = 50.0 + i * 0.01
            Candle(now - (99L - i) * 60_000L, close, close + 0.1, close - 0.1, close, 10_000.0)
        }
        val stock = Stock(
            symbol="TEST", companyName="Test", candles=candles, source="unit", dataTimestamp=now,
            isRealtime=true, delaySeconds=0, currentSessionIncluded=true, receivedAt=now,
            receivedElapsedRealtime=Long.MAX_VALUE/4, quotePrice=candles.last().close,
            market="BIST", historySymbol="TEST", interval="1m", exchangeTimezone="Europe/Istanbul",
            lastBarTime=candles.last().timestamp, lastBarClosed=false, previousClose=candles[candles.lastIndex-1].close
        )
        val result = OpportunityEngine.score(stock)
        assertNotNull(result)
        result!!
        assertEquals(SignalValidity.INSUFFICIENT, result.signalValidity)
        assertEquals(DecisionState.INSUFFICIENT_DATA, result.decisionState)
        assertEquals(0, result.rankingScore)
        assertTrue(result.signalValidityReason.contains("220"))
    }
}
