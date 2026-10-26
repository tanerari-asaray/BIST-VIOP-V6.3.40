package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.borsatakip.v5.model.ViopContract

class ViopContractCategoryPolicyTest {
    private fun c(symbol: String, underlying: String) = ViopContract(symbol, underlying, "2026-10")

    @Test fun categoriesAreSeparatedDeterministically() {
        assertEquals(ViopContractCategory.BIST30, ViopContractCategoryPolicy.classify(c("F_AKBNK1026", "AKBNK")))
        assertEquals(ViopContractCategory.FX, ViopContractCategoryPolicy.classify(c("F_USDTRY1026", "USDTRY")))
        assertEquals(ViopContractCategory.COMMODITY, ViopContractCategoryPolicy.classify(c("F_GOLD1026", "GOLD")))
        assertEquals(ViopContractCategory.OTHER, ViopContractCategoryPolicy.classify(c("F_UNKNOWN1026", "UNKNOWN")))
    }

    @Test fun categoryFilterDoesNotLeakOtherGroups() {
        val source = listOf(
            c("F_AKBNK1026", "AKBNK"),
            c("F_USDTRY1026", "USDTRY"),
            c("F_GOLD1026", "GOLD"),
            c("F_UNKNOWN1026", "UNKNOWN")
        )
        assertEquals(listOf("AKBNK"), ViopContractCategoryPolicy.filter(source, ViopContractCategory.BIST30).map { it.underlying })
        assertEquals(listOf("USDTRY"), ViopContractCategoryPolicy.filter(source, ViopContractCategory.FX).map { it.underlying })
        assertEquals(listOf("GOLD"), ViopContractCategoryPolicy.filter(source, ViopContractCategory.COMMODITY).map { it.underlying })
        assertEquals(listOf("UNKNOWN"), ViopContractCategoryPolicy.filter(source, ViopContractCategory.OTHER).map { it.underlying })
    }
}
