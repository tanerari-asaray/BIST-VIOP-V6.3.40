package tr.borsatakip.v5.data

/** Read only known gateway error codes and never display arbitrary server response bodies. */
internal object BackendUnavailableDiagnostic {
    private val codePattern = Regex("\"code\"\\s*:\\s*\"([A-Z0-9_]{3,96})\"")
    private val recognized = setOf(
        "LICENSED_VIOP_PROVIDER_NOT_CONNECTED",
        "LICENSED_BIST_PROVIDER_NOT_CONNECTED",
        "FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED"
    )
    fun extractCode(body: String): String? {
        if (body.length > 4096 || !body.trimStart().startsWith("{")) return null
        val code = codePattern.find(body)?.groupValues?.getOrNull(1) ?: return null
        return code.takeIf { it in recognized }
    }
    fun describe(status: Int, path: String, body: String): String? {
        if (status != 503 || !path.startsWith("/v1/")) return null
        val code = extractCode(body) ?: return null
        return when (code) {
            "LICENSED_VIOP_PROVIDER_NOT_CONNECTED" ->
                "DATA_SOURCE_NOT_CONNECTED • HTTP 503 • $path: Railway sunucusu yanıtlıyor, ancak lisanslı VİOP veri sağlayıcısı bağlı değil. " +
                "Sunucuda gerçek yetkili fiyat/sözleşme adapteri kurulmalı; tarama ve canlı sinyal kapalı tutuldu."
            "LICENSED_BIST_PROVIDER_NOT_CONNECTED" ->
                "DATA_SOURCE_NOT_CONNECTED • HTTP 503 • $path: Lisanslı BIST veri sağlayıcısı bağlı değil. " +
                "Sunucuda yetkili fiyat/geçmiş kaynağı gerekir; canlı tarama doğrulanmadı."
            "FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED" ->
                "DATA_SOURCE_NOT_CONNECTED • HTTP 503 • $path: Finansal tablo kaynağı bağlı değil. " +
                "Sunucuya yetkili gerçek KAP/bilanço kaynağı bağlanmalı; değerleme yapılmadı."
            else -> null
        }
    }
}
