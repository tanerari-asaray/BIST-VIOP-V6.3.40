package tr.borsatakip.v5.ui

import org.junit.Assert.*
import org.junit.Test

class MarketListOrderTest {
    private data class Row(val symbol: String, val change: Double?)
    private fun ordered(order: MarketListOrder, rows: List<Row>) = order.apply(rows, Row::symbol, Row::change)
    @Test fun sourceOrderKeepsTheScanRanking() {
        val rows = listOf(Row("ZZZ", -1.0), Row("AAA", 4.0))
        assertSame(rows, ordered(MarketListOrder.SOURCE, rows))
    }
    @Test fun symbolSortDoesNotChangeSourceOrScores() {
        val rows = listOf(Row("ZZZ", -1.0), Row("AAA", 4.0))
        assertEquals(listOf("AAA", "ZZZ"), ordered(MarketListOrder.SYMBOL, rows).map { it.symbol })
        assertEquals(listOf("ZZZ", "AAA"), rows.map { it.symbol })
    }
    @Test fun absentAndNonFiniteChangesSortAfterRealZeroAndNegativeChanges() {
        val rows = listOf(Row("NULL", null), Row("NAN", Double.NaN), Row("INF", Double.POSITIVE_INFINITY),
            Row("DOWN", -4.0), Row("ZERO", 0.0), Row("UP", 2.0))
        assertEquals(listOf("UP", "ZERO", "DOWN", "INF", "NAN", "NULL"),
            ordered(MarketListOrder.CHANGE, rows).map { it.symbol })
    }
    @Test fun cheapestPriceFirstAndInvalidPricesLast() {
        data class Priced(val symbol: String, val price: Double?)
        val rows = listOf(Priced("INVALID", 0.0), Priced("HIGH", 120.0),
            Priced("NAN", Double.NaN), Priced("CHEAP", 1.5), Priced("MISSING", null))
        val result = MarketListOrder.PRICE_ASC.apply(rows, Priced::symbol, { null }, Priced::price)
        assertEquals(listOf("CHEAP", "HIGH", "INVALID", "MISSING", "NAN"), result.map { it.symbol })
        assertEquals("INVALID", rows.first().symbol)
    }
    @Test fun equalChangesHaveDeterministicSymbolsAndEmptyInputWorks() {
        assertEquals(listOf("AAA", "ZZZ"), ordered(MarketListOrder.CHANGE,
            listOf(Row("ZZZ", 2.0), Row("AAA", 2.0))).map { it.symbol })
        MarketListOrder.entries.forEach { assertTrue(ordered(it, emptyList()).isEmpty()) }
    }
}
