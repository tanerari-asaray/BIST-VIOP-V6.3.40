package tr.borsatakip.v5.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

/**
 * Üretim yönlendiricisi: ana kaynak her zaman yapılandırılmış HTTPS backend'dir.
 * Yahoo yalnızca kullanıcı deneysel sağlayıcıları ve Yahoo yedeğini açıkça etkinleştirirse çalışır.
 */
class ProviderRouter(
    context: Context,
    private val experimentalFallbackOverride: Boolean? = null,
    private val connectionOverride: BackendConnectionConfig? = null
) : MarketDataProvider, ScanDiagnosticsSource, DelayedIntradayScanSource {
    companion object {
        /** Process-wide generation prevents primary→fallback→primary ABA cache attribution races. */
        private val mtfSourceGeneration = AtomicLong(0L)
    }
    private val settings = SettingsStore(context)
    private val primary = MobileMarketDataProvider(context, connectionOverride)
    private val fallback = YahooFallbackProvider(context)
    @Volatile private var diagnostics = ProviderScanDiagnostics()

    override suspend fun fetchNewsRisk(symbol: String, asOf: Long) = primary.fetchNewsRisk(symbol, asOf)

    override fun scanDiagnostics(): ProviderScanDiagnostics = diagnostics

    override fun useDirectDelayedIntradayScan(): Boolean =
        !BackendUrlPolicy.isValid((connectionOverride ?: settings.connectionSnapshot()).baseUrl) && allowExperimentalFallback()

    override val id: String get() = "provider_router"
    override val displayName: String get() = settings.lastProviderLabel
    override fun mtfCacheSourceKey(): String = buildString {
        append("provider_router|")
        append(settings.lastProviderId.ifBlank { "none" })
        append('|')
        append((connectionOverride ?: settings.connectionSnapshot()).sourceIdentity.ifBlank { "unconfigured" })
        append("|fallback=")
        append(allowExperimentalFallback())
        append("|gen=")
        append(mtfSourceGeneration.get())
    }

    override suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> {
        diagnostics = ProviderScanDiagnostics()
        if (!BackendUrlPolicy.isValid((connectionOverride ?: settings.connectionSnapshot()).baseUrl)) {
            if (!allowExperimentalFallback()) {
                throw IllegalStateException(
                    "Üretim veri sağlayıcısı yapılandırılmamış. Ayarlar'da gerçek HTTPS backend tanımlayın veya yalnız test için deneysel sağlayıcı modunu açın."
                )
            }
            return fallbackScan(onProgress)
        }

        var primaryFailure: Throwable? = null
        val primaryResult = try {
            primary.scan(onProgress)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            primaryFailure = t
            emptyList()
        }
        diagnostics = primary.scanDiagnostics()
        if (primaryResult.isNotEmpty()) {
            mark(primary.id, primaryResult.firstOrNull()?.source ?: primary.displayName)
            return primaryResult
        }

        if (!allowExperimentalFallback()) {
            if (primaryFailure is ProviderException) throw primaryFailure
            val detail = primaryFailure?.message?.takeIf { it.isNotBlank() }
                ?: "Üretim backend'i veri döndürmedi."
            throw IllegalStateException("$detail Deneysel fallback kapalı.", primaryFailure)
        }

        return try {
            fallbackScan(onProgress)
        } catch (ce: CancellationException) {
            throw ce
        } catch (fallbackFailure: Throwable) {
            val primaryDetail = primaryFailure?.message?.takeIf { it.isNotBlank() }
            val fallbackDetail = fallbackFailure.message?.takeIf { it.isNotBlank() }
                ?: "Deneysel fallback başarısız."
            val combined = buildString {
                if (primaryDetail != null) append("Production provider: $primaryDetail ")
                append("Deneysel provider: $fallbackDetail")
            }
            throw IllegalStateException(combined, fallbackFailure)
        }
    }

    override suspend fun fetchOne(symbol: String): Stock? {
        if (BackendUrlPolicy.isValid((connectionOverride ?: settings.connectionSnapshot()).baseUrl)) {
            val primaryResult = try {
                primary.fetchOne(symbol)
            } catch (ce: CancellationException) {
                throw ce
            } catch (pe: ProviderException) {
                if (!allowExperimentalFallback()) throw pe
                null
            } catch (_: Throwable) {
                null
            }
            if (primaryResult != null) {
                mark(primary.id, primaryResult.source)
                return primaryResult
            }
        }

        if (!allowExperimentalFallback()) return null
        val fallbackResult = fallback.fetchOne(symbol)
        if (fallbackResult != null) mark(fallback.id, fallback.displayName)
        return fallbackResult
    }

    override suspend fun listSymbols(): List<String> {
        if (BackendUrlPolicy.isValid((connectionOverride ?: settings.connectionSnapshot()).baseUrl)) {
            try {
                val primarySymbols = primary.listSymbols()
                if (primarySymbols.isEmpty()) {
                    throw ProviderException(ProviderFailureCode.BIST_SYMBOLS_ERROR, "BIST HİSSE LİSTESİ ALINAMADI.")
                }
                return primarySymbols
            } catch (ce: CancellationException) {
                throw ce
            } catch (pe: ProviderException) {
                if (!allowExperimentalFallback()) throw pe
            } catch (t: Throwable) {
                if (!allowExperimentalFallback()) {
                    throw ProviderException(ProviderFailureCode.BIST_SYMBOLS_ERROR, "BIST HİSSE LİSTESİ ALINAMADI: ${t.message ?: t.javaClass.simpleName}", t)
                }
            }
        }
        if (!allowExperimentalFallback()) {
            throw ProviderException(ProviderFailureCode.BIST_SYMBOLS_ERROR, "BIST HİSSE LİSTESİ ALINAMADI. Production provider hazır değil.")
        }
        val fallbackSymbols = fallback.listSymbols()
        if (fallbackSymbols.isEmpty()) throw ProviderException(ProviderFailureCode.BIST_SYMBOLS_ERROR, "Deneysel BIST sembol evreni alınamadı.")
        return fallbackSymbols
    }

    override suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int): List<Candle> {
        if (BackendUrlPolicy.isValid((connectionOverride ?: settings.connectionSnapshot()).baseUrl)) {
            try {
                val primaryResult = primary.fetchHistory(symbol, fromTime, toTime, intervalMinutes)
                if (primaryResult.isNotEmpty()) {
                    mark(primary.id, primary.displayName)
                    return primaryResult
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (pe: ProviderException) {
                if (!allowExperimentalFallback()) throw pe
            }
        }
        if (!allowExperimentalFallback()) return emptyList()
        val fallbackResult = fallback.fetchHistory(symbol, fromTime, toTime, intervalMinutes)
        if (fallbackResult.isNotEmpty()) mark(fallback.id, "${fallback.displayName} • DENEYSEL/YEDEK")
        return fallbackResult
    }

    override suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean): List<Candle> {
        if (BackendUrlPolicy.isValid((connectionOverride ?: settings.connectionSnapshot()).baseUrl)) {
            try {
                val primaryResult = primary.fetchDailyHistory(symbol, maximumRange)
                if (primaryResult.isNotEmpty()) {
                    mark(primary.id, primary.displayName)
                    return primaryResult
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (pe: ProviderException) {
                if (!allowExperimentalFallback()) throw pe
            }
        }
        if (!allowExperimentalFallback()) return emptyList()
        val fallbackResult = fallback.fetchDailyHistory(symbol, maximumRange)
        if (fallbackResult.isNotEmpty()) mark(fallback.id, "${fallback.displayName} • DENEYSEL/YEDEK")
        return fallbackResult
    }

    private suspend fun fallbackScan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> {
        val fallbackResult = try {
            fallback.scan(onProgress)
        } finally {
            diagnostics = fallback.scanDiagnostics()
        }
        if (fallbackResult.isEmpty()) {
            throw IllegalStateException("Deneysel Yahoo yedeğinden kullanılabilir OHLCV verisi alınamadı.")
        }
        mark(fallback.id, "${fallback.displayName} • DENEYSEL/YEDEK")
        return fallbackResult
    }

    private fun allowExperimentalFallback(): Boolean =
        experimentalFallbackOverride ?: (settings.experimentalProvidersEnabled && settings.yahooFallbackEnabled)

    private fun mark(providerId: String, label: String) {
        if (settings.lastProviderId != providerId) {
            mtfSourceGeneration.incrementAndGet()
            MtfHistoryCache.clear()
        }
        settings.lastProviderId = providerId
        settings.lastProviderLabel = label
        settings.lastProviderTimestamp = System.currentTimeMillis()
    }
}
