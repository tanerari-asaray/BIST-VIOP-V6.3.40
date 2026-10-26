package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test

class ViopV6PolicyTest {
    @Test fun riskLimitsBlockOversizedContracts() {
        val d = ViopV6Policy.size(100.0, 96.0, 100.0, 5.0, 1000.0)
        assertEquals(0, d.contracts)
        assertEquals("NO_CONTRACT_FITS_LIMITS", d.reason)
    }
    @Test fun riskLimitsAcceptOnlyAffordableSize() {
        val d = ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, 100.0)
        assertEquals(125, d.contracts)
        assertEquals(250.0, d.plannedRiskTry, 0.001)
        assertEquals("MANUAL_CONFIRMATION_REQUIRED", d.reason)
    }
    @Test fun noUnverifiedInputsOrMargin() {
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, null, 1.0, 100.0).contracts)
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, null).contracts)
        assertEquals(0, ViopV6Policy.size(Double.NaN, 99.0, 1.0, 1.0, 100.0).contracts)
    }
    @Test fun dailyBreakerAndOpenRiskCap() {
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, 100.0, dailyLossTry=1000.0).contracts)
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, 100.0, openRiskTry=500.0).contracts)
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, 100.0, activePositions=2).contracts)
    }
}
