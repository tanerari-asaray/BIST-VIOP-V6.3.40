package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class ProviderProbeGateTest {
    @Test fun activeTestIsNotRecovered() {
        val gate = ProviderProbeGate(100)
        gate.begin("a", 10) { }
        var recovered = false
        assertFalse(gate.recoverIfIdle("a", 20) { recovered = true })
        assertFalse(recovered)
    }

    @Test fun processRestartRecoversPersistedTestingState() {
        val gate = ProviderProbeGate()
        var state = "TESTING"
        assertTrue(gate.recoverIfIdle("a", 10) { state = "CONFIGURED" })
        assertEquals("CONFIGURED", state)
    }

    @Test fun expiredTestCannotPublishAfterRecovery() {
        val gate = ProviderProbeGate(100)
        val ticket = gate.begin("a", 10) { }
        assertTrue(gate.recoverIfIdle("a", 111) { })
        assertFalse(gate.finish(ticket) { fail("Expired test published") })
    }

    @Test fun supersededTestCannotOverwriteNewTest() {
        val gate = ProviderProbeGate()
        val old = gate.begin("a", 10) { }
        val fresh = gate.begin("a", 20) { }
        assertFalse(gate.finish(old) { fail("Old test published") })
        assertTrue(gate.finish(fresh) { })
    }

    @Test fun oldConfigurationRecoveryDoesNotStealNewTest() {
        val gate = ProviderProbeGate()
        val fresh = gate.begin("b", 20) { }
        assertFalse(gate.recoverIfIdle("a", 25) { fail("Wrong revision recovered") })
        assertTrue(gate.finish(fresh) { })
    }

    @Test fun cancellationCleanupCanPublishExactlyOnce() {
        val gate = ProviderProbeGate()
        val ticket = gate.begin("a", 10) { }
        var publications = 0
        assertTrue(gate.finish(ticket) { publications++ })
        assertFalse(gate.finish(ticket) { publications++ })
        assertEquals(1, publications)
    }

    @Test fun setupFailureDoesNotLeaveTestingActive() {
        val gate = ProviderProbeGate()
        try { gate.begin("a", 10) { error("setup") }; fail("Expected setup failure") }
        catch (_: IllegalStateException) { }
        assertTrue(gate.recoverIfIdle("a", 11) { })
    }

    @Test fun publicationFailureDoesNotRetainOwnership() {
        val gate = ProviderProbeGate()
        val ticket = gate.begin("a", 10) { }
        try { gate.finish(ticket) { error("storage") }; fail("Expected storage failure") }
        catch (_: IllegalStateException) { }
        assertTrue(gate.recoverIfIdle("a", 11) { })
    }
}
