package tr.borsatakip.v5.ui

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.ScanRun
import tr.borsatakip.v5.model.ScanRunStatus
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.scan.ScanState
import tr.borsatakip.v5.scan.ScanStatus
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class VisualScreenshotTest {
    private val tech = TechnicalSnapshot(31.8, 31.1, 29.2, 58.4, 0.42, 0.31, 34.0, 28.2, 0.91, 31.6, 1.45, 30.8, 33.5)
    private val opp = Opportunity(
        symbol="TRCAS", companyName="TURCAS PETROL A.Ş.", price=32.14, dailyChangePct=4.15,
        score=85, riskScore=47, direction="LONG", technicalLabel="YÜKSELEN", volumeLabel="Güçlü hacim",
        kapLabel="Veri yok", liquidityLabel="Yüksek", support=30.8, resistance=33.5, source="UI_TEST_FIXTURE",
        dataTimestamp=1_788_966_600_000L, candles=listOf(Candle(1_788_966_600_000L,31.0,32.5,30.8,32.14,2_400_000.0)),
        technical=tech, dataConfidenceScore=98, dataConfidenceLabel="Yüksek", finalSignalScore=85,
        isRealtime=true, delaySeconds=0, currentSessionIncluded=true, exchangeTimestamp=1_788_966_600_000L,
        receivedAt=1_788_966_600_500L, signalGeneratedAt=1_788_966_600_700L, dataMode=DataMode.REALTIME,
        signalValidity=SignalValidity.VALID, signalValidityReason="UI screenshot fixture"
    )

    @Before fun fixture() {
        AppSession.selected = opp
        AppSession.lastOpportunities = listOf(opp, opp.copy(symbol="BIMAS", companyName="BİM BİRLEŞİK MAĞAZALAR A.Ş.", finalSignalScore=82, score=82, dailyChangePct=1.8), opp.copy(symbol="ASELS", companyName="ASELSAN", finalSignalScore=76, score=76, dailyChangePct=2.1))
        val run = ScanRun("ui-test", 1_788_966_000_000L, 1_788_966_720_000L, "fixture", ScanRunStatus.PARTIAL, 3, 1)
        AppSession.lastScanState = ScanState(status=ScanStatus.COMPLETED, progress=100, processed=4, total=4, successful=3, skipped=1, results=AppSession.lastOpportunities, scanRun=run)
        AppSession.selectedViopContract = ViopContract("F_XU0301026", "XU030", "2026-10", lastPrice=12428.0, dailyChangePct=1.25, tickSize=25.0, multiplier=10.0, openInterest=245600, volume=245_600_000.0, providerId="fixture", providerLabel="UI_TEST_FIXTURE", dataTimestamp=1_788_966_600_000L, isRealtime=true, delaySeconds=0, currentSessionIncluded=true, receivedAt=1_788_966_600_500L, dataMode=DataMode.REALTIME, validity=SignalValidity.VALID, validityReason="UI screenshot fixture")
    }

    @After fun cleanup() { AppSession.selected=null; AppSession.lastOpportunities=emptyList(); AppSession.lastScanState=null; AppSession.selectedViopContract=null }

    @Test fun captureNewAndReworkedScreens() {
        capture("01_technical", TechnicalAnalysisActivity::class.java)
        capture("02_news", NewsActivity::class.java)
        capture("03_scan_results", ScanResultsActivity::class.java)
        capture("04_notifications", NotificationsActivity::class.java)
        capture("05_viop_contracts", ViopContractsActivity::class.java)
        capture("06_viop_detail", ViopDetailActivity::class.java)
        capture("07_stocks", StocksActivity::class.java)
        capture("08_opportunity", OpportunityActivity::class.java)
        capture("09_stock_detail", StockDetailActivity::class.java)
        capture("10_settings", SettingsActivity::class.java)
    }

    private fun <T: androidx.appcompat.app.AppCompatActivity> capture(name:String, clazz:Class<T>) {
        ActivityScenario.launch(clazz).use {
            Thread.sleep(900)
            val inst = InstrumentationRegistry.getInstrumentation()
            val bmp: Bitmap = inst.uiAutomation.takeScreenshot()
            val dir = File(inst.targetContext.getExternalFilesDir(null), "visual-screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
    }
}
