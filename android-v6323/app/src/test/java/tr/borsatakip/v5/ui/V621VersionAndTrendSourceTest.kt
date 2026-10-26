package tr.borsatakip.v5.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Static guards in addition to Android instrumentation tests. */
class V621VersionAndTrendSourceTest {
    @Test fun appLabelUsesCurrentGradleVersion() {
        val build = File("build.gradle.kts").readText()
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(Regex("versionCode\\s*=\\s*[1-9][0-9]*").containsMatchIn(build))
        assertTrue(build.contains("resValue(\"string\", \"app_name\", \"BIST / VİOP V${'$'}{versionName}\")"))
        assertTrue(!strings.contains("name=\"app_name\""))
    }
    @Test fun dayanakColorComesFromTechnicalTrendNotSignalDirection() {
        val adapter = File("src/main/java/tr/borsatakip/v5/ui/ViopUnderlyingOpportunityAdapter.kt").readText()
        assertTrue(adapter.contains("OpportunityTrendPresentation.from(o).trend"))
        assertTrue(adapter.contains("holder.chart.setTrendStyle(trend)"))
        assertTrue(!adapter.contains("holder.chart.setDirection(direction)"))
        val contractAdapter = File("src/main/java/tr/borsatakip/v5/ui/ViopOpportunityAdapter.kt").readText()
        assertTrue(contractAdapter.contains("TrendUiPolicy.resolve(x.decisionPrice, x.technical, x.decisionState)"))
        assertTrue(contractAdapter.contains("holder.chart.setTrendStyle(trend)"))
    }
    @Test fun failedUnderlyingScanCanRetry() {
        val activity = File("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt").readText()
        assertTrue(activity.contains("underlyingScanCompleted = false"))
        assertTrue(activity.contains("result.failures"))
    }
}
