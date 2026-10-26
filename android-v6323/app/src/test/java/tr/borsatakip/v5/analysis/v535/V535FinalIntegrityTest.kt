package tr.borsatakip.v5.analysis.v535

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V535FinalIntegrityTest {
    @Test fun sameInputSameHash() = assertEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput()))
    @Test fun oiMutationChangesHash() = assertNotEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput().copy(openInterestChangePct = 3.1)))
    @Test fun spreadMutationChangesHash() = assertNotEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput().copy(spreadPct = 0.31)))
    @Test fun liquidityMutationChangesHash() = assertNotEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput().copy(liquidityScore = 61)))
    @Test fun slippageMutationChangesHash() = assertNotEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput().copy(slippageSensitivity = 0.41)))
    @Test fun structureMutationChangesHash() = assertNotEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput().copy(structureDistanceAtr = 1.7)))
    @Test fun contractRiskMutationChangesHash() = assertNotEquals(V535RemoteInputHash.hash(validInput()), V535RemoteInputHash.hash(validInput().copy(contractRiskPct = 44)))

    @Test fun exactMirrorAccepted() = assertTrue(V535RemoteInputConsistency.validate(validInput(), mirror()).accepted)
    @Test fun oiMismatchRejected() = rejectMirror(mirror().copy(openInterestChangePct = 9.0), "OI_CHANGE_MISMATCH")
    @Test fun spreadMismatchRejected() = rejectMirror(mirror().copy(spreadPct = 9.0), "SPREAD_MISMATCH")
    @Test fun liquidityMismatchRejected() = rejectMirror(mirror().copy(liquidityScore = 1), "LIQUIDITY_MISMATCH")
    @Test fun slippageMismatchRejected() = rejectMirror(mirror().copy(slippageSensitivity = 9.0), "SLIPPAGE_MISMATCH")
    @Test fun structureMismatchRejected() = rejectMirror(mirror().copy(structureDistanceAtr = 9.0), "STRUCTURE_DISTANCE_MISMATCH")
    @Test fun contractRiskMismatchRejected() = rejectMirror(mirror().copy(contractRiskPct = 1), "CONTRACT_RISK_MISMATCH")
    @Test fun mtfMismatchRejected() = rejectMirror(mirror().copy(mtfConsensusScore = -70), "MTF_MISMATCH")

    @Test fun mtfLongLabel() = assertEquals("MTF LONG UYUM", V535MtfSemantics.labelFor(55))
    @Test fun mtfShortLabel() = assertEquals("MTF SHORT UYUM", V535MtfSemantics.labelFor(-55))
    @Test fun mtfMixedLabel() = assertEquals("MTF KARIŞIK", V535MtfSemantics.labelFor(0))
    @Test fun mtfMissingLabel() = assertEquals("MTF VERİ YOK", V535MtfSemantics.labelFor(null))
    @Test fun contradictoryMtfLabelRejected() = assertFalse(V535MtfSemantics.isConsistent(80, "MTF SHORT UYUM"))

    @Test fun exactClockEvidenceAccepted() = assertTrue(V535ClockIntegrity.validateItem(clock()).accepted)
    @Test fun futureAtToleranceAccepted() = assertTrue(V535ClockIntegrity.validateItem(clock().copy(generatedAt = T, exchangeTimestamp = T + 15_000L, dataTimestamp = T + 15_000L, receivedAt = T, declaredDataAgeMs = 0L)).accepted)
    @Test fun beyondFutureToleranceRejected() = rejectClock(clock().copy(generatedAt = T, exchangeTimestamp = T + 15_001L, dataTimestamp = T + 15_001L, receivedAt = T, declaredDataAgeMs = 0L), "EXCHANGE_IN_FUTURE")
    @Test fun staleBeyondLimitRejected() = rejectClock(clock().copy(exchangeTimestamp = T - 60_001L, dataTimestamp = T - 60_001L, declaredDataAgeMs = 60_000L), "EXCHANGE_TOO_OLD")
    @Test fun declaredAgeMismatchRejected() = rejectClock(clock().copy(declaredDataAgeMs = 60_000L), "DATA_AGE_MISMATCH")
    @Test fun receivedAfterServerToleranceRejected() = rejectClock(clock().copy(receivedAt = T + 15_001L), "RECEIVED_AFTER_SERVER")
    @Test fun receivedBeforeExchangeToleranceRejected() = rejectClock(clock().copy(exchangeTimestamp = T - 1_000L, dataTimestamp = T - 1_000L, receivedAt = T - 16_001L, declaredDataAgeMs = 1_000L), "RECEIVED_BEFORE_EXCHANGE")
    @Test fun generatedFutureRejected() = assertFalse(V535ClockIntegrity.validateSnapshot(T, T + 15_001L).accepted)
    @Test fun generatedTooOldRejected() = assertFalse(V535ClockIntegrity.validateSnapshot(T, T - 60_001L).accepted)

    @Test fun negativeSpreadRejected() = assertTrue("INPUT_SPREAD_NEGATIVE" in V535RemoteInputHash.validate(validInput().copy(spreadPct = -0.1)))
    @Test fun negativeSlippageRejected() = assertTrue("INPUT_SLIPPAGE_NEGATIVE" in V535RemoteInputHash.validate(validInput().copy(slippageSensitivity = -0.1)))
    @Test fun negativeStructureDistanceRejected() = assertTrue("INPUT_STRUCTURE_DISTANCE_NEGATIVE" in V535RemoteInputHash.validate(validInput().copy(structureDistanceAtr = -0.1)))
    @Test fun invalidLiquidityRejected() = assertTrue("INPUT_LIQUIDITY_RANGE" in V535RemoteInputHash.validate(validInput().copy(liquidityScore = 101)))
    @Test fun invalidContractRiskRejected() = assertTrue("INPUT_CONTRACT_RISK_RANGE" in V535RemoteInputHash.validate(validInput().copy(contractRiskPct = 101)))

    private fun rejectMirror(value: V535RemoteItemMirror, code: String) {
        val r = V535RemoteInputConsistency.validate(validInput(), value)
        assertFalse(r.accepted)
        assertTrue(code in r.violations)
    }

    private fun rejectClock(value: V535ClockEvidence, code: String) {
        val r = V535ClockIntegrity.validateItem(value)
        assertFalse(r.accepted)
        assertTrue(code in r.violations)
    }

    private fun validInput() = V535RemoteCalculationInput(
        symbol = "THYAO",
        analysisTimeframeMinutes = 5,
        evaluationTimestamp = T - 1_000L,
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
        openInterestChangePct = 2.5,
        mtfConsensusScore = 60,
        spreadPct = 0.15,
        liquidityScore = 80,
        slippageSensitivity = 0.2,
        structureDistanceAtr = 1.1,
        contractRiskPct = 30
    )

    private fun mirror() = validInput().let { i ->
        V535RemoteItemMirror(
            symbol = i.symbol,
            analysisTimeframeMinutes = i.analysisTimeframeMinutes,
            evaluationTimestamp = i.evaluationTimestamp,
            price = i.price,
            stockTrend = i.stockTrend,
            momentum = i.momentum,
            relativeVolume = i.relativeVolume,
            rsi14 = i.rsi14,
            macd = i.macd,
            macdSignal = i.macdSignal,
            atr14 = i.atr14,
            sessionVwap = i.sessionVwap,
            ema20 = i.ema20,
            ema50 = i.ema50,
            ema200 = i.ema200,
            openInterestChangePct = i.openInterestChangePct,
            mtfConsensusScore = i.mtfConsensusScore,
            spreadPct = i.spreadPct,
            liquidityScore = i.liquidityScore,
            slippageSensitivity = i.slippageSensitivity,
            structureDistanceAtr = i.structureDistanceAtr,
            contractRiskPct = i.contractRiskPct
        )
    }

    private fun clock() = V535ClockEvidence(
        serverTime = T,
        generatedAt = T - 500L,
        exchangeTimestamp = T - 1_000L,
        dataTimestamp = T - 1_000L,
        receivedAt = T - 500L,
        declaredDataAgeMs = 1_000L
    )

    companion object { const val T = 1_800_000_000_000L }
}
