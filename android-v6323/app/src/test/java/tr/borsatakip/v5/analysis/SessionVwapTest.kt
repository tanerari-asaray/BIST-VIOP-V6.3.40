package tr.borsatakip.v5.analysis

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import java.time.LocalDateTime
import java.time.ZoneId

class SessionVwapTest {
    @Test fun dailySeriesDoesNotPretendToBeSessionVwap() {
        val base=1_700_000_000_000L
        val c=List(30){i->
            val px=100.0+i*0.1
            Candle(base+i*86_400_000L,px,px+1.0,px-1.0,px,1000.0)
        }
        val t=TechnicalAnalyzer.analyze(c)
        assertNotNull(t.vwma20)
        assertNull(t.sessionVwap)
    }

    @Test fun intradaySeriesComputesRealSessionVwapFromLatestDayOnly() {
        val zone=ZoneId.of("Europe/Istanbul")
        val start=LocalDateTime.of(2026,9,11,10,0).atZone(zone).toInstant().toEpochMilli()
        val c=List(10){i->
            val px=100.0+i
            Candle(start+i*5*60_000L,px,px+1,px-1,px,1000.0+i*100)
        }
        val t=TechnicalAnalyzer.analyze(c)
        assertNotNull(t.sessionVwap)
        assertTrue(t.sessionVwap!! > 100.0)
    }
}
