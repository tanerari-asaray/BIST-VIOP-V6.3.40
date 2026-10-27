package tr.borsatakip.v5.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopQuote
import tr.borsatakip.v5.model.NewsItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.time.YearMonth

class BackendProvider(context: Context, private val connectionOverride: BackendConnectionConfig? = null) {
    private val s = SettingsStore(context)
    fun mtfCacheSourceKey(): String = "viop_backend|${(connectionOverride ?: s.connectionSnapshot()).sourceIdentity.ifBlank { "unconfigured" }}"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private suspend fun readJson(path: String, label: String): JSONObject {
        val connection = connectionOverride ?: s.connectionSnapshot()
        val request = Request.Builder()
            .url(BackendUrlPolicy.endpoint(connection.baseUrl, path))
            .header("Accept", "application/json")
            .apply { if (connection.apiKey.isNotBlank()) header("Authorization", "Bearer ${connection.apiKey}") }
            .build()
        val response = client.newCall(request).awaitBackendResponse(label)
        require(s.isConnectionCurrent(connection)) { "CONFIGURATION_CHANGED: Bağlantı ayarları değişti." }
        return try { JSONObject(response.body) } catch (e: org.json.JSONException) {
            throw BackendHttpException(response.evidence, "SCHEMA_ERROR: $label JSON biçimi geçersiz.", e)
        }
    }

