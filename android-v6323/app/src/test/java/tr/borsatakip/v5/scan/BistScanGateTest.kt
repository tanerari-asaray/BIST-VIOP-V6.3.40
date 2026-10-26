package tr.borsatakip.v5.scan

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.data.MarketDataProvider
import tr.borsatakip.v5.model.ScanRunStatus
import tr.borsatakip.v5.model.Stock

class BistScanGateTest {
    @Test fun rejectsSecondOwnerWithoutReleasingFirstOwner() {
        val first = requireNotNull(BistScanGate.tryAcquire("first", "MANUAL"))
        try {
            assertNull(BistScanGate.tryAcquire("second", "AUTOMATIC"))
            assertEquals("first", BistScanGate.snapshot()?.runId)
            assertEquals("MANUAL", BistScanGate.snapshot()?.owner)
        } finally { assertTrue(BistScanGate.release(first)) }
        assertNull(BistScanGate.snapshot())
        assertFalse(BistScanGate.release(first))
        val next = requireNotNull(BistScanGate.tryAcquire("third", "AUTOMATIC"))
        assertTrue(BistScanGate.release(next))
    }

    @Test fun blockedScannerDoesNotCallProviderOrHistoryRecorder() = runBlocking {
        val first = requireNotNull(BistScanGate.tryAcquire("active", "MANUAL"))
        try {
            var networkCalls = 0
            var historyCalls = 0
            val provider = object : MarketDataProvider {
                override val id = "guard_test"
                override val displayName = "guard test"
                override suspend fun listSymbols(): List<String> { networkCalls++; return listOf("THYAO") }
                override suspend fun scan(onProgress: (Int, Int) -> Unit): List<Stock> { networkCalls++; return emptyList() }
                override suspend fun fetchOne(symbol: String): Stock? { networkCalls++; return null }
            }
            val recorder = object : HistoryRecorder {
                override suspend fun record(state: ScanState): HistoryRecordResult {
                    historyCalls++
                    return HistoryRecordResult()
                }
            }
            val result = BistScanner(provider, recorder, BistScanMode.DELAYED_ANALYSIS, "AUTOMATIC").scan { }
            assertEquals(ScanStatus.CANCELLED, result.status)
            assertEquals(ScanRunStatus.SKIPPED, result.scanRun?.status)
            assertEquals("SCAN_ALREADY_RUNNING", result.errorMessage)
            assertEquals(0, networkCalls)
            assertEquals(0, historyCalls)
            assertNotNull(BistScanGate.snapshot())
        } finally { BistScanGate.release(first) }
    }
}
