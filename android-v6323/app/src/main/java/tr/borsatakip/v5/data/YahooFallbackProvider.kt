package tr.borsatakip.v5.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tr.borsatakip.v5.BuildConfig
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

/**
 * Deneysel yedek/gecikmeli kaynaktır. Hiçbir zaman REALTIME olarak etiketlenmez.
 *
 * Üretim backend'i yokken yalnız kullanıcı deneysel sağlayıcıyı açıkça etkinleştirmişse çalışır.
 * Sembol evreni cache'de yoksa doviz.com BIST hisse listesinden best-effort olarak yenilenir.
 * Bu yol üretim/lisanslı BIST veri sağlayıcısının yerine geçmez.
 */
class YahooFallbackProvider(context: Context) : MarketDataProvider, ScanDiagnosticsSource, DelayedIntradayScanSource {
    private val settings = SettingsStore(context)
    override val id = "yahoo_fallback"
    override val displayName = "Yahoo Finance • YEDEK / GECİKMELİ"
    @Volatile private var diagnostics = ProviderScanDiagnostics()
    override fun scanDiagnostics(): ProviderScanDiagnostics = diagnostics
    override fun useDirectDelayedIntradayScan(): Boolean = true

    override suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> = coroutineScope {
        diagnostics = ProviderScanDiagnostics()
        val symbols = loadExperimentalSymbols()
        require(symbols.isNotEmpty()) {
            "Deneysel BIST sembol evreni alınamadı. Production backend yapılandırın veya ağ bağlantısını kontrol edip tekrar deneyin."
        }

        val semaphore = Semaphore(6)
        val noData = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val messages = linkedMapOf<String, String>()
        val reasonCodes = linkedMapOf<String, String>()
        var done = 0
        val rows = symbols.map { symbol ->
            async(Dispatchers.IO) {
                val attempt = semaphore.withPermit { fetchDailyAttempt(symbol) }
                synchronized(this@YahooFallbackProvider) {
                    attempt.failure?.let { failure ->
                        if (failure.code in setOf(ProviderFailureCode.EMPTY_DATA, ProviderFailureCode.STALE_DATA)) noData += symbol
                        else errors += symbol
                        messages[symbol] = failure.message.orEmpty()
                        reasonCodes[symbol] = failure.diagnosticCode
                    }
                    done++
                    onProgress(done, symbols.size)
                }
                attempt.stock
            }
        }.awaitAll().filterNotNull()
        diagnostics = ProviderScanDiagnostics(noData, errors, messages.toMap(), reasonCodes.toMap())
        rows
    }

    data class DisplayQuoteResult(val stock: Stock?, val error: String? = null)

    /**
     * Takip/favori ekranı için yalnız son quote'u alır. Günlük 220 mum şartına bağlı değildir.
     * Bu yol karar/tarama verisi değildir; yalnız görüntüleme içindir.
     */
    suspend fun fetchDisplayQuoteOnly(symbol: String): DisplayQuoteResult = withContext(Dispatchers.IO) {
        val normalized = symbol.trim().uppercase()
        if (!normalized.matches(SYMBOL_REGEX)) return@withContext DisplayQuoteResult(null, "Geçersiz sembol")
        val attempt = fetchDelayedQuoteResult("$normalized.IS")
        val q = attempt.quote ?: return@withContext DisplayQuoteResult(null, attempt.error ?: "Yahoo quote alınamadı")
        DisplayQuoteResult(quoteOnlyStock(normalized, q), null)
    }

