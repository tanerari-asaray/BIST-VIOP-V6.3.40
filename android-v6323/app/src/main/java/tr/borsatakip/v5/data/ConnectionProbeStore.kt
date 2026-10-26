package tr.borsatakip.v5.data

import android.content.Context
import org.json.JSONObject

/** Diagnostic observations are kept per market; they never authorize production scans or trades. */
class ConnectionProbeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(market: String, result: BackendPreflightClient.Result, secret: String) {
        require(market == "BIST" || market == "VIOP")
        val e = result.connection
        val json = JSONObject().apply {
            put("market", market)
            put("status", if (result.ok) "CHECK_PASSED" else "CHECK_FAILED")
            put("lastTestEpochMs", System.currentTimeMillis())
            put("error", result.failureKind.name)
            put("message", BackendHttpDiagnostic.safeText(result.message, secret))
            put("httpStatus", e.httpStatus ?: JSONObject.NULL)
            put("endpoint", e.endpoint)
            put("providerCode", e.providerCode)
            put("failureStage", e.failureStage)
            put("upstreamHttpStatus", e.upstreamHttpStatus ?: JSONObject.NULL)
            put("gatewayAuthenticated", e.gatewayAuthenticated ?: JSONObject.NULL)
            put("health", result.healthOk)
            put("dataAuthentication", result.authOk)
            put("symbols", result.symbolsOk)
            put("quote", if (market == "BIST") result.quoteOk else result.viopQuoteOk)
            put("history", if (market == "BIST") result.historyOk else result.viopHistoryOk)
            put("contracts", result.viopContractsOk)
        }
        prefs.edit().putString(market, json.toString()).apply()
    }

    fun read(market: String): JSONObject = runCatching {
        JSONObject(prefs.getString(market, "").orEmpty())
    }.getOrElse { JSONObject().put("market", market).put("status", "NOT_TESTED").put("lastTestEpochMs", 0L) }

    companion object {
        private const val PREFS = "provider_connection_probes_v1"
        fun clear(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
