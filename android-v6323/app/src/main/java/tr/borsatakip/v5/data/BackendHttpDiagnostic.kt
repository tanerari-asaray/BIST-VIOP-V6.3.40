package tr.borsatakip.v5.data

/** Evidence of one HTTP response; absence is different from HTTP 0 or a successful test. */
data class BackendHttpEvidence(
    val httpStatus: Int? = null,
    val endpoint: String = "",
    val providerCode: String = "",
    val failureStage: String = "",
    val upstreamHttpStatus: Int? = null,
    val gatewayAuthenticated: Boolean? = null
) {
    fun encode(): String = listOf(httpStatus?.toString().orEmpty(), endpoint, providerCode,
        failureStage, upstreamHttpStatus?.toString().orEmpty(), gatewayAuthenticated?.toString().orEmpty()).joinToString("|")

    companion object {
        fun decode(raw: String): BackendHttpEvidence {
            val p = raw.split('|')
            if (p.size != 6) return BackendHttpEvidence()
            return BackendHttpEvidence(
                p[0].toIntOrNull()?.takeIf { it in 100..599 },
                BackendHttpDiagnostic.safeEndpoint(p[1]),
                p[2].takeIf(BackendHttpDiagnostic::allowedCode).orEmpty(),
                p[3].takeIf { it in BackendHttpDiagnostic.STAGES }.orEmpty(),
                p[4].toIntOrNull()?.takeIf { it in 100..599 },
                when (p[5]) { "true" -> true; "false" -> false; else -> null }
            )
        }
    }
}

