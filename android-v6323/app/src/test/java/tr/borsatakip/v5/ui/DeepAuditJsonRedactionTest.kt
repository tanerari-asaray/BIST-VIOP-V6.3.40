package tr.borsatakip.v5.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exported JSON must keep its string terminator and object terminator after redaction. */
class DeepAuditJsonRedactionTest {
    @Test fun authorizationMessagePreservesJsonStructure() {
        val token = "SYNTHETIC_AUDIT_TOKEN_123"
        val value = SystemReportV2.redact("{\"message\":\"Authorization: Bearer $token\"}", "")
        assertFalse(value.contains(token))
        assertTrue("Redaction removed JSON terminators: $value", value.endsWith("\"}"))
    }

    @Test fun apiKeyMessagePreservesJsonStructure() {
        val token = "synthetic_audit_key_123"
        val value = SystemReportV2.redact("{\"message\":\"api_key=$token\"}", "")
        assertFalse(value.contains(token))
        assertTrue("Redaction removed JSON terminators: $value", value.endsWith("\"}"))
    }

    @Test fun ordinaryJsonMessageIsUnchanged() {
        val value = "{\"message\":\"HTTP 503\",\"status\":\"PARTIAL\"}"
        assertEquals(value, SystemReportV2.redact(value, ""))
    }
}
