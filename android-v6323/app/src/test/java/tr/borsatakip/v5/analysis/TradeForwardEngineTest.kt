package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RiskPlan

class TradeForwardEngineTest {
    private val plan = RiskPlan(100.0, 95.0, 110.0, 120.0, 2.0, 4.0)

    @Test fun longStopBeforeTargetIsRecordedAsStop() {
        val c = listOf(
            Candle(2, 100.0, 104.0, 94.0, 96.0, 1000.0),
            Candle(3, 96.0, 112.0, 96.0, 111.0, 1000.0)
        )
        val r = TradeForwardEngine.evaluate("LONG", 1, plan, c)
        assertEquals(TradeForwardEngine.Event.STOP, r.event)
        assertTrue((r.grossReturnPct ?: 0.0) < 0.0)
    }

    @Test fun sameFutureCandleStopAndTargetIsAmbiguous() {
        val c = listOf(Candle(2, 100.0, 111.0, 94.0, 100.0, 1000.0))
        assertEquals(TradeForwardEngine.Event.AMBIGUOUS, TradeForwardEngine.evaluate("LONG", 1, plan, c).event)
    }

    @Test fun target2CanBeReachedAfterTarget1() {
        val c = listOf(
            Candle(2, 100.0, 111.0, 99.0, 109.0, 1000.0),
            Candle(3, 109.0, 121.0, 108.0, 120.0, 1000.0)
        )
        val r = TradeForwardEngine.evaluate("LONG", 1, plan, c)
        assertEquals(TradeForwardEngine.Event.TARGET2, r.event)
        assertEquals(2L, r.target1Time)
    }

    @Test fun target1ThenStopIsExplicitlyRecorded() {
        val c = listOf(
            Candle(2, 100.0, 111.0, 99.0, 109.0, 1000.0),
            Candle(3, 109.0, 109.5, 94.0, 95.0, 1000.0)
        )
        val r = TradeForwardEngine.evaluate("LONG", 1, plan, c)
        assertEquals(TradeForwardEngine.Event.TARGET1_THEN_STOP, r.event)
        assertEquals(2L, r.target1Time)
        assertEquals(3L, r.eventTime)
        assertTrue((r.grossReturnPct ?: 0.0) < 0.0)
    }


    @Test fun target1ThenLaterStopAndT1RevisitIsNotAmbiguous() {
        val c = listOf(
            Candle(2, 100.0, 111.0, 99.0, 109.0, 1000.0),
            Candle(3, 109.0, 111.0, 94.0, 95.0, 1000.0)
        )
        val r = TradeForwardEngine.evaluate("LONG", 1, plan, c)
        assertEquals(TradeForwardEngine.Event.TARGET1_THEN_STOP, r.event)
        assertEquals(2L, r.target1Time)
        assertEquals(3L, r.eventTime)
    }

    @Test fun entryCandleIsNeverCountedAsFutureOutcome() {
        val c = listOf(
            Candle(10, 100.0, 121.0, 94.0, 110.0, 1000.0),
            Candle(11, 108.0, 109.0, 106.0, 108.0, 1000.0)
        )
        val r = TradeForwardEngine.evaluate("LONG", 10, plan, c)
        assertEquals(TradeForwardEngine.Event.OPEN, r.event)
        assertEquals(1, r.candlesChecked)
        assertNull(r.eventTime)
    }

    @Test fun target1WithoutTarget2IsFullExit() {
        val noT2 = RiskPlan(100.0, 95.0, 110.0, null, 2.0, null)
        val c = listOf(Candle(2, 100.0, 111.0, 99.0, 110.0, 1.0))
        val r = TradeForwardEngine.evaluate("LONG", 1, noT2, c)
        assertEquals(TradeForwardEngine.Event.TARGET1, r.event)
        assertEquals(2L, r.eventTime)
    }

    @Test fun configuredCostsProduceNetPnl() {
        val noT2 = RiskPlan(100.0, 95.0, 110.0, null, 2.0, null)
        val c = listOf(Candle(2, 100.0, 111.0, 99.0, 110.0, 1.0))
        val costs = TradeForwardEngine.CostConfig(quantity = 2.0, contractMultiplier = 10.0, commissionPerSide = 1.0, slippageBpsPerSide = 10.0)
        val r = TradeForwardEngine.evaluate("LONG", 1, noT2, c, costs)
        assertEquals(TradeForwardEngine.Event.TARGET1, r.event)
        assertNotNull(r.grossPnl)
        assertNotNull(r.totalCosts)
        assertNotNull(r.netPnl)
        assertTrue(r.netPnl!! < r.grossPnl!!)
    }
}
