package tr.borsatakip.v5.ui

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.scan.FundamentalValuationEngine as Engine

class ChartFinancialCardsTest {
    private val now = 1790000000000L
    private fun row(code: String, cap: Double = 1000.0, type: Engine.BusinessType = Engine.BusinessType.NON_FINANCIAL) =
        Engine.Snapshot(code, "Sanayi", type, "TMS29", "TRY", now - 70L * 86400000,
            now - 60L * 86400000, now - 3600000, cap, cap / 100, 100.0,
            100.0, 500.0, 200.0, 100.0, 90.0, "https://www.kap.org.tr/tr/Bildirim/123", code)
    private fun assess(own: Engine.Snapshot, count: Int = 5): Engine.Assessment {
        val rows = listOf(own) + (1..count).map { row("PEER$it", 2000.0, own.businessType) }
        return Engine.scan(rows.map { it.symbol }, rows, now).first()
    }
    private fun page(a: Engine.Assessment?, symbol: String = "TEST", market: String = "BIST", clock: Long = now) =
        ChartFinancialCards.build(symbol, market, a, clock)

    @Test fun cardsUseExistingEngineValuesAndActualSectorPeers() {
        val p = page(assess(row("TEST")))
        assertEquals(listOf(10.0, 2.0, 0.5), p.metrics.map { it.value })
        assertEquals(3, p.comparisons.size)
        val pe = p.comparisons.first { it.metric == "F/K" }
        assertEquals(20.0, pe.median, 0.001); assertEquals(5, pe.peerCount)
        assertEquals((1..5).map { "PEER$it" }, pe.peerSymbols)
        assertEquals("VERİYE GÖRE SEKTÖREL İSKONTO", p.conclusion)
    }
    @Test fun missingDataHasAllMetricDefinitionsButNoInventedValues() {
        val p = page(null)
        assertEquals(listOf("F/K", "PD/DD", "Net Borç/FAVÖK"), p.metrics.map { it.key })
        assertTrue(p.metrics.all { it.value == null }); assertTrue(p.comparisons.isEmpty()); assertNull(p.snapshot)
    }
    @Test fun retainedAssessmentBecomesEmptyWhenItsPriceIsStale() {
        val p = page(assess(row("TEST")), clock = now + 11L * 86400000)
        assertNull(p.snapshot); assertTrue(p.metrics.all { it.value == null }); assertTrue(p.comparisons.isEmpty())
        assertTrue(p.reason.contains("eski/geçersiz"))
    }
    @Test fun anotherSymbolCannotReuseThePreviousCompanyRatios() {
        val p = page(assess(row("OTHER")))
        assertNull(p.snapshot); assertTrue(p.metrics.all { it.value == null }); assertTrue(p.comparisons.isEmpty())
    }
    @Test fun bankDoesNotReceiveAnIndustrialDebtRatio() {
        val p = page(assess(row("TEST", type = Engine.BusinessType.BANK)))
        assertNotNull(p.metrics[0].value); assertNull(p.metrics[2].value)
        assertTrue(p.metrics[2].comment.contains("uygulanmaz"))
    }
    @Test fun lossesAndNegativeEquityDoNotBecomePositiveValuationMultiples() {
        val p = page(assess(row("TEST").copy(netIncomeTtmTry = -1.0, equityTry = -10.0)))
        assertNull(p.metrics[0].value); assertNull(p.metrics[1].value)
        assertEquals("FİNANSAL RİSK", p.conclusion)
    }
    @Test fun netCashIsPreservedButZeroEbitdaHasNoDebtRatio() {
        val cash = page(assess(row("TEST").copy(netDebtTry = -100.0)))
        assertEquals(-0.5, cash.metrics[2].value!!, 0.001); assertTrue(cash.metrics[2].comment.contains("Net nakit"))
        assertNull(page(assess(row("TEST").copy(ebitdaTtmTry = 0.0))).metrics[2].value)
    }
    @Test fun viopNeverReceivesEquityMetricsEvenWithAnAvailableCompanySnapshot() {
        val p = page(assess(row("TEST")), market = "VIOP")
        assertFalse(p.applicable); assertNull(p.snapshot)
        assertTrue(p.metrics.all { it.value == null }); assertTrue(p.comparisons.isEmpty())
        assertTrue(p.reason.contains("VİOP"))
    }
    @Test fun fewerThanFivePeersDoesNotCreateAMedianOrDiscountChart() {
        val p = page(assess(row("TEST"), count = 4))
        assertNotNull(p.snapshot); assertTrue(p.comparisons.isEmpty())
        assertEquals("EMSAL VERİSİ YETERSİZ", p.conclusion)
    }
}
