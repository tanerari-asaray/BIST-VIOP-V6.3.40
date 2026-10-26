package tr.borsatakip.v5.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TradingViewChartLinkTest {
    @Test
    fun plainBistTickerMapsToTradingViewExchangeSymbol() {
        assertEquals("BIST:KIMMR", TradingViewChartLink.toBistSymbol("KIMMR"))
    }

    @Test
    fun alreadyPrefixedOrYahooStyleTickerIsNormalized() {
        assertEquals("BIST:THYAO", TradingViewChartLink.toBistSymbol("bist:thyao"))
        assertEquals("BIST:AKBNK", TradingViewChartLink.toBistSymbol("akbnk.is"))
    }

    @Test
    fun unsafeOrEmptyTickerFailsClosed() {
        assertNull(TradingViewChartLink.toBistSymbol(""))
        assertNull(TradingViewChartLink.toBistSymbol("THYAO/../"))
    }
}
