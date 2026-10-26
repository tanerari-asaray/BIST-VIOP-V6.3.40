package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ViopManualPositionMathTest {
    @Test fun longRiskAndPnlAreSeparated() {
        val x=ViopManualPositionStore.Position(
            id="1", symbol="F_TEST", side="LONG", quantity=2,
            entry=100.0, stop=99.0, target=103.0,
            multiplier=10.0, costPerContract=2.0, marginPerContract=1000.0,
            openedAt=1L, closedAt=2L, exitPrice=102.0
        )
        assertEquals(24.0, x.plannedRiskTry, 0.001)
        assertEquals(2000.0, x.reservedMarginTry, 0.001)
        assertEquals(36.0, x.realizedPnlTry!!, 0.001)
    }
    @Test fun shortLossIncludesDeclaredRoundtripFees() {
        val x=ViopManualPositionStore.Position(
            id="2", symbol="F_TEST", side="SHORT", quantity=3,
            entry=100.0, stop=102.0, target=97.0,
            multiplier=5.0, costPerContract=1.0, marginPerContract=1500.0,
            openedAt=1L, closedAt=2L, exitPrice=103.0
        )
        assertEquals(33.0, x.plannedRiskTry, 0.001)
        assertEquals(-48.0, x.realizedPnlTry!!, 0.001)
    }
}
