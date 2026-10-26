package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.ViopContract
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Browsing is independent of signal eligibility. No nearest-expiry or suggestion limit. */
object ViopAnalysisCatalog {
    enum class Kind { ALL, FUTURE, OPTION }

    fun available(source: List<ViopContract>, nowMs: Long = System.currentTimeMillis()): List<ViopContract> {
        val month = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(ZoneId.of("Europe/Istanbul")))
        return source.asSequence()
            .filterNot { it.isManual }
            .filter { it.symbol.uppercase(Locale.ROOT).matches(Regex("[FO]_[A-Z0-9_.:-]+")) }
            .filterNot { c ->
                (c.lastTradingAt?.let { it <= nowMs } == true) ||
                    (c.expiryAt?.let { it <= nowMs } == true) ||
                    runCatching { YearMonth.parse(c.expiry) < month }.getOrDefault(false)
            }
            .distinctBy { it.symbol.uppercase(Locale.ROOT) }
            .sortedWith(compareBy<ViopContract> { ViopContractSearch.normalize(it.underlying) }
                .thenBy { it.expiry }.thenBy { it.symbol })
            .toList()
    }

    fun filter(source: List<ViopContract>, query: String, kind: Kind, expiry: String?): List<ViopContract> {
        val tokens = ViopContractSearch.normalize(query).split(' ').filter { it.isNotBlank() }
        return source.filter { c ->
            (kind == Kind.ALL || c.symbol.startsWith(if (kind == Kind.FUTURE) "F_" else "O_", true)) &&
                (expiry == null || c.expiry == expiry) &&
                tokens.all { token -> ViopContractSearch.normalize(
                    "${c.symbol} ${c.underlying} ${ViopContractSearch.displayUnderlying(c)} ${c.expiry} ${c.contractType}"
                ).contains(token) }
        }
    }
}
