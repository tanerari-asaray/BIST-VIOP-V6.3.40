package tr.borsatakip.v5.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReportRedactorTest {
    @Test fun nestedCredentialsAreMaskedAndJsonValuesRetainTheirTypes() {
        val raw = """{"status":"PARTIAL","detail":{"api_key":"SYNTHETIC_A","Authorization":"Bearer SYNTHETIC_B"},"checks":[true,12,null,"password=SYNTHETIC_C"]}"""
        val safe = ReportRedactor.redact(raw, "")
        val json = JSONObject(safe)
        assertEquals("PARTIAL", json.getString("status"))
        assertEquals("[GİZLENDİ]", json.getJSONObject("detail").getString("api_key"))
        assertEquals("[GİZLENDİ]", json.getJSONObject("detail").getString("Authorization"))
        assertTrue(json.getJSONArray("checks").getBoolean(0))
        assertEquals(12, json.getJSONArray("checks").getInt(1))
        assertTrue(json.getJSONArray("checks").isNull(2))
        assertFalse(safe.contains("SYNTHETIC_"))
    }

    @Test fun knownSecretWithJsonEscapesIsRemovedAfterDecoding() {
        val secret = "synthetic_\"quote\\slash\nline"
        val raw = JSONObject().put("message", secret).toString()
        val json = JSONObject(ReportRedactor.redact(raw, secret))
        assertEquals("[GİZLENDİ]", json.getString("message"))
    }

    @Test fun topLevelArrayAndNestedObjectsRemainParseable() {
        val safe = ReportRedactor.redact("""[{"message":"Authorization: Bearer SYNTHETIC_A"},{"access-token":"SYNTHETIC_B"}]""", "")
        val array = JSONArray(safe)
        assertEquals(2, array.length())
        assertFalse(safe.contains("SYNTHETIC_"))
    }

    @Test fun jsonBracesQuotesAndLineBreaksInOrdinaryMessageArePreserved() {
        val value = "HTTP 503 {detail} \"quoted\"\nsecond line"
        val json = JSONObject().put("message", value)
        assertEquals(value, JSONObject(ReportRedactor.redact(json.toString(), "")).getString("message"))
    }

    @Test fun privateUrlPathQueryAndUserInformationAreRemoved() {
        val raw = """{"message":"HTTPS://user:pass@backend.example/private/path?token=SYNTHETIC_PRIVATE#secret"}"""
        val safe = ReportRedactor.redact(raw, "")
        assertEquals("https://backend.example/[URL_PATH_GİZLENDİ]", JSONObject(safe).getString("message"))
        assertFalse(safe.contains("pass@"))
        assertFalse(safe.contains("SYNTHETIC_PRIVATE"))
    }

    @Test fun plainTextAndRepeatedRedactionDoNotAccumulateBrackets() {
        val raw = "HTTP 403\nAuthorization: Bearer SYNTHETIC_A\napi_key=SYNTHETIC_B"
        val safe = ReportRedactor.redact(raw, "")
        assertFalse(safe.contains("SYNTHETIC_"))
        assertEquals(safe, ReportRedactor.redact(safe, ""))
        assertFalse(safe.contains("]]"))
    }
}
