package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class DelayedScanScopeTest {
    @Test fun manualListTakesPriority() {
        assertEquals(listOf("THYAO", "EREGL", "KCHOL"),
            DelayedScanScope.select(listOf("THYAO.IS", "EREGL"), listOf("EREGL", "KCHOL")))
    }
    @Test fun rejectsInvalidAndDeduplicates() {
        assertEquals(listOf("THYAO", "AKBNK"),
            DelayedScanScope.select(listOf("THYAO", "BAD$", "THYAO.IS", "AKBNK"), emptyList()))
    }
    @Test fun localListIsNotTruncatedAtThirty() {
        val many = (100..199).map { "ABC$it" }
        assertEquals(100, DelayedScanScope.select(many, emptyList(), 9999).size)
        assertEquals(100, DelayedScanScope.select(many, emptyList()).size)
        assertEquals(7, DelayedScanScope.select(many, emptyList(), 7).size)
    }
    @Test fun doesNotInventUniverse() {
        assertTrue(DelayedScanScope.select(emptyList(), emptyList()).isEmpty())
    }
}
