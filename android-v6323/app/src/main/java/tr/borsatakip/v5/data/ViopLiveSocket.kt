package tr.borsatakip.v5.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Independent licensed VİOP channel. Never creates a trading signal from a tick. */
class ViopLiveSocket(context: Context) {
    private val settings = SettingsStore(context)
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private val sequenceBySymbol = mutableMapOf<String, Long>()
    private val symbolPattern = Regex("[A-Z0-9_.:-]{2,64}")

    data class Quote(val symbol: String, val price: Double, val bid: Double?, val ask: Double?,
        val exchangeTimestamp: Long, val sequence: Long, val receivedAt: Long)
    interface Listener {
        fun onConnected()
        fun onQuote(quote: Quote)
        fun onDisconnected(reason: String)
        fun onError(reason: String)
    }

    fun connect(symbols: List<String>, listener: Listener) {
        close()
        val names = symbols.map { it.trim().uppercase() }.distinct()
        if (names.isEmpty() || names.size > 50 || names.any { !symbolPattern.matches(it) }) {
            listener.onError("VİOP sözleşme listesi geçersiz."); return
        }
        val connection = settings.connectionSnapshot()
        val base = connection.baseUrl
        if (!BackendUrlPolicy.isValid(base) || connection.apiKey.isBlank()) {
            listener.onError("Lisanslı HTTPS ara servis ve erişim anahtarı gerekli."); return
        }
        val request = Request.Builder().url(BackendUrlPolicy.websocket(base, "/v1/viop/live"))
            .header("Authorization", "Bearer ${connection.apiKey}").build()
        val allowed = names.toSet()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // A socket open is not proof of market-data entitlement.
                ws.send(JSONObject().put("action", "subscribe").put("symbols", org.json.JSONArray(names)).toString())
            }
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    if (j.optString("type") == "subscribed" && j.optString("market") == "VIOP") {
                        listener.onConnected(); return
                    }
                    if (j.optString("type") != "tick" || j.optString("market") != "VIOP") return
                    val symbol = j.optString("symbol").uppercase()
                    val seq = j.optLong("sequence", -1L)
                    val timestamp = j.optLong("exchangeTimestamp", 0L)
                    val now = System.currentTimeMillis()
                    val price = j.optDouble("price", Double.NaN)
                    val age = now - timestamp
                    if (symbol !in allowed || seq < 0 || seq <= (sequenceBySymbol[symbol] ?: -1L) ||
                        timestamp < 1_000_000_000_000L || age < -15_000L || age > 60_000L ||
                        !price.isFinite() || price <= 0 || j.optBoolean("realtime") != true ||
                        j.optInt("delaySeconds", -1) != 0 || !j.optBoolean("currentSessionIncluded")) return
                    val bid = if (j.has("bid") && !j.isNull("bid")) j.optDouble("bid") else null
                    val ask = if (j.has("ask") && !j.isNull("ask")) j.optDouble("ask") else null
                    if (bid != null && (!bid.isFinite() || bid <= 0)) return
                    if (ask != null && (!ask.isFinite() || ask <= 0)) return
                    if (bid != null && ask != null && bid > ask) return
                    sequenceBySymbol[symbol] = seq
                    listener.onQuote(Quote(symbol, price, bid, ask, timestamp, seq, now))
                } catch (_: Exception) { listener.onError("Geçersiz VİOP akış mesajı") }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { listener.onDisconnected(reason) }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) { listener.onDisconnected(reason) }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                listener.onDisconnected(t.message ?: "VİOP bağlantısı kesildi")
            }
        })
    }
    fun close() { socket?.close(1000, "stop"); socket = null; sequenceBySymbol.clear() }
}
