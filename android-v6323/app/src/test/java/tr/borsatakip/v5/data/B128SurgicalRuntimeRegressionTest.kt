package tr.borsatakip.v5.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract

class B128SurgicalRuntimeRegressionTest {
    private val now = 1_700_000_000_000L

    private fun candle(ts: Long, close: Double = 100.0) = Candle(
        timestamp = ts,
        open = close,
        high = close + 1.0,
        low = close - 1.0,
        close = close,
        volume = 1000.0
    )

    @After fun clearCache() = MtfHistoryCache.clear()

    @Test fun viopOptionalMtfFailureDoesNotEscapeAndMainOpportunityCanSurvive() = runBlocking {
        val result = MtfHistoryCache.loadOptionalFresh(
            sourceKey = "viop_backend|https://a.example",
            symbol = "F_XU030",
            timeframe = "1m",
            ttlMs = 30_000L,
            nowMs = { now },
            loader = { emptyList() }
        )
        assertTrue(result.isEmpty())
    }

    @Test fun optionalMtfDoesNotSwallowCancellation() = runBlocking {
        var cancelled = false
        try {
            MtfHistoryCache.loadOptionalFresh(
                sourceKey = "viop_backend|https://a.example",
                symbol = "F_XU030",
                timeframe = "1m",
                ttlMs = 30_000L,
                nowMs = { now },
                loader = { throw CancellationException("cancel") }
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun selectorRejectsOtherwiseValidContractWhenLastTradingAtMissing() {
        val contract = ViopContract(
            symbol = "F_XU030",
            underlying = "XU030",
            expiry = "2026-12",
            tickSize = 0.01,
            multiplier = 1.0,
            validity = SignalValidity.VALID,
            lastTradingAt = null,
            expiryAt = now + 86_400_000L
        )
        assertTrue(ViopContractSelector.candidates(listOf(contract), nowMs = now).isEmpty())
    }

    @Test fun bistRawHistoryOrderViolationIsRejectedInsteadOfSorted() {
        val raw = listOf(
            candle(now - 60_000L),
            candle(now - 120_000L),
            candle(now)
        )
        val failure = runCatching { BistHistoryIntegrityPolicy.requireStrict(raw, minimum = 3, nowWall = now) }.exceptionOrNull()
        assertTrue(failure is ProviderException)
        assertEquals(ProviderFailureCode.BIST_HISTORY_ERROR, (failure as ProviderException).code)
        assertTrue(failure.message.orEmpty().contains("zaman sırası"))
    }

    @Test fun mtfCacheSeparatesBackendOriginsAndDoesNotReuseOldSource() = runBlocking {
        var aLoads = 0
        var bLoads = 0
        val a = MtfHistoryCache.loadFresh(
            "viop_backend|https://a.example", "F_XU030", "1m", 30_000L,
            { now }, { aLoads++; listOf(candle(now, 101.0)) }
        )
        val b = MtfHistoryCache.loadFresh(
            "viop_backend|https://b.example", "F_XU030", "1m", 30_000L,
            { now }, { bLoads++; listOf(candle(now, 202.0)) }
        )
        assertEquals(1, aLoads)
        assertEquals(1, bLoads)
        assertEquals(101.0, a.single().close, 0.0)
        assertEquals(202.0, b.single().close, 0.0)
    }

    @Test fun sourceSwitchDuringLoadIsNotStoredUnderOldCacheKey() = runBlocking {
        var currentSource = "router|mobile_backend|https://a.example"
        var loads = 0
        val first = MtfHistoryCache.loadFresh(
            sourceKey = currentSource,
            symbol = "THYAO",
            timeframe = "1m",
            ttlMs = 30_000L,
            nowMs = { now },
            loader = {
                loads++
                currentSource = "router|yahoo_fallback|https://a.example"
                listOf(candle(now, 111.0))
            },
            sourceKeyNow = { currentSource }
        )
        assertEquals(111.0, first.single().close, 0.0)

        currentSource = "router|mobile_backend|https://a.example"
        val second = MtfHistoryCache.loadFresh(
            sourceKey = currentSource,
            symbol = "THYAO",
            timeframe = "1m",
            ttlMs = 30_000L,
            nowMs = { now },
            loader = { loads++; listOf(candle(now, 222.0)) },
            sourceKeyNow = { currentSource }
        )
        assertEquals(2, loads)
        assertEquals(222.0, second.single().close, 0.0)
    }
}