    /** Piyasa özet kartları için Yahoo'nun ham ticker adlarını dener (ör. TRY=X). */
    suspend fun fetchExternalQuote(symbolLabel: String, providerTickers: List<String>): DisplayQuoteResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for (ticker in providerTickers.map { it.trim() }.filter { it.isNotBlank() }.distinct()) {
            val attempt = fetchDelayedQuoteResult(ticker)
            val q = attempt.quote
            if (q != null) return@withContext DisplayQuoteResult(quoteOnlyStock(symbolLabel.trim().uppercase(), q), null)
            attempt.error?.let { errors += "$ticker: $it" }
        }
        DisplayQuoteResult(null, errors.joinToString(" • ").ifBlank { "Yahoo quote alınamadı" })
    }

    /**
     * Genel fetchOne önce quote'u alır. Günlük history başarısız olsa bile geçerli quote kaybolmaz.
     * Böylece takip ekranı için fiyat, analiz history'sine yanlışlıkla bağımlı olmaz.
     */
    override suspend fun fetchOne(symbol: String): Stock? = withContext(Dispatchers.IO) {
        val normalized = symbol.trim().uppercase()
        val quoteResult = fetchDisplayQuoteOnly(normalized)
        val quoteStock = quoteResult.stock ?: return@withContext null
        val daily = fetch(normalized) ?: return@withContext quoteStock
        DelayedQuoteMergePolicy.merge(daily, quoteStock)
    }

    /** One cancellable daily request for observation; no 220-bar gate for display. */
    suspend fun fetchObservation(symbol: String, range: String = "1y", interval: String = "1d"): Stock {
        require(symbol.matches(SYMBOL_REGEX))
        require(range in setOf("1d", "5d", "1mo", "3mo", "1y", "max"))
        require(interval in setOf("5m", "15m", "1d"))
        val ticker = URLEncoder.encode("$symbol.IS", "UTF-8")
        val request = okhttp3.Request.Builder()
            .url("https://query1.finance.yahoo.com/v8/finance/chart/$ticker?range=$range&interval=$interval&events=history")
            .header("User-Agent", "BorsaTakip/${BuildConfig.VERSION_NAME}").build()
        val body = observationClient.newCall(request).awaitBody("DELAYED_OBSERVATION")
        return parse(body, symbol, System.currentTimeMillis(), SystemClock.elapsedRealtime(), 1)
            ?: throw ProviderException(ProviderFailureCode.EMPTY_DATA, "EMPTY_DATA")
    }

    override suspend fun listSymbols(): List<String> = loadExperimentalSymbols()

    override suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int): List<Candle> = withContext(Dispatchers.IO) {
        if (fromTime <= 0L || toTime <= fromTime) return@withContext emptyList()
        val interval = when {
            intervalMinutes <= 1 -> "1m"
            intervalMinutes <= 5 -> "5m"
            intervalMinutes <= 15 -> "15m"
            intervalMinutes <= 30 -> "30m"
            else -> "60m"
        }
        val encoded = URLEncoder.encode("${symbol.trim().uppercase()}.IS", "UTF-8")
        val windows = if (interval == "1m") {
            boundedHistoryWindows(fromTime, toTime, ONE_MINUTE_HISTORY_CHUNK_MS)
        } else {
            listOf(fromTime to toTime)
        }
        windows
            .flatMap { (windowStart, windowEnd) -> fetchHistoryWindow(encoded, windowStart, windowEnd, interval) }
            .asSequence()
            .filter { it.timestamp in fromTime..toTime }
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
            .toList()
    }

    /** Two public chart mirrors; failures remain explicit (no fabricated replacement bars). */
    private fun fetchHistoryWindow(encodedSymbol: String, fromTime: Long, toTime: Long, interval: String): List<Candle> {
        val path = "/v8/finance/chart/$encodedSymbol?period1=${fromTime / 1000L}&period2=${toTime / 1000L}&interval=$interval&events=history"
        return fetchDelayedChartCandles(path)
    }

    private fun fetchDelayedChartCandles(path: String): List<Candle> {
        var lastFailure: ProviderException? = null
        for (host in listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")) {
            var con: HttpURLConnection? = null
            try {
                con = URL("https://$host$path").openConnection() as HttpURLConnection
                con.connectTimeout = 7_000
                con.readTimeout = 7_000
                con.setRequestProperty("User-Agent", "Mozilla/5.0 BorsaTakip/${BuildConfig.VERSION_NAME} Android")
                con.setRequestProperty("Accept", "application/json")
                val code = con.responseCode
                if (code !in 200..299) throw httpFailure(code)
                val bars = parseCandlesOnly(con.inputStream.bufferedReader().use { it.readText() })
                if (bars.isNotEmpty()) return bars
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: ProviderException) {
                lastFailure = failure
            } catch (error: Exception) {
                lastFailure = networkFailure(error)
            } finally {
                runCatching { con?.disconnect() }
            }
        }
        if (lastFailure != null) throw lastFailure
        return emptyList()
    }

    private fun boundedHistoryWindows(fromTime: Long, toTime: Long, maxWindowMs: Long): List<Pair<Long, Long>> {
        if (fromTime <= 0L || toTime <= fromTime || maxWindowMs <= 0L) return emptyList()
        val out = mutableListOf<Pair<Long, Long>>()
        var cursor = fromTime
        while (cursor < toTime) {
            val end = minOf(toTime, cursor + maxWindowMs)
            out += cursor to end
            if (end >= toTime) break
            cursor = end + 1L
        }
        return out
    }

    override suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean): List<Candle> = withContext(Dispatchers.IO) {
        val normalized = symbol.trim().uppercase()
        if (!normalized.matches(SYMBOL_REGEX)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        // Avoid an unbounded Yahoo max-range response for view rendering. Ten years is
        // sufficient to construct weekly/monthly charts without misrepresenting the period.
        val from = now - (if (maximumRange) 3650L else 370L) * 24 * 60 * 60 * 1000
        val encoded = URLEncoder.encode("$normalized.IS", "UTF-8")
        try {
            fetchDelayedChartCandles("/v8/finance/chart/$encoded?period1=${from / 1000L}&period2=${now / 1000L}&interval=1d&events=history")
                .sortedBy { it.timestamp }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun loadExperimentalSymbols(): List<String> = withContext(Dispatchers.IO) {
        val cached = settings.cachedBistSymbols
            .map { it.trim().uppercase() }
            .filter { it.matches(SYMBOL_REGEX) }
            .distinct()
            .sorted()
        if (cached.isNotEmpty()) return@withContext cached

        val discovered = discoverBistSymbolsFromPublicList()
        if (discovered.isNotEmpty()) {
            settings.cachedBistSymbols = discovered.toSet()
            settings.cachedBistSymbolCount = discovered.size
            settings.cachedBistSymbolsFetchedAt = System.currentTimeMillis()
            settings.cachedBistSymbolsProviderId = "doviz.com:bist-universe-experimental"
        }
        discovered
    }

    /**
     * Experimental bootstrap only. No price/quote is taken from this page; it is used only to discover
     * publicly listed BIST ticker codes when there is no production symbol cache yet.
     */
    private fun discoverBistSymbolsFromPublicList(): List<String> {
        var con: HttpURLConnection? = null
        return try {
            con = URL("https://m.doviz.com/borsa/hisseler").openConnection() as HttpURLConnection
            con.requestMethod = "GET"
            con.connectTimeout = 8_000
            con.readTimeout = 12_000
            con.instanceFollowRedirects = true
            con.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            con.setRequestProperty("User-Agent", "Mozilla/5.0 BorsaTakip/${BuildConfig.VERSION_NAME} Android")
            if (con.responseCode !in 200..299) return emptyList()

            val html = con.inputStream.bufferedReader().use { it.readText() }
            if (html.isBlank()) return emptyList()

            val regex = Regex("(?:https://borsa\\.doviz\\.com)?/hisseler/([a-z0-9_]{3,12})-", RegexOption.IGNORE_CASE)
            regex.findAll(html)
                .mapNotNull { match ->
                    match.groupValues.getOrNull(1)
                        ?.trim()
                        ?.uppercase()
                        ?.takeIf { it.matches(SYMBOL_REGEX) }
                }
                .distinct()
                .sorted()
                .toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { con?.disconnect() }
        }
    }


    private data class DelayedQuote(
        val price: Double,
        val previousClose: Double?,
        val timestamp: Long,
        val companyName: String? = null,
        val currency: String? = null
    )

    private data class DelayedQuoteAttempt(
        val quote: DelayedQuote?,
        val error: String? = null,
        val retryable: Boolean = false
    )

    private fun quoteOnlyStock(symbol: String, q: DelayedQuote): Stock = Stock(
        symbol = symbol,
        companyName = q.companyName,
        candles = emptyList(),
        source = displayName,
        dataTimestamp = q.timestamp,
        isRealtime = false,
        delaySeconds = null,
        currentSessionIncluded = true,
        receivedAt = System.currentTimeMillis(),
        receivedElapsedRealtime = SystemClock.elapsedRealtime(),
        quotePrice = q.price,
        previousClose = q.previousClose,
        currency = q.currency,
        market = "DISPLAY_ONLY"
    )

    /** Quote isteğinde 429/5xx ve geçici ağ hataları için sınırlı retry/backoff uygular. */
    private suspend fun fetchDelayedQuoteResult(providerTicker: String): DelayedQuoteAttempt {
        var last = DelayedQuoteAttempt(null, "Yahoo quote alınamadı")
        repeat(MAX_QUOTE_ATTEMPTS) { index ->
            last = fetchDelayedQuoteOnce(providerTicker)
            if (last.quote != null || !last.retryable) return last
            if (index < MAX_QUOTE_ATTEMPTS - 1) delay(QUOTE_RETRY_BASE_MS * (index + 1))
        }
        return last
    }

    private fun fetchDelayedQuoteOnce(providerTicker: String): DelayedQuoteAttempt {
        val encoded = URLEncoder.encode(providerTicker, "UTF-8")
        var con: HttpURLConnection? = null
        return try {
            con = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=1d&interval=1m&events=history")
                .openConnection() as HttpURLConnection
            con.connectTimeout = 7_000
            con.readTimeout = 7_000
            con.setRequestProperty("User-Agent", "Mozilla/5.0 BorsaTakip/${BuildConfig.VERSION_NAME} Android")
            val code = con.responseCode
            if (code !in 200..299) {
                return DelayedQuoteAttempt(null, "Yahoo HTTP $code", retryable = code == 429 || code in 500..599)
            }
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val r = JSONObject(body).optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
                ?: return DelayedQuoteAttempt(null, "Yahoo cevap yapısı geçersiz")
            val meta = r.optJSONObject("meta")
            val timestamps = r.optJSONArray("timestamp")
                ?: return DelayedQuoteAttempt(null, "Yahoo timestamp yok")
            val quote = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
                ?: return DelayedQuoteAttempt(null, "Yahoo quote dizisi yok")
            val closes = quote.optJSONArray("close")
                ?: return DelayedQuoteAttempt(null, "Yahoo kapanış dizisi yok")
            var lastIndex = -1
            for (i in minOf(timestamps.length(), closes.length()) - 1 downTo 0) {
                if (!closes.isNull(i)) { lastIndex = i; break }
            }
            if (lastIndex < 0) return DelayedQuoteAttempt(null, "Yahoo geçerli son fiyat yok")
            val close = closes.optDouble(lastIndex, Double.NaN)
            val metaPrice = meta?.optDouble("regularMarketPrice", Double.NaN)
            val pair = YahooQuotePairPolicy.select(metaPrice,
                meta?.optLong("regularMarketTime", 0L)?.times(1000L), close,
                timestamps.optLong(lastIndex, 0L) * 1000L)
                ?: return DelayedQuoteAttempt(null, "Yahoo fiyat/zaman çifti geçersiz")
            val price = pair.price
            val previousClose = listOf(
                meta?.optDouble("previousClose", Double.NaN),
                meta?.optDouble("chartPreviousClose", Double.NaN)
            ).firstOrNull { it != null && it.isFinite() && it > 0.0 }
            val timestamp = pair.timestamp
            if (timestamp <= 0L) return DelayedQuoteAttempt(null, "Yahoo veri zamanı geçersiz")
            val companyName = listOf(meta?.optString("longName"), meta?.optString("shortName"))
                .firstOrNull { !it.isNullOrBlank() }
            DelayedQuoteAttempt(
                DelayedQuote(price, previousClose, timestamp, companyName, meta?.optString("currency")?.takeIf { it.isNotBlank() }),
                null
            )
        } catch (e: Exception) {
            DelayedQuoteAttempt(null, "Yahoo ${e.javaClass.simpleName}: ${e.message ?: "ağ hatası"}", retryable = true)
        } finally {
            runCatching { con?.disconnect() }
        }
    }

    private data class DailyFetchAttempt(val stock: Stock?, val failure: ProviderException? = null)

    private fun fetch(symbol: String): Stock? = fetchDailyAttempt(symbol).stock

    private fun fetchDailyAttempt(symbol: String): DailyFetchAttempt {
        var con: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode("$symbol.IS", "UTF-8")
            con = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=1y&interval=1d&events=history")
                .openConnection() as HttpURLConnection
            con.connectTimeout = 7_000
            con.readTimeout = 7_000
            con.setRequestProperty("User-Agent", "Mozilla/5.0 BorsaTakip/${BuildConfig.VERSION_NAME} Android")
            val code = con.responseCode
            if (code !in 200..299) throw httpFailure(code)
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val receivedAt = System.currentTimeMillis()
            val receivedElapsed = SystemClock.elapsedRealtime()
            val stock = parse(body, symbol, receivedAt, receivedElapsed)
                ?: throw ProviderException(ProviderFailureCode.EMPTY_DATA,
                    "Günlük cevap boş, geçersiz veya 220 mum koşulunu karşılamıyor.",
                    diagnosticCode = "YAHOO_DAILY_REQUIREMENT_NOT_MET")
            DailyFetchAttempt(stock)
        } catch (c: CancellationException) {
            throw c
        } catch (p: ProviderException) {
            DailyFetchAttempt(null, p)
        } catch (e: Exception) {
            DailyFetchAttempt(null, networkFailure(e))
        } finally {
            runCatching { con?.disconnect() }
        }
    }

    private fun httpFailure(status: Int): ProviderException = ProviderException(
        when {
            status == 404 -> ProviderFailureCode.EMPTY_DATA
            status == 429 -> ProviderFailureCode.RATE_LIMIT
            status in 500..599 -> ProviderFailureCode.SERVER_ERROR
            status == 401 || status == 403 -> ProviderFailureCode.AUTH_ERROR
            else -> ProviderFailureCode.BIST_HISTORY_ERROR
        },
        "Yahoo HTTP $status", diagnosticCode = "YAHOO_HTTP_$status"
    )

    private fun networkFailure(error: Exception): ProviderException {
        val code = when (error) {
            is SocketTimeoutException -> ProviderFailureCode.NETWORK_TIMEOUT
            is UnknownHostException -> ProviderFailureCode.DNS_ERROR
            is IOException -> ProviderFailureCode.NETWORK_ERROR
            else -> ProviderFailureCode.UNKNOWN_ERROR
        }
        return ProviderException(code, "Yahoo veri isteği başarısız: ${code.name}", error,
            diagnosticCode = "YAHOO_${code.name}")
    }


    private fun parseCandlesOnly(json: String): List<Candle> {
        val r = JSONObject(json).optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return emptyList()
        val ts = r.optJSONArray("timestamp") ?: return emptyList()
        val q = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0) ?: return emptyList()
        val o = q.optJSONArray("open") ?: return emptyList()
        val h = q.optJSONArray("high") ?: return emptyList()
        val l = q.optJSONArray("low") ?: return emptyList()
        val c = q.optJSONArray("close") ?: return emptyList()
        val v = q.optJSONArray("volume") ?: return emptyList()
        val out = mutableListOf<Candle>()
        for (i in 0 until ts.length()) {
            if (o.isNull(i) || h.isNull(i) || l.isNull(i) || c.isNull(i) || v.isNull(i)) continue
            val candle = Candle(ts.getLong(i) * 1000L, o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i), v.getDouble(i))
            if (listOf(candle.open, candle.high, candle.low, candle.close, candle.volume).any { !it.isFinite() }) continue
            if (candle.high < candle.low || candle.close <= 0.0 || candle.volume < 0.0) continue
            out += candle
        }
        return out
    }

    private fun parse(json: String, fallback: String, receivedAt: Long, receivedElapsed: Long, minimumBars: Int = 220): Stock? {
        val r = JSONObject(json).optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return null
        val ts = r.optJSONArray("timestamp") ?: return null
        val q = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0) ?: return null
        val o = q.optJSONArray("open") ?: return null
        val h = q.optJSONArray("high") ?: return null
        val l = q.optJSONArray("low") ?: return null
        val c = q.optJSONArray("close") ?: return null
        val v = q.optJSONArray("volume") ?: return null
        val candles = mutableListOf<Candle>()
        for (i in 0 until ts.length()) {
            if (o.isNull(i) || h.isNull(i) || l.isNull(i) || c.isNull(i) || v.isNull(i)) continue
            val candle = Candle(ts.getLong(i) * 1000, o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i), v.getDouble(i))
            if (listOf(candle.open, candle.high, candle.low, candle.close, candle.volume).any { !it.isFinite() }) continue
            if (candle.high < candle.low || candle.close <= 0.0 || candle.volume < 0.0) continue
            candles += candle
        }
        if (candles.size < minimumBars) return null
        val meta = r.optJSONObject("meta")
        val previousClose = listOf(
            meta?.optDouble("previousClose", Double.NaN)
        ).firstOrNull { it != null && it.isFinite() && it > 0.0 }
        return Stock(
            symbol = fallback,
            companyName = meta?.optString("longName")?.takeIf { it.isNotBlank() },
            candles = candles.sortedBy { it.timestamp },
            source = displayName,
            dataTimestamp = meta?.optLong("regularMarketTime", 0L)?.times(1000L)?.takeIf { it > 0 } ?: candles.maxOf { it.timestamp },
            isRealtime = false,
            delaySeconds = null,
            currentSessionIncluded = false,
            receivedAt = receivedAt,
            receivedElapsedRealtime = receivedElapsed,
            quotePrice = meta?.optDouble("regularMarketPrice", Double.NaN)?.takeIf { it.isFinite() && it > 0.0 },
            currency = meta?.optString("currency")?.trim()?.takeIf { it.isNotBlank() },
            previousClose = previousClose
        )
    }

    companion object {
        private val observationClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(18, java.util.concurrent.TimeUnit.SECONDS).build()
        private val SYMBOL_REGEX = Regex("[A-Z0-9_]{3,12}")
        private const val ONE_MINUTE_HISTORY_CHUNK_MS = 5L * 24 * 60 * 60 * 1000
        private const val MAX_QUOTE_ATTEMPTS = 3
        private const val QUOTE_RETRY_BASE_MS = 350L
    }
}
