package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.data.ViopBuiltinContractCatalog
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.SignalValidity
import java.time.ZoneId
import java.time.ZonedDateTime

class ViopBuiltinContractCatalogTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val now = ZonedDateTime.of(2026, 9, 13, 10, 0, 0, 0, istanbul).toInstant().toEpochMilli()

    @Test fun akbankSearchWorksWithoutProviderUniverse() {
        val catalog = ViopBuiltinContractCatalog.current(now)
        val byCode = ViopContractSearch.search(catalog, "AKBNK", nowMs = now)
        val byName = ViopContractSearch.search(catalog, "AKBANK", nowMs = now)
        val byTypo = ViopContractSearch.search(catalog, "AKBAK", nowMs = now)

        assertTrue(byCode.isNotEmpty())
        assertEquals(byCode.map { it.symbol }, byName.map { it.symbol })
        assertEquals(byCode.map { it.symbol }, byTypo.map { it.symbol })
        assertTrue(byCode.all { it.underlying == "AKBNK" })
    }

    @Test fun builtinRowsNeverPretendToBeRealtimeMarketData() {
        val rows = ViopBuiltinContractCatalog.current(now)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.providerId == "builtin_catalog" })
        assertTrue(rows.all { it.dataMode == DataMode.UNVERIFIED })
        assertTrue(rows.all { it.validity == SignalValidity.WATCH })
        assertTrue(rows.all { it.lastPrice == null && !it.isRealtime })
    }

    @Test fun catalogContainsOnlyCurrentOrFutureExpiries() {
        val rows = ViopBuiltinContractCatalog.current(now)
        assertTrue(rows.all { (it.expiryAt ?: Long.MIN_VALUE) >= now })
    }
    @Test fun offlineCatalogProvidesBist30FxAndCommodityRoots() {
        val rows = ViopBuiltinContractCatalog.current(now)
        assertTrue(ViopContractCategoryPolicy.filter(rows, ViopContractCategory.BIST30).isNotEmpty())
        assertTrue(ViopContractCategoryPolicy.filter(rows, ViopContractCategory.FX).isNotEmpty())
        assertTrue(ViopContractCategoryPolicy.filter(rows, ViopContractCategory.COMMODITY).isNotEmpty())
    }

}
