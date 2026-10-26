package tr.borsatakip.v5.data

import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class B129SurgicalCancellationAndCacheTest {
    private val now = 1_700_000_000_000L

    private fun candle(ts: Long, close: Double) = Candle(
        timestamp = ts,
        open = close,
        high = close + 1.0,
        low = close - 1.0,
        close = close,
        volume = 1000.0
    )

    @After fun clearCache() = MtfHistoryCache.clear()

    @Test fun optionalMtfCatchesOrdinaryExceptionButNotCancellation() = runBlocking {
        val ordinary = MtfHistoryCache.loadOptionalFresh(
            sourceKey = "router|primary|gen=1",
            symbol = "THYAO",
            timeframe = "1m",
            ttlMs = 30_000L,
            nowMs = { now },
            loader = { throw IllegalArgumentException("bad history") }
        )
        assertTrue(ordinary.isEmpty())

        var cancelled = false
        try {
            MtfHistoryCache.loadOptionalFresh(
                sourceKey = "router|primary|gen=1",
                symbol = "THYAO",
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

    @Test fun abaGenerationChangePreventsFallbackFromBeingStoredUnderOldPrimaryKey() = runBlocking {
        var currentSource = "router|primary|https://a.example|gen=7"
        val oldPrimary = currentSource
        val result = MtfHistoryCache.loadFresh(
            sourceKey = oldPrimary,
            symbol = "THYAO",
            timeframe = "1m",
            ttlMs = 30_000L,
            nowMs = { now },
            loader = {
                currentSource = "router|fallback|https://a.example|gen=8"
                currentSource = "router|primary|https://a.example|gen=9"
                listOf(candle(now, 999.0))
            },
            sourceKeyNow = { currentSource }
        )
        assertEquals(999.0, result.single().close, 0.0)
        assertNull(MtfHistoryCache.peek(oldPrimary, "THYAO", "1m"))
    }

    @Test fun okhttpAwaitBodyIsCancelledByCoroutineTimeout() = runBlocking {
        val server = ServerSocket(0)
        val worker = Thread {
            try {
                server.accept().use { socket ->
                    Thread.sleep(2_000L)
                    socket.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray()
                    )
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // Test timeout closes/cancels the socket path; no action required.
            }
        }
        worker.isDaemon = true
        worker.start()

        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val call = client.newCall(
            Request.Builder().url("http://127.0.0.1:${server.localPort}/slow").build()
        )
        val elapsed = measureTimeMillis {
            val body = withTimeoutOrNull(150L) { call.awaitBody("test") }
            assertNull(body)
        }
        assertTrue("Coroutine timeout blocking OkHttp call'u beklememeli: ${elapsed}ms", elapsed < 1_000L)
        assertTrue(call.isCanceled())
        server.close()
    }

    @Test fun providerRouterSourceKeyCarriesGenerationGuard() {
        val source = java.io.File("src/main/java/tr/borsatakip/v5/data/ProviderRouter.kt").readText()
        assertTrue(source.contains("mtfSourceGeneration"))
        assertTrue(source.contains("append(mtfSourceGeneration.get())"))
        assertTrue(source.contains("mtfSourceGeneration.incrementAndGet()"))
    }

    @Test fun bistMtfUsesCancellationSafeOptionalCacheLoader() {
        val source = java.io.File("src/main/java/tr/borsatakip/v5/scan/BistScanner.kt").readText()
        assertTrue(source.contains("MtfHistoryCache.loadOptionalFresh"))
        assertFalse(source.contains("catch (_: Throwable) { emptyList() }"))
    }
}
