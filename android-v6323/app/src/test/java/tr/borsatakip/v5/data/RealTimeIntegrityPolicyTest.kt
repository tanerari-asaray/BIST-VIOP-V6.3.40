package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class RealTimeIntegrityPolicyTest {
    private fun stock(
        receivedAt: Long,
        receivedElapsed: Long = 10_000L,
        realtime: Boolean = true,
        delay: Int? = 0,
        currentSession: Boolean = true,
        timestamp: Long = receivedAt,
        symbol: String = "ASELS",
        historySymbol: String = symbol,
        candlesOverride: List<Candle>? = null
    ): Stock {
        val candles = candlesOverride ?: (0 until 220).map { i ->
            Candle(receivedAt - (220L - i) * 86_400_000L,100.0,102.0,99.0,101.0,1_000_000.0)
        }
        return Stock(
            symbol=symbol, companyName="TEST", candles=candles, source="licensed-test-provider",
            dataTimestamp=timestamp, isRealtime=realtime, delaySeconds=delay, currentSessionIncluded=currentSession,
            receivedAt=receivedAt, receivedElapsedRealtime=receivedElapsed, market="BIST", historySymbol=historySymbol,
            interval="1d", exchangeTimezone="Europe/Istanbul", lastBarTime=candles.last().timestamp, lastBarClosed=true,
            previousClose=candles.last().close
        )
    }

    @Test fun acceptsVerifiedRealtimeData() {
        val received = 1_800_000_000_000L
        assertTrue(RealTimeIntegrityPolicy.validate(stock(received),10_500L,received).accepted)
    }


    @Test fun rejectsOpenSelectedTimeframeBar() {
        val received = 1_800_000_000_000L
        val x = stock(received).copy(lastBarClosed = false)
        assertFalse(RealTimeIntegrityPolicy.validate(x,10_500L,received).accepted)
    }

    @Test fun rejectsUnverifiedRealtimeFlag() {
        val received = 1_800_000_000_000L
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,realtime=false),10_500L,received).accepted)
    }

    @Test fun rejectsStaleDataUsingReceiptAnchor() {
        val received = 1_800_000_000_000L
        val stale = received - RealTimeIntegrityPolicy.MAX_DATA_AGE_MS - 1L
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,timestamp=stale),10_500L,received).accepted)
    }

    @Test fun monotonicElapsedMakesFreshDataStaleWithoutWallClockDependency() {
        val received = 1_800_000_000_000L
        val x = stock(received,receivedElapsed=10_000L)
        assertFalse(RealTimeIntegrityPolicy.validate(x,10_000L + RealTimeIntegrityPolicy.MAX_DATA_AGE_MS + 1L,received).accepted)
    }

    @Test fun rejectsFutureTimestamp() {
        val received = 1_800_000_000_000L
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,timestamp=received+16_000L),10_500L,received).accepted)
    }

    @Test fun rejectsDeclaredDelayAboveLimit() {
        val received = 1_800_000_000_000L
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,delay=RealTimeIntegrityPolicy.MAX_DECLARED_DELAY_SECONDS+1),10_500L,received).accepted)
    }

    @Test fun rejectsMissingCurrentSession() {
        val received = 1_800_000_000_000L
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,currentSession=false),10_500L,received).accepted)
    }

    @Test fun rejectsQuoteHistorySymbolMismatch() {
        val received = 1_800_000_000_000L
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,symbol="ASELS",historySymbol="GARAN"),10_500L,received).accepted)
    }

    @Test fun rejectsDuplicateCandles() {
        val received = 1_800_000_000_000L
        val valid = (0 until 220).map { i -> Candle(received-(220L-i)*86_400_000L,100.0,102.0,99.0,101.0,1000.0) }.toMutableList()
        valid[219] = valid[218]
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,candlesOverride=valid),10_500L,received).accepted)
    }

    @Test fun rejectsInvalidOhlcBounds() {
        val received = 1_800_000_000_000L
        val valid = (0 until 220).map { i -> Candle(received-(220L-i)*86_400_000L,100.0,102.0,99.0,101.0,1000.0) }.toMutableList()
        valid[219] = valid[219].copy(open=-1.0, close=150.0)
        assertFalse(RealTimeIntegrityPolicy.validate(stock(received,candlesOverride=valid),10_500L,received).accepted)
    }
}
