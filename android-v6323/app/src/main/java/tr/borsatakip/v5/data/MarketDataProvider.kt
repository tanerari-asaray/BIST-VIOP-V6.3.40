package tr.borsatakip.v5.data

import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

/** Bir tarama turunda veri alınamayan ve hata veren sembolleri birbirinden ayırır. */
data class ProviderScanDiagnostics(
    val noDataSymbols: List<String> = emptyList(),
    val errorSymbols: List<String> = emptyList(),
    val messages: Map<String, String> = emptyMap(),
    /** Controlled codes only; raw URLs, tokens and exception messages are not exported. */
    val reasonCodes: Map<String, String> = emptyMap()
) {
    val noDataCount: Int get() = noDataSymbols.distinct().size
    val errorCount: Int get() = errorSymbols.distinct().size
}

interface ScanDiagnosticsSource {
    fun scanDiagnostics(): ProviderScanDiagnostics
}

/** Selected-period fallback scanning must not depend on 220 daily bootstrap bars. */
interface DelayedIntradayScanSource {
    fun useDirectDelayedIntradayScan(): Boolean
}

/** Veri sağlayıcılarından bağımsız ortak sözleşme. */
interface MarketDataProvider {
    val id: String
    val displayName: String

    /** MTF cache kimliği gerçek veri kaynağını/origin'i ayırmalıdır. */
    fun mtfCacheSourceKey(): String = id
    suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock>
    suspend fun fetchOne(symbol: String): Stock?
    suspend fun listSymbols(): List<String> = emptyList()
    suspend fun fetchNewsRisk(symbol: String, asOf: Long): tr.borsatakip.v5.analysis.BistNewsRisk =
        tr.borsatakip.v5.analysis.BistNewsRisk()


    /**
     * Forward performans doğrulaması ve timeframe taraması için zaman pencereli gerçek tarihsel veri.
     * Sağlayıcı bu yeteneği desteklemiyorsa boş liste döndürür; güncel quote tarihsel fiyat gibi kullanılmaz.
     */
    suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int = 1): List<Candle> = emptyList()

    /** Grafik/tarama için gerçek günlük geçmiş. 1 GÜN seçimi bu yolu kullanır; 240 DK kullanılmaz. */
    suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean = false): List<Candle> =
        fetchOne(symbol)?.candles ?: emptyList()
}
