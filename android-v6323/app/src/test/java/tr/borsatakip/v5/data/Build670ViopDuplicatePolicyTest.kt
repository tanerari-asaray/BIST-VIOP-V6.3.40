package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class Build670ViopDuplicatePolicyTest {
    @Test fun identicalSymbolWithIdenticalContractFieldsIsSingleRecord() {
        val seen=mutableMapOf<String,String>()
        assertEquals(ViopContractDuplicatePolicy.Decision.FIRST,
            ViopContractDuplicatePolicy.register(seen,"F_XU0301026","FUT|XU030|2026-10|0.25|10"))
        assertEquals(ViopContractDuplicatePolicy.Decision.IDENTICAL_REPEAT,
            ViopContractDuplicatePolicy.register(seen,"F_XU0301026","FUT|XU030|2026-10|0.25|10"))
        assertEquals(1,seen.size)
    }

    @Test fun conflictingExpiryOrMultiplierMustNotSelectFirstSilently() {
        val seen=mutableMapOf<String,String>()
        ViopContractDuplicatePolicy.register(seen,"F_XU0301026","FUT|XU030|2026-10|0.25|10")
        assertEquals(ViopContractDuplicatePolicy.Decision.CONFLICT,
            ViopContractDuplicatePolicy.register(seen,"F_XU0301026","FUT|XU030|2026-11|0.25|10"))
        assertEquals("FUT|XU030|2026-10|0.25|10",seen["F_XU0301026"])
    }

    @Test fun distinctContractsRemainSeparate() {
        val seen=mutableMapOf<String,String>()
        assertEquals(ViopContractDuplicatePolicy.Decision.FIRST,
            ViopContractDuplicatePolicy.register(seen,"F_XU0301026","1"))
        assertEquals(ViopContractDuplicatePolicy.Decision.FIRST,
            ViopContractDuplicatePolicy.register(seen,"F_XU0301126","2"))
        assertEquals(2,seen.size)
    }
}
