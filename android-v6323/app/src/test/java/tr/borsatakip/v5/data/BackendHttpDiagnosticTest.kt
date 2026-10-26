package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class BackendHttpDiagnosticTest {
    @Test fun missingViopSourceRetainsHttpEndpointAndCode() {
        val e = BackendHttpDiagnostic.fromResponse(503, "/v1/health?market=VIOP",
            """{"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}""")
        assertEquals(503, e.httpStatus)
        assertEquals("/v1/health?market=VIOP", e.endpoint)
        assertEquals("LICENSED_VIOP_PROVIDER_NOT_CONNECTED", e.providerCode)
        assertEquals("PROVIDER_CONFIG", e.failureStage)
        assertNull(e.gatewayAuthenticated)
    }
    @Test fun upstreamAuthenticationIsNotAnApplicationKeyFailure() {
        val e = BackendHttpDiagnostic.fromResponse(401, "/v1/health?market=BIST",
            """{"detail":{"code":"BIST_UPSTREAM_HTTP_401","upstreamHttpStatus":401}}""")
        assertEquals("PROVIDER_AUTH", e.failureStage)
        assertEquals(401, e.upstreamHttpStatus)
        assertTrue(BackendHttpDiagnostic.describe(e).contains("veri sağlayıcısı"))
    }
    @Test fun gatewayAuthenticationHasItsOwnStage() {
        val e = BackendHttpDiagnostic.fromResponse(401, "/v1/health",
            """{"detail":{"code":"INVALID_GATEWAY_APP_TOKEN"}}""")
        assertEquals("GATEWAY_AUTH", e.failureStage)
    }
    @Test fun upstream500IsRetainedWhenGatewayReturns502() {
        val e = BackendHttpDiagnostic.fromResponse(502, "/v1/viop/contracts",
            """{"detail":{"code":"VIOP_UPSTREAM_HTTP_500","upstreamHttpStatus":500}}""")
        assertEquals(502, e.httpStatus)
        assertEquals(500, e.upstreamHttpStatus)
        assertEquals("VIOP_UPSTREAM_HTTP_500", e.providerCode)
    }
    @Test fun noHttpResponseIsNotReportedAsHttpZero() {
        val e = BackendHttpEvidence(endpoint="/v1/health")
        assertNull(e.httpStatus)
        assertNull(BackendHttpEvidence.decode(e.encode()).httpStatus)
    }
    @Test fun boundedEvidenceSurvivesPersistence() {
        val e = BackendHttpDiagnostic.fromResponse(503, "/v1/health?market=BIST",
            """{"gatewayAuthenticated":true,"detail":{"code":"LICENSED_BIST_PROVIDER_NOT_CONNECTED","stage":"PROVIDER_CONFIG"}}""")
        assertEquals(e, BackendHttpEvidence.decode(e.encode()))
    }
    @Test fun malformedPersistenceDoesNotReconstructReadiness() {
        assertEquals(BackendHttpEvidence(), BackendHttpEvidence.decode("bad"))
        assertNull(BackendHttpEvidence.decode("999|/v1/health||||").httpStatus)
    }
    @Test fun secretQueryIsNeverStoredInEndpoint() {
        val path = BackendHttpDiagnostic.safeEndpoint("/v1/health?token=PRIVATE_TEST_SECRET")
        assertEquals("/v1/health", path)
        assertEquals("", BackendHttpDiagnostic.safeEndpoint("https://user:pass@host/path?token=secret"))
    }
    @Test fun arbitraryBodyCannotIntroduceAnErrorCode() {
        val e = BackendHttpDiagnostic.fromResponse(503, "/v1/health",
            """{"detail":{"code":"PRIVATE_TOKEN_123456","message":"Bearer PRIVATE_TEST_SECRET"}}""")
        assertEquals("", e.providerCode)
        assertFalse(e.encode().contains("PRIVATE_TEST_SECRET"))
    }
    @Test fun oversizedAndHtmlBodiesAreNotParsed() {
        val json = """{"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}"""
        assertEquals("", BackendHttpDiagnostic.fromResponse(503, "/v1/health", json + " ".repeat(4096)).providerCode)
        assertEquals("", BackendHttpDiagnostic.fromResponse(503, "/v1/health", "<html>$json</html>").providerCode)
    }
    @Test fun unknownStageCannotBePersistedAsVerified() {
        val e = BackendHttpDiagnostic.fromResponse(503, "/v1/health",
            """{"detail":{"code":"VIOP_UPSTREAM_HTTP_503","stage":"READY"}}""")
        assertEquals("PROVIDER", e.failureStage)
        assertNull(e.gatewayAuthenticated)
    }
    @Test fun authenticatedGatewayAndFailedProviderCanCoexist() {
        val e = BackendHttpDiagnostic.fromResponse(503, "/v1/health",
            """{"gatewayAuthenticated":true,"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}""")
        assertEquals(true, e.gatewayAuthenticated)
        assertEquals("PROVIDER_CONFIG", e.failureStage)
        assertEquals(503, e.httpStatus)
    }
    @Test fun credentialsAreRemovedFromDiagnosticText() {
        val text = BackendHttpDiagnostic.safeText(
            "KEY=PRIVATE_TEST_SECRET Bearer abc123 https://user:password@example.invalid/data?key=xyz",
            "PRIVATE_TEST_SECRET")
        assertFalse(text.contains("PRIVATE_TEST_SECRET"))
        assertFalse(text.contains("abc123"))
        assertFalse(text.contains("password"))
        assertFalse(text.contains("xyz"))
    }
    @Test fun diagnosticMessagesHaveABoundedLength() {
        assertEquals(2000, BackendHttpDiagnostic.safeText("x".repeat(5000)).length)
    }
    @Test fun fragmentedHttpBodyIsReadUntilEof() {
        val body = """{"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}"""
        var offset = 0
        val fragmented = object : java.io.Reader() {
            override fun read(target: CharArray, off: Int, len: Int): Int {
                if (offset == body.length) return -1
                val count = minOf(3, len, body.length - offset)
                body.toCharArray(target, off, offset, offset + count)
                offset += count
                return count
            }
            override fun close() = Unit
        }
        val read = BackendHttpDiagnostic.readBounded(fragmented)
        assertEquals(body, read)
        assertEquals("LICENSED_VIOP_PROVIDER_NOT_CONNECTED",
            BackendHttpDiagnostic.fromResponse(503, "/v1/health", read).providerCode)
    }
    @Test fun boundedHttpBodyDoesNotReadPastLimit() {
        val reader = java.io.StringReader("a".repeat(100))
        assertEquals(10, BackendHttpDiagnostic.readBounded(reader, 10).length)
        assertEquals('a'.code, reader.read())
    }
    @Test fun uppercaseUrlQuerySecretsAreRedacted() {
        val text = BackendHttpDiagnostic.safeText("HTTPS://example.invalid/api?key=PRIVATE_QUERY_VALUE")
        assertFalse(text.contains("PRIVATE_QUERY_VALUE"))
    }
    @Test fun httpEvidenceRemainsAvailableWhenBodyIsEmptyOrMalformed() {
        val e = BackendHttpDiagnostic.fromResponse(502, "/v1/health?market=VIOP", "<bad>")
        assertEquals(502, e.httpStatus)
        assertEquals("/v1/health?market=VIOP", e.endpoint)
        assertEquals("", e.providerCode)
    }
}
