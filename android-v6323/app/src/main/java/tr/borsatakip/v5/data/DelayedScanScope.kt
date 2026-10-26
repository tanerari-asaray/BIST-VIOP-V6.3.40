package tr.borsatakip.v5.data

/** The experimental scan never presents a subset as the full BIST universe. */
object DelayedScanScope {
    const val DEFAULT_MAX_SYMBOLS = Int.MAX_VALUE
    private val SYMBOL = Regex("[A-Z0-9]{3,10}")

    /** Prefer user-imported symbols, then local cache. No remote symbol scraping. */
    fun select(userSymbols: List<String>, cachedSymbols: List<String>, limit: Int = DEFAULT_MAX_SYMBOLS): List<String> {
        val max = limit.coerceAtLeast(1)
        return (userSymbols + cachedSymbols).asSequence()
            .map { it.trim().uppercase(java.util.Locale.ROOT).removeSuffix(".IS") }
            .filter { SYMBOL.matches(it) }
            .distinct()
            .take(max)
            .toList()
    }
}
