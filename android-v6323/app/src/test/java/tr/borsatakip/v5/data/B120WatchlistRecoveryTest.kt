package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class B120WatchlistRecoveryTest {
    @Test fun bootstrapContainsCoreSymbolsButDoesNotInventKarc() {
        assertTrue("AKBNK" in BistBootstrapCatalog.symbols)
        assertTrue("GARAN" in BistBootstrapCatalog.symbols)
        assertFalse("KARC" in BistBootstrapCatalog.symbols)
    }

    @Test fun mistypedAkbbSuggestsAkbnk() {
        val out = BistSymbolMatcher.matches(BistBootstrapCatalog.symbols, emptyMap(), "AKBB")
        assertTrue(out.contains("AKBNK"))
    }

    @Test fun exactAndPrefixMatchesArePrioritized() {
        val out = BistSymbolMatcher.matches(listOf("GARAN","GARFA","KARSN"), emptyMap(), "GAR")
        assertEquals(listOf("GARAN","GARFA"), out.take(2))
    }

    @Test fun closestRejectsDistantGarbage() {
        assertTrue(BistSymbolMatcher.closest(listOf("AKBNK","GARAN"), "ZZZZ").isEmpty())
    }
}
