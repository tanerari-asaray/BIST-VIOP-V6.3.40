package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract
import java.time.Instant

class ViopAnalysisCatalogTest {
    private val now = Instant.parse("2026-10-19T19:00:00Z").toEpochMilli()
    private fun contract(code: String, expiry: String = "2026-12") =
        ViopContract(code, "AKBNK", expiry, providerId = "backend", validity = SignalValidity.VALID,
            lastTradingAt = now + 86400000, expiryAt = now + 86400000)

    @Test fun everyMaturityAndMoreThanTwentyContractsRemainVisible() {
        val source = (1..105).map { contract("F_AKBNK${it}", if (it % 2 == 0) "2026-11" else "2026-12") }
        assertEquals(105, ViopAnalysisCatalog.available(source, now).size)
        assertEquals(setOf("2026-11", "2026-12"), ViopAnalysisCatalog.available(source, now).map { it.expiry }.toSet())
    }

    @Test fun optionsAndAllDataQualitiesAreBrowsableWithoutCreatingSignals() {
        val source = SignalValidity.entries.mapIndexed { i, validity -> contract("O_AKBNK$i").copy(validity = validity) }
        assertEquals(source.toSet(), ViopAnalysisCatalog.available(source, now).toSet())
    }

    @Test fun pastLastTradingDateExpiryTimestampOrMonthAreExcluded() {
        val source = listOf(contract("F_ENDED1").copy(lastTradingAt = now),
            contract("F_ENDED2").copy(expiryAt = now - 1), contract("F_ENDED3", "2026-09"), contract("F_OK"))
        assertEquals(listOf("F_OK"), ViopAnalysisCatalog.available(source, now).map { it.symbol })
    }

    @Test fun missingDatesRemainUnverifiedRatherThanInventingAnExpiry() {
        val c = contract("F_UNKNOWN", "-").copy(lastTradingAt = null, expiryAt = null, validity = SignalValidity.INSUFFICIENT)
        assertEquals(listOf(c), ViopAnalysisCatalog.available(listOf(c), now))
    }

    @Test fun manualUnderlyingAndDuplicateRowsAreNotMergedIntoProviderUniverse() {
        val c = contract("F_AKBNK1226")
        val source = listOf(c, c.copy(symbol = "f_akbnk1226"), contract("AKBNK"), contract("F_MANUAL").copy(isManual = true))
        assertEquals(listOf(c), ViopAnalysisCatalog.available(source, now))
    }

    @Test fun kindExpiryAndTurkishCompanySearchCompose() {
        val source = listOf(contract("F_AKBNK1126", "2026-11"), contract("F_AKBNK1226"), contract("O_AKBNK1226"))
        assertEquals(listOf("F_AKBNK1226"), ViopAnalysisCatalog.filter(source, "akbank 2026-12", ViopAnalysisCatalog.Kind.FUTURE, "2026-12").map { it.symbol })
        assertEquals(listOf("O_AKBNK1226"), ViopAnalysisCatalog.filter(source, "", ViopAnalysisCatalog.Kind.OPTION, null).map { it.symbol })
        assertTrue(ViopAnalysisCatalog.filter(source, "NO_MATCH", ViopAnalysisCatalog.Kind.ALL, null).isEmpty())
    }

    @Test fun blankSearchAndAllFiltersReturnFullUniverse() {
        val source = (1..80).map { contract("F_AKBNK$it") }
        assertEquals(source, ViopAnalysisCatalog.filter(source, " ", ViopAnalysisCatalog.Kind.ALL, null))
    }
}
