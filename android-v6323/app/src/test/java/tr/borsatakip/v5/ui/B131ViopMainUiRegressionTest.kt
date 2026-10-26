package tr.borsatakip.v5.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class B131ViopMainUiRegressionTest {
    private fun source(path: String) = File(path).readText()

    private fun resource(name: String): String {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/market_rows.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .first { it.attributes.getNamedItem("name").nodeValue == name }.textContent
    }

    @Test fun viopMainUsesVisibleTerminalDashboardAndVerticalList() {
        val layout = source("src/main/res/layout/activity_viop.xml")
        val activity = source("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(layout.contains("@+id/summaryLong"))
        assertTrue(layout.contains("@+id/summaryShort"))
        assertTrue(layout.contains("@+id/viopSearch"))
        assertTrue(layout.contains("@+id/filterAll"))
        assertTrue(activity.contains("RecyclerView.VERTICAL"))
        assertFalse(activity.contains("RecyclerView.HORIZONTAL"))
    }

    @Test fun underlyingCardNeverDisplaysUnderlyingPriceAsViopPrice() {
        val adapter = source("src/main/java/tr/borsatakip/v5/ui/ViopUnderlyingOpportunityAdapter.kt")
        assertTrue(adapter.contains("holder.price.setText(R.string.underlying_row_viop_missing)"))
        assertTrue(resource("underlying_row_viop_missing") == "VİOP —")
        assertFalse(adapter.contains("holder.price.text = o.price"))
        assertTrue(adapter.contains("R.string.underlying_row_reason, o.signalValidityReason"))
        assertTrue(resource("underlying_row_reason").contains("Gerçek VİOP kontratı değildir"))
        assertTrue(adapter.contains("DataMode.DELAYED, DataMode.EOD -> context.getString(R.string.underlying_row_delayed)"))
        assertTrue(resource("underlying_row_delayed") == "GECİKMELİ")
    }
}
