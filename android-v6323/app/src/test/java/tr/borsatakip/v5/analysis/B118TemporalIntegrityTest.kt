package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class B118TemporalIntegrityTest {
    private fun candle(i: Int, ts0: Long = 1_699_999_920_000L): Candle {
        val close = 100.0 + i * 0.1
        return Candle(
            timestamp = ts0 + i * 60_000L,
            open = close - 0.05,
            high = close + 0.20,
            low = close - 0.20,
            close = close,
            volume = 1000.0 + i
        )
    }

    @Test fun incompleteAggregateBucketIsRejected() {
        val base = listOf(candle(0), candle(2))
        val out = OhlcvResampler.aggregate(base, 1, 3)
        assertTrue(out.isEmpty())
    }

    @Test fun completeAggregateBucketIsAccepted() {
        val base = listOf(candle(0), candle(1), candle(2))
        val out = OhlcvResampler.aggregate(base, 1, 3)
        assertEquals(1, out.size)
        assertEquals(base.first().open, out.first().open, 0.0001)
        assertEquals(base.last().close, out.first().close, 0.0001)
    }

    @Test fun closedBarDecisionDoesNotUseNewerQuotePrice() {
        val candles = (0 until 230).map { candle(it) }
        val stock = Stock(
            symbol = "TEST",
            companyName = "Test",
            candles = candles,
            source = "Yahoo Finance • YEDEK / GECİKMELİ",
            dataTimestamp = candles.last().timestamp,
            isRealtime = false,
            quotePrice = 999.0,
            interval = "1m",
            lastBarTime = candles.last().timestamp,
            lastBarClosed = true,
            previousClose = 99.0
        )
        val result = OpportunityEngine.score(stock)
        assertNotNull(result)
        assertEquals(candles.last().close, result!!.price, 0.0001)
    }

    @Test fun canonicalRankingUsesIndependentRiskAndQuality() {
        val a = OpportunityRankingPolicy.calculate(80, 90, 20, 2.5, 1.5, 100)
        val b = OpportunityRankingPolicy.calculate(80, 60, 70, 1.0, 0.5, 55)
        assertTrue(a > b)
    }
}
