package tr.borsatakip.v5.data

data class StockQuoteFailureGroup(val code: String, val symbols: List<String>, val httpEvidence: BackendHttpEvidence?)

/** Local setup errors are distinct from an HTTP response and from an empty quote. */
object StockQuoteRefreshDiagnostics {
    fun configurationFailure(connection: BackendConnectionConfig): String? = when {
        connection.baseUrl.isBlank() -> "BACKEND_URL_MISSING"
        !BackendUrlPolicy.isValid(connection.baseUrl) -> "INVALID_HTTPS"
        connection.apiKey.isBlank() -> "API_KEY_MISSING"
        else -> null
    }

    private fun safeCode(code: String): String = code.takeIf {
        it in setOf("BACKEND_URL_MISSING", "INVALID_HTTPS", "API_KEY_MISSING", "NO_QUOTE_DATA",
            "AUTH_ERROR", "RATE_LIMIT", "TIMEOUT", "SCHEMA_ERROR", "NETWORK_OR_PROVIDER_ERROR", "HTTP_ERROR") ||
            Regex("HTTP_[1-5][0-9]{2}").matches(it) || BackendHttpDiagnostic.allowedCode(it)
    } ?: "NETWORK_OR_PROVIDER_ERROR"

    /** Group a shared cause once, keeping counts and HTTP stages without per-symbol URL noise. */
    fun groups(result: StockQuoteRefreshResult): List<StockQuoteFailureGroup> = result.issues.entries
        .groupBy { (symbol, code) ->
            safeCode(code) to result.httpFailures[symbol]?.let {
                BackendHttpEvidence.decode(it.encode()).copy(endpoint = "")
            }
        }.map { (cause, entries) -> StockQuoteFailureGroup(cause.first, entries.map { it.key }.sorted(), cause.second) }
        .sortedWith(compareBy({ it.code }, { it.httpEvidence?.httpStatus ?: 0 }))
}
