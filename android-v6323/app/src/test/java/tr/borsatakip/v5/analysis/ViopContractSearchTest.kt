package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.ViopContract

class ViopContractSearchTest {
    private fun c(symbol: String, underlying: String, expiryAt: Long? = null) = ViopContract(
        symbol = symbol,
        underlying = underlying,
        expiry = "10/2026",
        expiryAt = expiryAt
    )

    @Test fun companyNameSearchFindsAkbankContracts() {
        val source = listOf(c("F_AKBNK1026", "AKBNK"), c("F_THYAO1026", "THYAO"))
        val out = ViopContractSearch.search(source, "AKBANK", nowMs = 1L)
        assertEquals(listOf("F_AKBNK1026"), out.map { it.symbol })
    }

    @Test fun oneCharacterTypoStillFindsAkbank() {
        val source = listOf(c("F_AKBNK1026", "AKBNK"), c("F_GARAN1026", "GARAN"))
        val out = ViopContractSearch.search(source, "AKBAK", nowMs = 1L)
        assertEquals("F_AKBNK1026", out.firstOrNull()?.symbol)
    }

    @Test fun expiredContractIsNotSuggested() {
        val now = 10_000L
        val source = listOf(c("F_AKBNKOLD", "AKBNK", expiryAt = 9_000L), c("F_AKBNKNEW", "AKBNK", expiryAt = 11_000L))
        val out = ViopContractSearch.search(source, "AKBNK", nowMs = now)
        assertEquals(listOf("F_AKBNKNEW"), out.map { it.symbol })
    }

    @Test fun resultsAreLimitedToTwenty() {
        val source = (1..30).map { c("F_AKBNK${1000 + it}", "AKBNK") }
        assertTrue(ViopContractSearch.search(source, "AKBANK", limit = 20, nowMs = 1L).size <= 20)
    }
    @Test fun firstLetterReturnsOnlyPrefixMatches() {
        val source = listOf(
            c("F_AKBNK1026", "AKBNK"),
            c("F_ASELS1026", "ASELS"),
            c("F_GARAN1026", "GARAN")
        )
        val out = ViopContractSearch.search(source, "A", nowMs = 1L)
        assertEquals(listOf("AKBNK", "ASELS"), out.map { it.underlying }.sorted())
    }

    @Test fun singleLetterCanMatchLocalizedAliasPrefix() {
        val source = listOf(c("F_USDTRY1026", "USDTRY"), c("F_EURTRY1026", "EURTRY"))
        val out = ViopContractSearch.search(source, "D", nowMs = 1L)
        assertEquals(listOf("USDTRY"), out.map { it.underlying })
    }

    @Test fun browseReturnsContractsWithoutTypingWhenCategoryIsSelected() {
        val source = listOf(c("F_GARAN1026", "GARAN"), c("F_AKBNK1026", "AKBNK"))
        val out = ViopContractSearch.browse(source, nowMs = 1L)
        assertEquals(listOf("AKBNK", "GARAN"), out.map { it.underlying })
    }

    @Test fun exactAkbnkCodeNeverLeaksYkbnk() {
        val source = listOf(
            c("F_AKBNK1026", "AKBNK"),
            c("F_YKBNK1026", "YKBNK")
        )
        val out = ViopContractSearch.search(source, "AKBNK", nowMs = 1L)
        assertEquals(listOf("F_AKBNK1026"), out.map { it.symbol })
    }

}
