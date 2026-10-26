package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class B125ViopConfigAndUiFixTest {
    private fun read(path: String) = File(path).readText()

    private fun resource(name: String): String {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/market_rows.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .first { it.attributes.getNamedItem("name").nodeValue == name }.textContent
    }

    @Test fun githubBuildCanInjectNonSecretBackendUrlWithoutEmbeddingApiKey() {
        val gradle = read("build.gradle.kts")
        val settings = read("src/main/java/tr/borsatakip/v5/data/SettingsStore.kt")
        assertTrue(gradle.contains("BORSA_BACKEND_URL"))
        assertTrue(gradle.contains("DEFAULT_BACKEND_URL"))
        assertFalse(gradle.contains("DEFAULT_API_KEY"))
        assertTrue(settings.contains("BuildConfig.DEFAULT_BACKEND_URL"))
    }

    @Test fun viopActionsAndStrongSignalsHeaderDoNotDependOnHorizontalOverflow() {
        val xml = read("src/main/res/layout/activity_viop.xml")
        val activity = read("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(xml.contains("android:id=\"@+id/signalSummary\""))
        assertTrue(xml.contains("android:id=\"@+id/summaryLong\""))
        assertTrue(xml.contains("android:id=\"@+id/summaryShort\""))
        assertTrue(activity.contains("RecyclerView.VERTICAL"))
        assertFalse(activity.contains("RecyclerView.HORIZONTAL"))
    }

    @Test fun opportunityCardIsResponsiveAndUnderlyingFallbackIsExplicit() {
        val item = read("src/main/res/layout/item_viop_opportunity.xml")
        val adapter = read("src/main/java/tr/borsatakip/v5/ui/ViopUnderlyingOpportunityAdapter.kt")
        val catalog = read("src/main/java/tr/borsatakip/v5/data/ViopBuiltinContractCatalog.kt")
        assertFalse(item.contains("android:layout_width=\"300dp\""))
        assertTrue(adapter.contains("R.string.underlying_row_direction, trend.arrow, trend.label"))
        assertTrue(resource("underlying_row_direction").startsWith("DAYANAK ·"))
        assertTrue(adapter.contains("trend.label"))
        assertFalse(adapter.contains("DAYANAK LONG"))
        assertFalse(adapter.contains("DAYANAK SHORT"))
        assertTrue(adapter.contains("R.string.underlying_row_reason, o.signalValidityReason"))
        assertTrue(resource("underlying_row_reason").contains("Gerçek VİOP kontratı değildir"))
        assertTrue(catalog.contains("UNDERLYING:"))
        assertFalse(catalog.contains("symbol = \"F_"))
    }
}
