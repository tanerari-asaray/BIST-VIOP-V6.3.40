package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot

class RealtimeShortPresentationTest {
    @Test
    fun verifiedShortIsPresentedAsStrongSell() {
        val technical = TechnicalSnapshot(
            ema20 = 92.0,
            ema50 = 95.0,
            ema200 = 100.0,
            rsi14 = 40.0,
            macd = -2.0,
            macdSignal = -1.0,
            bbUpper = null,
            bbLower = null,
            atr14 = 1.5,
            vwap = 94.0,
            volumeRatio = 2.0,
            support = null,
            resistance = null
        )
        val x = Opportunity(
            symbol = "TEST",
            companyName = "Test",
            price = 90.0,
            dailyChangePct = -2.2,
            score = 90,
            riskScore = 25,
            direction = "SHORT",
            technicalLabel = "Çok Güçlü",
            volumeLabel = "2.0x",
            kapLabel = "Veri yok",
            liquidityLabel = "STRONG_DOWN",
            support = null,
            resistance = null,
            source = "licensed-test",
            dataTimestamp = System.currentTimeMillis(),
            candles = emptyList(),
            technical = technical,
            dataConfidenceScore = 95,
            dataConfidenceLabel = "Canlı feed doğrulandı",
            finalSignalScore = 90,
            isRealtime = true,
            delaySeconds = 0,
            currentSessionIncluded = true,
            dataMode = DataMode.REALTIME,
            signalValidity = SignalValidity.VALID,
            longScore = 18,
            shortScore = 90,
            decisionState = DecisionState.VERIFIED_OPPORTUNITY,
            rankingScore = 90
        )

        val p = OpportunityDiscoveryPresentation.from(x)
        assertEquals("GÜÇLÜ SAT", p.classification)
        assertTrue(p.reason.contains("aşağı yönlü") || p.reason.contains("negatif momentum"))
    }
}
