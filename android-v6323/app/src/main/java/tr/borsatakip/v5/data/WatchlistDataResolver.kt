package tr.borsatakip.v5.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tr.borsatakip.v5.model.Stock

/**
 * UI takip/favori ekranı için veri çözücü.
 * Tarama motorunun production-only politikasını değiştirmez.
 * Production quote yoksa yalnız görüntüleme amacıyla açıkça gecikmeli Yahoo yedeğine düşer.
 */
class WatchlistDataResolver(context: Context) {
    private val settings = SettingsStore(context)
    private val strict = ProviderRouter(context, experimentalFallbackOverride = false)
    private val delayed = YahooFallbackProvider(context)

    data class SymbolUniverse(val symbols: List<String>, val sourceLabel: String)
    data class FetchResult(val stock: Stock?, val error: String? = null)

    suspend fun symbolUniverse(): SymbolUniverse {
        val merged = linkedSetOf<String>()
        settings.cachedBistSymbols.mapTo(merged) { it.trim().uppercase() }
        var source = if (merged.isNotEmpty()) "Önbellek" else ""

        val production = try { strict.listSymbols() } catch (ce: CancellationException) { throw ce } catch (_: Throwable) { emptyList() }
        if (production.isNotEmpty()) {
            merged.addAll(production.map { it.trim().uppercase() })
            source = "Production"
        }

        if (production.isEmpty()) {
            val fallback = try { delayed.listSymbols() } catch (ce: CancellationException) { throw ce } catch (_: Throwable) { emptyList() }
            if (fallback.isNotEmpty()) {
                merged.addAll(fallback.map { it.trim().uppercase() })
                source = "Yedek sembol dizini"
            }
        }

        merged.addAll(BistBootstrapCatalog.symbols)
        if (source.isBlank()) source = "Yerleşik başlangıç dizini"
        return SymbolUniverse(
            merged.filter { it.matches(Regex("[A-Z0-9_]{3,12}")) }.distinct().sorted(),
            source
        )
    }

    suspend fun fetchDisplayStock(symbol: String): Stock? = fetchDisplayStockResult(symbol).stock

    /**
     * Takip listesinde aynı anda çok sayıda sembol istendiğinde provider'a kontrolsüz fan-out yapmaz.
     * Production ve Yahoo hata nedenlerini de UI'ya taşır; tüm hatalar artık yalnızca "Veri yok" olmaz.
     */
    suspend fun fetchDisplayStockResult(symbol: String): FetchResult = DISPLAY_REQUEST_LIMIT.withPermit {
        val normalized = symbol.trim().uppercase()
        if (!normalized.matches(Regex("[A-Z0-9_]{3,12}"))) return@withPermit FetchResult(null, "Geçersiz sembol")

        var primaryError: String? = null
        if (BackendUrlPolicy.isValid(settings.baseUrl)) {
            val primary = try {
                strict.fetchOne(normalized)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                primaryError = "Production: ${t.message ?: t.javaClass.simpleName}"
                null
            }
            if (primary != null) return@withPermit FetchResult(primary)
            if (primaryError == null) primaryError = "Production: veri dönmedi"
        } else {
            primaryError = "Production provider yapılandırılmamış"
        }

        val fallback = try {
            delayed.fetchDisplayQuoteOnly(normalized)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            YahooFallbackProvider.DisplayQuoteResult(null, "Yahoo: ${t.message ?: t.javaClass.simpleName}")
        }
        if (fallback.stock != null) return@withPermit FetchResult(fallback.stock)
        val delayedError = fallback.error?.let { "Yahoo: $it" }
        FetchResult(null, listOfNotNull(primaryError, delayedError).joinToString(" • "))
    }

    companion object {
        /** 20 satırlık takip ekranında aynı anda en fazla 4 provider isteği. */
        private val DISPLAY_REQUEST_LIMIT = Semaphore(4)
    }
}
