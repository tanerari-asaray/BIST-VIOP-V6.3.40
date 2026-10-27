package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class Build670StageCountersTest {
    @Test fun priceSuccessDoesNotImplyHistoryOrTechnicalSuccess() {
        val row=FullUniversePolicy.Entry(
            FullUniversePolicy.Market.VIOP, "F_XU0301026", "test", 49.25, "TRY",
            FullUniversePolicy.Trend.UNKNOWN, FullUniversePolicy.Quality.DELAYED,
            "FIXTURE", 1800000000000L, 1800000000000L,
            "GEÇMİŞ_VERİSİ_HATASI: NETWORK_TIMEOUT")
        val c=FullUniversePolicy.stages(listOf(row))
        assertEquals(1,c.priceOk)
        assertEquals(0,c.historyOk)
        assertEquals(0,c.technicalOk)
        assertEquals(0,c.financialAssessed)
        assertEquals(0,c.discounted)
    }

    @Test fun failedQuoteNeverCountsAsSuccessfulPrice() {
        val row=FullUniversePolicy.Entry(
            FullUniversePolicy.Market.VIOP, "F_XU0301026", "test", null, "TRY",
            FullUniversePolicy.Trend.UNKNOWN, FullUniversePolicy.Quality.NO_DATA,
            "FIXTURE", 0L, 0L, "Quote failed", reasonCode="SERVICE_UNAVAILABLE")
        val c=FullUniversePolicy.stages(listOf(row))
        assertEquals(0,c.priceOk)
        assertEquals(1,c.errors)
    }
}
