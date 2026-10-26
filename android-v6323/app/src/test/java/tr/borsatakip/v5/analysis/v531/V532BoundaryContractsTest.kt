package tr.borsatakip.v5.analysis.v531

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.TechnicalSnapshot

class V532BoundaryContractsTest {
    @Test
    fun confidenceBandBoundariesAreExact() {
        assertEquals(V531ConfidenceBand.BLOCKED, ConfidenceEngine.bandForScore(39))
        assertEquals(V531ConfidenceBand.LOW, ConfidenceEngine.bandForScore(40))
        assertEquals(V531ConfidenceBand.LOW, ConfidenceEngine.bandForScore(59))
        assertEquals(V531ConfidenceBand.DEGRADED, ConfidenceEngine.bandForScore(60))
        assertEquals(V531ConfidenceBand.DEGRADED, ConfidenceEngine.bandForScore(79))
        assertEquals(V531ConfidenceBand.NORMAL, ConfidenceEngine.bandForScore(80))
    }

    @Test
    fun normalConfidenceUses70SignalBoundary() {
        val below = SignalDecisionEngine.evaluate(69, 20, 80, 100, null)
        val exact = SignalDecisionEngine.evaluate(70, 20, 80, 100, null)

        assertEquals(V531Direction.WATCH, below.decision)
        assertTrue(V531ReasonCode.LOW_SCORE in below.reasonCodes)
        assertEquals(V531Direction.LONG, exact.decision)
    }

    @Test
    fun degradedConfidenceUses75SignalBoundary() {
        val below = SignalDecisionEngine.evaluate(74, 20, 79, 100, null)
        val exact = SignalDecisionEngine.evaluate(75, 20, 79, 100, null)

        assertEquals(V531Direction.WATCH, below.decision)
        assertTrue(V531ReasonCode.LOW_SCORE in below.reasonCodes)
        assertEquals(V531Direction.LONG, exact.decision)
    }

    @Test
    fun directionMarginFiveIsConflictButSixIsAccepted() {
        val margin5 = SignalDecisionEngine.evaluate(80, 75, 90, 100, null)
        val margin6 = SignalDecisionEngine.evaluate(80, 74, 90, 100, null)

        assertEquals(V531Direction.WATCH, margin5.decision)
        assertTrue(V531ReasonCode.DIRECTION_CONFLICT in margin5.reasonCodes)
        assertEquals(V531Direction.LONG, margin6.decision)
    }

    @Test
    fun availableWeight64IsInsufficientBut65PassesCoverageGate() {
        val weight64 = SignalDecisionEngine.evaluate(80, 20, 90, 64, null)
        val weight65 = SignalDecisionEngine.evaluate(80, 20, 90, 65, null)

        assertEquals(V531Direction.WATCH, weight64.decision)
        assertTrue(V531ReasonCode.INSUFFICIENT_SIGNAL_COVERAGE in weight64.reasonCodes)
        assertEquals(V531Direction.LONG, weight65.decision)
    }

    @Test
    fun mtfConflict54DoesNotTriggerSevereGateBut55Does() {
        val conflict54 = SignalDecisionEngine.evaluate(85, 20, 90, 100, -54)
        val conflict55 = SignalDecisionEngine.evaluate(85, 20, 90, 100, -55)

        assertEquals(V531Direction.LONG, conflict54.decision)
        assertTrue(conflict54.conflictPenalty > 0)
        assertEquals(V531Direction.WATCH, conflict55.decision)
        assertTrue(V531ReasonCode.TIMEFRAME_CONFLICT in conflict55.reasonCodes)
    }

    @Test
    fun mtfMinus100ZeroPlus100StayWithinFivePointWeight() {
        val negative = MultiTimeframeEngine.evaluate(-100)
        val neutral = MultiTimeframeEngine.evaluate(0)
        val positive = MultiTimeframeEngine.evaluate(100)

        assertEquals(0, negative.longContribution)
        assertEquals(5, negative.shortContribution)
        assertEquals(0, neutral.longContribution)
        assertEquals(0, neutral.shortContribution)
        assertEquals(5, positive.longContribution)
        assertEquals(0, positive.shortContribution)
    }

    @Test
    fun rsiZeroFiftyHundredProduceExpectedMomentumDirection() {
        val candles = flatCandles(6)
        val low = MomentumEngine.evaluate(candles, technical(rsi = 0.0))
        val neutral = MomentumEngine.evaluate(candles, technical(rsi = 50.0))
        val high = MomentumEngine.evaluate(candles, technical(rsi = 100.0))

        assertTrue(low.shortContribution > 0)
        assertEquals(0, low.longContribution)
        assertEquals(0, neutral.longContribution)
        assertEquals(0, neutral.shortContribution)
        assertTrue(high.longContribution > 0)
        assertEquals(0, high.shortContribution)
    }

    @Test
    fun macdEqualSignalAddsNoDirectionalMomentum() {
        val component = MomentumEngine.evaluate(
            flatCandles(6),
            technical(rsi = 50.0, macd = 1.25, macdSignal = 1.25, atr = 1.0)
        )

        assertEquals(0, component.longContribution)
        assertEquals(0, component.shortContribution)
    }

    private fun flatCandles(count: Int): List<Candle> = (0 until count).map { i ->
        Candle(
            timestamp = 1_700_000_000_000L + i * 60_000L,
            open = 100.0,
            high = 100.5,
            low = 99.5,
            close = 100.0,
            volume = 1_000_000.0
        )
    }

    private fun technical(
        rsi: Double? = null,
        macd: Double? = null,
        macdSignal: Double? = null,
        atr: Double? = null
    ) = TechnicalSnapshot(
        ema20 = null,
        ema50 = null,
        ema200 = null,
        rsi14 = rsi,
        macd = macd,
        macdSignal = macdSignal,
        bbUpper = null,
        bbLower = null,
        atr14 = atr,
        vwap = null,
        volumeRatio = null,
        support = null,
        resistance = null
    )
}
