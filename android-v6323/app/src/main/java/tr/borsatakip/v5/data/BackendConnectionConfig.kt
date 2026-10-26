package tr.borsatakip.v5.data

import java.net.URI
import java.util.Locale

/** Immutable request credentials. Secrets are deliberately excluded from identity and toString. */
class BackendConnectionConfig(val baseUrl: String, val apiKey: String, val revision: String) {
    val origin: String get() = BackendUrlPolicy.origin(baseUrl)
    val sourceIdentity: String get() = BackendUrlPolicy.canonicalOrNull(baseUrl)?.let { "BACKEND|$it|$revision" }.orEmpty()
    override fun toString(): String = "BackendConnectionConfig(origin=$origin, revision=$revision, apiKey=[GİZLİ])"
}

/** One URL contract for validation, HTTP routes and WebSocket routes. */
object BackendUrlPolicy {
    fun canonicalOrNull(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            host in setOf("localhost", "127.0.0.1", "10.0.2.2") ||
            uri.port !in -1..65535 || uri.port == 0) return null
        val path = uri.rawPath.orEmpty().trimEnd('/')
        if (path.split('/').any { it == "." || it == ".." }) return null
        "https://$host" + (if (uri.port > 0 && uri.port != 443) ":${uri.port}" else "") + path
    }.getOrNull()

    fun isValid(raw: String): Boolean = canonicalOrNull(raw) != null

    fun origin(raw: String): String = canonicalOrNull(raw)?.let {
        val uri = URI(it)
        "https://${uri.host}" + if (uri.port > 0) ":${uri.port}" else ""
    }.orEmpty()

    fun endpoint(base: String, path: String): String {
        val canonical = requireNotNull(canonicalOrNull(base)) { "Geçerli HTTPS backend adresi gerekli; query, fragment ve kullanıcı bilgisi kabul edilmez." }
        require(path.startsWith("/") && !path.startsWith("//") && '#' !in path &&
            path.none { it.isISOControl() }) { "Geçersiz backend uç noktası." }
        return canonical + path
    }

    fun websocket(base: String, path: String): String =
        "wss://" + endpoint(base, path).removePrefix("https://")
}
