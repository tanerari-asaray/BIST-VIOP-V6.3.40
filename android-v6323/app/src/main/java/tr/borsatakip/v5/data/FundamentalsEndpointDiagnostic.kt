package tr.borsatakip.v5.data

/** Financial API connectivity does not prove the accuracy of balance-sheet data. */
internal object FundamentalsEndpointDiagnostic {
    private const val path = "/v1/bist/fundamentals"
    fun explain(message: String): String? = when {
        message.startsWith("NOT_FOUND:") || message.startsWith("API_ROUTE_NOT_FOUND:") ->
            "API_ROUTE_NOT_FOUND • HTTP 404 • $path: Railway sunucusunda temel finansallar API yolu bulunamadı. " +
            "V6.3.3 sunucu onarım paketi mevcut Railway servisine ayrıca dağıtılmalı. " +
            "632 sembolün bulunması bilanço verisi olduğu anlamına gelmez; değerleme yapılmadı."
        message.startsWith("SERVER_ERROR:") && message.contains("HTTP 503") &&
                message.contains("FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED") ->
            "FINANCIAL_SOURCE_NOT_CONNECTED • HTTP 503 • $path: Railway sunucusunda finansal tablo sağlayıcısı tanımlı değil. " +
            "BORSA_FUNDAMENTALS_UPSTREAM_URL için yetkili, gerçek ve doğrulanmış bilanço servisi bağlanmalı. " +
            "Fiyat verisi bilanço değildir; değerleme yapılmadı."
        message.startsWith("SERVER_ERROR:") && message.contains("HTTP 503") ->
            "FINANCIAL_SOURCE_UNAVAILABLE • HTTP 503 • $path: API yanıt veriyor ancak finansal tablo kaynağı " +
            "hazır olmayabilir veya sunucu geçici olarak kullanılamıyor. Kaynağı sunucuda doğrulayın; değerleme yapılmadı."
        else -> null
    }
}
