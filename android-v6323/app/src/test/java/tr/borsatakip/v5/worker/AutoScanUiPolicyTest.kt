package tr.borsatakip.v5.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScanUiPolicyTest {
    private val now = 1800000000000L
    @Test fun enabledButNoRunnerEvidenceIsNotRunning() {
        val x = AutoScanUiPolicy.evaluate(true, "STARTING", "", 0, now, 5, now)
        assertEquals(AutoScanUiPolicy.State.STARTING, x.state)
        assertFalse(x.active)
    }
    @Test fun scanningWithHeartbeatIsRunning() {
        val x = AutoScanUiPolicy.evaluate(true, "SCANNING", "50/100", now-500L, now-1000L, 5, now)
        assertEquals(AutoScanUiPolicy.State.RUNNING, x.state)
        assertTrue(x.active)
    }
    @Test fun staleHeartbeatIsInterruptedEvenWhenPreferenceEnabled() {
        val x = AutoScanUiPolicy.evaluate(true, "SCANNING", "", now-60*60*1000L, now-60*60*1000L, 5, now)
        assertEquals(AutoScanUiPolicy.State.INTERRUPTED, x.state)
        assertFalse(x.active)
    }
    @Test fun providerFailureNeverClaimsRunning() {
        val x = AutoScanUiPolicy.evaluate(true, "NO_DATA", "Yahoo hazır değil", now, now, 5, now)
        assertEquals(AutoScanUiPolicy.State.FAILED, x.state)
        assertFalse(x.active)
    }
    @Test fun completedScanIsWaitingNotScanning() {
        val x = AutoScanUiPolicy.evaluate(true, "COMPLETED", "gözlem", now, now-4000L, 5, now)
        assertEquals(AutoScanUiPolicy.State.WAITING, x.state)
    }
    @Test fun failedSchedulerIsNotAWorkingOnSwitch() {
        val x = AutoScanUiPolicy.evaluate(false, "CONFIG_ERROR", "foreground başlatılamadı", 0L, now, 5, now)
        assertEquals(AutoScanUiPolicy.State.FAILED, x.state)
    }
}
