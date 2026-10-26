package tr.borsatakip.v5.data
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle
class ObservationAnalysisTest {
    private fun bars(n:Int)=List(n){i-> val p=100.0+i;Candle(1_700_000_000_000L+i*86_400_000L,p,p+2,p-2,p,100.0) }
    @Test fun risingSeriesHasExpectedWilderAndAverages() {
        val m=ObservationAnalysis.calculate(bars(30))
        assertEquals(124.5,m.sma10!!,0.00001);assertEquals(119.5,m.sma20!!,0.00001)
        assertEquals(100.0,m.rsi14!!,0.00001);assertEquals(4.0,m.atr14!!,0.00001)
        assertEquals(107.0,m.support!!,0.00001);assertEquals(130.0,m.resistance!!,0.00001)
    }
    @Test fun currentVolumeIsExcludedFromBaseline() {
        val b=bars(21).toMutableList();b[20]=b[20].copy(volume=1000.0)
        assertEquals(10.0,ObservationAnalysis.calculate(b).volumeRatio!!,0.00001)
    }
    @Test fun missingHistoryDoesNotProduceSignals() {
        val m=ObservationAnalysis.calculate(bars(4));assertNull(m.rsi14);assertNull(m.atr14);assertNull(m.sma10)
        assertEquals(FullUniversePolicy.Trend.UNKNOWN,FullUniversePolicy.trend(bars(4).map{it.close}))
    }
    @Test fun invalidAndDuplicateBarsAreExcluded() {
        val b=bars(20);val cleaned=ObservationAnalysis.clean(b.reversed()+b.first()+b.first().copy(timestamp=0)+b.last().copy(close=Double.NaN))
        assertEquals(b,cleaned)
    }
    @Test fun flatSeriesIsNeutral() {
        val b=bars(20).map{it.copy(open=100.0,close=100.0,high=102.0,low=98.0)}
        assertEquals(50.0,ObservationAnalysis.calculate(b).rsi14!!,0.00001)
        assertEquals(FullUniversePolicy.Trend.FLAT,FullUniversePolicy.trend(b.map{it.close}))
    }
}
