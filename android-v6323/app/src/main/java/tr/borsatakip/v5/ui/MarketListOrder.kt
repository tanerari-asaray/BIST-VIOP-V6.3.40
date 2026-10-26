package tr.borsatakip.v5.ui

/** Only orders the visible list; never changes scan ranking, eligibility or signal decisions. */
internal enum class MarketListOrder(val label: String) {
    SOURCE("Tarama sırası"), SYMBOL("Sembol A–Z"), CHANGE("Günlük değişim ↓"), PRICE_ASC("En ucuz fiyat ↑");

    fun <T> apply(items: List<T>, symbol: (T) -> String, change: (T) -> Double?, price: (T) -> Double? = { null }): List<T> = when (this) {
        SOURCE -> items
        SYMBOL -> items.sortedBy { symbol(it) }
        CHANGE -> items.sortedWith(compareByDescending<T> { change(it)?.takeIf(Double::isFinite) }
            .thenBy { symbol(it) })
        PRICE_ASC -> items.sortedWith(compareBy<T> { price(it)?.takeIf { v -> v.isFinite() && v > 0.0 } ?: Double.POSITIVE_INFINITY }
            .thenBy { symbol(it) })
    }
}
