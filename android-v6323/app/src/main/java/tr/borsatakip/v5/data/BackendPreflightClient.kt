package tr.borsatakip.v5.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Production taraması başlamadan önce backend readiness zincirini tek noktadan doğrular.
 * Config/auth/BIST/VİOP adımlarından biri başarısızsa provider READY sayılmaz.
 */
class BackendPreflightClient(private val context: Context) {
    private val settings = SettingsStore(context)
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    enum class FailureKind {
        NONE, BACKEND_NOT_CONFIGURED, API_KEY_MISSING, HTTPS_REQUIRED, INVALID_URL,
        AUTH_ERROR, HTTP_ERROR, API_ROUTE_NOT_FOUND, RATE_LIMIT, SERVER_ERROR, DATA_SOURCE_NOT_CONNECTED, NETWORK_TIMEOUT, DNS_ERROR, TLS_ERROR,
        EMPTY_DATA, SYMBOLS_ERROR, QUOTE_ERROR, HISTORY_ERROR,
        VIOP_CONTRACTS_ERROR, VIOP_QUOTE_ERROR, VIOP_HISTORY_ERROR, STALE_DATA, INVALID_DATA
    }

    data class Result(
        val ok: Boolean,
        val failureKind: FailureKind,
        val message: String,
        val healthOk: Boolean = false,
        val authOk: Boolean = false,
        val symbolsOk: Boolean = false,
        val quoteOk: Boolean = false,
        val historyOk: Boolean = false,
        val viopContractsOk: Boolean = false,
        val viopQuoteOk: Boolean = false,
        val viopHistoryOk: Boolean = false,
        val symbolCount: Int = 0,
        val viopContractCount: Int = 0,
        val sampleSymbol: String? = null,
        val sampleViopSymbol: String? = null,
        val provider: String? = null,
        val elapsedMs: Long = 0L,
        val connection: BackendHttpEvidence = BackendHttpEvidence()
    )

    suspend fun check(): Result = checkViopQuote()

    /**
     * A working VİOP quote does not require BIST entitlements or 220 daily candles.
     * This is a quote-readiness check, NOT a trade-signal authorization.
     */
    suspend fun checkViopQuote(connection: BackendConnectionConfig = settings.connectionSnapshot()): Result =
        runProbe("VIOP", connection) { checkViopQuoteCore(connection) }

