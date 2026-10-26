package tr.borsatakip.v5.analysis.v531

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.TechnicalSnapshot

class V531CalculationEngineTest {
    @Test
    fun sameInputProducesSameDeterministicOutput() {
        val input = baseInput()

        val first = V531CalculationEngine.calculate(input)
        val second = V531CalculationEngine.calculate(input)

        assertEquals(first, second)
        assertEquals(first.components, second.components)
        assertEquals(first.reasonCodes, second.reasonCodes)
        assertEquals(first.dataConfidence, second.dataConfidence)
        assertEquals(first.riskScore, second.riskScore)
        assertEquals(first.decision, second.decision)
        assertEquals(first.auditLines, second.auditLines)
        assertEquals(ScoringConfig.ENGINE_VERSION, first.engineVersion)
    }

    @Test
    fun missingOpenInterestIsUnavailableNotZeroStrength() {
        val result = V531CalculationEngine.calculate(
            baseInput().copy(openInterestChangePct = null, mtfConsensusScore = null)
        )

        val oi = result.components.single { it.id == "OPEN_INTEREST" }
        assertNull(oi.signedStrength)
        assertFalse(oi.available)
        assertEquals(0, oi.longContribution)
        assertEquals(0, oi.shortContribution)
        assertEquals(85, result.availableSignalWeight)
    }

    @Test
    fun blockedConfidenceForcesWatchEvenWhenSignalMathIsUnchanged() {
        val healthy = V531CalculationEngine.calculate(baseInput())
        val blocked = V531CalculationEngine.calculate(
            baseInput().copy(
                dataQuality = V531DataQualityInput(
                    dataAgeMs = 600_001L,
                    candleCount = 240,
                    candlesValid = true,
                    quoteValid = false,
                    providerHealthy = false,
                    symbolResolved = false,
                    volumeAvailable = false,
                    openInterestApplicable = false,
                    openInterestAvailable = false
                )
            )
        )

        assertEquals(healthy.longScore, blocked.longScore)
        assertEquals(healthy.shortScore, blocked.shortScore)
        assertEquals(healthy.rawSignalScore, blocked.rawSignalScore)
        assertTrue(blocked.dataConfidence < ScoringConfig.BLOCK_CONFIDENCE_BELOW)
        assertEquals(V531ConfidenceBand.BLOCKED, blocked.confidenceBand)
        assertEquals(V531Direction.WATCH, blocked.decision)
        assertTrue(V531ReasonCode.DATA_CONFIDENCE_BLOCKED in blocked.reasonCodes)
    }

    @Test
    fun opposingMtfAppliesConflictPenaltyAndCanForceWatch() {
        val base = V531CalculationEngine.calculate(baseInput().copy(mtfConsensusScore = null))
        val contextual = V531ContextEngine.applyMtf(
            longScore = base.longScore,
            shortScore = base.shortScore,
            dataConfidence = base.dataConfidence,
            riskScore = base.riskScore,
            availableSignalWeight = base.availableSignalWeight,
            mtfConsensusScore = -90
        )

        assertTrue(contextual.conflictPenalty > 0)
        assertTrue(V531ReasonCode.TIMEFRAME_CONFLICT in contextual.reasonCodes)
        assertEquals(V531Direction.WATCH, contextual.direction)
        assertTrue(contextual.finalSignalScore < maxOf(contextual.longScore, contextual.shortScore))
    }

    private fun baseInput(): V531CalculationInput {
        val candles = (0 until 240).map { i ->
            val close = 100.0 + i * 0.35
            Candle(
                timestamp = 1_700_000_000_000L + i * 60_000L,
                open = close - 0.20,
                high = close + 0.50,
                low = close - 0.50,
                close = close,
                volume = if (i == 239) 2_500_000.0 else 1_000_000.0 + i * 2_000.0
            )
        }
        val last = candles.last().close
        val technical = TechnicalSnapshot(
            ema20 = last - 2.0,
            ema50 = last - 5.0,
            ema200 = last - 12.0,
            rsi14 = 68.0,
            macd = 2.0,
            macdSignal = 1.0,
            bbUpper = last + 5.0,
            bbLower = last - 5.0,
            atr14 = 2.0,
            vwap = last - 1.0,
            volumeRatio = 2.0,
            support = last - 8.0,
            resistance = last + 8.0
        )
        return V531CalculationInput(
            candles = candles,
            technical = technical,
            currentPrice = last,
            dataQuality = V531DataQualityInput(
                dataAgeMs = 1_000L,
                candleCount = candles.size,
                candlesValid = true,
                quoteValid = true,
                providerHealthy = true,
                symbolResolved = true,
                volumeAvailable = true,
                openInterestApplicable = false,
                openInterestAvailable = false
            ),
            openInterestChangePct = null,
            mtfConsensusScore = null,
            spreadPct = 0.10,
            liquidityScore = 95,
            slippageSensitivity = 0.10,
            structureDistanceAtr = 2.0,
            contractRiskPct = null
        )
    }
}
