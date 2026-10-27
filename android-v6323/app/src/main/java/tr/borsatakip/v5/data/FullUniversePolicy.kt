package tr.borsatakip.v5.data

import java.util.Locale

/** Provider-discovered universe is not proof of exchange-wide completeness. */
object FullUniversePolicy {
    enum class Market { BIST, VIOP }
    enum class Trend { UP, DOWN, FLAT, UNKNOWN }
    enum class Quality { VERIFIED, DELAYED, UNVERIFIED, NO_DATA }
    enum class State { IDLE, RUNNING, PARTIAL, LOCAL_LIST_FINISHED, FINISHED_UNVERIFIED, FAILED, CANCELLED }

    data class Entry(
        val market: Market,
        val symbol: String,
        val subtitle: String,
        val price: Double?,
        val currency: String?,
        val trend: Trend,
        val quality: Quality,
        val source: String,
        val dataTimeMs: Long,
        val receivedMs: Long,
        val detail: String,
        val closes: List<Double> = emptyList(),
        val reasonCode: String? = null,
        val candles: List<tr.borsatakip.v5.model.Candle> = emptyList(),
        val changePct: Double? = null,
        val financialNote: String = "Temel veri yok; göreli iskonto hesaplanamadı.",
        val relativeDiscountPct: Double? = null,
        val durationMs: Long = 0L,
        val attempts: Int = 1,
        val attemptErrors: List<String> = emptyList(),
        val financialStatus: String = "NOT_EVALUATED"
    )

    /** Stage-specific observations; a quote is not proof that history or valuation succeeded. */
    data class StageCounters(
        val priceOk: Int, val historyOk: Int, val technicalOk: Int,
        val financialAssessed: Int, val discounted: Int, val errors: Int,
        val financialMissing: Int, val financialRisk: Int, val financialInvalid: Int
    )

    fun stages(entries: List<Entry>): StageCounters = StageCounters(
        priceOk = entries.count { it.price?.let { p -> p.isFinite() && p > 0.0 } == true },
        historyOk = entries.count { it.candles.isNotEmpty() },
        technicalOk = entries.count { it.trend != Trend.UNKNOWN && it.candles.size >= 10 },
        financialAssessed = entries.count { it.market == Market.BIST && it.financialStatus in setOf("RELATIVE_DISCOUNT", "NOT_DISCOUNT", "FINANCIAL_RISK", "INSUFFICIENT_PEERS") },
        discounted = entries.count { it.market == Market.BIST && it.financialStatus == "RELATIVE_DISCOUNT" &&
            it.relativeDiscountPct?.isFinite() == true && it.relativeDiscountPct > 0.0 },
        errors = entries.count { it.reasonCode != null },
        financialMissing = entries.count { it.market == Market.BIST && it.financialStatus in setOf("NOT_EVALUATED", "NO_FINANCIALS") },
        financialRisk = entries.count { it.market == Market.BIST && it.financialStatus == "FINANCIAL_RISK" },
        financialInvalid = entries.count { it.market == Market.BIST && it.financialStatus == "INVALID_OR_STALE" }
    )

    data class Counters(
        val discoveredBist: Int = 0,
        val discoveredViop: Int = 0,
        val triedBist: Int = 0,
        val triedViop: Int = 0,
        val successfulBist: Int = 0,
        val successfulViop: Int = 0,
        val noDataBist: Int = 0,
        val noDataViop: Int = 0,
        val errorsBist: Int = 0,
        val errorsViop: Int = 0,
        val skippedBist: Int = 0,
        val skippedViop: Int = 0,
        val bistDiscoveryOk: Boolean = false,
        val viopDiscoveryOk: Boolean = false
    ) {
        val tried: Int get() = triedBist + triedViop
        val expected: Int get() = discoveredBist + discoveredViop
        /** Attempted provider records != verified complete exchange coverage. */
        val attemptedDiscoveredUniverse: Boolean
            get() = bistDiscoveryOk && viopDiscoveryOk &&
                triedBist + skippedBist == discoveredBist &&
                triedViop + skippedViop == discoveredViop
        val bistListComplete: Boolean get() = discoveredBist > 0 && triedBist == discoveredBist &&
            successfulBist == discoveredBist && errorsBist == 0 && noDataBist == 0
        val failures: Int get() = errorsBist + errorsViop + noDataBist + noDataViop
        val canClaimExchangeComplete: Boolean get() = false
        fun valid(): Boolean = listOf(
            discoveredBist, discoveredViop, triedBist, triedViop,
            successfulBist, successfulViop, noDataBist, noDataViop,
            errorsBist, errorsViop, skippedBist, skippedViop
        ).all { it >= 0 } &&
            triedBist + skippedBist <= discoveredBist &&
            triedViop + skippedViop <= discoveredViop &&
            successfulBist + noDataBist + errorsBist <= triedBist &&
            successfulViop + noDataViop + errorsViop <= triedViop
    }

    fun normalizeSymbols(raw: List<String>): List<String> =
        raw.asSequence().map { it.trim().uppercase(Locale.ROOT).removeSuffix(".IS") }
            .filter { it.matches(Regex("[A-Z0-9]{3,10}")) }
            .distinct().sorted().toList()

    /** Trend is informational only; 10 observed closes are mandatory. */
    fun trend(closes: List<Double>): Trend {
        val valid = closes.filter { it.isFinite() && it > 0.0 }
        if (valid.size < 10) return Trend.UNKNOWN
        val sma10 = valid.takeLast(10).average()
        val p = valid.last()
        return when {
            p > sma10 * 1.002 -> Trend.UP
            p < sma10 * 0.998 -> Trend.DOWN
            else -> Trend.FLAT
        }
    }

    /** No BIST vs VIOP global score comparison, no nominal cheapness. */
    fun visible(entries: List<Entry>, market: Market?, query: String): List<Entry> {
        val q = query.trim().uppercase(Locale.ROOT)
        return entries.asSequence()
            .filter { market == null || it.market == market }
            .filter { q.isBlank() || it.symbol.contains(q) || it.subtitle.uppercase(Locale.ROOT).contains(q) }
            .sortedWith(compareBy<Entry> { it.market.ordinal }
                .thenBy { if (it.price == null || !it.price.isFinite() || it.price <= 0.0) Double.POSITIVE_INFINITY else it.price }
                .thenBy { it.symbol })
            .toList()
    }

    fun summary(counts: Counters, state: State): String {
        val evidence = when {
            state == State.LOCAL_LIST_FINISHED -> "YEREL/GEÇİKMELİ LİSTE TAMAMLANDI (borsa evreni doğrulanmadı)"
            counts.attemptedDiscoveredUniverse -> "Sağlayıcının keşfettiği listeler denendi"
            else -> "KISMİ / KEŞİF TAMAMLANMADI"
        }
        return "Durum: " + state + " • " + evidence +
            " • BIST fiyat kapsamı: " + (if (counts.bistListComplete) "LİSTE TAMAMLANDI" else "KISMİ") +
            " • BIST " + counts.triedBist + "/" + counts.discoveredBist +
            " • VİOP " + counts.triedViop + "/" + counts.discoveredViop +
            " • başarılı " + (counts.successfulBist + counts.successfulViop) +
            " • veri yok " + (counts.noDataBist + counts.noDataViop) +
            " • hata " + (counts.errorsBist + counts.errorsViop) +
            " • TAM BORSA KAPSAMI DOĞRULANMADI"
    }
}
