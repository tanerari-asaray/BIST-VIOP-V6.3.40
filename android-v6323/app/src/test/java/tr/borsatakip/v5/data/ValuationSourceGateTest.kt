package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuationSourceGateTest {
    @Test fun noBackendAndNoFileOpensGuidedDataSetup() {
        assertEquals(ValuationSourceGate.Mode.MISSING_FINANCIAL_SOURCE,
            ValuationSourceGate.decide(false, "", false))
        assertFalse(ValuationSourceGate.canCalculate(ValuationSourceGate.Mode.MISSING_FINANCIAL_SOURCE))
    }

    @Test fun apiKeyDoesNotCompensateForMissingHttpsOrigin() {
        assertEquals(ValuationSourceGate.Mode.MISSING_FINANCIAL_SOURCE,
            ValuationSourceGate.decide(false, "http://not-secure.example", true))
    }

    @Test fun configuredUrlWithoutKeyIsNotReady() {
        assertEquals(ValuationSourceGate.Mode.MISSING_API_KEY,
            ValuationSourceGate.decide(false, "https://example.org", false))
        assertFalse(ValuationSourceGate.canCalculate(ValuationSourceGate.Mode.MISSING_API_KEY))
    }

    @Test fun importedFinancialJsonWorksWithoutBackend() {
        assertEquals(ValuationSourceGate.Mode.LOCAL_JSON,
            ValuationSourceGate.decide(true, "", false))
        assertTrue(ValuationSourceGate.canCalculate(ValuationSourceGate.Mode.LOCAL_JSON))
    }

    @Test fun importedJsonTakesPrecedenceAndAvoidsBackendRequest() {
        assertEquals(ValuationSourceGate.Mode.LOCAL_JSON,
            ValuationSourceGate.decide(true, "https://example.org", true))
    }

    @Test fun configuredHttpsAndKeyMayAttemptBackendButDoesNotProveHealth() {
        assertEquals(ValuationSourceGate.Mode.CONFIGURED_HTTPS,
            ValuationSourceGate.decide(false, "https://example.org", true))
        assertTrue(ValuationSourceGate.canCalculate(ValuationSourceGate.Mode.CONFIGURED_HTTPS))
    }
}
