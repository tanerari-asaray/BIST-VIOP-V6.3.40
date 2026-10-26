package tr.borsatakip.v5.analysis.v534

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V534RemoteInputHashTest {
    @Test fun sameInputProducesSameHash() = assertEquals(V534RemoteInputHash.hash(valid()), V534RemoteInputHash.hash(valid()))
    @Test fun rsiMutationChangesHash() = assertNotEquals(V534RemoteInputHash.hash(valid()), V534RemoteInputHash.hash(valid().copy(rsi14 = 61.0)))
    @Test fun emaMutationChangesHash() = assertNotEquals(V534RemoteInputHash.hash(valid()), V534RemoteInputHash.hash(valid().copy(ema20 = 101.0)))
    @Test fun macdMutationChangesHash() = assertNotEquals(V534RemoteInputHash.hash(valid()), V534RemoteInputHash.hash(valid().copy(macd = 1.5)))
    @Test fun priceMutationChangesHash() = assertNotEquals(V534RemoteInputHash.hash(valid()), V534RemoteInputHash.hash(valid().copy(price = 102.0)))
    @Test fun mtfMutationChangesHash() = assertNotEquals(V534RemoteInputHash.hash(valid()), V534RemoteInputHash.hash(valid().copy(mtfConsensusScore = -40)))
    @Test fun invalidTimestampRejected() = assertTrue(V534RemoteInputHash.validate(valid().copy(evaluationTimestamp = 0L)).isNotEmpty())
    @Test fun invalidMtfRejected() = assertTrue(V534RemoteInputHash.validate(valid().copy(mtfConsensusScore = 101)).isNotEmpty())

    private fun valid() = V534RemoteCalculationInput(
        symbol = "THYAO",
        analysisTimeframeMinutes = 5,
        evaluationTimestamp = 1_800_000_000_000L,
        price = 100.0,
        stockTrend = "UP",
        momentum = "POSITIVE",
        relativeVolume = 1.4,
        rsi14 = 60.0,
        macd = 1.2,
        macdSignal = 0.9,
        atr14 = 2.0,
        sessionVwap = 99.0,
        ema20 = 98.0,
        ema50 = 95.0,
        ema200 = 90.0,
        openInterestChangePct = null,
        mtfConsensusScore = 45,
        spreadPct = 0.15,
        liquidityScore = 80,
        slippageSensitivity = 0.2,
        structureDistanceAtr = 1.1,
        contractRiskPct = null
    )
}