    private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun loadViop(): Result<List<ViopContract>> = withContext(Dispatchers.IO) {
        providerResult {
            val root = readJson("/v1/viop/contracts", "VİOP veri sağlayıcısı")
            val array = root.optJSONArray("items") ?: error("SCHEMA_ERROR: items alanı yok.")
            require(array.length() <= 10000) { "SCHEMA_ERROR: Sözleşme listesi sınırı aşıldı." }
            val seen=mutableSetOf<String>()
            buildList {
                for (i in 0 until array.length()) {
                    val x = array.optJSONObject(i) ?: error("SCHEMA_ERROR: Sözleşme nesnesi geçersiz.")
                    val symbol = x.optString("symbol").trim().uppercase()
                    require(symbol.matches(Regex("[FO]_[A-Z0-9_.:-]+"))) { "SCHEMA_ERROR: Geçersiz VİOP sembolü." }
                    if(!seen.add(symbol)) continue
                    require(x.optString("contractType").isNotBlank()) { "SCHEMA_ERROR: Sözleşme türü eksik." }
                    val underlying = x.optString("underlying").trim().uppercase()
                    val expiry = x.optString("expiry").trim()
                    val tick = x.optDouble("tickSize", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
                    val multiplier = x.optDouble("multiplier", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
                    val dataTimestamp = x.optLong("dataTimestamp", 0L)
                    val realtime = x.optBoolean("realtime", false)
                    val delay = if (x.has("delaySeconds") && !x.isNull("delaySeconds")) x.optInt("delaySeconds") else null
                    val currentSession = x.optBoolean("currentSessionIncluded", false)
                    val receivedAt = System.currentTimeMillis()
                    val lastTradingAt = x.optLong("lastTradableAt", x.optLong("lastTradingAt", 0L))
                        .takeIf { it > 0L }
                    val expiryAt = x.optLong("expiryAt", 0L).takeIf { it > 0L }
                    val mode = when {
                        realtime && currentSession && delay != null && delay in 0..RealTimeIntegrityPolicy.MAX_DECLARED_DELAY_SECONDS -> DataMode.REALTIME
                        delay != null && delay > RealTimeIntegrityPolicy.MAX_DECLARED_DELAY_SECONDS -> DataMode.DELAYED
                        else -> DataMode.UNVERIFIED
                    }
                    val expiryOk = runCatching { YearMonth.parse(expiry) >= YearMonth.now() }.getOrDefault(false)
                    val validity = when {
                        !expiryOk || (lastTradingAt != null && lastTradingAt <= receivedAt) -> SignalValidity.REJECTED
                        underlying.isBlank() || tick == null || multiplier == null || lastTradingAt == null -> SignalValidity.INSUFFICIENT
                        dataTimestamp < 1_000_000_000_000L -> SignalValidity.REJECTED
                        mode != DataMode.REALTIME -> SignalValidity.WATCH
                        else -> SignalValidity.VALID
                    }
                    val reason = when (validity) {
                        SignalValidity.VALID -> "Vade, son işlem zamanı, dayanak, tickSize, multiplier ve veri kökeni doğrulandı."
                        SignalValidity.WATCH -> "Sözleşme parametreleri mevcut ancak gerçek zamanlı veri modu doğrulanmadı."
                        SignalValidity.INSUFFICIENT -> if (lastTradingAt == null) "Gerçek son işlem zamanı (lastTradingAt) eksik." else "Dayanak, tickSize veya multiplier zorunlu alanlarından biri eksik."
                        SignalValidity.REJECTED -> when {
                            lastTradingAt != null && lastTradingAt < 1_000_000_000_000L -> "TIMESTAMP_UNIT_ERROR: lastTradableAt milisaniye olmalı."
                            !expiryOk || (lastTradingAt != null && lastTradingAt <= receivedAt) -> "Vade/son işlem zamanı geçersiz veya sona ermiş."
                            else -> "TIMESTAMP_UNIT_ERROR: Sözleşme veri zamanı epoch milisaniye olmalı."
                        }
                    }
                    add(ViopContract(
                        symbol=symbol, underlying=underlying.ifBlank { "-" }, expiry=expiry.ifBlank { "-" },
                        contractType=x.optString("contractType").ifBlank { "Vadeli İşlem" },
                        lastPrice=x.optDouble("lastPrice", Double.NaN).takeIf { it.isFinite() && it > 0.0 },
                        bid=x.optDouble("bid", Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                        ask=x.optDouble("ask", Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                        dailyChangePct=x.optDouble("dailyChangePct", Double.NaN).takeIf { it.isFinite() },
                        tickSize=tick, multiplier=multiplier,
                        openInterest=x.optLong("openInterest", -1L).takeIf { it >= 0L },
                        volume=x.optDouble("volume", Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                        liquidity=x.optString("liquidity").takeIf { it.isNotBlank() },
                        rollover=x.optString("rollover").takeIf { it.isNotBlank() },
                        providerId="backend", providerLabel=x.optString("source").ifBlank { "Ana Backend" },
                        isManual=false, currency=x.optString("currency").takeIf { it.isNotBlank() },
                        status=when(validity){ SignalValidity.VALID->"Doğrulanmış sözleşme"; SignalValidity.WATCH->"İzleme"; SignalValidity.INSUFFICIENT->"Yetersiz veri"; SignalValidity.REJECTED->"Reddedildi" },
                        dataTimestamp=dataTimestamp, isRealtime=realtime, delaySeconds=delay,
                        currentSessionIncluded=currentSession, receivedAt=receivedAt, dataMode=mode,
                        validity=validity, validityReason=reason,
                        lastTradingAt=lastTradingAt,
                        expiryAt=expiryAt,
                        exchangeTimezone=x.optString("exchangeTimezone").takeIf { it.isNotBlank() },
                        settlementType=x.optString("settlementType").takeIf { it.isNotBlank() }
                    ))
                }
            }
        }
    }

    suspend fun loadViopQuote(symbol: String, observationOnly: Boolean = false): Result<ViopQuote> = withContext(Dispatchers.IO) {
        providerResult {
            val safe = URLEncoder.encode(symbol.uppercase(), "UTF-8")
            val x = readJson("/v1/viop/quote/$safe", "VİOP quote")
            val price = x.optDouble("price", Double.NaN)
            require(price.isFinite() && price > 0.0) { "QUOTE_ERROR: Geçerli son fiyat yok." }
            val exchangeTs = x.optLong("exchangeTimestamp", 0L)
            require(exchangeTs >= 1_000_000_000_000L) { "STALE_DATA: Piyasa veri zamanı eksik." }
            val realtime = x.optBoolean("realtime", false)
            val delay = if (x.has("delaySeconds") && !x.isNull("delaySeconds")) x.optInt("delaySeconds") else null
            val currentSession = x.optBoolean("currentSessionIncluded", false)
            val now = System.currentTimeMillis()
            if (!observationOnly) require(realtime && currentSession) { "STALE_DATA: Gerçek zamanlı/seans verisi doğrulanmadı." }
            if (!observationOnly) require(delay != null && delay in 0..RealTimeIntegrityPolicy.MAX_DECLARED_DELAY_SECONDS) { "STALE_DATA: Gecikme eşiği aşıldı veya bildirilmedi." }
            require(exchangeTs <= now + RealTimeIntegrityPolicy.MAX_FUTURE_CLOCK_SKEW_MS) { "STALE_DATA: Piyasa zamanı gelecekte." }
            if (!observationOnly) require(now - exchangeTs <= RealTimeIntegrityPolicy.MAX_DATA_AGE_MS) { "STALE_DATA: Quote güncel değil." }
            val responseSymbol = x.optString("symbol").trim().uppercase()
            require(responseSymbol.isNotBlank()) { "QUOTE_ERROR: Yanıtta sembol kimliği bulunmuyor." }
            require(responseSymbol == symbol.trim().uppercase()) { "QUOTE_ERROR: Sembol kimliği uyuşmuyor." }
            val oiChange = x.optDouble("openInterestChangePct", Double.NaN).takeIf { it.isFinite() }
            val oiAsOf = if (x.has("openInterestAsOfTimestamp") && !x.isNull("openInterestAsOfTimestamp")) x.optLong("openInterestAsOfTimestamp", 0L) else null
            require(oiChange == null || (oiAsOf != null && oiAsOf > 0L && oiAsOf <= exchangeTs)) {
                "QUOTE_ERROR: OI değişimi için geçerli point-in-time zaman damgası gerekli."
            }
            ViopQuote(
                symbol=responseSymbol, price=price,
                bid=x.optDouble("bid", Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                ask=x.optDouble("ask", Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                dailyChangePct=x.optDouble("dailyChangePct", Double.NaN).takeIf { it.isFinite() },
                volume=x.optDouble("volume", Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                openInterest=x.optLong("openInterest", -1L).takeIf { it >= 0L },
                // receivedAt is the local arrival instant, never an upstream-provided fresh timestamp.
                exchangeTimestamp=exchangeTs, receivedAt=System.currentTimeMillis(),
                source=x.optString("source").ifBlank { "Ana Backend" }, realtime=realtime,
                delaySeconds=delay, currentSessionIncluded=currentSession,
                openInterestChangePct=oiChange, openInterestAsOfTimestamp=oiAsOf
            )
        }
    }

    suspend fun loadViopHistory(symbol: String): Result<List<Candle>> = loadViopHistoryFlexible(symbol, "1y", "1d", 220)

    suspend fun loadViopHistoryFlexible(symbol: String, range: String, interval: String, minimumBars: Int = 0,
                                       forDisplay: Boolean = false): Result<List<Candle>> = withContext(Dispatchers.IO) {
        providerResult {
            require(range.matches(Regex("[0-9]+[dmy]"))) { "HISTORY_ERROR: Geçersiz range." }
            require(interval in setOf("1m","3m","5m","15m","60m","1d")) { "HISTORY_ERROR: Geçersiz interval." }
            val safe = URLEncoder.encode(symbol.uppercase(), "UTF-8")
            val root = readJson("/v1/viop/history/$safe?range=$range&interval=$interval", "VİOP history")
            val array = root.optJSONArray("candles") ?: error("HISTORY_ERROR: candles alanı yok.")
            val responseSymbol = root.optString("symbol")
            val responseInterval = root.optString("interval")
            val lastBarClosed = root.has("lastBarClosed") && !root.isNull("lastBarClosed") && root.optBoolean("lastBarClosed", false)
            val exchangeTimestamp = root.optLong("exchangeTimestamp", root.optLong("lastExchangeTimestamp", 0L))
            ViopHistoryIntegrityPolicy.validateMetadata(
                requestedSymbol = symbol,
                requestedInterval = interval,
                responseSymbol = responseSymbol,
                responseInterval = responseInterval,
                lastBarClosed = lastBarClosed,
                exchangeTimestamp = exchangeTimestamp,
                allowForming = true
            )?.let { error("HISTORY_ERROR: $it") }
            if (!forDisplay) ViopHistoryIntegrityPolicy.validateExchangeFreshness(
                exchangeTimestamp = exchangeTimestamp,
                nowMs = System.currentTimeMillis(),
                interval = interval
            )?.let { error("HISTORY_ERROR: $it") }
            val candles = buildList {
                for (i in 0 until array.length()) {
                    val x=array.optJSONObject(i) ?: error("HISTORY_ERROR: candles[$i] nesne değil.")
                    val c=Candle(x.optLong("timestamp",0L),x.optDouble("open",Double.NaN),x.optDouble("high",Double.NaN),x.optDouble("low",Double.NaN),x.optDouble("close",Double.NaN),x.optDouble("volume",Double.NaN))
                    require(c.timestamp > 0L) { "HISTORY_ERROR: candles[$i] zaman damgası geçersiz." }
                    require(listOf(c.open,c.high,c.low,c.close,c.volume).all { it.isFinite() }) { "HISTORY_ERROR: candles[$i] sonlu olmayan değer içeriyor." }
                    require(c.open > 0.0 && c.high > 0.0 && c.low > 0.0 && c.close > 0.0 && c.volume >= 0.0) { "HISTORY_ERROR: candles[$i] fiyat/hacim kurallarına uymuyor." }
                    require(c.low <= c.high && c.open in c.low..c.high && c.close in c.low..c.high) { "HISTORY_ERROR: candles[$i] OHLC aralığı tutarsız." }
                    add(c)
                }
            }
            ViopHistoryIntegrityPolicy.validateCandles(candles)?.let { error("HISTORY_ERROR: $it") }
            RealTimeIntegrityPolicy.validateCandles(candles)?.let { error("HISTORY_ERROR: $it") }
            val usable = if (forDisplay || lastBarClosed) candles else candles.dropLast(1)
            if (!forDisplay) ViopHistoryIntegrityPolicy.validateLastBarClosed(
                candles = usable, nowMs = System.currentTimeMillis(), interval = interval,
                // sessionClosedAt describes the response's last bar, not the preceding
                // closed bar retained when the final forming bar was removed.
                sessionClosedAt = root.optLong("sessionClosedAt", 0L).takeIf { lastBarClosed && it > 0L }
            )?.let { error("HISTORY_ERROR: $it") }
            if(minimumBars>0) require(usable.size>=minimumBars){"INSUFFICIENT_HISTORY: ${usable.size} mum; minimum $minimumBars."}
            usable
        }
    }

    suspend fun loadNews(symbol: String? = null, category: String = "ALL"): Result<List<NewsItem>> = withContext(Dispatchers.IO) {
        providerResult {
            val symbolPart = symbol?.takeIf { it.isNotBlank() }?.let { "&symbol=" + URLEncoder.encode(it.uppercase(), "UTF-8") }.orEmpty()
            val cat = URLEncoder.encode(category.uppercase(), "UTF-8")
            val root = readJson("/v1/news?category=$cat$symbolPart", "Haber sağlayıcısı")
            val array = root.optJSONArray("items") ?: return@providerResult emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val x = array.optJSONObject(i) ?: continue
                    val title = x.optString("title").trim(); val source = x.optString("source").trim(); val published = x.optLong("publishedAt", 0L)
                    if (title.isBlank() || source.isBlank() || published <= 0L) continue
                    add(NewsItem(
                        id=x.optString("id").ifBlank { "$source:$published:$i" },
                        symbol=x.optString("symbol").takeIf { it.isNotBlank() }?.uppercase(),
                        category=x.optString("category").ifBlank { "GENEL" }.uppercase(),
                        title=title, summary=x.optString("summary").takeIf { it.isNotBlank() }, source=source, publishedAt=published,
                        url=x.optString("url").takeIf { it.startsWith("https://") }, verified=x.optBoolean("verified", false),
                        receivedAt=x.optLong("receivedAt", 0L).takeIf { it > 0L },
                        availableAt=x.optLong("availableAt", 0L).takeIf { it > 0L },
                        eventTime=x.optLong("eventTime", 0L).takeIf { it > 0L },
                        claimKey=x.optString("claimKey").takeIf { it.isNotBlank() },
                        eventKey=x.optString("eventKey").takeIf { it.isNotBlank() },
                        sourceType=x.optString("sourceType").takeIf { it.isNotBlank() },
                        originSourceId=x.optString("originSourceId").takeIf { it.isNotBlank() },
                        supportsClaim=x.optBoolean("supportsClaim", true)
                    ))
                }
            }.sortedByDescending { it.publishedAt }
        }
    }

}

