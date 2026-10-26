package tr.borsatakip.v5.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Display-only metadata and index quotes; this path never creates stock trading signals. */
class StocksReferenceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val issues = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val settings = SettingsStore(context)
    private val cache = context.getSharedPreferences("stocks_kap_directory_v1", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    suspend fun refresh(): StocksReferences = supervisorScope {
        issues.clear()
        val directory = async { attempt("KAP") { loadDirectory() } }
        val quotes = listOf("XU100", "XU030", "XUTUM").associateWith { symbol -> async { attempt(symbol) { loadIndex(symbol) } } }
        val loaded = quotes.mapValues { it.value.await() }.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        val names = directory.await()
        StocksReferences(names, loaded, buildString {
            append("Endeksler: ${loaded.size}/3")
            append(if (names == null) " · BIST 30 üyelik verisi doğrulanmadı" else " · KAP: 30 üye${if (names.cached) " (önbellek)" else ""}")
        }, issues.toMap())
    }

    private suspend fun <T> attempt(label: String, block: suspend () -> T): T? = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { issues[label] = StockQuotePolicy.failureCode(error); null }

    suspend fun refreshStockQuotes(symbols: List<String>, onProgress: (Int, Int) -> Unit = { _, _ -> }): StockQuoteRefreshResult {
        val connection = settings.connectionSnapshot()
        val unique = symbols.map { it.trim().uppercase(java.util.Locale.ROOT) }.distinct()
            .filter { it.matches(Regex("[A-Z0-9]{2,12}")) }
        val configurationFailure = StockQuoteRefreshDiagnostics.configurationFailure(connection)
        val fallbackEnabled = settings.experimentalProvidersEnabled && settings.yahooFallbackEnabled
        if (configurationFailure != null && !fallbackEnabled) {
            onProgress(unique.size, unique.size)
            return StockQuoteRefreshResult(emptyMap(), unique.associateWith { configurationFailure })
        }
        val loaded = java.util.concurrent.ConcurrentHashMap<String, tr.borsatakip.v5.model.DisplayQuoteSnapshot>()
        val failures = java.util.concurrent.ConcurrentHashMap<String, String>()
        val httpFailures = java.util.concurrent.ConcurrentHashMap<String, BackendHttpEvidence>()
        val done = java.util.concurrent.atomic.AtomicInteger()
        val permits = kotlinx.coroutines.sync.Semaphore(4)
        kotlinx.coroutines.withTimeoutOrNull(60_000L) {
            supervisorScope {
                unique.map { symbol -> async {
                    permits.acquire()
                    try {
                        var quote: tr.borsatakip.v5.model.DisplayQuoteSnapshot? = null
                        if (configurationFailure == null) {
                            try {
                                val x = JSONObject(read(BackendUrlPolicy.endpoint(connection.baseUrl, "/v1/bist/quote/$symbol"), connection))
                                require(x.optString("symbol") == symbol)
                                quote = tr.borsatakip.v5.model.DisplayQuoteSnapshot(x.getDouble("price"),
                                    x.optDouble("previousClose", Double.NaN).takeIf { it.isFinite() && it > 0 },
                                    x.getLong("exchangeTimestamp"), x.getString("source"), x.optBoolean("realtime"),
                                    if (x.has("delaySeconds")) x.getInt("delaySeconds") else null,
                                    x.optBoolean("currentSessionIncluded"))
                                require(quote.price.isFinite() && quote.price > 0 && quote.timestamp >= 1_000_000_000_000L &&
                                    quote.timestamp <= System.currentTimeMillis() + 15_000L)
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) {
                                failures[symbol] = StockQuotePolicy.failureCode(error)
                                if (error is BackendHttpException) httpFailures[symbol] = error.evidence
                                quote = null
                            }
                        }
                        if (quote == null && fallbackEnabled) {
                            try {
                                val result = YahooFallbackProvider(appContext).fetchDisplayQuoteOnly(symbol)
                                quote = result.stock?.let(StockQuotePolicy::from)
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) { failures.putIfAbsent(symbol, configurationFailure ?: StockQuotePolicy.failureCode(error)) }
                        }
                        if (quote != null) { loaded[symbol] = quote; failures.remove(symbol); httpFailures.remove(symbol) }
                        else failures.putIfAbsent(symbol, configurationFailure ?: "NO_QUOTE_DATA")
                    } finally { permits.release(); onProgress(done.incrementAndGet(), unique.size) }
                } }.forEach { it.await() }
            }
        }
        unique.filterNot { loaded.containsKey(it) || failures.containsKey(it) }.forEach { failures[it] = "TIMEOUT" }
        return StockQuoteRefreshResult(loaded.toMap(), failures.toMap(), httpFailures.toMap())
    }

    private suspend fun read(url: String, connection: BackendConnectionConfig? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 BorsaTakip/6.3.33").apply {
            if (connection != null) {
                require(BackendUrlPolicy.origin(url.substringBefore('?')) == connection.origin)
                header("Authorization", "Bearer ${connection.apiKey}")
            }
        }.build()
        val body = client.newCall(request).awaitBody("Hisse referans verisi")
        require(body.length <= 4_000_000)
        body
    }

    private suspend fun loadDirectory(): KapStockDirectory {
        try {
            val html = read("https://www.kap.org.tr/tr/Endeksler")
            val value = withContext(Dispatchers.Default) { KapStockDirectoryParser.parse(html) }
            val json = JSONObject().put("fetchedAt", value.fetchedAt).put("names", JSONObject(value.names))
                .put("bist30", JSONArray(value.bist30.toList()))
                .put("memberships", JSONObject().apply { value.memberships.forEach { (key, members) -> put(key, JSONArray(members.toList())) } })
            cache.edit().putString("verified_directory", json.toString()).apply()
            return value
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            val saved = runCatching {
                val json = JSONObject(cache.getString("verified_directory", "").orEmpty())
                val names = json.getJSONObject("names")
                val members = json.getJSONArray("bist30")
                KapStockDirectory(names.keys().asSequence().associateWith { names.getString(it) },
                    (0 until members.length()).map { members.getString(it) }.toSet(), json.getLong("fetchedAt"), true,
                    json.optJSONObject("memberships")?.let { groups -> groups.keys().asSequence().associateWith { key ->
                        val rows = groups.getJSONArray(key); (0 until rows.length()).map { rows.getString(it) }.toSet()
                    } }.orEmpty())
            }.getOrNull()?.takeIf { it.usable() }
            return saved ?: throw error
        }
    }

    private suspend fun loadIndex(symbol: String): StockIndexQuote {
        val connection = settings.connectionSnapshot()
        if (BackendUrlPolicy.isValid(connection.baseUrl) && connection.apiKey.isNotBlank()) {
            val primary = attempt("$symbol/primary") {
                val x = JSONObject(read(BackendUrlPolicy.endpoint(connection.baseUrl, "/v1/bist/quote/$symbol"), connection))
                val time = x.optLong("exchangeTimestamp", 0L)
                val delay = if (x.has("delaySeconds") && !x.isNull("delaySeconds")) x.optInt("delaySeconds", -1) else -1
                StockIndexQuote(x.optString("symbol"), x.optDouble("price", Double.NaN),
                    x.optDouble("dailyChangePct", Double.NaN).takeIf { it.isFinite() }, time, x.optString("source"),
                    x.optBoolean("realtime") && x.optBoolean("currentSessionIncluded") && delay in 0..2 &&
                        System.currentTimeMillis() - time in 0L..60_000L).also { require(it.valid(symbol)) }
            }
            if (primary != null) return primary
        }
        require(settings.experimentalProvidersEnabled && settings.yahooFallbackEnabled) { "Endeks sağlayıcısı bağlı değil" }
        for (ticker in listOf("$symbol.IS", "^$symbol")) {
            val quote = attempt("$symbol/$ticker") {
                val x = JSONObject(read("https://query1.finance.yahoo.com/v8/finance/chart/${java.net.URLEncoder.encode(ticker, "UTF-8")}?range=1d&interval=5m"))
                    .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val meta = x.getJSONObject("meta")
                require(meta.getString("symbol") == ticker)
                // regularMarketPrice is paired with its own timestamp, never a candle's timestamp.
                val price = meta.getDouble("regularMarketPrice")
                val timestamp = meta.getLong("regularMarketTime") * 1000L
                val prev = meta.optDouble("chartPreviousClose", Double.NaN).takeIf { it.isFinite() && it > 0 }
                    ?: meta.optDouble("previousClose", Double.NaN).takeIf { it.isFinite() && it > 0 }
                StockIndexQuote(symbol, price, prev?.let { (price - it) / it * 100 }, timestamp,
                    "Yahoo Finance · gecikmeli", false).also { require(it.valid(symbol)) }
            }
            if (quote != null) return quote
        }
        error("Endeks fiyatı alınamadı")
    }
}
