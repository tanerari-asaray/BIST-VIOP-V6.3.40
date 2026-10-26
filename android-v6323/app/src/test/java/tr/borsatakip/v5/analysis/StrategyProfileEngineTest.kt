package tr.borsatakip.v5.analysis

import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.*

class StrategyProfileEngineTest {
    @Test fun combinedStrategyUsesRankingScoreNotForwardData() {
        val o = Opportunity(
            symbol="TEST", companyName=null, price=100.0, dailyChangePct=1.0, score=70, riskScore=20,
            direction="LONG", technicalLabel="Güçlü", volumeLabel="1.5x", kapLabel="", liquidityLabel="",
            support=98.0, resistance=105.0, source="test", dataTimestamp=1L, candles=emptyList(),
            technical=TechnicalSnapshot(99.0, 95.0, 90.0, 60.0, 2.0, 1.0, null, null, 2.0, null, 1.6, 98.0, 105.0),
            rankingScore=82, mtfConsensusScore=60, marketRegime="TREND YUKARI", marketRegimeConfidence=80,
            setupType="KIRILIM"
        )
        val scores = StrategyProfileEngine.evaluate(o)
        assertTrue(scores.first { it.id == StrategyProfileEngine.StrategyId.COMBINED }.score == 82)
        assertTrue(scores.first { it.id == StrategyProfileEngine.StrategyId.BREAKOUT }.active)
        assertTrue(scores.first { it.id == StrategyProfileEngine.StrategyId.MTF_REGIME }.active)
    }
}
