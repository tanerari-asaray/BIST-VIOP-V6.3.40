package tr.borsatakip.v5.ui

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class ChartDisplayStateTest {
    private val bars = listOf(Candle(1L, 10.0, 12.0, 9.0, 11.0, 100.0))
    @Test fun startingAnotherPeriodClearsPriceBeforeNetworkCompletes() {
        val state = ChartDisplayState()
        state.complete(state.begin(), bars, recorded = true)
        assertEquals(ChartDisplayState.Phase.RECORDED, state.phase)
        val next = state.begin()
        assertTrue(state.candles.isEmpty()); assertFalse(state.tradingSignalAllowed)
        state.fail(next)
        assertTrue(state.candles.isEmpty()); assertEquals(ChartDisplayState.Phase.ERROR, state.phase)
    }
    @Test fun lateResponseAndCancellationCannotRestoreOldPeriod() {
        val state = ChartDisplayState()
        val old = state.begin(); val current = state.begin()
        assertFalse(state.complete(old, bars)); assertFalse(state.fail(old))
        assertEquals(ChartDisplayState.Phase.LOADING, state.phase)
        assertTrue(state.complete(current, emptyList()))
        assertEquals(ChartDisplayState.Phase.ERROR, state.phase); assertTrue(state.candles.isEmpty())
    }
    @Test fun successfulDownloadAloneCannotAuthorizeATrade() {
        val state = ChartDisplayState()
        state.complete(state.begin(), bars)
        assertEquals(ChartDisplayState.Phase.LOADED, state.phase)
        assertFalse(state.tradingSignalAllowed)
    }
    @Test fun futureDatedSnapshotCannotBeShownAsARecordedPrice() {
        val state = ChartDisplayState()
        state.complete(state.begin(), bars.map { it.copy(timestamp = System.currentTimeMillis() + 86_400_000) }, recorded = true)
        assertTrue(state.candles.isEmpty()); assertEquals(ChartDisplayState.Phase.ERROR, state.phase)
    }
}
