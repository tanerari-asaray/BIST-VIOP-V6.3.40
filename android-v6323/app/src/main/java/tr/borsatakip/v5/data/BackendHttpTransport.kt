package tr.borsatakip.v5.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException

internal class BackendHttpResponse(val body: String, val evidence: BackendHttpEvidence)

/** Includes queueing and body consumption in the deadline; cancellation also cancels the socket. */
internal suspend fun Call.awaitBackendResponse(
    label: String, maxBytes: Int = 8 * 1024 * 1024, timeoutMs: Long = 20_000L
): BackendHttpResponse {
    require(maxBytes > 0 && timeoutMs > 0)
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<BackendHttpResponse> { continuation ->
            continuation.invokeOnCancellation { cancel() }
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) { runCatching { response.close() }; return }
                    var evidence = BackendHttpDiagnostic.fromResponse(
                        response.code, response.request.url.encodedPath, "")
                    // Close before resuming: a body/read/close exception must complete the waiter too.
                    val result = runCatching {
                        response.use {
                            val limit = if (it.isSuccessful) maxBytes else 4096
                            val output = ByteArrayOutputStream()
                            it.body?.byteStream()?.use { stream ->
                                val buffer = ByteArray(8192)
                                while (output.size() <= limit) {
                                    val n = stream.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                                    if (n < 0) break
                                    if (n == 0) continue
                                    output.write(buffer, 0, n)
                                }
                            }
                            if (it.isSuccessful && output.size() > limit)
                                throw IOException("Backend yanıtı izin verilen boyutu aştı.")
                            val body = output.toByteArray().let { bytes ->
                                String(bytes, 0, minOf(bytes.size, limit), Charsets.UTF_8)
                            }
                            evidence = BackendHttpDiagnostic.fromResponse(it.code, it.request.url.encodedPath, body.take(4096))
                            if (!it.isSuccessful) throw BackendHttpException(evidence,
                                "$label • ${BackendHttpDiagnostic.describe(evidence)}")
                            if (body.isBlank()) throw BackendHttpException(evidence, "$label boş yanıt döndürdü.")
                            BackendHttpResponse(body, evidence)
                        }
                    }.recoverCatching { failure ->
                        if (failure is BackendHttpException) throw failure
                        throw BackendHttpException(evidence, "$label yanıt gövdesi okunamadı (HTTP ${evidence.httpStatus}).", failure)
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            }
            try { enqueue(callback) }
            catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
        }
    } ?: throw SocketTimeoutException("$label isteği toplam süre sınırını aştı.")
}

internal suspend fun Call.awaitBody(label: String): String = awaitBackendResponse(label).body
