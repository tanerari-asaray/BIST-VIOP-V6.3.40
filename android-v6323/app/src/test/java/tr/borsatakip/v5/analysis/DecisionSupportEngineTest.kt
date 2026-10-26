package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class DecisionSupportEngineTest {
    @Test
    fun opportunityCarriesExplainableDecisionFields() {
        val now = System.currentTimeMillis()
        val candles = (0 until 240).map { i ->
            val close = 100.0 + i * 0.20
            Candle(
                timestamp = now - (239L - i) * 86_400_000L,
                open = close - 0.10,
                high = close + 0.40,
                low = close - 0.40,
                close = close,
                volume = 1_000_000.0 + i * 20_000.0
            )
        }
        val stock = Stock(
            symbol = "TEST",
            companyName = "Test",
            candles = candles,
            source = "licensed-unit",
            dataTimestamp = now,
            isRealtime = true,
            delaySeconds = 0,
            currentSessionIncluded = true,
            receivedAt = now,
            receivedElapsedRealtime = 1L,
            quotePrice = candles.last().close,
            market = "BIST",
            historySymbol = "TEST",
            interval = "1d",
            exchangeTimezone = "Europe/Istanbul",
            lastBarTime = candles.last().timestamp,
            lastBarClosed = true,
            previousClose = candles[candles.lastIndex - 1].close
        )

        val result = OpportunityEngine.score(stock)
        assertNotNull(result)
        result!!
        assertEquals(maxOf(result.longScore, result.shortScore), result.finalSignalScore)
        assertTrue(result.rankingScore in 0..100)
        assertTrue(result.dataConfidenceScore in 0..100)
        if (result.decisionState == tr.borsatakip.v5.model.DecisionState.VERIFIED_OPPORTUNITY) {
            assertNotNull(result.riskPlan)
        } else {
            // Without a verified publication gate, trade direction must remain non-actionable.
            assertTrue(result.direction == "NEUTRAL" || result.direction == "İZLEME")
        }
        assertTrue(result.setupType.isNotBlank())
        assertTrue(result.scoreBreakdown.any { it.startsWith("LONG_SCORE=") })
        assertTrue(result.scoreBreakdown.any { it.startsWith("SHORT_SCORE=") })
        assertTrue(result.scoreBreakdown.any { it.startsWith("DECISION_STATE=") })
    }
}
