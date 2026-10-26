package tr.borsatakip.v5.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingSymbolDiagnosticTest {
    @Test fun controlledReasonCodesSurviveWithoutExportingRawMessages() {
        val diagnostics = classifyMissingSymbols(listOf("AAA"), emptyList(), emptyList(), listOf("AAA"),
            mapOf("AAA" to "private URL and token must not appear"), mapOf("AAA" to "YAHOO_HTTP_429"))
        assertEquals("PROVIDER_REPORTED:YAHOO_HTTP_429", diagnostics.single().evidence)
    }

    @Test fun categorizesEveryMissingSymbolWithoutInventingCauses() {
        val diagnostics = classifyMissingSymbols(
            listOf(" AAA ", "BBB", "CCC", "DDD", "AAA"), listOf("aaa"),
            listOf("bbb"), listOf("CCC"))
        assertEquals(listOf("BBB", "CCC", "DDD"), diagnostics.map { it.symbol })
        assertEquals(listOf("PROVIDER_NO_DATA", "PROVIDER_ERROR", "UNKNOWN_PROVIDER_OMISSION"), diagnostics.map { it.code })
        assertEquals("NO_UPSTREAM_REASON", diagnostics.last().evidence)
    }

    @Test fun providerErrorsTakePrecedenceAndFetchedSymbolsAreExcluded() {
        val diagnostics = classifyMissingSymbols(listOf("AAA", "BBB"), listOf("AAA"), listOf("AAA", "BBB"), listOf("BBB"))
        assertEquals(1, diagnostics.size)
        assertEquals("PROVIDER_ERROR", diagnostics.single().code)
    }
}
