package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.RiskPlan
import tr.borsatakip.v5.model.TechnicalSnapshot

class Opportunity2PresentationTest {
    private val technical = TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null)

    @Test fun typedClassSetupTimingAndRrWinOverLegacyBreakdownText() {
        val x = Opportunity(
            symbol="TEST", companyName=null, price=100.0, dailyChangePct=null, score=80, riskScore=20,
            direction="LONG", technicalLabel="", volumeLabel="", kapLabel="", liquidityLabel="",
            support=null, resistance=null, source="test", dataTimestamp=1L, candles=emptyList(), technical=technical,
            scoreBreakdown=listOf("CLASS=YANLIŞ", "SETUP=YANLIŞ", "RR=9.9", "TIMING=YANLIŞ"),
            setupType="BREAKOUT", qualityClass="A SINIFI", timingStatus="ZAMANINDA",
            riskPlan=RiskPlan(100.0,95.0,110.0,120.0,2.0,4.0)
        )
        val p = Opportunity2Presentation.from(x)
        assertEquals("A SINIFI",p.klass)
        assertEquals("BREAKOUT",p.setup)
        assertEquals(2.0,p.riskReward!!,0.000001)
        assertEquals("ZAMANINDA",p.timing)
    }
}