    private suspend fun checkViopQuoteCore(connection: BackendConnectionConfig): Result = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val base = connection.baseUrl
        if (base.isBlank()) return@withContext fail(FailureKind.BACKEND_NOT_CONFIGURED, "HTTPS VİOP ara servis adresi eksik.", started)
        if (connection.apiKey.isBlank()) return@withContext fail(FailureKind.API_KEY_MISSING, "VİOP ara servis anahtarı eksik.", started)
        if (!validHttps(base)) return@withContext fail(FailureKind.INVALID_URL, "VİOP ara servisi için geçerli HTTPS adresi gerekli.", started)
        val health = requestJson(connection, "/v1/health?market=VIOP")
        if (!health.ok) return@withContext health.toResult(started, "VİOP sunucu bağlantısı başarısız")
        val healthJson = health.json ?: return@withContext fail(FailureKind.INVALID_DATA, "Health JSON geçersiz.", started)
        val provider = healthJson.optString("provider").ifBlank { "VİOP Backend" }
        if (!healthJson.optBoolean("ok", false)) return@withContext fail(
            FailureKind.HTTP_ERROR, "VİOP sunucu sağlığı başarısız.", started, provider = provider
        )
        val backend = BackendProvider(context, connection)
        val universe = backend.loadViop().getOrElse { error ->
            return@withContext fail(FailureKind.VIOP_CONTRACTS_ERROR,
                "VİOP sözleşme isteği başarısız: ${error.message}", started, healthOk = true, provider = provider,
                connection = (error as? BackendHttpException)?.evidence ?: BackendHttpEvidence())
        }
        val candidates = ViopContractSelector.candidates(universe, allowWatch = false)
        if (candidates.isEmpty()) return@withContext fail(
            FailureKind.VIOP_CONTRACTS_ERROR, "NO_CONTRACT: Gerçek, aktif ve doğrulanmış vadeli sözleşme bulunamadı.",
            started, healthOk = true, provider = provider, viopContractCount = universe.size
        )
        var lastQuoteError: Throwable? = null
        for (candidate in candidates.take(MAX_VIOP_PREFLIGHT_CANDIDATES)) {
            val quote = backend.loadViopQuote(candidate.symbol)
            if (quote.isFailure) {
                lastQuoteError = quote.exceptionOrNull()
                continue
            }
            // History is diagnostic: missing 220 daily bars NEVER invalidate a real quote.
            val history = backend.loadViopHistoryFlexible(candidate.symbol, "1y", "1d", 0)
            val historyReady = history.isSuccess && (history.getOrNull()?.size ?: 0) >= 220
            val detail = if (historyReady) "VİOP FİYAT BAĞLI • 220 günlük analiz mumu hazır"
                else "VİOP FİYAT BAĞLI • ANALİZ YETERSİZ: ${(history.getOrNull()?.size ?: 0)} günlük mum; ${history.exceptionOrNull()?.message ?: "minimum 220"}"
            return@withContext Result(
                ok = true, failureKind = FailureKind.NONE, message = detail,
                healthOk = true, authOk = true, viopContractsOk = true, viopQuoteOk = true,
                viopHistoryOk = historyReady, viopContractCount = universe.size,
                sampleViopSymbol = candidate.symbol, provider = provider,
                elapsedMs = System.currentTimeMillis() - started,
                connection = health.connection
            )
        }
        fail(FailureKind.VIOP_QUOTE_ERROR,
            "VİOP sözleşmeleri bulundu ancak güncel quote yok: ${lastQuoteError?.message ?: "veri alınamadı"}",
            started, healthOk = true, viopContractsOk = true,
            viopContractCount = universe.size, provider = provider,
            connection = (lastQuoteError as? BackendHttpException)?.evidence ?: BackendHttpEvidence())
    }

    suspend fun checkBist(): Result {
        val connection = settings.connectionSnapshot()
        return runProbe("BIST", connection) { checkCore(includeViop = false, connection = connection) }
    }

    suspend fun checkAll(): Result {
        val connection = settings.connectionSnapshot()
        return currentResult(connection, checkCore(includeViop = true, connection = connection))
    }

    private suspend fun runProbe(market: String, connection: BackendConnectionConfig, block: suspend () -> Result): Result {
        val gate = if (market == "BIST") bistProbes else viopProbes
        val started = System.currentTimeMillis()
        val ticket = gate.begin(connection.revision, android.os.SystemClock.elapsedRealtime()) { }
        try {
            val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) { block() }
                ?: fail(FailureKind.NETWORK_TIMEOUT, "Bağlantı testinin toplam süre sınırı aşıldı.", started)
            var published = false
            gate.finish(ticket) {
                published = settings.ifConnectionCurrent(connection) {
                    ConnectionProbeStore(context).record(market, result, connection.apiKey)
                }
            }
            return if (published) currentResult(connection, result) else result.copy(
                ok = false, failureKind = FailureKind.INVALID_DATA,
                message = "Bağlantı testi yeni test veya ayar değişikliği nedeniyle geçersiz; yeniden test edin.")
        } finally { gate.finish(ticket) { } }
    }

    private fun currentResult(connection: BackendConnectionConfig, result: Result): Result =
        if (settings.isConnectionCurrent(connection)) result else result.copy(
            ok = false, failureKind = FailureKind.INVALID_DATA,
            message = "Bağlantı ayarları test sırasında değişti; yeni ayarlarla yeniden test edin.")

    private suspend fun checkCore(includeViop: Boolean, connection: BackendConnectionConfig): Result = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val base = connection.baseUrl
        if (base.isBlank()) return@withContext fail(FailureKind.BACKEND_NOT_CONFIGURED, "Production Backend yapılandırılmamış. Gerçek HTTPS backend adresi ve API erişim anahtarı girin.", started)
        if (connection.apiKey.isBlank()) return@withContext fail(FailureKind.API_KEY_MISSING, "Production Backend yapılandırılmamış. API erişim anahtarı girin.", started)
        if (!validHttps(base)) return@withContext fail(FailureKind.INVALID_URL, "Geçersiz Production Backend URL. Yalnız geçerli HTTPS adresi kabul edilir.", started)

        val health = requestJson(connection, "/v1/health?market=BIST")
        if (!health.ok) return@withContext health.toResult(started, "Health kontrolü başarısız")
        val healthJson = health.json ?: return@withContext fail(FailureKind.INVALID_DATA, "Health cevabı geçersiz.", started)
        val healthOk = healthJson.optBoolean("ok", false)
        val provider = healthJson.optString("provider").ifBlank { "Production Backend" }
        if (!healthOk) return@withContext fail(FailureKind.HTTP_ERROR, healthJson.optString("message").ifBlank { "Backend ayakta değil." }, started, provider = provider)

        // BIST readiness: backend'in merkezi /v1/preflight sözleşmesi.
        val preflight = requestJson(connection, "/v1/preflight")
        if (!preflight.ok) return@withContext preflight.toResult(started, "Production preflight başarısız", healthOk = true, provider = provider)
        val json = preflight.json ?: return@withContext fail(FailureKind.INVALID_DATA, "Preflight cevabı geçersiz.", started, healthOk = true, provider = provider)
        fun check(name: String): JSONObject? = json.optJSONObject(name)
        val authOk = check("authentication")?.optBoolean("ok", false) == true
        val symbolsOk = check("symbols")?.optBoolean("ok", false) == true
        val quoteOk = check("quote")?.optBoolean("ok", false) == true
        val historyOk = check("history")?.optBoolean("ok", false) == true
        val symbolCount = json.optInt("symbolCount", 0)
        val sample = json.optString("sampleSymbol").takeIf { it.isNotBlank() }

        if (!authOk) return@withContext fail(FailureKind.AUTH_ERROR, check("authentication")?.optString("message").orEmpty().ifBlank { "Authentication başarısız." }, started, healthOk = true, provider = provider)
        if (!symbolsOk || symbolCount <= 0) return@withContext fail(FailureKind.SYMBOLS_ERROR, check("symbols")?.optString("message").orEmpty().ifBlank { "BIST sembol evreni alınamadı." }, started, true, true, provider = provider, symbolCount = symbolCount)
        if (!quoteOk) return@withContext fail(FailureKind.QUOTE_ERROR, check("quote")?.optString("message").orEmpty().ifBlank { "BIST quote doğrulanamadı." }, started, true, true, true, provider = provider, symbolCount = symbolCount, sampleSymbol = sample)
        if (!historyOk || !json.optBoolean("ok", false)) return@withContext fail(FailureKind.HISTORY_ERROR, check("history")?.optString("message").orEmpty().ifBlank { "BIST history doğrulanamadı." }, started, true, true, true, true, provider = provider, symbolCount = symbolCount, sampleSymbol = sample)

        if (!includeViop) {
            settings.ifConnectionCurrent(connection) { settings.cachedBistSymbolCount = symbolCount }
            return@withContext Result(true, FailureKind.NONE,
                "BIST READY • Health ✓ • Authentication ✓ • Symbols ✓ • Quote ✓ • History ✓",
                true, true, true, true, true, false, false, false,
                symbolCount, 0, sample, null, provider, System.currentTimeMillis() - started, health.connection)
        }

        // VİOP readiness: gerçek provider ile aynı parser/validator ve merkezi kontrat seçimi kullanılır.
        val backendProvider = BackendProvider(context, connection)
        val contractsResult = backendProvider.loadViop()
        if (contractsResult.isFailure) {
            return@withContext fail(FailureKind.VIOP_CONTRACTS_ERROR, "VİOP contract universe başarısız • ${contractsResult.exceptionOrNull()?.message ?: "bilinmeyen hata"}", started, true, true, true, true, true, provider = provider, symbolCount = symbolCount, sampleSymbol = sample)
        }
        val contractUniverse = contractsResult.getOrDefault(emptyList())
        val viopCount = contractUniverse.size
        val candidates = ViopContractSelector.candidates(contractUniverse, allowWatch = false)
        if (candidates.isEmpty()) {
            return@withContext fail(FailureKind.VIOP_CONTRACTS_ERROR, "NO_CONTRACT: Doğrulanmış aktif VİOP sözleşmesi bulunamadı.", started, true, true, true, true, true, provider = provider, symbolCount = symbolCount, viopContractCount = viopCount, sampleSymbol = sample)
        }

        var selectedSymbol: String? = null
        var lastQuoteError: Throwable? = null
        var lastHistoryError: Throwable? = null
        for (candidate in candidates.take(MAX_VIOP_PREFLIGHT_CANDIDATES)) {
            val quote = backendProvider.loadViopQuote(candidate.symbol)
            if (quote.isFailure) {
                lastQuoteError = quote.exceptionOrNull()
                continue
            }
            val history = backendProvider.loadViopHistory(candidate.symbol)
            if (history.isFailure) {
                lastHistoryError = history.exceptionOrNull()
                continue
            }
            selectedSymbol = candidate.symbol
            break
        }
        if (selectedSymbol == null) {
            val detail = lastHistoryError?.message ?: lastQuoteError?.message ?: "Uygun VİOP kontratında quote/history doğrulanamadı."
            val kind = if (lastHistoryError != null) FailureKind.VIOP_HISTORY_ERROR else FailureKind.VIOP_QUOTE_ERROR
            return@withContext fail(kind, detail, started, true, true, true, true, true, true, lastHistoryError == null, provider = provider, symbolCount = symbolCount, viopContractCount = viopCount, sampleSymbol = sample)
        }

        settings.cachedBistSymbolCount = symbolCount
        Result(true, FailureKind.NONE,
            "Provider READY • Health ✓ • Authentication ✓ • BIST Symbols ✓ • BIST Quote ✓ • BIST History ✓ • VİOP Contracts ✓ • VİOP Quote ✓ • VİOP History ✓",
            true, true, true, true, true, true, true, true,
            symbolCount, viopCount, sample, selectedSymbol, provider, System.currentTimeMillis() - started, health.connection)
    }

    private data class HttpResult(
        val ok: Boolean, val code: Int? = null, val json: JSONObject? = null,
        val kind: FailureKind = FailureKind.NONE, val detail: String = "",
        val connection: BackendHttpEvidence = BackendHttpEvidence()
    )

    private suspend fun requestJson(connection: BackendConnectionConfig, path: String): HttpResult {
        var observed = BackendHttpEvidence(endpoint = BackendHttpDiagnostic.safeEndpoint(path))
        return try {
            val request = Request.Builder().url(BackendUrlPolicy.endpoint(connection.baseUrl, path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer ${connection.apiKey}").build()
            val response = client.newCall(request).awaitBackendResponse("Bağlantı testi", maxBytes = 65_536)
            observed = response.evidence
            HttpResult(true, observed.httpStatus, JSONObject(response.body), connection = observed)
        } catch (e: CancellationException) { throw e }
        catch (e: BackendHttpException) {
            val evidence = e.evidence
            val code = evidence.httpStatus
            val kind = when {
                e.cause is SocketTimeoutException -> FailureKind.NETWORK_TIMEOUT
                e.cause is UnknownHostException -> FailureKind.DNS_ERROR
                e.cause is SSLException -> FailureKind.TLS_ERROR
                evidence.failureStage == "PROVIDER_AUTH" -> FailureKind.SERVER_ERROR
                evidence.providerCode in setOf("LICENSED_BIST_PROVIDER_NOT_CONNECTED",
                    "LICENSED_VIOP_PROVIDER_NOT_CONNECTED", "FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED") -> FailureKind.DATA_SOURCE_NOT_CONNECTED
                code == 401 || code == 403 -> FailureKind.AUTH_ERROR
                code == 404 -> FailureKind.API_ROUTE_NOT_FOUND
                code == 429 -> FailureKind.RATE_LIMIT
                code != null && code >= 500 -> FailureKind.SERVER_ERROR
                else -> FailureKind.HTTP_ERROR
            }
            val version = if (code == 404 && path.substringBefore('?') == "/v1/health")
                diagnoseServiceVersion(connection) else ""
            HttpResult(false, code, kind = kind,
                detail = BackendHttpDiagnostic.describe(evidence) + version, connection = evidence)
        } catch (_: org.json.JSONException) {
            HttpResult(false, observed.httpStatus, kind = FailureKind.INVALID_DATA,
                detail = "Sunucu yanıtı geçerli JSON değil.", connection = observed)
        } catch (_: SocketTimeoutException) {
            HttpResult(false, kind = FailureKind.NETWORK_TIMEOUT, detail = "Bağlantı testi zaman aşımına uğradı.", connection = observed)
        } catch (_: UnknownHostException) {
            HttpResult(false, kind = FailureKind.DNS_ERROR, detail = "Sunucunun alan adı çözümlenemedi (DNS).", connection = observed)
        } catch (_: SSLException) {
            HttpResult(false, kind = FailureKind.TLS_ERROR, detail = "TLS bağlantısı kurulamadı.", connection = observed)
        } catch (e: Exception) {
            HttpResult(false, kind = FailureKind.HTTP_ERROR,
                detail = BackendHttpDiagnostic.safeText(e.message ?: "Üretim veri servisine ulaşılamıyor.", connection.apiKey),
                connection = observed)
        }
    }

    private suspend fun diagnoseServiceVersion(connection: BackendConnectionConfig): String {
        val info = requestJson(connection, "/v1/service-info")
        val json = info.json
        return if (info.ok && json != null && json.optString("service") == "bist-viop-gateway" &&
            json.optString("apiContract") == "v1")
            " Sunucu gateway kimliğini verdi fakat sağlık yolu eksik; route/deployment eşleşmesini kontrol edin."
        else " Servis sürüm tanısı alınamadı; API dağıtımını denetleyin."
    }

    private fun quoteFresh(json: JSONObject): Boolean {
        if (!json.optBoolean("realtime", false) || !json.optBoolean("currentSessionIncluded", false)) return false
        val ts = json.optLong("exchangeTimestamp", 0L)
        val delay = json.optInt("delaySeconds", Int.MAX_VALUE)
        val age = System.currentTimeMillis() - ts
        return ts > 0L && delay in 0..5 && age in -15_000L..60_000L
    }

    private fun HttpResult.toResult(started: Long, prefix: String, healthOk: Boolean = false, provider: String? = null): Result =
        fail(kind.takeIf { it != FailureKind.NONE } ?: FailureKind.HTTP_ERROR, "$prefix • ${detail.ifBlank { "Bağlantı başarısız" }}", started, healthOk = healthOk, provider = provider, connection = connection)

    private fun fail(kind: FailureKind, message: String, started: Long,
        healthOk: Boolean = false, authOk: Boolean = false, symbolsOk: Boolean = false,
        quoteOk: Boolean = false, historyOk: Boolean = false, viopContractsOk: Boolean = false,
        viopQuoteOk: Boolean = false, viopHistoryOk: Boolean = false, symbolCount: Int = 0,
        viopContractCount: Int = 0, sampleSymbol: String? = null, sampleViopSymbol: String? = null,
        provider: String? = null, connection: BackendHttpEvidence = BackendHttpEvidence()): Result {
        return Result(false, kind, message, healthOk, authOk, symbolsOk, quoteOk, historyOk,
            viopContractsOk, viopQuoteOk, viopHistoryOk, symbolCount, viopContractCount,
            sampleSymbol, sampleViopSymbol, provider, System.currentTimeMillis() - started, connection)
    }

    companion object {
        fun validHttps(raw: String): Boolean = BackendUrlPolicy.isValid(raw)

        private const val PROBE_TIMEOUT_MS = 90_000L
        private val bistProbes = ProviderProbeGate()
        private val viopProbes = ProviderProbeGate()
        private const val MAX_VIOP_PREFLIGHT_CANDIDATES = 8
    }
}
