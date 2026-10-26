package tr.borsatakip.v5.analysis.v533

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class V533TemporalCausalityTest {
    @Test fun pointInTimeSequenceAccepted() = assertTrue(V533TemporalCausality.evaluate(candles(5), evidence(ts(4))).accepted)
    @Test fun futureCandleRejected() { val c = candles(6); assertFalse(V533TemporalCausality.evaluate(c, evidence(ts(4))).accepted) }
    @Test fun futureTechnicalTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(technicalAsOfTimestamp = ts(5))).accepted)
    @Test fun futureOiTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(openInterestAsOfTimestamp = ts(5))).accepted)
    @Test fun futureMtfTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(mtfAsOfTimestamp = ts(5))).accepted)
    @Test fun zeroOiTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(openInterestAsOfTimestamp = 0L)).accepted)
    @Test fun negativeOiTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(openInterestAsOfTimestamp = -1L)).accepted)
    @Test fun zeroMtfTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(mtfAsOfTimestamp = 0L)).accepted)
    @Test fun negativeMtfTimestampRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5), evidence(ts(4)).copy(mtfAsOfTimestamp = -1L)).accepted)
    @Test fun duplicateCandleTimestampRejected() { val c = candles(5).toMutableList(); c[4] = c[4].copy(timestamp = c[3].timestamp); assertFalse(V533TemporalCausality.evaluate(c, evidence(ts(4))).accepted) }
    @Test fun reverseCandleOrderRejected() = assertFalse(V533TemporalCausality.evaluate(candles(5).reversed(), evidence(ts(4))).accepted)
    @Test fun zeroTimestampRejected() { val c = candles(5).toMutableList(); c[0] = c[0].copy(timestamp = 0L); assertFalse(V533TemporalCausality.evaluate(c, evidence(ts(4))).accepted) }
    @Test fun futureAppendDoesNotChangeHistoricalSlice() { val base = candles(5); val extended = base + candle(5); assertEquals(base, V533TemporalCausality.candlesAtOrBefore(extended, ts(4))) }
    @Test fun changingHistoricalCandleChangesHistoricalSlice() { val base = candles(5); val modified = base.toMutableList(); modified[4] = modified[4].copy(close = 130.0); assertTrue(V533TemporalCausality.candlesAtOrBefore(base, ts(4)) != V533TemporalCausality.candlesAtOrBefore(modified, ts(4))) }

    private fun evidence(t: Long) = V533TemporalEvidence(t, t, t, t)
    private fun ts(i: Int) = 1_700_000_000_000L + i * 60_000L
    private fun candle(i: Int) = Candle(ts(i), 100.0 + i, 101.0 + i, 99.0 + i, 100.5 + i, 1_000_000.0 + i)
    private fun candles(n: Int) = (0 until n).map(::candle)
}
