package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.model.ViopQuote

class ViopScannerPolicyTest {
    @Test fun expiredContractIsHardRejectedByTimestampPolicy() {
        val now = 10_000L
        assertTrue(ViopScanner.isPastLastTradingAt(contract("OLD", 9_999L), now))
        assertFalse(ViopScanner.isPastLastTradingAt(contract("NEW", 10_001L), now))
        assertFalse(ViopScanner.isPastLastTradingAt(contract("UNK", null), now))
    }

    @Test fun sharedRankingSorterUsesRankingThenFinalThenRisk() {
        val items = listOf(
            opp("A", ranking = 70, final = 95, risk = 10),
            opp("B", ranking = 90, final = 80, risk = 30),
            opp("C", ranking = 90, final = 80, risk = 20),
            opp("D", ranking = 90, final = 85, risk = 50)
        )
        assertEquals(listOf("D", "C", "B", "A"), ViopScanner.sortOpportunities(items).map { it.contract.symbol })
    }

    private fun contract(symbol: String, lastTradingAt: Long?) = ViopContract(
        symbol = symbol, underlying = "AKBNK", expiry = "10/2026", lastTradingAt = lastTradingAt
    )

    private fun opp(symbol: String, ranking: Int, final: Int, risk: Int): ViopOpportunity {
        val c = contract(symbol, 20_000L)
        val q = ViopQuote(symbol, 10.0, null, null, null, 1000.0, 100L, 1L, 1L, "unit", true, 0, true)
        val t = TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null)
        return ViopOpportunity(
            contract=c, quote=q, candles=listOf(Candle(1L,10.0,10.0,10.0,10.0,1.0)), technical=t,
            technicalScore=final, riskScore=risk, liquidityScore=80, expiryRisk=10,
            longScore=final, shortScore=0, finalScore=final, direction="LONG", signalReason="unit",
            validity=SignalValidity.VALID, dataAgeMs=0L, historyCandleCount=220,
            decisionState=DecisionState.VERIFIED_OPPORTUNITY, dataConfidenceScore=90, rankingScore=ranking
        )
    }
}
