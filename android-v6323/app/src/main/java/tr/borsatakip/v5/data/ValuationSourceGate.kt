package tr.borsatakip.v5.data

/** Only selects a data path. It never claims that financial statements are verified. */
object ValuationSourceGate {
    enum class Mode { LOCAL_JSON, CONFIGURED_HTTPS, MISSING_FINANCIAL_SOURCE, MISSING_API_KEY }

    fun decide(hasLocalJson: Boolean, baseUrl: String, apiKeyPresent: Boolean): Mode = when {
        hasLocalJson -> Mode.LOCAL_JSON
        !BackendUrlPolicy.isValid(baseUrl) -> Mode.MISSING_FINANCIAL_SOURCE
        !apiKeyPresent -> Mode.MISSING_API_KEY
        else -> Mode.CONFIGURED_HTTPS
    }

    fun canCalculate(mode: Mode) = mode == Mode.LOCAL_JSON || mode == Mode.CONFIGURED_HTTPS
}
