package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class ReadableStockAnalysisTest {
    private fun candles(count: Int, step: Double = 0.25, intervalMs: Long = 300_000L): List<Candle> =
        (0 until count).map { i ->
            val p = 20.0 + step * i
            Candle(1_700_000_000_000L + i * intervalMs, p, p + 0.5, p - 0.5, p, 1000.0 + i)
        }

    @Test fun missingDataDoesNotProduceAnalysis() {
        assertNull(ReadableStockAnalysis.analyze(emptyList(), ChartTimeframe.FIVE_MIN))
        assertNull(ReadableStockAnalysis.analyze(candles(30), ChartTimeframe.FIVE_MIN))
    }

    @Test fun risingAndFallingAreClassifiedFromObservedCandles() {
        assertEquals(ReadableStockAnalysis.Direction.RISING,
            ReadableStockAnalysis.analyze(candles(50), ChartTimeframe.FIVE_MIN)!!.direction)
        assertEquals(ReadableStockAnalysis.Direction.FALLING,
            ReadableStockAnalysis.analyze(candles(50, -0.25), ChartTimeframe.ONE_DAY)!!.direction)
    }

    @Test fun newestPotentiallyUnclosedBarDoesNotAlterAssessment() {
        val data = candles(50)
        val a = ReadableStockAnalysis.analyze(data, ChartTimeframe.FIVE_MIN)!!
        val last = data.last()
        val reversed = data.dropLast(1) + last.copy(open = 1.0, high = 1000.0, low = 1.0, close = 1000.0, volume = 9_000_000.0)
        val b = ReadableStockAnalysis.analyze(reversed, ChartTimeframe.FIVE_MIN)!!
        assertEquals(a.direction, b.direction)
        assertEquals(a.examinedPrice, b.examinedPrice, 0.0)
        assertEquals(a.support, b.support, 0.0)
        assertEquals(a.resistance, b.resistance, 0.0)
    }

    @Test fun supportResistanceAndConditionsHaveActualLevels() {
        val a = ReadableStockAnalysis.analyze(candles(51, intervalMs = 600_000L), ChartTimeframe.TEN_MIN)!!
        assertTrue(a.support < a.resistance)
        assertTrue(a.importantLevels.contains("destek"))
        assertTrue(a.importantLevels.contains("direnç"))
        assertTrue(a.changeConditions.contains("hacmi"))
        assertTrue(a.plainIndicatorNote.contains("RSI"))
    }

    @Test fun intradayBarGapCannotManufactureFiveMinuteTrend() {
        val data = candles(70).mapIndexed { index, candle ->
            if (index >= 45) candle.copy(timestamp = candle.timestamp + 600_000L) else candle
        }
        assertNull(ReadableStockAnalysis.analyze(data, ChartTimeframe.FIVE_MIN))
    }

    @Test fun wronglyLabeledTenMinuteSeriesCannotBeTreatedAsValid() {
        assertNull(ReadableStockAnalysis.analyze(candles(70), ChartTimeframe.TEN_MIN))
    }

    @Test fun aSeriesWithOnlyOneObservationPerSessionHasNoIntradayTrend() {
        val isolated = candles(70, intervalMs = 86_400_000L)
        assertNull(ReadableStockAnalysis.analyze(isolated, ChartTimeframe.FIVE_MIN))
    }

    @Test fun futureDatedMarketDataCannotProduceTrend() {
        val shifted = candles(70).dropLast(1) + candles(70).last().copy(
            timestamp = System.currentTimeMillis() + 86_400_000L)
        assertNull(ReadableStockAnalysis.analyze(shifted, ChartTimeframe.FIVE_MIN))
    }

    @Test fun invalidInputCannotManufactureValidAnalysis() {
        val invalid = candles(35).map { it.copy(volume = -10.0) }
        assertNull(ReadableStockAnalysis.analyze(invalid, ChartTimeframe.ONE_DAY))
    }
}
