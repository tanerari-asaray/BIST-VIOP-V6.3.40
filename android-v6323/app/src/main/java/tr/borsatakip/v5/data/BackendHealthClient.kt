package tr.borsatakip.v5.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BackendHealthClient(context: Context) {
    private val settings = SettingsStore(context)
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    data class Health(val ok: Boolean, val provider: String, val latencyMs: Long,
        val serverTime: Long?, val message: String, val connection: BackendHttpEvidence = BackendHttpEvidence())

    suspend fun check(): Health = withContext(Dispatchers.IO) {
        val config = settings.connectionSnapshot()
        if (config.baseUrl.isBlank()) return@withContext Health(false, "tanımsız", 0, null, "Servis adresi tanımlı değil.")
        if (!BackendUrlPolicy.isValid(config.baseUrl))
            return@withContext Health(false, "tanımsız", 0, null, "Geçerli HTTPS servis adresi gerekli.")
        val path = "/v1/health?market=VIOP"
        val start = System.currentTimeMillis()
        var evidence = BackendHttpEvidence(endpoint = path)
        try {
            val request = Request.Builder().url(BackendUrlPolicy.endpoint(config.baseUrl, path))
                .header("Accept", "application/json")
                .apply { if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}") }.build()
            val response = client.newCall(request).awaitBackendResponse("Sağlık testi", maxBytes = 65_536)
            evidence = response.evidence
            val json = JSONObject(response.body)
            Health(json.optBoolean("ok", false) && settings.isConnectionCurrent(config),
                json.optString("provider").ifBlank { "mobil backend" }, System.currentTimeMillis() - start,
                json.optLong("serverTime", 0L).takeIf { it > 0 },
                BackendHttpDiagnostic.safeText(if (!settings.isConnectionCurrent(config))
                    "Bağlantı ayarları test sırasında değişti; yeniden test gerekli."
                else json.optString("message").ifBlank {
                    if (json.optBoolean("ok", false)) "Sağlık kontrolü başarılı" else "Sağlık kontrolü hazır olduğunu doğrulamadı."
                }, config.apiKey), evidence)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is BackendHttpException) evidence = e.evidence
            val detail = when (e) {
                is BackendHttpException -> if (e.cause is java.net.SocketTimeoutException)
                    "Yanıt gövdesi okunurken zaman aşımı oluştu (HTTP ${e.evidence.httpStatus})."
                else if (e.cause is java.io.IOException)
                    "Yanıt gövdesi okunamadı (HTTP ${e.evidence.httpStatus})."
                else BackendHttpDiagnostic.describe(e.evidence)
                is JSONException -> "Sunucu yanıtı geçerli JSON değil."
                is java.net.SocketTimeoutException -> "Bağlantı zaman aşımına uğradı."
                is java.net.UnknownHostException -> "Sunucunun alan adı çözümlenemedi (DNS)."
                is javax.net.ssl.SSLException -> "Güvenli bağlantı kurulamadı (TLS)."
                else -> BackendHttpDiagnostic.safeText(e.message ?: "Bağlantı kurulamadı.", config.apiKey)
            }
            Health(false, "bilinmiyor", System.currentTimeMillis() - start, null, detail, evidence)
        }
    }
}
