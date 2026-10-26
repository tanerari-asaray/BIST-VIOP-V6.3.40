package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class B122ViopProviderRecoveryTest {
    private fun read(path: String) = File(path).readText()

    @Test fun viopActivityUsesAutomaticProviderRecovery() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(s.contains("recoverProviderAndLoad()"))
        assertTrue(s.contains("AUTO_TEST_COOLDOWN_MS"))
        assertTrue(s.contains("EXTRA_RETURN_TO_VIOP"))
    }

    @Test fun settingsSaveAutomaticallyTestsConfiguredBackend() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/SettingsActivity.kt")
        assertTrue(s.contains("autoTestAfterSave()"))
        assertTrue(s.contains("VİOP BAĞLANTISI HAZIR"))
    }

    @Test fun settingsDocumentsRequiredViopEndpoints() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val builder = factory.newDocumentBuilder()
        val strings = mutableMapOf<String, String>()
        File("src/main/res/values").listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val values = builder.parse(file).getElementsByTagName("string")
            for (i in 0 until values.length) {
                val value = values.item(i) as Element
                strings[value.getAttribute("name")] = value.textContent.removeSurrounding("\"")
            }
        }
        val views = builder.parse(File("src/main/res/layout/activity_settings.xml")).getElementsByTagName("*")
        val s = (0 until views.length).joinToString("\n") { index ->
            val text = (views.item(index) as Element)
                .getAttributeNS("http://schemas.android.com/apk/res/android", "text")
            if (text.startsWith("@string/")) strings[text.removePrefix("@string/")].orEmpty() else text
        }
        assertTrue(s.contains("/v1/viop/contracts"))
        assertTrue(s.contains("/v1/viop/quote/{symbol}"))
        assertTrue(s.contains("/v1/viop/history/{symbol}"))
    }

    @Test fun noFakeViopFallbackWasIntroduced() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertFalse(s.contains("YahooFallbackProvider"))
        assertFalse(s.contains("fakeViop"))
    }
}
