package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.TechnicalSnapshot

class OpportunityRankingPolicyTest {
    private val technical = TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null)
    private fun x(symbol:String, ranking:Int, signal:Int, confidence:Int, risk:Int) = Opportunity(
        symbol=symbol, companyName=null, price=1.0, dailyChangePct=null, score=signal, riskScore=risk,
        direction="NEUTRAL", technicalLabel="", volumeLabel="", kapLabel="", liquidityLabel="",
        support=null, resistance=null, source="test", dataTimestamp=1L, candles=emptyList(), technical=technical,
        finalSignalScore=signal, dataConfidenceScore=confidence, rankingScore=ranking
    )

    @Test fun rankingScoreRemainsPrimaryAcrossUiAndEngine() {
        val lowSignalHighRanking = x("A",90,70,70,30)
        val highSignalLowRanking = x("B",80,99,99,1)
        assertEquals(listOf("A","B"), OpportunityRankingPolicy.sort(listOf(highSignalLowRanking,lowSignalHighRanking)).map { it.symbol })
    }
}
