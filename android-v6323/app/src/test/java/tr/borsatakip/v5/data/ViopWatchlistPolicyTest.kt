package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViopWatchlistPolicyTest {
    @Test fun duplicateIsRejectedWithoutChangingList() {
        val m = ViopWatchlistStore.addTo(listOf("F_AKBNK1026"), "f_akbnk1026")
        assertEquals(ViopWatchlistStore.AddResult.ALREADY_EXISTS, m.result)
        assertEquals(listOf("F_AKBNK1026"), m.symbols)
    }

    @Test fun twentiethIsAcceptedButTwentyFirstIsRejected() {
        val nineteen = (1..19).map { "F_TEST${1000 + it}" }
        val twentieth = ViopWatchlistStore.addTo(nineteen, "F_TEST2020")
        assertEquals(ViopWatchlistStore.AddResult.ADDED, twentieth.result)
        assertEquals(20, twentieth.symbols.size)
        val twentyFirst = ViopWatchlistStore.addTo(twentieth.symbols, "F_TEST2021")
        assertEquals(ViopWatchlistStore.AddResult.LIMIT_REACHED, twentyFirst.result)
        assertEquals(20, twentyFirst.symbols.size)
    }

    @Test fun removePolicyDeletesOnlyRequestedSymbol() {
        val next = ViopWatchlistStore.removeFrom(listOf("F_AKBNK1026", "F_GARAN1026"), "F_AKBNK1026")
        assertFalse(next.contains("F_AKBNK1026"))
        assertTrue(next.contains("F_GARAN1026"))
    }
}
