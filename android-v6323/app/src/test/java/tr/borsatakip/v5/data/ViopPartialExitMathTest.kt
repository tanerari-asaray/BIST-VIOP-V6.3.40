package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViopPartialExitMathTest {
    @Test fun oneOfTwoContractsClosedLeavesOnlyOneAtRisk() {
        val pos = ViopManualPositionStore.Position(
            "test", "F_TEST", "LONG", 2,
            100.0, 99.0, 105.0, 10.0, 2.0, 1000.0,
            1000L, null, null, "ref", null,
            listOf(ViopManualPositionStore.ExitFill(1, 102.0, 2000L))
        )
        assertEquals(1,pos.remainingQuantity)
        assertEquals(12.0,pos.openRiskTry,0.0001)
        assertEquals(1000.0,pos.openMarginTry,0.0001)
        assertEquals(18.0,pos.realizedPnlTry!!,0.0001)
    }

    @Test fun multipleShortExitsAccountForAdditionalCharges() {
        val pos = ViopManualPositionStore.Position(
            "test", "F_TEST", "SHORT", 3,
            100.0, 102.0, 96.0, 5.0, 1.0, 1500.0,
            1000L, 4000L, 99.0, "ref", null,
            listOf(
                ViopManualPositionStore.ExitFill(1,101.0,2000L,1.0),
                ViopManualPositionStore.ExitFill(2,99.0,4000L,3.0)
            )
        )
        assertEquals(0,pos.remainingQuantity)
        assertEquals(-2.0,pos.realizedPnlTry!!,0.0001)
    }

    @Test fun legacyFullCloseWithoutFillListRetainsNetPnl() {
        val pos = ViopManualPositionStore.Position("legacy","F_TEST","LONG",2,100.0,99.0,104.0,10.0,2.0,1000.0,1000L,2000L,102.0)
        assertEquals(0,pos.remainingQuantity)
        assertEquals(36.0,pos.realizedPnlTry!!,0.0001)
    }
}
