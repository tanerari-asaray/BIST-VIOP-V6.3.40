package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendApiRouteFailureTest {
    @Test fun health404DoesNotProveReadiness() {
        val text = requireNotNull(BackendApiRouteFailure.describe(404, "/v1/health"))
        assertTrue(text.contains("API_ROUTE_NOT_FOUND"))
        assertTrue(text.contains("/v1/health"))
        assertTrue(text.contains("diğer API adımları test edilemedi"))
        assertFalse(text.contains("hazırdır"))
    }

    @Test fun preflight404HasSpecificExplanation() {
        val text = requireNotNull(BackendApiRouteFailure.describe(404, "/v1/preflight"))
        assertTrue(text.contains("BIST yetki/veri denetim"))
    }

    @Test fun otherStatusesRemainUnmodified() {
        assertNull(BackendApiRouteFailure.describe(200, "/v1/health"))
        assertNull(BackendApiRouteFailure.describe(401, "/v1/health"))
        assertNull(BackendApiRouteFailure.describe(503, "/v1/health"))
    }
}
