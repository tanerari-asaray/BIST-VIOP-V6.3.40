package tr.borsatakip.v5.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.BuildConfig
import tr.borsatakip.v5.R

/** Android runtime verification for detailed system report and private AI JSON copy. */
@RunWith(AndroidJUnit4::class)
class SystemReportV2RuntimeTest {
    @Test
    fun detailedSystemReportRendersAndCopiesSafeJson() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(app, SettingsDetailActivity::class.java)
            .putExtra(SettingsDetailActivity.EXTRA_MODE, SettingsDetailActivity.MODE_SYSTEM_STATUS)

        ActivityScenario.launch<SettingsDetailActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.detailContent)
                // Technical diagnostics are collapsed by default; open them as a user would.
                val expand = findButton(root, "TEKNİK DETAYLAR / DERİN TEŞHİS")
                assertTrue("Deep diagnostics button missing", expand != null)
                assertTrue(expand!!.performClick())
                val allText = mutableListOf<String>()
                collectText(root, allText)
                val joined = allText.joinToString("\n")
                assertTrue("Detailed diagnostics heading missing", joined.contains("[8] TEKNİK DURUM ÖZETİ"))
                assertTrue("Scan counter diagnostics missing", joined.contains("[9] BIST SAYAÇLARININ ANLAMI"))
                assertTrue("Evidence matrix missing", joined.contains("[13] DOĞRULAMA MATRİSİ"))
                assertTrue("JSON download action missing", joined.contains("KISA JSON İNDİR"))
                assertTrue("TXT download action missing", joined.contains("KISA TXT İNDİR"))

                val copyButton = findButton(root, "AYRINTILI JSON KOPYALA")
                assertTrue("JSON copy button must exist", copyButton != null)
                copyButton!!.performClick()
            }
            // Android ClipboardService can publish the clip after the click callback
            // completes; do not query it immediately within the same UI transaction.
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            var copied: String? = null
            // Android 13+ restricts clipboard reads when the app window loses focus.
            // Retry after restoring activity focus instead of asserting on a transient empty read.
            repeat(5) {
                if (!copied.isNullOrBlank()) return@repeat
                scenario.onActivity { activity ->
                    activity.window.decorView.requestFocus()
                    if (activity.hasWindowFocus()) {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()
                    }
                }
                if (copied.isNullOrBlank()) android.os.SystemClock.sleep(200)
            }
            assertTrue("JSON clipboard empty after UI idle", !copied.isNullOrBlank())
            val parsed = JSONObject(requireNotNull(copied))
            assertEquals("V5.4.1-SYSTEM-DIAGNOSTICS/4", parsed.getString("schemaVersion"))
            assertEquals(BuildConfig.APPLICATION_ID, parsed.getString("applicationId"))
            assertEquals(false, parsed.getBoolean("secretValuesIncluded"))
            assertEquals(false, parsed.getBoolean("liveTradingReady"))
            assertTrue(parsed.has("productionProviderLastProbeAgeMs"))
            assertTrue(parsed.has("secretFiltering"))
            assertTrue(parsed.has("backend"))
            assertTrue(parsed.has("scan"))
            assertTrue(parsed.has("findings"))
            assertTrue(parsed.has("humanReadableReport"))
        }
    }

    @Test
    fun secretRedactionPreservesSafeOriginAndMasksKeys() {
        val sensitive = "Authorization: Bearer abc123456789 XYZ api_key=MY_PRIVATE_KEY_123 " +
            "https://secret.example.org/api/private/orders?token=HIDDEN_TOKEN"
        val sanitized = SystemReportV2.redact(sensitive, "MY_PRIVATE_KEY_123")
        assertTrue(!sanitized.contains("abc123456789"))
        assertTrue(!sanitized.contains("MY_PRIVATE_KEY_123"))
        assertTrue(!sanitized.contains("HIDDEN_TOKEN"))
        assertTrue(!sanitized.contains("/api/private/orders"))
        assertTrue(sanitized.contains("https://secret.example.org/[URL_PATH_GİZLENDİ]"))
    }

    private fun collectText(root: View, result: MutableList<String>) {
        if (root is TextView) result += root.text.toString()
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            collectText(root.getChildAt(i), result)
        }
    }

    private fun findButton(root: View, label: String): Button? {
        if (root is Button && root.text.toString() == label) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            val found = findButton(root.getChildAt(i), label)
            if (found != null) return found
        }
        return null
    }
}

