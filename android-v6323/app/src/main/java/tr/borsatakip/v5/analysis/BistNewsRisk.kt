package tr.borsatakip.v5.analysis

/** Provider-reviewed point-in-time coverage, never inferred from absent headlines. */
data class BistNewsRisk(
    val symbol: String = "",
    val state: String = "UNKNOWN",
    val coverageThrough: Long = 0L,
    val availableAt: Long = 0L,
    val source: String = "",
    val verified: Boolean = false
) {
    fun knownFor(requested: String, asOf: Long): Boolean = verified && source.isNotBlank() &&
        symbol == requested && asOf > 0L && coverageThrough >= asOf &&
        coverageThrough <= asOf + 15_000L && availableAt in 1L..asOf && state in setOf("CLEAR", "CRITICAL")
}
