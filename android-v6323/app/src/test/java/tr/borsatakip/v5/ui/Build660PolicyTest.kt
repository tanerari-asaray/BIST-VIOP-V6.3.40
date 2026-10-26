package tr.borsatakip.v5.ui

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.data.FullUniversePolicy as P

class Build660PolicyTest {
    private fun row(m:P.Market,s:String)=P.Entry(m,s,"TEST FIXTURE",10.0,"TRY",P.Trend.UNKNOWN,P.Quality.UNVERIFIED,"TEST",0,0,"")
    @Test fun marketFiltersNeverSubstituteStocksForContracts() {
        val rows=listOf(row(P.Market.BIST,"TEST"),row(P.Market.VIOP,"F_TEST1026"),row(P.Market.VIOP,"F_TEST1226"))
        assertEquals(3,P.visible(rows,null,"").size)
        assertEquals(listOf("TEST"),P.visible(rows,P.Market.BIST,"").map { it.symbol })
        assertEquals(2,P.visible(rows,P.Market.VIOP,"").size)
        assertTrue(P.visible(rows.take(1),P.Market.VIOP,"").isEmpty())
    }
    @Test fun missingMarketCannotClaimCoverage() {
        val c=P.Counters(discoveredBist=1,triedBist=1,successfulBist=1,bistDiscoveryOk=true)
        assertTrue(c.valid());assertFalse(c.attemptedDiscoveredUniverse);assertFalse(c.canClaimExchangeComplete)
        assertTrue(P.summary(c,P.State.PARTIAL).contains("KISMİ"))
    }
    @Test fun failuresAreDistinctFromNoDataAndRemainPartial() {
        val c=P.Counters(discoveredViop=3,triedViop=3,successfulViop=1,noDataViop=1,errorsViop=1)
        assertTrue(c.valid());assertEquals(2,c.failures);assertFalse(c.canClaimExchangeComplete)
    }
    @Test fun networkAndHttpErrorsHaveStableCategories() {
        for(code in listOf(401,403))assertEquals("AUTH_ERROR",FullUniverseScanRuntime.reason(Exception("HTTP $code")))
        assertEquals("RATE_LIMIT",FullUniverseScanRuntime.reason(Exception("HTTP 429")))
        assertEquals("SERVICE_UNAVAILABLE",FullUniverseScanRuntime.reason(Exception("HTTP 503")))
        assertEquals("NETWORK_TIMEOUT",FullUniverseScanRuntime.reason(java.net.SocketTimeoutException()))
        assertEquals("NETWORK_ERROR",FullUniverseScanRuntime.reason(java.io.IOException()))
    }
}
