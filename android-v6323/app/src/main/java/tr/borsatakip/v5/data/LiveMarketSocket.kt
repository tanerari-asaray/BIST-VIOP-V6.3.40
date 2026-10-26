package tr.borsatakip.v5.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveMarketSocket(context: Context) {
    private val settings = SettingsStore(context)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    private val generation = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Tick(
        val symbol: String,
        val price: Double,
        val bid: Double?,
        val ask: Double?,
        val changePct: Double?,
        val volume: Double?,
        val previousClose: Double?,
        val timestamp: Long,
        val market: String,
        val source: String?,
        val providerId: String?,
        val sequence: Long,
        val delaySeconds: Int,
        val currentSessionIncluded: Boolean
    )

    data class CandleUpdate(
        val symbol: String,
        val interval: String,
        val timestamp: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
        val source: String?,
        val lastBarClosed: Boolean?
    )

    data class TradingViewSignalEvent(
        val id: String,
        val symbol: String,
        val action: String,
        val price: Double?,
        val signalTime: Long,
        val receivedAt: Long,
        val interval: String?,
        val strategy: String?,
        val message: String?,
        val rsi: Double?,
        val macdHistogram: Double?,
        val volumeRatio: Double?,
        val score: Double?,
        val trend: String?,
        val freshSignal: Boolean,
        val source: String
    )

    interface Listener {
        fun onConnected()
        fun onTick(tick: Tick)
        fun onDisconnected(reason: String)
        fun onError(message: String)
        fun onSubscribed(message: String) {}
        fun onCandle(candle: CandleUpdate) {}
        fun onTradingViewSignal(signal: TradingViewSignalEvent) {}
    }

    fun connect(
        symbols: List<String>,
        listener: Listener,
        includeSignals: Boolean = true,
        includeCandles: Boolean = false,
        candleInterval: String = "1m"
    ) {
        close()
        val token = generation.get()
        val sequences = mutableMapOf<String, Long>()
        val candleStates = mutableMapOf<String, CandleUpdate>()
        val normalizedSymbols = symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        if (normalizedSymbols.isEmpty()) {
            listener.onError("Canlı veri için en az bir geçerli sembol gerekli.")
            return
        }
        val subscribed = normalizedSymbols.toSet()
        val connection = settings.connectionSnapshot()
        val base = connection.baseUrl
        if (!BackendUrlPolicy.isValid(base)) {
            listener.onError("Canlı veri için HTTPS backend adresi gerekli.")
            return
        }
        if (connection.apiKey.isBlank()) {
            listener.onError("Canlı veri için backend API anahtarı gerekli.")
            return
        }
        val wsUrl = BackendUrlPolicy.websocket(base, "/v1/live")
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer ${connection.apiKey}")
            .build()
        fun current() = generation.get() == token && settings.isConnectionCurrent(connection)
        fun deliver(block: () -> Unit) { mainHandler.post { if (current()) block() } }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!current()) { webSocket.close(1000, "obsolete_connection"); return }
                deliver { listener.onConnected() }
                val payload = JSONObject()
                    .put("action", "subscribe")
                    .put("symbols", normalizedSymbols)
                    .put("includeSignals", includeSignals)
                    .put("includeCandles", includeCandles)
                    .put("candleInterval", candleInterval)
                    .put("signalsAfter", System.currentTimeMillis())
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!current()) return
                try {
                    if (text.length > 1_048_576) { webSocket.close(1009, "payload_too_large"); return }
                    val x = JSONObject(text)
                    when (x.optString("type")) {
                        "subscribed" -> deliver { listener.onSubscribed("Canlı kanal hazır.") }
                        "tick" -> parseTick(x, subscribed, sequences)?.let { tick -> deliver { listener.onTick(tick) } }
                        "candle" -> parseCandle(x, subscribed, candleInterval)?.let { candle ->
                            val key = "${candle.symbol}|${candle.interval}"
                            val prior = candleStates[key]
                            if (prior == null || (candle.timestamp >= prior.timestamp &&
                                !(prior.lastBarClosed == true && candle.timestamp == prior.timestamp))) {
                                candleStates[key] = candle
                                deliver { listener.onCandle(candle) }
                            }
                        }
                        "tradingview_signal" -> parseTradingViewSignal(x, subscribed)?.let { signal -> deliver { listener.onTradingViewSignal(signal) } }
                        "error" -> deliver { listener.onError("Canlı veri sağlayıcısı hata döndürdü.") }
                    }
                } catch (e: Exception) {
                    deliver { listener.onError("Canlı veri mesajı çözümlenemedi.") }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                deliver { listener.onDisconnected("Bağlantı kapatılıyor") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                deliver { listener.onDisconnected("Bağlantı kapandı") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                deliver { listener.onError("Canlı veri bağlantısı kurulamadı.") }
            }
        })
    }

    private fun parseTick(x: JSONObject, subscribed: Set<String>, lastSequenceBySymbol: MutableMap<String, Long>): Tick? {
        val price = x.optDouble("price", Double.NaN)
        val symbol = x.optString("symbol").trim().uppercase()
        val timestamp = x.optLong("timestamp", 0L)
        val delay = x.optInt("delaySeconds", -1)
        val sequence = x.optLong("sequence", -1L)
        val market = x.optString("market").ifBlank { "BIST" }.uppercase()
        val now = System.currentTimeMillis()
        val previousSequence = lastSequenceBySymbol[symbol]
        if (symbol !in subscribed || !price.isFinite() || price <= 0.0 || timestamp <= 0L ||
            timestamp > now + RealTimeIntegrityPolicy.MAX_FUTURE_CLOCK_SKEW_MS || now - timestamp > RealTimeIntegrityPolicy.MAX_DATA_AGE_MS ||
            delay !in 0..RealTimeIntegrityPolicy.MAX_DECLARED_DELAY_SECONDS || !x.optBoolean("realtime", false) || !x.optBoolean("currentSessionIncluded", false) ||
            sequence < 0L || market != "BIST" || (previousSequence != null && sequence <= previousSequence)) return null
        lastSequenceBySymbol[symbol] = sequence
        return Tick(
            symbol = symbol,
            price = price,
            bid = x.optFinitePositive("bid"),
            ask = x.optFinitePositive("ask"),
            changePct = x.optFinite("changePct"),
            volume = x.optFiniteNonNegative("volume"),
            previousClose = x.optFinitePositive("previousClose"),
            timestamp = timestamp,
            market = market,
            source = x.optString("source").takeIf { it.isNotBlank() },
            providerId = x.optString("providerId").takeIf { it.isNotBlank() },
            sequence = sequence,
            delaySeconds = delay,
            currentSessionIncluded = true
        )
    }

    private fun parseCandle(x: JSONObject, subscribed: Set<String>, requestedInterval: String): CandleUpdate? {
        val symbol = x.optString("symbol").trim().uppercase()
        val timestamp = x.optLong("timestamp", 0L)
        if (symbol !in subscribed || timestamp <= 0L || !x.optBoolean("currentSessionIncluded", false)) return null
        val open = x.optDouble("open", Double.NaN)
        val high = x.optDouble("high", Double.NaN)
        val low = x.optDouble("low", Double.NaN)
        val close = x.optDouble("close", Double.NaN)
        val volume = x.optDouble("volume", Double.NaN)
        if (!listOf(open, high, low, close, volume).all { it.isFinite() } || open <= 0 || high <= 0 || low <= 0 || close <= 0 || volume < 0 || high < low) return null
        val interval = x.optString("interval")
        if (!LiveCandlePolicy.accepts(tr.borsatakip.v5.model.Candle(timestamp, open, high, low, close, volume),
                interval, requestedInterval, System.currentTimeMillis())) return null
        return CandleUpdate(
            symbol = symbol,
            interval = x.optString("interval").ifBlank { "1m" },
            timestamp = timestamp,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
            source = x.optString("source").takeIf { it.isNotBlank() },
            lastBarClosed = if (x.has("lastBarClosed") && !x.isNull("lastBarClosed")) x.optBoolean("lastBarClosed") else null
        )
    }

    private fun parseTradingViewSignal(x: JSONObject, subscribed: Set<String>): TradingViewSignalEvent? {
        val id = x.optString("id").trim()
        val symbol = x.optString("symbol").trim().uppercase()
        val action = x.optString("action").trim().uppercase()
        val signalTime = x.optLong("signalTime", 0L)
        val receivedAt = x.optLong("receivedAt", 0L)
        if (id.isBlank() || symbol !in subscribed || action !in setOf("BUY", "SELL", "LONG", "SHORT", "EXIT", "WATCH") || signalTime <= 0L || receivedAt <= 0L) return null
        // TradingView olayı yalnız sinyal kanalıdır. Piyasa verisi olarak işaretlenirse kabul etmeyiz.
        if (x.optBoolean("marketDataRealtime", false)) return null
        return TradingViewSignalEvent(
            id = id,
            symbol = symbol,
            action = action,
            price = x.optFinitePositive("price"),
            signalTime = signalTime,
            receivedAt = receivedAt,
            interval = x.optString("interval").takeIf { it.isNotBlank() },
            strategy = x.optString("strategy").takeIf { it.isNotBlank() },
            message = x.optString("message").takeIf { it.isNotBlank() },
            rsi = x.optFinite("rsi"),
            macdHistogram = x.optFinite("macdHistogram"),
            volumeRatio = x.optFiniteNonNegative("volumeRatio"),
            score = x.optFinite("score")?.takeIf { it in 0.0..100.0 },
            trend = x.optString("trend").takeIf { it.isNotBlank() },
            freshSignal = x.optBoolean("freshSignal", false),
            source = x.optString("source").ifBlank { "TRADINGVIEW_WEBHOOK" }
        )
    }

    private fun parseBackendError(x: JSONObject): String {
        val channel = x.optString("channel").takeIf { it.isNotBlank() }
        val symbol = x.optString("symbol").takeIf { it.isNotBlank() }
        val detail = x.opt("detail")?.toString()?.takeIf { it.isNotBlank() } ?: "Backend hata döndürdü."
        return listOfNotNull(channel, symbol, detail).joinToString(" • ")
    }

    private fun JSONObject.optFinite(name: String): Double? = optDouble(name, Double.NaN).takeIf { it.isFinite() }
    private fun JSONObject.optFinitePositive(name: String): Double? = optFinite(name)?.takeIf { it > 0.0 }
    private fun JSONObject.optFiniteNonNegative(name: String): Double? = optFinite(name)?.takeIf { it >= 0.0 }

    fun close() {
        generation.incrementAndGet()
        socket?.close(1000, "client_close")
        socket = null
    }
}
