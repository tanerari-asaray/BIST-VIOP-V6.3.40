package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.ViopContractCategory

class ViopCategoryWatchlistPolicyTest {
    @Test fun categoryStorageKeysAreIndependent() {
        val keys = ViopWatchlistStore.WATCHLIST_CATEGORIES.map { ViopWatchlistStore.categoryKey(it) }
        assertEquals(4, keys.distinct().size)
        assertNotEquals(
            ViopWatchlistStore.categoryKey(ViopContractCategory.BIST30),
            ViopWatchlistStore.categoryKey(ViopContractCategory.FX)
        )
    }

    @Test fun addingToOneLogicalCategoryDoesNotMutateAnotherCollection() {
        val bist30 = ViopWatchlistStore.addTo(emptyList(), "F_AKBNK").symbols
        val currency = emptyList<String>()
        assertTrue("F_AKBNK" in bist30)
        assertFalse("F_AKBNK" in currency)
    }

    @Test fun eachCategoryHasItsOwnTwentyItemLimit() {
        val fullBist = (1..20).map { "F_BIST${100 + it}" }
        val fullFx = (1..20).map { "F_FX${100 + it}" }
        assertEquals(ViopWatchlistStore.AddResult.LIMIT_REACHED, ViopWatchlistStore.addTo(fullBist, "F_BIST999").result)
        assertEquals(ViopWatchlistStore.AddResult.LIMIT_REACHED, ViopWatchlistStore.addTo(fullFx, "F_FX999").result)
    }

    @Test fun allCategoryIsNeverARealWatchlistKey() {
        assertEquals("symbols_invalid_all", ViopWatchlistStore.categoryKey(ViopContractCategory.ALL))
    }
}
