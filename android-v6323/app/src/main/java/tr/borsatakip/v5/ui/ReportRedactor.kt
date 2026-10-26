package tr.borsatakip.v5.ui

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.util.Locale

/** Redact values and credential fields before serialization, never JSON punctuation. */
object ReportRedactor {
    private const val MASK = "[GİZLENDİ]"
    private val credentialKeys = setOf("apikey", "xapikey", "accesstoken", "refreshtoken",
        "authorization", "password", "clientsecret")

    fun redact(report: String, secret: String): String {
        val trimmed = report.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsed = runCatching {
                val tokener = JSONTokener(report)
                val value = tokener.nextValue()
                require(tokener.nextClean() == '\u0000') { "JSON sonunda beklenmeyen içerik." }
                require(value is JSONObject || value is JSONArray)
                val cleaned = sanitize(value, secret).toString()
                if (cleaned == value.toString()) report else cleaned
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return redactText(report, secret)
    }

    private fun sanitize(value: Any?, secret: String): Any? = when (value) {
        is JSONObject -> JSONObject().apply {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val credential = key.lowercase(Locale.ROOT).replace("_", "").replace("-", "") in credentialKeys
                put(redactText(key, secret), if (credential) MASK else sanitize(value.get(key), secret))
            }
        }
        is JSONArray -> JSONArray().apply {
            for (i in 0 until value.length()) put(sanitize(value.get(i), secret))
        }
        is String -> redactText(value, secret)
        else -> value
    }

    fun redactText(value: String, secret: String): String {
        var safe = if (secret.isNotBlank()) value.replace(secret, MASK) else value
        safe = Regex("""(?i)https?://[^\s"'<>,;]+""").replace(safe) { match ->
            val uri = runCatching { URI(match.value.replace("/[URL_PATH_GİZLENDİ]", "/%5BURL_PATH_GIZLENDI%5D")) }.getOrNull()
            val host = uri?.host
            if (uri == null || host.isNullOrBlank()) "[URL_GİZLENDİ]"
            else {
                val origin = "https://$host" + if (uri.port > 0) ":${uri.port}" else ""
                if (!uri.rawPath.isNullOrBlank() && uri.rawPath != "/" ||
                    uri.rawQuery != null || uri.rawUserInfo != null || uri.rawFragment != null)
                    "$origin/[URL_PATH_GİZLENDİ]" else origin
            }
        }
        safe = Regex("""(?i)\bBearer\s+(?:\[GİZLENDİ\]|[^\s"'&,;}\]]+)""").replace(safe, "Bearer $MASK")
        safe = Regex("""(?i)\b((?:api[_-]?key|x-api-key|access[_-]?token|refresh[_-]?token|authorization|password|client[_-]?secret)\s*[:=]\s*)(?:Bearer\s+)?(?:\[GİZLENDİ\]|[^\s"'&,;}\]]+)""")
            .replace(safe) { "${it.groupValues[1]}$MASK" }
        return Regex("""\bbt_[A-Za-z0-9_-]{12,}\b""").replace(safe, MASK)
    }
}
