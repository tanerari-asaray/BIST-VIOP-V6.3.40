package tr.borsatakip.v5.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class ProviderState {
    PROVIDER_NOT_CONFIGURED,
    PROVIDER_CONFIGURED,
    PROVIDER_TESTING,
    PROVIDER_READY,
    PROVIDER_STALE_READY,
    PROVIDER_ERROR
}

enum class ProviderFailureCode {
    NONE,
    BACKEND_URL_MISSING,
    API_KEY_MISSING,
    INVALID_HTTPS,
    TLS_ERROR,
    HEALTH_ERROR,
    AUTH_ERROR,
    BIST_SYMBOLS_ERROR,
    BIST_QUOTE_ERROR,
    BIST_HISTORY_ERROR,
    VIOP_CONTRACTS_ERROR,
    VIOP_QUOTE_ERROR,
    VIOP_HISTORY_ERROR,
    STALE_DATA,
    RATE_LIMIT,
    API_ROUTE_NOT_FOUND,
    SERVER_ERROR,
    DATA_SOURCE_NOT_CONNECTED,
    NETWORK_ERROR,
    DNS_ERROR,
    NETWORK_TIMEOUT,
    EMPTY_DATA,
    UNKNOWN_ERROR
}

data class ProviderReadinessSnapshot(
    val state: ProviderState,
    val failureCode: ProviderFailureCode = ProviderFailureCode.NONE,
    val message: String = "",
    val healthOk: Boolean = false,
    val authOk: Boolean = false,
    val bistSymbolsOk: Boolean = false,
    val bistQuoteOk: Boolean = false,
    val bistHistoryOk: Boolean = false,
    val viopContractsOk: Boolean = false,
    val viopQuoteOk: Boolean = false,
    val viopHistoryOk: Boolean = false,
    val bistSymbolCount: Int = 0,
    val viopContractCount: Int = 0,
    val testedAt: Long = 0L,
    val connection: BackendHttpEvidence = BackendHttpEvidence()
)

