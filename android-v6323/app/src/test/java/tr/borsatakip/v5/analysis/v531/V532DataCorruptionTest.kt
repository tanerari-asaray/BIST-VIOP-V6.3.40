package tr.borsatakip.v5.analysis.v531

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.TechnicalSnapshot

class V532DataCorruptionTest {
    @Test
    fun nanNegativeAndZeroPriceAreRejected() {
        listOf(Double.NaN, -1.0, 0.0).forEach { price ->
            val result = V531CalculationEngine.calculate(baseInput().copy(currentPrice = price))
            assertEquals(V531Direction.WATCH, result.decision)
            assertEquals(0, result.finalSignalScore)
            assertTrue(V531ReasonCode.INVALID_PRICE in result.reasonCodes)
        }
    }

    @Test
    fun nanAndNegativeAtrMakeVolatilityUnavailableWithoutCrash() {
        listOf(Double.NaN, -2.0).forEach { atr ->
            val input = baseInput().copy(technical = baseTechnical().copy(atr14 = atr))
            val result = V531CalculationEngine.calculate(input)
            val volatility = result.components.single { it.id == "VOLATILITY" }

            assertFalse(volatility.available)
            assertTrue(result.riskScore in 0..100)
            assertTrue(result.riskCoveragePercent in 0..100)
        }
    }

    @Test
    fun malformedCandlesAreIgnoredRatherThanPoisoningStructureMath() {
        val candles = risingCandles(30).toMutableList()
        candles[5] = candles[5].copy(high = 90.0, low = 110.0)
        candles[6] = candles[6].copy(close = Double.NaN)

        val result = V531CalculationEngine.calculate(
            baseInput().copy(candles = candles, currentPrice = candles.last().close)
        )

        assertTrue(result.finalSignalScore in 0..100)
        assertTrue(result.components.single { it.id == "STRUCTURE" }.available)
    }

    @Test
    fun allZeroVolumeWithoutDeclaredRvolIsUnavailable() {
        val candles = risingCandles(25).map { it.copy(volume = 0.0) }
        val component = VolumeEngine.evaluate(candles, baseTechnical().copy(volumeRatio = null))

        assertFalse(component.available)
        assertEquals(0, component.longContribution)
        assertEquals(0, component.shortContribution)
    }

    @Test
    fun missingEmaMakesTrendUnavailable() {
        val component = TrendEngine.evaluate(100.0, emptyTechnical())

        assertFalse(component.available)
    }

    @Test
    fun missingRsiAndMacdStillAllowsPriceRocMomentumWithoutInventedIndicatorEvidence() {
        val component = MomentumEngine.evaluate(risingCandles(25), emptyTechnical())

        assertTrue(component.available)
        assertFalse(component.evidence.any { it.startsWith("RSI14=") })
        assertFalse(component.evidence.any { it.startsWith("MACD_SPREAD=") })
    }

    @Test
    fun allSignalComponentsUnavailableProduceZeroCoverageAndWatch() {
        val input = V531CalculationInput(
            candles = emptyList(),
            technical = emptyTechnical(),
            currentPrice = 100.0,
            dataQuality = quality(candleCount = 0, candlesValid = false, volumeAvailable = false),
            openInterestChangePct = null,
            mtfConsensusScore = null
        )

        val result = V531CalculationEngine.calculate(input)

        assertEquals(0, result.availableSignalWeight)
        assertEquals(V531Direction.WATCH, result.decision)
        assertTrue(V531ReasonCode.INSUFFICIENT_SIGNAL_COVERAGE in result.reasonCodes)
    }

    @Test
    fun onlyTrendAvailableDoesNotPassMinimumCoverageGate() {
        val input = V531CalculationInput(
            candles = emptyList(),
            technical = emptyTechnical().copy(ema20 = 90.0),
            currentPrice = 100.0,
            dataQuality = quality(candleCount = 0, candlesValid = false, volumeAvailable = false),
            openInterestChangePct = null,
            mtfConsensusScore = null
        )

        val result = V531CalculationEngine.calculate(input)

        assertEquals(ScoringConfig.TREND_WEIGHT, result.availableSignalWeight)
        assertEquals(V531Direction.WATCH, result.decision)
        assertTrue(V531ReasonCode.INSUFFICIENT_SIGNAL_COVERAGE in result.reasonCodes)
    }

    @Test
    fun zeroAndInvalidFromPriceNeverProducePercentReturn() {
        assertEquals(null, ScoreNormalizer.pct(0.0, 100.0))
        assertEquals(null, ScoreNormalizer.pct(-1.0, 100.0))
        assertEquals(null, ScoreNormalizer.pct(Double.NaN, 100.0))
    }

    private fun baseInput() = V531CalculationInput(
        candles = risingCandles(240),
        technical = baseTechnical(),
        currentPrice = risingCandles(240).last().close,
        dataQuality = quality(candleCount = 240, candlesValid = true, volumeAvailable = true),
        openInterestChangePct = null,
        mtfConsensusScore = null,
        spreadPct = 0.1,
        liquidityScore = 90,
        slippageSensitivity = 0.1,
        structureDistanceAtr = 2.0
    )

    private fun quality(candleCount: Int, candlesValid: Boolean, volumeAvailable: Boolean) = V531DataQualityInput(
        dataAgeMs = 1_000L,
        candleCount = candleCount,
        candlesValid = candlesValid,
        quoteValid = true,
        providerHealthy = true,
        symbolResolved = true,
        volumeAvailable = volumeAvailable,
        openInterestApplicable = false,
        openInterestAvailable = false
    )

    private fun risingCandles(count: Int): List<Candle> = (0 until count).map { i ->
        val close = 100.0 + i * 0.2
        Candle(
            timestamp = 1_700_000_000_000L + i * 60_000L,
            open = close - 0.1,
            high = close + 0.4,
            low = close - 0.4,
            close = close,
            volume = 1_000_000.0 + i * 1_000.0
        )
    }

    private fun baseTechnical(): TechnicalSnapshot {
        val price = risingCandles(240).last().close
        return TechnicalSnapshot(
            ema20 = price - 2.0,
            ema50 = price - 5.0,
            ema200 = price - 10.0,
            rsi14 = 65.0,
            macd = 1.5,
            macdSignal = 1.0,
            bbUpper = null,
            bbLower = null,
            atr14 = 2.0,
            vwap = price - 1.0,
            volumeRatio = 1.5,
            support = price - 5.0,
            resistance = price + 5.0
        )
    }

    private fun emptyTechnical() = TechnicalSnapshot(
        ema20 = null,
        ema50 = null,
        ema200 = null,
        rsi14 = null,
        macd = null,
        macdSignal = null,
        bbUpper = null,
        bbLower = null,
        atr14 = null,
        vwap = null,
        volumeRatio = null,
        support = null,
        resistance = null
    )
}
