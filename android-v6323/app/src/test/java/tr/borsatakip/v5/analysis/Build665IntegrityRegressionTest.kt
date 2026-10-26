package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.data.*
import tr.borsatakip.v5.model.*

class Build665IntegrityRegressionTest {
    private val now = 1_792_500_000_000L
    private fun stock(previous: Double? = 100.0): Stock {
        val bars=(0 until 230).map { i -> Candle(now-(230L-i)*86_400_000,99.0,101.0,98.0,100.0,1_000_000.0) }
        return Stock("TEST", "TEST VERİSİ",bars,"Yahoo gecikmeli",now, quotePrice=110.0,
            previousClose=previous,lastBarClosed=true,lastBarTime=bars.last().timestamp)
    }
    private fun evidence()=BistPublicationPolicy.Evidence(true,true,true,true,true,true,true,false,
        true,true,true,false,40_000_000.0,1.5,true,1.0,false,newsRiskKnown=true)

    @Test fun quotePriceTimeAndDailyPercentStaySeparateFromDecision() {
        val result=requireNotNull(OpportunityEngine.score(stock()))
        assertEquals(100.0,result.price,0.0001)
        assertEquals(110.0,StockQuotePolicy.price(result),0.0001)
        assertEquals(now,StockQuotePolicy.timestamp(result))
        assertEquals(10.0,requireNotNull(result.dailyChangePct),0.0001)
        assertEquals(result.candles.last().close,result.snapshot!!.price,0.0001)
    }
    @Test fun absentDailyCloseNeverUsesPreviousIntradayCandle() {
        val result=requireNotNull(OpportunityEngine.score(stock(null)))
        assertNull(result.dailyChangePct);assertNull(StockQuotePolicy.change(result))
    }
    @Test fun quoteEligibilityExpiresEvenIfTheRealtimeFlagStaysTrue() {
        val q=DisplayQuoteSnapshot(110.0,100.0,now,"TEST",true,0,true)
        assertTrue(StockQuotePolicy.live(q,now+60000));assertFalse(StockQuotePolicy.live(q,now+60001))
        assertFalse(StockQuotePolicy.live(q,now-1));assertEquals(now,q.timestamp)
    }
    @Test fun metaQuoteUsesMarketTimeInsteadOfLastCandleTime() {
        val q=YahooQuotePairPolicy.select(110.0,now,100.0,now-300000,now)!!
        assertEquals(110.0,q.price,0.0);assertEquals(now,q.timestamp)
    }
    @Test fun missingMetaTimeUsesCompleteCandlePair() {
        val q=YahooQuotePairPolicy.select(110.0,null,100.0,now-300000,now)!!
        assertEquals(100.0,q.price,0.0);assertEquals(now-300000,q.timestamp)
        assertNull(YahooQuotePairPolicy.select(110.0,null,Double.NaN,now,now))
    }
    @Test fun formingHistoryIsAcceptedOnlyWhenRequestedExplicitly() {
        assertNotNull(ViopHistoryIntegrityPolicy.validateMetadata("F_TEST","5m","F_TEST","5m",false,now))
        assertNull(ViopHistoryIntegrityPolicy.validateMetadata("F_TEST","5m","F_TEST","5m",false,now,allowForming=true))
        assertNotNull(ViopHistoryIntegrityPolicy.validateMetadata("F_TEST","5m","F_OTHER","5m",false,now,true))
    }
    @Test fun dailyClosureCanUseExplicitSessionCloseAndRejectsFutureClose() {
        val bars=listOf(Candle(now-10_000,10.0,11.0,9.0,10.0,100.0))
        assertNotNull(ViopHistoryIntegrityPolicy.validateLastBarClosed(bars,now,"1d"))
        assertNull(ViopHistoryIntegrityPolicy.validateLastBarClosed(bars,now,"1d",now-1000))
        assertNotNull(ViopHistoryIntegrityPolicy.validateLastBarClosed(bars,now,"1d",now+1000))
    }
    @Test fun independentEvidenceHasSixCategoriesAndMaximum100Points() {
        val x=BistPublicationPolicy.assess(evidence().copy(news=true))
        assertEquals(100,x.score);assertEquals(6,x.confirmations.size);assertTrue(x.eligible)
        assertEquals(setOf("TREND","PRICE","VOLUME","RS","MOMENTUM","MARKET"),x.confirmations)
    }
    @Test fun turnoverBelowThirtyMillionOrUnknownBlocksPublication() {
        for(value in listOf(29_999_999.0,null,Double.NaN)) {
            val x=BistPublicationPolicy.assess(evidence().copy(averageTurnoverTry=value))
            assertFalse(x.eligible);assertTrue(x.reasons.contains("DAILY_TURNOVER_BELOW_30M_OR_UNKNOWN"))
        }
    }
    @Test fun rrOnePointFourNineDoesNotPassDespiteHighScore() {
        val x=BistPublicationPolicy.assess(evidence().copy(rr=1.49))
        assertTrue(x.score>=75);assertFalse(x.eligible);assertTrue(x.reasons.contains("RR_BELOW_1_5_OR_UNKNOWN"))
    }
    @Test fun noVolumeConfirmationGapAndRsiRequireWaiting() {
        assertFalse(BistPublicationPolicy.assess(evidence().copy(volume=false)).eligible)
        assertFalse(BistPublicationPolicy.assess(evidence().copy(gapPct=5.01)).eligible)
        assertFalse(BistPublicationPolicy.assess(evidence().copy(gapPct=-5.01)).eligible)
        assertFalse(BistPublicationPolicy.assess(evidence().copy(excessiveRsi=true)).eligible)
    }
    @Test fun missingBenchmarkCannotBecomeKnownMarketRegime() {
        val x=BistPublicationPolicy.assess(evidence().copy(market=false,marketKnown=false))
        assertFalse(x.eligible);assertTrue(x.reasons.contains("MARKET_REGIME_UNKNOWN"))
    }
    @Test fun dailyTurnoverExcludesTheDecisionDayEvenWithHugeCurrentVolume() {
        val result=requireNotNull(OpportunityEngine.score(stock()))
        val bars=stock().candles.takeLast(21).map { it.copy(volume=100.0) }
        val t=bars.last().timestamp
        val input=bars.dropLast(1)+bars.last().copy(volume=1e12)
        val x=BistPublicationPolicy.evaluate(result.copy(candles=listOf(bars.last()),dataTimestamp=t),input,null)
        assertEquals(10_000.0,requireNotNull(x.averageTurnoverTry),0.001)
        assertFalse(x.eligible)
    }
    @Test fun bistRiskPlanUsesOnePointFiveAtrStopAndMinimumRr() {
        val t=TechnicalSnapshot(99.0,98.0,95.0,55.0,0.1,0.0,105.0,95.0,2.0,100.0,2.0,98.5,120.0)
        val bars=listOf(Candle(now-1000,100.0,101.0,98.0,100.0,1000.0))
        val x=V540FinalDecisionFlow.finalize(tr.borsatakip.v5.analysis.v531.V531Direction.LONG,
            90,90,20,100.0,t,bars,100,6,6,minFirstTargetGrossRr=1.5,stopAtrMultiplier=1.5)
        assertEquals(97.0,requireNotNull(x.riskPlan?.stop),0.0001)
        assertTrue(requireNotNull(x.rr1)>=1.5)
        val wide=V540FinalDecisionFlow.finalize(tr.borsatakip.v5.analysis.v531.V531Direction.LONG,
            90,90,20,100.0,t.copy(support=90.0),bars,100,6,6,minFirstTargetGrossRr=1.5,stopAtrMultiplier=1.5)
        assertNull("Wide structural risk cannot invent a target beyond the allowed extension",wide.riskPlan)
        assertEquals(tr.borsatakip.v5.analysis.v531.V531Direction.WATCH,wide.publishedDirection)
    }
    @Test fun diagnosticsKeepActionableCategoriesWithoutProviderSecrets() {
        assertEquals("AUTH_ERROR",StockQuotePolicy.failureCode(IllegalArgumentException("HTTP 401")))
        assertEquals("RATE_LIMIT",StockQuotePolicy.failureCode(IllegalArgumentException("HTTP 429")))
        assertEquals("TIMEOUT",StockQuotePolicy.failureCode(java.net.SocketTimeoutException()))
    }
}
