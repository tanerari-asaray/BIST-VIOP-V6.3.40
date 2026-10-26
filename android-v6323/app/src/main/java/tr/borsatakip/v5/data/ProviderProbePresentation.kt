package tr.borsatakip.v5.data

/** Human-readable scope for ProviderReadinessService.test(), which calls VIOP-only checkViopQuote().
 * false means unverified, NOT necessarily a failed network request. Never promotes READY.
 */
internal object ProviderProbePresentation {
    private const val SKIPPED = "— TEST EDİLMEDİ"
    fun describe(result: ProviderReadinessSnapshot): String = buildString {
        if (result.testedAt <= 0L) {
            append("Alt adımlar: TEST EDİLMEDİ")
            return@buildString
        }
        append("Test kapsamı: VİOP sağlık, sözleşme, fiyat ve geçmiş. BIST testleri bu çalışmada çağrılmadı.\n")
        if (result.connection.httpStatus != null) {
            append("Sunucu erişimi: HTTP yanıtı alındı (" + result.connection.httpStatus + ")\n")
        }
        append("Uygulama → sunucu kimlik doğrulama: " + when (result.connection.gatewayAuthenticated) {
            true -> "DOĞRULANDI"
            false -> "REDDEDİLDİ"
            null -> "AYRI SONUÇ KAYDI YOK"
        } + "\n")
        append("HTTPS/Health: ${if (result.healthOk) "✓ DOĞRULANDI" else "✕ BAŞARISIZ"}\n")
        if (!result.healthOk) {
            append("Sağlayıcı veri erişimi: $SKIPPED (health geçilemedi)\n")
            append("BIST Symbols: $SKIPPED (ayrı BIST testi gerekir)\n")
            append("BIST Quote: $SKIPPED\n")
            append("BIST History: $SKIPPED\n")
            append("VİOP Contracts: $SKIPPED\n")
            append("VİOP Quote: $SKIPPED\n")
            append("VİOP History: $SKIPPED")
            return@buildString
        }
        append("Sağlayıcı veri erişimi: ${if (result.authOk) "✓ DOĞRULANDI" else "— DOĞRULANMADI (ayrı auth testi yok)"}\n")
        append("BIST Symbols: $SKIPPED (ayrı BIST testi gerekir)\n")
        append("BIST Quote: $SKIPPED\n")
        append("BIST History: $SKIPPED\n")
        append("VİOP Contracts: ${if (result.viopContractsOk) "✓ DOĞRULANDI (${result.viopContractCount})" else "✕ DOĞRULANMADI"}\n")
        append("VİOP Quote: ${if (!result.viopContractsOk) SKIPPED else if (result.viopQuoteOk) "✓ DOĞRULANDI" else "✕ DOĞRULANMADI"}\n")
        append("VİOP History: ${if (!result.viopQuoteOk) SKIPPED else if (result.viopHistoryOk) "✓ 220 GÜNLÜK VERİ DOĞRULANDI" else "— ANALİZ GEÇMİŞİ YETERSİZ / DOĞRULANMADI"}")
    }
}
