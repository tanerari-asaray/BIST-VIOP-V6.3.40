package tr.borsatakip.v5.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

class ProviderException(
    val code: ProviderFailureCode,
    message: String,
    cause: Throwable? = null,
    val diagnosticCode: String = code.name,
    val connection: BackendHttpEvidence = BackendHttpEvidence()
) : Exception(message, cause)

class MobileMarketDataProvider(context: Context, private val connectionOverride: BackendConnectionConfig? = null) : MarketDataProvider, ScanDiagnosticsSource {
    private val settings = SettingsStore(context)
    override val id = "mobile_backend"
    override val displayName = "Üretim canlı veri servisi"
    override fun mtfCacheSourceKey(): String = "mobile_backend|${connection().sourceIdentity.ifBlank { "unconfigured" }}"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .build()

    @Volatile private var diagnostics = ProviderScanDiagnostics()

    override fun scanDiagnostics(): ProviderScanDiagnostics = diagnostics

    override suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> = withContext(Dispatchers.IO) {
        ensureConfigured()
        diagnostics = ProviderScanDiagnostics()
        val symbols = loadSymbols()
        if (symbols.isEmpty()) throw ProviderException(ProviderFailureCode.BIST_SYMBOLS_ERROR, "BIST HİSSE LİSTESİ ALINAMADI.")

        val noData = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val messages = linkedMapOf<String, String>()
        fun remember(symbol: String, code: ProviderFailureCode, message: String?) {
            val msg = message ?: code.name
            messages[symbol] = msg
            if (code in NO_DATA_CODES) noData += symbol else errors += symbol
            logDataError(symbol, "base", code.name, msg)
        }

        Log.i(TAG, "[BIST_SCAN] TOTAL=${symbols.size} batchSize=$BATCH_SIZE")
        val out = mutableListOf<Stock>()
        var done = 0
        onProgress(0, symbols.size)
        val batches = symbols.chunked(BATCH_SIZE)
        try {
            for ((batchIndex, batch) in batches.withIndex()) {
                val batchResult = try {
                    loadBatchWithRetry(batch, batchIndex + 1, batches.size)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (pe: ProviderException) {
                    Log.w(TAG, "[BIST_SCAN] BATCH_FAILED index=${batchIndex + 1} size=${batch.size} code=${pe.code} message=${pe.message}")
                    if (pe.code in FATAL_FAILURES) throw pe
                    batch.forEach { remember(it, pe.code, pe.message) }
                    done = (done + batch.size).coerceAtMost(symbols.size)
                    onProgress(done, symbols.size)
                    continue
                }

                val items = batchResult.optJSONArray("items")
                    ?: throw ProviderException(ProviderFailureCode.EMPTY_DATA, "Batch tarama yanıtında items alanı yok.")
                val expected = batch.toSet()
                val returned = mutableSetOf<String>()
                for (i in 0 until items.length()) {
                    val row = items.optJSONObject(i)
                        ?: throw ProviderException(ProviderFailureCode.EMPTY_DATA, "Batch öğesi nesne değil.")
                    val requested = row.optString("symbol").trim().uppercase()
                    if (requested !in expected || !returned.add(requested)) {
                        throw ProviderException(ProviderFailureCode.EMPTY_DATA, "Batch sözleşmesi bozuk: beklenmeyen veya tekrarlanan sembol $requested")
                    }
                    if (row.has("error")) {
                        val errorObj = row.optJSONObject("error")
                        val rawCode = errorObj?.optString("code").orEmpty().uppercase()
                        val mapped = mapProviderCode(rawCode)
                        val msg = errorObj?.optString("message").ifNullOrBlank("Batch provider error: $rawCode")
                        if (mapped in FATAL_FAILURES) throw ProviderException(mapped, msg)
                        remember(requested, mapped, msg)
                        continue
                    }
                    val q = row.optJSONObject("quote")
                    val h = row.optJSONObject("history")
                    if (q == null || h == null) {
                        remember(requested, ProviderFailureCode.EMPTY_DATA, "$requested quote/history alanı eksik.")
                        continue
                    }
                    try {
                        out += parseStockPair(requested, q, h)
                    } catch (pe: ProviderException) {
                        if (pe.code in FATAL_FAILURES) throw pe
                        remember(requested, pe.code, pe.message)
                    } catch (t: Throwable) {
                        remember(requested, ProviderFailureCode.UNKNOWN_ERROR, t.message ?: t.javaClass.simpleName)
                    }
                }
                if (returned != expected || items.length() != batch.size) {
                    throw ProviderException(
                        ProviderFailureCode.EMPTY_DATA,
                        "Batch count/symbol mismatch: requested=${batch.size} returned=${items.length()} unique=${returned.size}"
                    )
                }
                done = (done + batch.size).coerceAtMost(symbols.size)
                onProgress(done, symbols.size)
            }
            out
        } finally {
            diagnostics = ProviderScanDiagnostics(noData.distinct(), errors.distinct(), messages.toMap())
        }
    }

    private suspend fun loadBatchWithRetry(batch: List<String>, batchIndex: Int, batchCount: Int): JSONObject {
        val encoded = URLEncoder.encode(batch.joinToString(","), "UTF-8")
        var last: ProviderException? = null
        for (attempt in 1..MAX_BATCH_ATTEMPTS) {
            try {
                return withTimeoutOrNull(BATCH_TIMEOUT_MS) {
                    getJson("/v1/bist/snapshot-batch?symbols=$encoded")
                } ?: throw ProviderException(
                    ProviderFailureCode.NETWORK_TIMEOUT,
                    "Batch $batchIndex/$batchCount zaman aşımına uğradı."
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (pe: ProviderException) {
                last = pe
                if (pe.code in FATAL_FAILURES) throw pe
                if (pe.code !in RETRYABLE_FAILURES || attempt >= MAX_BATCH_ATTEMPTS) break
                val waitMs = (RETRY_BASE_DELAY_MS * attempt).coerceAtMost(2_000L)
                Log.w(TAG, "[BIST_SCAN] BATCH_RETRY index=$batchIndex/$batchCount attempt=$attempt code=${pe.code} waitMs=$waitMs")
                delay(waitMs)
            }
        }
        throw last ?: ProviderException(ProviderFailureCode.UNKNOWN_ERROR, "Batch $batchIndex/$batchCount başarısız.")
    }

    override suspend fun fetchOne(symbol: String): Stock? = withContext(Dispatchers.IO) {
        ensureConfigured()
        withTimeout(20_000) { fetchStock(normalizeSymbol(symbol)) }
    }

    override suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int): List<Candle> = withContext(Dispatchers.IO) {
        ensureConfigured()
        if (fromTime <= 0L || toTime <= fromTime) return@withContext emptyList()
        val requested = normalizeSymbol(symbol)
        val safeInterval = intervalMinutes.coerceIn(1, 60)
        val encoded = URLEncoder.encode(requested, "UTF-8")
        val json = getJson("/v1/bist/history-window/$encoded?from=$fromTime&to=$toTime&interval=${safeInterval}m")
        requireMatchingSymbol(requested, json.optString("symbol"), "history-window")
        val candles = parseCandlesStrict(json.optJSONArray("candles") ?: JSONArray(), minimum = 1)
        candles.filter { it.timestamp in fromTime..toTime }.sortedBy { it.timestamp }
    }


    override suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean): List<Candle> = withContext(Dispatchers.IO) {
        ensureConfigured()
        val requested = normalizeSymbol(symbol)
        val encoded = URLEncoder.encode(requested, "UTF-8")
        val range = if (maximumRange) "max" else "1y"
        val json = getJson("/v1/bist/history/$encoded?range=$range&interval=1d")
        requireMatchingSymbol(requested, json.optString("symbol"), "daily-history")
        parseCandlesStrict(json.optJSONArray("candles") ?: JSONArray(), minimum = 1).sortedBy { it.timestamp }
    }

    override suspend fun fetchNewsRisk(symbol: String, asOf: Long): tr.borsatakip.v5.analysis.BistNewsRisk = withContext(Dispatchers.IO) {
        require(symbol.matches(Regex("[A-Z0-9]{2,12}")) && asOf > 0L)
        val x = getJson("/v1/bist/news-risk/$symbol?asOf=$asOf")
        tr.borsatakip.v5.analysis.BistNewsRisk(
            symbol = x.optString("symbol"), state = x.optString("state", "UNKNOWN"),
            coverageThrough = x.optLong("coverageThrough", 0L), availableAt = x.optLong("availableAt", 0L),
            source = x.optString("source"), verified = x.optBoolean("verified", false))
    }

    override suspend fun listSymbols(): List<String> = loadSymbols()

    private suspend fun loadSymbols(): List<String> {
        val config = connection()
        val json = getJson("/v1/bist/symbols", config)
        val items = json.optJSONArray("items") ?: throw ProviderException(ProviderFailureCode.EMPTY_DATA, "BIST sembol yanıtı boş.")
        val symbols = (0 until items.length()).mapNotNull { i ->
            items.optString(i).trim().uppercase().takeIf { it.matches(Regex("[A-Z0-9_]{3,12}")) }
        }.distinct()
        if (symbols.isNotEmpty()) settings.ifConnectionCurrent(config) {
            settings.cachedBistSymbols = symbols.toSet()
            settings.cachedBistSymbolCount = symbols.size
            settings.cachedBistSymbolsFetchedAt = System.currentTimeMillis()
            settings.cachedBistSymbolsProviderId = json.optString("source").ifBlank { displayName }
        }
        return symbols
    }

    private suspend fun fetchStock(symbol: String): Stock {
        val requested = normalizeSymbol(symbol)
        val encoded = URLEncoder.encode(requested, "UTF-8")
        val config = connection()
        val quoteJson = getJson("/v1/bist/quote/$encoded", config)
        val historyJson = getJson("/v1/bist/history/$encoded?range=1y&interval=1d", config)
        return parseStockPair(requested, quoteJson, historyJson)
    }

    private fun parseStockPair(requestedRaw: String, quoteJson: JSONObject, historyJson: JSONObject): Stock {
        val requested = normalizeSymbol(requestedRaw)
        val quoteSymbol = quoteJson.optString("symbol").trim().uppercase()
        val historySymbol = historyJson.optString("symbol").trim().uppercase()
        requireMatchingSymbol(requested, quoteSymbol, "quote"); requireMatchingSymbol(requested, historySymbol, "history")
        if (quoteSymbol != historySymbol) throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR, "Quote/history sembol kimliği uyuşmuyor: $quoteSymbol/$historySymbol")
        if (quoteJson.optString("market", "BIST").uppercase() != "BIST" || historyJson.optString("market", "BIST").uppercase() != "BIST") throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR, "Piyasa kimliği BIST ile eşleşmiyor.")
        val quotePrice=quoteJson.optDouble("price",Double.NaN); val exchangeTimestamp=quoteJson.optLong("exchangeTimestamp",0L)
        val delaySeconds=if(quoteJson.has("delaySeconds")&&!quoteJson.isNull("delaySeconds")) quoteJson.optInt("delaySeconds") else null
        val realtime=quoteJson.optBoolean("realtime",false); val currentSession=historyJson.optBoolean("currentSessionIncluded",quoteJson.optBoolean("currentSessionIncluded",false))
        if(!quotePrice.isFinite()||quotePrice<=0.0||exchangeTimestamp<=0L) throw ProviderException(ProviderFailureCode.BIST_QUOTE_ERROR,"$requested quote verisi geçersiz.")
        val candles=parseCandlesStrict(historyJson.optJSONArray("candles")?:JSONArray(),minimum=RealTimeIntegrityPolicy.MIN_HISTORY_BARS)
        val lastBarTime=historyJson.optLong("lastBarTime",candles.last().timestamp)
        if(lastBarTime!=candles.last().timestamp) throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR,"History lastBarTime son mumla eşleşmiyor.")
        val interval=historyJson.optString("interval","1d"); val timezone=historyJson.optString("exchangeTimezone").ifBlank{null}?:throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR,"History exchangeTimezone alanı eksik.")
        val previousClose=quoteJson.optDouble("previousClose",Double.NaN).takeIf{it.isFinite()&&it>0.0}?:historyJson.optDouble("previousClose",Double.NaN).takeIf{it.isFinite()&&it>0.0}
        val stock=Stock(symbol=requested,companyName=historyJson.optString("name").takeIf{it.isNotBlank()},candles=candles,source=quoteJson.optString("source").ifBlank{displayName},dataTimestamp=exchangeTimestamp,isRealtime=realtime,delaySeconds=delaySeconds,currentSessionIncluded=currentSession,receivedAt=System.currentTimeMillis(),receivedElapsedRealtime=SystemClock.elapsedRealtime(),quotePrice=quotePrice,currency=quoteJson.optString("currency").takeIf{it.isNotBlank()},market="BIST",historySymbol=historySymbol,interval=interval,exchangeTimezone=timezone,sessionId=historyJson.optString("sessionId").takeIf{it.isNotBlank()},lastBarTime=lastBarTime,lastBarClosed=if(historyJson.has("lastBarClosed"))historyJson.optBoolean("lastBarClosed")else null,previousClose=previousClose,bid=quoteJson.optDouble("bid",Double.NaN).takeIf{it.isFinite()&&it>0.0},ask=quoteJson.optDouble("ask",Double.NaN).takeIf{it.isFinite()&&it>0.0})
        val verdict=RealTimeIntegrityPolicy.validate(stock); if(!verdict.accepted) throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR,verdict.reason)
        return stock
    }

    private fun parseCandlesStrict(candlesArray: JSONArray, minimum: Int): List<Candle> {
        val candles = ArrayList<Candle>(candlesArray.length())
        for (i in 0 until candlesArray.length()) {
            val x = candlesArray.optJSONObject(i) ?: throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR, "OHLCV[$i] nesne değil.")
            val c = Candle(
                timestamp = x.optLong("timestamp", 0L),
                open = x.optDouble("open", Double.NaN),
                high = x.optDouble("high", Double.NaN),
                low = x.optDouble("low", Double.NaN),
                close = x.optDouble("close", Double.NaN),
                volume = x.optDouble("volume", Double.NaN)
            )
            candles += c
        }
        return BistHistoryIntegrityPolicy.requireStrict(candles, minimum)
    }

    private fun normalizeSymbol(symbol: String): String {
        val s = symbol.trim().uppercase()
        if (!s.matches(Regex("[A-Z0-9_]{3,12}"))) throw ProviderException(ProviderFailureCode.EMPTY_DATA, "Geçersiz BIST sembolü: $symbol")
        return s
    }

    private fun requireMatchingSymbol(requested: String, actualRaw: String, source: String) {
        val actual = actualRaw.trim().uppercase()
        if (actual.isBlank() || actual != requested) throw ProviderException(ProviderFailureCode.BIST_HISTORY_ERROR, "$source sembolü istekle eşleşmiyor: requested=$requested actual=${actual.ifBlank { "<empty>" }}")
    }

    private fun connection(): BackendConnectionConfig = connectionOverride ?: settings.connectionSnapshot()

    private fun ensureConfigured() {
        val config = connection()
        if (!BackendUrlPolicy.isValid(config.baseUrl)) throw ProviderException(ProviderFailureCode.INVALID_HTTPS, "Geçerli HTTPS Production backend yapılandırılmamış.")
        if (config.apiKey.isBlank()) throw ProviderException(ProviderFailureCode.API_KEY_MISSING, "Production backend API anahtarı yapılandırılmamış.")
    }

    private suspend fun getJson(path: String, config: BackendConnectionConfig = connection()): JSONObject {
        if (!BackendUrlPolicy.isValid(config.baseUrl))
            throw ProviderException(ProviderFailureCode.INVALID_HTTPS, "Yalnız geçerli HTTPS Production backend kullanılabilir.")
        val request = Request.Builder().url(BackendUrlPolicy.endpoint(config.baseUrl, path)).get()
            .header("Accept", "application/json").header("Authorization", "Bearer ${config.apiKey}").build()
        val response = try {
            client.newCall(request).awaitBackendResponse("BIST veri isteği")
        } catch (e: CancellationException) { throw e }
        catch (e: BackendHttpException) {
            val code = BackendFailurePolicy.code(e)
            throw ProviderException(code, BackendHttpDiagnostic.safeText(e.message.orEmpty(), config.apiKey),
                e, e.evidence.providerCode.ifBlank { code.name }, e.evidence)
        } catch (e: IOException) {
            val code = BackendFailurePolicy.networkCode(e)
            throw ProviderException(code, "Backend ağ isteği başarısız: ${code.name}", e)
        }
        if (!settings.isConnectionCurrent(config)) throw ProviderException(
            ProviderFailureCode.NETWORK_ERROR, "Bağlantı ayarları değişti; istek sonucu kullanılmadı.", diagnosticCode = "CONFIGURATION_CHANGED")
        return try { JSONObject(response.body) } catch (e: org.json.JSONException) {
            throw ProviderException(ProviderFailureCode.EMPTY_DATA, "Production backend geçersiz JSON döndürdü.",
                e, connection = response.evidence)
        }
    }

    private fun String?.ifNullOrBlank(fallback: String): String = if (this.isNullOrBlank()) fallback else this

    private fun mapProviderCode(raw: String): ProviderFailureCode = when (raw.uppercase()) {
        "AUTH_ERROR", "AUTH_NOT_CONFIGURED" -> ProviderFailureCode.AUTH_ERROR
        "TLS_ERROR" -> ProviderFailureCode.TLS_ERROR
        "DNS_ERROR" -> ProviderFailureCode.DNS_ERROR
        "NETWORK_TIMEOUT", "BATCH_DEADLINE_EXCEEDED" -> ProviderFailureCode.NETWORK_TIMEOUT
        "RATE_LIMIT" -> ProviderFailureCode.RATE_LIMIT
        "NOT_CONFIGURED" -> ProviderFailureCode.BACKEND_URL_MISSING
        "NO_CANDLES", "EMPTY_DATA", "INSUFFICIENT_HISTORY", "HISTORY_ERROR", "STALE_DATA" -> ProviderFailureCode.EMPTY_DATA
        "QUOTE_ERROR" -> ProviderFailureCode.BIST_QUOTE_ERROR
        "SERVER_ERROR", "HTTP_ERROR" -> ProviderFailureCode.SERVER_ERROR
        else -> ProviderFailureCode.UNKNOWN_ERROR
    }

    private fun logDataError(symbol: String, timeframe: String, code: String, message: String) {
        Log.w(
            TAG,
            "SYMBOL=$symbol TIMEFRAME=$timeframe DATA_SOURCE=$id ERROR_CODE=$code " +
                "ERROR_MESSAGE=$message TIMESTAMP=${System.currentTimeMillis()}"
        )
    }

    companion object {
        private const val TAG = "BIST_SCAN"
        private const val BATCH_SIZE = 20
        private const val BATCH_TIMEOUT_MS = 25_000L
        private const val MAX_BATCH_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 400L
        private val FATAL_FAILURES = setOf(
            ProviderFailureCode.AUTH_ERROR, ProviderFailureCode.TLS_ERROR, ProviderFailureCode.DNS_ERROR,
            ProviderFailureCode.BACKEND_URL_MISSING, ProviderFailureCode.API_KEY_MISSING, ProviderFailureCode.INVALID_HTTPS
        )
        private val RETRYABLE_FAILURES = setOf(
            ProviderFailureCode.NETWORK_TIMEOUT, ProviderFailureCode.RATE_LIMIT,
            ProviderFailureCode.SERVER_ERROR, ProviderFailureCode.NETWORK_ERROR
        )
        private val NO_DATA_CODES = setOf(
            ProviderFailureCode.EMPTY_DATA, ProviderFailureCode.BIST_HISTORY_ERROR,
            ProviderFailureCode.STALE_DATA, ProviderFailureCode.BIST_QUOTE_ERROR
        )
    }
}
