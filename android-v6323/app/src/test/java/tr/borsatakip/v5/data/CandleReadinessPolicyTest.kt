package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class CandleReadinessPolicyTest {
    private fun stock(count: Int, closed: Boolean?, interval: String = "5m"): Stock {
        val now = 1_800_000_000_000L
        val candles = (0 until count).map { i ->
            val ts = now - (count - i).toLong() * 300_000L
            Candle(ts,100.0,101.0,99.0,100.0,1000.0)
        }
        return Stock("TEST",null,candles,"test",now,false,null,false,interval=interval,lastBarTime=candles.last().timestamp,lastBarClosed=closed)
    }

    @Test fun derivedRequirementPreservesCurrentEma200WarmupContract() {
        assertEquals(220, CandleReadinessPolicy.requiredClosedBars())
    }

    @Test fun openLastBarDoesNotCountTowardReadiness() {
        val result = CandleReadinessPolicy.evaluate(stock(220,false))
        assertEquals(219, result.availableClosedBars)
        assertFalse(result.timeframeReady)
    }

    @Test fun closedLastBarCountsAndBecomesReady() {
        val result = CandleReadinessPolicy.evaluate(stock(220,true))
        assertEquals(220, result.availableClosedBars)
        assertTrue(result.timeframeReady)
        assertEquals(100, result.historyCoveragePct)
    }
}