class ProviderReadinessService(
    context: Context,
    private val probeOverride: (suspend (BackendConnectionConfig) -> BackendPreflightClient.Result)? = null
) {
    private val app = context.applicationContext
    private val journal = DiagnosticJournal(app)
    private val settings = SettingsStore(context)
    private val preflight = BackendPreflightClient(context)

    fun localConfigState(config: BackendConnectionConfig = settings.connectionSnapshot()): ProviderReadinessSnapshot {
        val url = config.baseUrl
        val key = config.apiKey
        if (url.isBlank()) return ProviderReadinessSnapshot(
            ProviderState.PROVIDER_NOT_CONFIGURED,
            ProviderFailureCode.BACKEND_URL_MISSING,
            "Production Backend yapılandırılmamış. Gerçek HTTPS backend adresi ve API erişim anahtarı girin."
        )
        if (key.isBlank()) return ProviderReadinessSnapshot(
            ProviderState.PROVIDER_NOT_CONFIGURED,
            ProviderFailureCode.API_KEY_MISSING,
            "Production Backend yapılandırılmamış. API erişim anahtarı girin."
        )
        if (!isValidHttps(url)) return ProviderReadinessSnapshot(
            ProviderState.PROVIDER_ERROR,
            ProviderFailureCode.INVALID_HTTPS,
            "Geçersiz Production Backend adresi. Yalnız geçerli HTTPS URL kabul edilir."
        )
        if (settings.lastProviderState == ProviderState.PROVIDER_TESTING.name) {
            probes.recoverIfIdle(config.revision, SystemClock.elapsedRealtime()) {
                settings.ifConnectionCurrent(config) {
                    if (settings.lastProviderState == ProviderState.PROVIDER_TESTING.name)
                        persist(ProviderReadinessSnapshot(ProviderState.PROVIDER_CONFIGURED,
                            message = "Önceki bağlantı testi tamamlanmadı; yeniden test edin."), config.apiKey)
                }
            }
        }
        val storedState = runCatching { ProviderState.valueOf(settings.lastProviderState) }.getOrNull()
        val storedCode = runCatching { ProviderFailureCode.valueOf(settings.lastProviderFailureCode) }.getOrNull()
        val snapshot = when (storedState) {
            ProviderState.PROVIDER_READY -> {
                val testedAt = settings.lastBackendHealthAt
                val age = if (testedAt > 0L) System.currentTimeMillis() - testedAt else Long.MAX_VALUE
                if (age !in 0..READY_TTL_MS || !settings.lastViopQuoteReady) {
                    ProviderReadinessSnapshot(
                        ProviderState.PROVIDER_STALE_READY,
                        ProviderFailureCode.STALE_DATA,
                        "Provider READY doğrulamasının süresi doldu; tarama öncesi hızlı doğrulama gerekli.",
                        healthOk = settings.lastBackendHealthOk,
                        bistSymbolCount = settings.cachedBistSymbolCount,
                        testedAt = testedAt
                    )
                } else {
                    ProviderReadinessSnapshot(
                        ProviderState.PROVIDER_READY,
                        ProviderFailureCode.NONE,
                        settings.lastProviderMessage.ifBlank { "Production provider hazır." },
                        healthOk = settings.lastBackendHealthOk,
                        bistSymbolCount = settings.cachedBistSymbolCount,
                        viopQuoteOk = settings.lastViopQuoteReady,
                        viopHistoryOk = settings.lastViopAnalysisReady,
                        testedAt = testedAt
                    )
                }
            }
            ProviderState.PROVIDER_STALE_READY -> ProviderReadinessSnapshot(
                ProviderState.PROVIDER_STALE_READY,
                ProviderFailureCode.STALE_DATA,
                "Provider doğrulaması eski; yeniden test gerekli.",
                testedAt = settings.lastBackendHealthAt
            )
            ProviderState.PROVIDER_ERROR -> ProviderReadinessSnapshot(
                ProviderState.PROVIDER_ERROR,
                storedCode ?: ProviderFailureCode.UNKNOWN_ERROR,
                settings.lastProviderMessage.ifBlank { "Son provider testi başarısız." },
                testedAt = settings.lastBackendHealthAt
            )
            ProviderState.PROVIDER_TESTING -> ProviderReadinessSnapshot(
                ProviderState.PROVIDER_TESTING,
                ProviderFailureCode.NONE,
                "Production provider test ediliyor...",
                testedAt = settings.lastBackendHealthAt
            )
            else -> ProviderReadinessSnapshot(
                ProviderState.PROVIDER_CONFIGURED,
                ProviderFailureCode.NONE,
                "Production Backend yapılandırıldı; bağlantı testi gerekli.",
                testedAt = settings.lastBackendHealthAt
            )
        }
        val observed = ConnectionProbeStore(app).read("VIOP")
        if (!settings.isConnectionCurrent(config)) return ProviderReadinessSnapshot(
            ProviderState.PROVIDER_CONFIGURED, message = "Bağlantı ayarları değişti; yeniden test gerekli.")
        return snapshot.copy(
            connection = BackendHttpEvidence.decode(settings.lastProviderHttpEvidence),
            healthOk = observed.optBoolean("health", snapshot.healthOk),
            authOk = observed.optBoolean("dataAuthentication", snapshot.authOk),
            viopContractsOk = observed.optBoolean("contracts", snapshot.viopContractsOk)
        )
    }

    suspend fun test(): ProviderReadinessSnapshot {
        val probeStarted = SystemClock.elapsedRealtime()
        val config = settings.connectionSnapshot()
        val local = localConfigState(config)
        if (local.state == ProviderState.PROVIDER_NOT_CONFIGURED || local.failureCode == ProviderFailureCode.INVALID_HTTPS) {
            val published = settings.ifConnectionCurrent(config) { persist(local, config.apiKey) }
            journal.record("PROVIDER", "CONFIG", local.failureCode.name)
            return if (published) local else localConfigState()
        }

        val ticket = probes.begin(config.revision, probeStarted) {
            settings.ifConnectionCurrent(config) {
                persist(ProviderReadinessSnapshot(ProviderState.PROVIDER_TESTING,
                    message = "Production provider test ediliyor..."), config.apiKey)
            }
        }
        try {
        val result = withTimeoutOrNull(TEST_TIMEOUT_MS) { probeOverride?.invoke(config) ?: preflight.checkViopQuote(config) }
            ?: throw java.net.SocketTimeoutException("Bağlantı testi toplam zaman aşımı.")
        val snapshot = if (result.ok) {
            ProviderReadinessSnapshot(
                state = ProviderState.PROVIDER_READY,
                message = result.message,
                healthOk = result.healthOk,
                authOk = result.authOk,
                bistSymbolsOk = result.symbolsOk,
                bistQuoteOk = result.quoteOk,
                bistHistoryOk = result.historyOk,
                viopContractsOk = result.viopContractsOk,
                viopQuoteOk = result.viopQuoteOk,
                viopHistoryOk = result.viopHistoryOk,
                bistSymbolCount = result.symbolCount,
                viopContractCount = result.viopContractCount,
                testedAt = System.currentTimeMillis(),
                connection = result.connection
            )
        } else {
            ProviderReadinessSnapshot(
                state = if (result.failureKind == BackendPreflightClient.FailureKind.BACKEND_NOT_CONFIGURED || result.failureKind == BackendPreflightClient.FailureKind.API_KEY_MISSING) ProviderState.PROVIDER_NOT_CONFIGURED else ProviderState.PROVIDER_ERROR,
                failureCode = mapFailure(result.failureKind),
                message = result.message,
                healthOk = result.healthOk,
                authOk = result.authOk,
                bistSymbolsOk = result.symbolsOk,
                bistQuoteOk = result.quoteOk,
                bistHistoryOk = result.historyOk,
                viopContractsOk = result.viopContractsOk,
                viopQuoteOk = result.viopQuoteOk,
                viopHistoryOk = result.viopHistoryOk,
                bistSymbolCount = result.symbolCount,
                viopContractCount = result.viopContractCount,
                testedAt = System.currentTimeMillis(),
                connection = result.connection
            )
        }
        var published = false
        probes.finish(ticket) {
            published = settings.ifConnectionCurrent(config) { persist(snapshot, config.apiKey) }
        }
        if (!published) return localConfigState()
        journal.record("PROVIDER", "VIOP_PREFLIGHT", if (snapshot.state == ProviderState.PROVIDER_READY) "VIOP_QUOTE_CHECK_OK" else snapshot.failureCode.name,
            durationMs = (SystemClock.elapsedRealtime() - probeStarted).coerceAtLeast(0L))
        return snapshot
        } catch (e: java.net.SocketTimeoutException) {
            val failed = ProviderReadinessSnapshot(ProviderState.PROVIDER_ERROR,
                ProviderFailureCode.NETWORK_TIMEOUT, "Bağlantı testinin toplam süre sınırı aşıldı.",
                testedAt = System.currentTimeMillis())
            var published = false
            probes.finish(ticket) { published = settings.ifConnectionCurrent(config) { persist(failed, config.apiKey) } }
            return if (published) failed else localConfigState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failed = ProviderReadinessSnapshot(ProviderState.PROVIDER_ERROR,
                ProviderFailureCode.UNKNOWN_ERROR,
                BackendHttpDiagnostic.safeText(e.message ?: "Bağlantı testi tamamlanamadı.", config.apiKey),
                testedAt = System.currentTimeMillis())
            var published = false
            probes.finish(ticket) { published = settings.ifConnectionCurrent(config) { persist(failed, config.apiKey) } }
            return if (published) failed else localConfigState()
        } finally {
            probes.finish(ticket) {
                settings.ifConnectionCurrent(config) {
                    persist(ProviderReadinessSnapshot(ProviderState.PROVIDER_CONFIGURED,
                        message = "Bağlantı testi iptal edildi; yeniden test edebilirsiniz."), config.apiKey)
                }
            }
        }
    }

    private fun persist(snapshot: ProviderReadinessSnapshot, secret: String) {
        settings.lastProviderState = snapshot.state.name
        settings.lastProviderFailureCode = snapshot.failureCode.name
        settings.lastProviderMessage = BackendHttpDiagnostic.safeText(snapshot.message, secret)
        settings.lastProviderHttpEvidence = snapshot.connection.encode()
        settings.lastBackendHealthAt = snapshot.testedAt
        settings.lastBackendHealthOk = snapshot.state == ProviderState.PROVIDER_READY
        settings.lastViopQuoteReady = snapshot.viopQuoteOk && snapshot.state == ProviderState.PROVIDER_READY
        settings.lastViopAnalysisReady = snapshot.viopHistoryOk && snapshot.state == ProviderState.PROVIDER_READY
    }

    companion object {
        const val READY_TTL_MS: Long = 5 * 60_000L

        private val probes = ProviderProbeGate()
        private const val TEST_TIMEOUT_MS = 90_000L

        fun isValidHttps(raw: String): Boolean = BackendUrlPolicy.isValid(raw)

        private fun mapFailure(kind: BackendPreflightClient.FailureKind): ProviderFailureCode = when (kind) {
            BackendPreflightClient.FailureKind.NONE -> ProviderFailureCode.NONE
            BackendPreflightClient.FailureKind.BACKEND_NOT_CONFIGURED -> ProviderFailureCode.BACKEND_URL_MISSING
            BackendPreflightClient.FailureKind.API_KEY_MISSING -> ProviderFailureCode.API_KEY_MISSING
            BackendPreflightClient.FailureKind.HTTPS_REQUIRED,
            BackendPreflightClient.FailureKind.INVALID_URL -> ProviderFailureCode.INVALID_HTTPS
            BackendPreflightClient.FailureKind.TLS_ERROR -> ProviderFailureCode.TLS_ERROR
            BackendPreflightClient.FailureKind.AUTH_ERROR -> ProviderFailureCode.AUTH_ERROR
            BackendPreflightClient.FailureKind.SYMBOLS_ERROR -> ProviderFailureCode.BIST_SYMBOLS_ERROR
            BackendPreflightClient.FailureKind.QUOTE_ERROR -> ProviderFailureCode.BIST_QUOTE_ERROR
            BackendPreflightClient.FailureKind.HISTORY_ERROR -> ProviderFailureCode.BIST_HISTORY_ERROR
            BackendPreflightClient.FailureKind.VIOP_CONTRACTS_ERROR -> ProviderFailureCode.VIOP_CONTRACTS_ERROR
            BackendPreflightClient.FailureKind.VIOP_QUOTE_ERROR -> ProviderFailureCode.VIOP_QUOTE_ERROR
            BackendPreflightClient.FailureKind.VIOP_HISTORY_ERROR -> ProviderFailureCode.VIOP_HISTORY_ERROR
            BackendPreflightClient.FailureKind.STALE_DATA -> ProviderFailureCode.STALE_DATA
            BackendPreflightClient.FailureKind.EMPTY_DATA -> ProviderFailureCode.EMPTY_DATA
            BackendPreflightClient.FailureKind.NETWORK_TIMEOUT -> ProviderFailureCode.NETWORK_TIMEOUT
            BackendPreflightClient.FailureKind.DNS_ERROR -> ProviderFailureCode.DNS_ERROR
            BackendPreflightClient.FailureKind.RATE_LIMIT -> ProviderFailureCode.RATE_LIMIT
            BackendPreflightClient.FailureKind.API_ROUTE_NOT_FOUND -> ProviderFailureCode.API_ROUTE_NOT_FOUND
            BackendPreflightClient.FailureKind.DATA_SOURCE_NOT_CONNECTED -> ProviderFailureCode.DATA_SOURCE_NOT_CONNECTED
            BackendPreflightClient.FailureKind.HTTP_ERROR,
            BackendPreflightClient.FailureKind.SERVER_ERROR -> ProviderFailureCode.SERVER_ERROR
            else -> ProviderFailureCode.UNKNOWN_ERROR
        }
    }
}