class BackendHttpException(val evidence: BackendHttpEvidence, message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Never copies arbitrary error bodies, URLs, request headers or tokens into diagnostic evidence. */
object BackendHttpDiagnostic {
    internal val STAGES = setOf("GATEWAY_AUTH", "GATEWAY_CONFIG", "PROVIDER_AUTH", "PROVIDER_CONFIG", "PROVIDER", "REQUEST")
    private val codePattern = Regex(""""code"\s*:\s*"([A-Z0-9_]{3,96})"""")
    private val stagePattern = Regex(""""stage"\s*:\s*"([A-Z_]{3,32})"""")
    private val upstreamPattern = Regex(""""upstreamHttpStatus"\s*:\s*([1-5][0-9]{2})(?=[,\s}])""")
    private val authPattern = Regex(""""gatewayAuthenticated"\s*:\s*(true|false)(?=[,\s}])""")
    private val permittedCode = Regex(
        """(?:LICENSED_(?:BIST|VIOP)_PROVIDER_NOT_CONNECTED|FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED|PRIVATE_GATEWAY_KEY_NOT_CONFIGURED|INVALID_GATEWAY_APP_TOKEN|(?:BIST|VIOP|NEWS)_(?:UPSTREAM_[A-Z0-9_]+|SCHEMA_INVALID|[A-Z_]+_INVALID|EMPTY_RESPONSE|RESPONSE_TOO_LARGE))"""
    )
    fun allowedCode(value: String): Boolean = value.length <= 96 && permittedCode.matches(value)
    fun safeEndpoint(value: String): String {
        val path = value.substringBefore('?')
        if (!Regex("""/v1/[A-Za-z0-9_./:-]{1,160}""").matches(path)) return ""
        val market = value.substringAfter('?', "").takeIf { it == "market=BIST" || it == "market=VIOP" }
        return path + if (market == null) "" else "?$market"
    }
    fun fromResponse(status: Int, endpoint: String, raw: String): BackendHttpEvidence {
        val body = raw.takeIf { it.length <= 4096 && it.trimStart().startsWith("{") }.orEmpty()
        val code = codePattern.find(body)?.groupValues?.get(1)?.takeIf(::allowedCode).orEmpty()
        val declaredStage = stagePattern.find(body)?.groupValues?.get(1)?.takeIf { it in STAGES }
        val stage = declaredStage ?: when {
            code == "INVALID_GATEWAY_APP_TOKEN" -> "GATEWAY_AUTH"
            code == "PRIVATE_GATEWAY_KEY_NOT_CONFIGURED" -> "GATEWAY_CONFIG"
            Regex("""(?:BIST|VIOP|NEWS)_UPSTREAM_HTTP_(?:401|403)""").matches(code) -> "PROVIDER_AUTH"
            code.endsWith("_NOT_CONNECTED") || code.endsWith("_NOT_CONFIGURED") ||
                code.endsWith("_URL_INVALID") || code.endsWith("_SELF_REFERENCE") -> "PROVIDER_CONFIG"
            code.isNotEmpty() -> "PROVIDER"
            else -> ""
        }
        return BackendHttpEvidence(status.takeIf { it in 100..599 }, safeEndpoint(endpoint), code, stage,
            upstreamPattern.find(body)?.groupValues?.get(1)?.toIntOrNull(),
            authPattern.find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull())
    }
    fun describe(evidence: BackendHttpEvidence): String {
        val reason = when {
            evidence.failureStage == "PROVIDER_AUTH" -> "Sunucuya erişildi; veri sağlayıcısı kimlik doğrulamasını reddetti."
            evidence.failureStage == "GATEWAY_AUTH" -> "Uygulamanın sunucu erişim anahtarı reddedildi."
            evidence.providerCode == "LICENSED_VIOP_PROVIDER_NOT_CONNECTED" -> "Sunucu yanıtlıyor; VİOP veri kaynağı sunucuda bağlı değil."
            evidence.providerCode == "LICENSED_BIST_PROVIDER_NOT_CONNECTED" -> "Sunucu yanıtlıyor; BIST veri kaynağı sunucuda bağlı değil."
            evidence.failureStage == "GATEWAY_CONFIG" -> "Sunucunun uygulama erişim anahtarı yapılandırılmamış."
            evidence.providerCode.endsWith("_URL_INVALID") -> "Sunucudaki veri kaynağı adresi geçersiz."
            evidence.providerCode.endsWith("_SELF_REFERENCE") -> "Veri kaynağı adresi sunucunun kendi adresi olamaz."
            evidence.httpStatus == 429 -> "İstek sınırı aşıldı."
            evidence.httpStatus == 404 -> "İstenen API uç noktası bulunamadı."
            evidence.httpStatus in listOf(401, 403) -> "Kimlik doğrulama veya erişim yetkisi reddedildi."
            else -> "Veri isteği tamamlanamadı; hata kodunu ve bağlantı aşamasını kontrol edin."
        }
        return listOfNotNull(
            evidence.httpStatus?.let { "HTTP $it" }, evidence.endpoint.takeIf { it.isNotBlank() },
            evidence.providerCode.takeIf { it.isNotBlank() },
            evidence.failureStage.takeIf { it.isNotBlank() }?.let { "Aşama: $it" },
            evidence.upstreamHttpStatus?.let { "Sağlayıcı HTTP $it" }, reason
        ).joinToString(" • ")
    }
    /** Readers may return only part of a network chunk. Continue until EOF or the size limit. */
    fun readBounded(reader: java.io.Reader, maxChars: Int = 4096): String {
        require(maxChars > 0)
        val output = StringBuilder()
        val buffer = CharArray(minOf(2048, maxChars))
        while (output.length < maxChars) {
            val length = reader.read(buffer, 0, minOf(buffer.size, maxChars - output.length))
            if (length <= 0) break
            output.append(buffer, 0, length)
        }
        return output.toString()
    }

    fun safeText(value: String, secret: String = ""): String {
        var text = if (secret.isNotBlank()) value.replace(secret, "[GİZLİ]") else value
        text = text.replace(Regex("""(?i)Bearer\s+[^\s,;"']+"""), "Bearer [GİZLİ]")
            .replace(Regex("""\bbt_[A-Za-z0-9_-]{12,}\b"""), "[GİZLİ]")
            .replace(Regex("""(?i)(https?://)[^/\s@]+@"""), "$1[GİZLİ]@")
            .replace(Regex("""(?i)(https?://[^\s?]+)\?[^\s]+"""), "$1?[GİZLİ]")
        return text.take(2000)
    }
}
