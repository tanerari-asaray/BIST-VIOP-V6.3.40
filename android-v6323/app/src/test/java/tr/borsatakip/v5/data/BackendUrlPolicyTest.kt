package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class BackendUrlPolicyTest {
    @Test fun uppercaseSchemeHostAndDefaultPortAreCanonical() {
        assertEquals("https://backend.example/api", BackendUrlPolicy.canonicalOrNull(" HTTPS://BACKEND.EXAMPLE:443/api/// "))
        assertEquals("https://backend.example", BackendUrlPolicy.origin("HTTPS://BACKEND.EXAMPLE:443/api"))
    }

    @Test fun queryFragmentCredentialsAndInvalidHostsAreRejectedConsistently() {
        val invalid = listOf("", "not-url", "http://backend.example", "https://",
            "https://backend.example?x=1", "https://backend.example/#fragment",
            "https://user:pass@backend.example", "https://localhost", "https://127.0.0.1",
            "https://10.0.2.2", "https://backend.example:0", "https://backend.example:65536",
            "https://backend.example/a/../b")
        invalid.forEach { value ->
            assertNull(value, BackendUrlPolicy.canonicalOrNull(value))
            assertFalse(value, ProviderReadinessService.isValidHttps(value))
            assertFalse(value, BackendPreflightClient.validHttps(value))
        }
    }

    @Test fun explicitPortAndBasePathAreSharedByHttpAndWebsocket() {
        assertEquals("https://backend.example:8443/api/v1/health?market=BIST",
            BackendUrlPolicy.endpoint("HTTPS://BACKEND.EXAMPLE:8443/api/", "/v1/health?market=BIST"))
        assertEquals("wss://backend.example:8443/api/v1/live",
            BackendUrlPolicy.websocket("HTTPS://BACKEND.EXAMPLE:8443/api/", "/v1/live"))
    }

    @Test fun endpointCannotReplaceAuthority() {
        for (path in listOf("//other.example/private", "https://other.example", "/v1/health#fragment", "/v1/health\n")) {
            try { BackendUrlPolicy.endpoint("https://backend.example", path); fail(path) }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun immutableCredentialsNeverAppearInIdentityOrToString() {
        val config = BackendConnectionConfig("https://old.example/api", "SYNTHETIC_PRIVATE_KEY", "rev-a")
        assertFalse(config.toString().contains(config.apiKey))
        assertFalse(config.sourceIdentity.contains(config.apiKey))
        assertEquals("https://old.example", config.origin)
        assertEquals("https://old.example/api/v1/health", BackendUrlPolicy.endpoint(config.baseUrl, "/v1/health"))
        assertEquals("SYNTHETIC_PRIVATE_KEY", config.apiKey)
    }
}
