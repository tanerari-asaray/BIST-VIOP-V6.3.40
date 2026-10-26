package tr.borsatakip.v5.data

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackendHttpTransportTest {
    private class FakeCall(private val answer: ((Callback, Call) -> Unit)? = null) : Call {
        private val req = Request.Builder().url("https://backend.example/v1/bist/symbols").build()
        var callback: Callback? = null
        var cancelled = false
        override fun request(): Request = req
        override fun enqueue(responseCallback: Callback) { callback = responseCallback; answer?.invoke(responseCallback, this) }
        override fun execute(): Response = error("Only async transport is permitted")
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = callback != null
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = FakeCall(answer)
    }

    private fun response(call: Call, code: Int = 200, body: ResponseBody = "{}".toResponseBody()): Response =
        Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1).code(code)
            .message("test").body(body).build()

    private class ThrowingBody(private val failOnClose: Boolean = false,
                               private val readFailure: IOException = IOException("truncated body")) : ResponseBody() {
        var closed = false
        private val input = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!failOnClose) throw readFailure
                return -1
            }
            override fun timeout(): Timeout = Timeout()
            override fun close() { closed = true; if (failOnClose) throw IOException("close failure") }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = input
    }

    @Test fun bodyReadFailureCompletesWaiterAndPreservesHttpStatus() = runTest {
        val body = ThrowingBody()
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, body = body)) }
        try { call.awaitBackendResponse("test"); fail("Expected body error") }
        catch (e: BackendHttpException) {
            assertEquals(200, e.evidence.httpStatus)
            assertTrue(e.cause is IOException)
        }
        assertTrue(body.closed)
    }

    @Test fun bodyCloseFailureCompletesWaiter() = runTest {
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, body = ThrowingBody(true))) }
        try { call.awaitBackendResponse("test"); fail("Expected close error") }
        catch (e: BackendHttpException) { assertTrue(e.cause is IOException) }
    }

    @Test fun missingCallbackHasTotalDeadlineAndCancelsCall() = runTest {
        val call = FakeCall()
        try { call.awaitBackendResponse("test", timeoutMs = 100); fail("Expected deadline") }
        catch (_: SocketTimeoutException) { }
        assertTrue(call.cancelled)
    }

    @Test fun parentCancellationPropagatesAndClosesLateResponse() = runTest {
        val call = FakeCall()
        val pending = launch { call.awaitBackendResponse("test") }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertTrue(call.cancelled)
        val body = ThrowingBody()
        requireNotNull(call.callback).onResponse(call, response(call, body = body))
        assertTrue(body.closed)
    }

    @Test fun successfulOversizedBodyIsRejected() = runTest {
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, body = "12345".toResponseBody())) }
        try { call.awaitBackendResponse("test", maxBytes = 4); fail("Expected response limit") }
        catch (e: BackendHttpException) { assertTrue(e.cause is IOException) }
    }

    @Test fun provider403RetainsProviderStageAndIsNotGatewayAuthError() = runTest {
        val raw = """{"code":"BIST_UPSTREAM_HTTP_403","gatewayAuthenticated":true,"detail":{"stage":"PROVIDER_AUTH","upstreamHttpStatus":403}}"""
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, 403, raw.toResponseBody())) }
        try { call.awaitBackendResponse("test"); fail("Expected provider rejection") }
        catch (e: BackendHttpException) {
            assertEquals(403, e.evidence.httpStatus)
            assertEquals(403, e.evidence.upstreamHttpStatus)
            assertEquals("PROVIDER_AUTH", e.evidence.failureStage)
            assertEquals("BIST_UPSTREAM_HTTP_403", e.evidence.providerCode)
            assertEquals(ProviderFailureCode.SERVER_ERROR, BackendFailurePolicy.code(e.evidence))
        }
    }

    @Test fun gateway401IsAnApplicationCredentialFailure() = runTest {
        val raw = """{"code":"INVALID_GATEWAY_APP_TOKEN"}"""
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, 401, raw.toResponseBody())) }
        try { call.awaitBackendResponse("test"); fail("Expected gateway rejection") }
        catch (e: BackendHttpException) {
            assertEquals("GATEWAY_AUTH", e.evidence.failureStage)
            assertEquals(ProviderFailureCode.AUTH_ERROR, BackendFailurePolicy.code(e.evidence))
        }
    }

    @Test fun missingLicensedProviderRetainsItsTypedCode() = runTest {
        val raw = """{"code":"LICENSED_BIST_PROVIDER_NOT_CONNECTED"}"""
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, 503, raw.toResponseBody())) }
        try { call.awaitBackendResponse("test"); fail("Expected missing source") }
        catch (e: BackendHttpException) {
            assertEquals(ProviderFailureCode.DATA_SOURCE_NOT_CONNECTED, BackendFailurePolicy.code(e.evidence))
            assertEquals("/v1/bist/symbols", e.evidence.endpoint)
        }
    }

    @Test fun successfulBodyIsReturnedWithoutDroppingBytes() = runTest {
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, body = "{\"items\":[\"THYAO\"]}".toResponseBody())) }
        val result = call.awaitBackendResponse("test")
        assertEquals("{\"items\":[\"THYAO\"]}", result.body)
        assertEquals(200, result.evidence.httpStatus)
    }

    @Test fun enqueueFailureAlsoCompletesWaiter() = runTest {
        val call = FakeCall { _, _ -> throw IllegalStateException("enqueue") }
        try { call.awaitBackendResponse("test"); fail("Expected enqueue failure") }
        catch (_: IllegalStateException) { }
    }

    @Test fun responseBodyTimeoutKeepsHttpEvidenceAndTimeoutClassification() = runTest {
        val body = ThrowingBody(readFailure = SocketTimeoutException("read timeout"))
        val call = FakeCall { cb, c -> cb.onResponse(c, response(c, body = body)) }
        try { call.awaitBackendResponse("test"); fail("Expected body timeout") }
        catch (e: BackendHttpException) {
            assertEquals(200, e.evidence.httpStatus)
            assertEquals(ProviderFailureCode.NETWORK_TIMEOUT, BackendFailurePolicy.code(e))
        }
        assertTrue(body.closed)
    }

    @Test fun wrappedNetworkFailuresRetainTheirSpecificCategory() {
        val evidence = BackendHttpEvidence(httpStatus = 200, endpoint = "/v1/health")
        val cases = listOf(
            java.net.UnknownHostException("dns") to ProviderFailureCode.DNS_ERROR,
            javax.net.ssl.SSLException("tls") to ProviderFailureCode.TLS_ERROR,
            IOException("socket") to ProviderFailureCode.NETWORK_ERROR)
        cases.forEach { (cause, expected) ->
            assertEquals(expected, BackendFailurePolicy.code(BackendHttpException(evidence, "test", cause)))
        }
    }
}
