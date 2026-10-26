package tr.borsatakip.v5.data

import java.time.Instant
import java.time.ZoneId

data class KapStockDirectory(val names: Map<String, String>, val bist30: Set<String>, val fetchedAt: Long,
    val cached: Boolean = false, val memberships: Map<String, Set<String>> = emptyMap()) {
    fun usable(now: Long = System.currentTimeMillis()): Boolean = valid() && now >= fetchedAt &&
        now - fetchedAt <= 3_600_000L &&
        Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Istanbul")).toLocalDate() ==
        Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.of("Europe/Istanbul")).toLocalDate()
    fun valid(): Boolean = fetchedAt >= 1_000_000_000_000L && bist30.size == 30 && names.size in 30..10000 &&
        names.all { (code, name) -> code.matches(Regex("[A-Z0-9]{2,12}")) && name.isNotBlank() && name.length <= 300 } &&
        bist30.all { it in names } && memberships.all { (code, members) ->
            code in setOf("XUBNK", "XUSIN", "XUTEK") && members.isNotEmpty() && members.all { it in names }
        }
}

data class StockIndexQuote(val symbol: String, val price: Double, val changePct: Double?,
    val exchangeTimestamp: Long, val source: String, val realtime: Boolean = false) {
    fun live(now: Long = System.currentTimeMillis()): Boolean = realtime && now - exchangeTimestamp in 0L..60_000L
    fun valid(expected: String, now: Long = System.currentTimeMillis()): Boolean = symbol == expected &&
        price.isFinite() && price > 0 && exchangeTimestamp >= 1_000_000_000_000L &&
        exchangeTimestamp <= now + 60_000 && source.isNotBlank() && (changePct == null || changePct.isFinite())
}

data class StocksReferences(val directory: KapStockDirectory? = null,
    val indices: Map<String, StockIndexQuote> = emptyMap(), val message: String = "Üyelik ve endeks verisi henüz alınmadı",
    val issues: Map<String, String> = emptyMap())

data class StockQuoteRefreshResult(val quotes: Map<String, tr.borsatakip.v5.model.DisplayQuoteSnapshot>,
                                 val issues: Map<String, String>,
                                 val httpFailures: Map<String, BackendHttpEvidence> = emptyMap())
