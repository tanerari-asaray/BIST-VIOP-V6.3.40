package tr.borsatakip.v5.ui
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import tr.borsatakip.v5.data.*
import tr.borsatakip.v5.model.Candle
import java.io.File
@RunWith(AndroidJUnit4::class)
class Build648RuntimeTest {
    @Test fun checkpointSurvivesRecreationAndScreensOpen() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val rows=listOf(FullUniversePolicy.Entry(FullUniversePolicy.Market.BIST,"TEST","TEST FIXTURE",100.0,"TRY",
            FullUniversePolicy.Trend.FLAT,FullUniversePolicy.Quality.DELAYED,"TEST FIXTURE",1700000000000,1700000000001,
            "Yalnız emülatör testi",candles=List(25){i->Candle(1700000000000+i*86400000L,100.0,102.0,98.0,100.0,1000.0)}))
        ObservationCheckpoint(context).write(true,listOf("TEST","NEXT"),rows)
        val restored=ObservationCheckpoint(context).read()!!
        assertEquals(2,restored.universe.size);assertEquals(25,restored.rows.single().candles.size)
        assertEquals(100.0,restored.rows.single().price!!,0.001)
        capture("01-home",MainActivity::class.java)
        ActivityScenario.launch(FullUniverseScanActivity::class.java).use { scenario ->
            inst.waitForIdleSync();Thread.sleep(700);scenario.recreate();inst.waitForIdleSync()
            Thread.sleep(700)
            screenshot("02-scan-restored")
            fun findList(view:android.view.View):androidx.recyclerview.widget.RecyclerView? {
                if(view is androidx.recyclerview.widget.RecyclerView)return view
                if(view is android.view.ViewGroup)for(i in 0 until view.childCount) {
                    val found=findList(view.getChildAt(i));if(found!=null)return found
                }
                return null
            }
            scenario.onActivity { activity ->
                val list=findList(activity.window.decorView)!!
                assertEquals(1,list.adapter!!.itemCount)
                assertTrue(list.findViewHolderForAdapterPosition(0)!!.itemView.performClick())
            }
            inst.waitForIdleSync();screenshot("03-observed-detail-test-fixture")
            scenario.onActivity { it.activeDetailDialog?.dismiss() }
            ObservationCheckpoint(context).write(true,listOf("TEST"),rows)
            scenario.onActivity { tr.borsatakip.v5.worker.UniverseObservationService.start(it,true,true) }
            val deadline=System.currentTimeMillis()+10000
            while(System.currentTimeMillis()<deadline) {
                val current=FullUniverseScanRuntime.snapshots.value
                if(current.runId.isNotBlank() && current.state!=FullUniversePolicy.State.RUNNING && !FullUniverseScanRuntime.isRunning)break
                Thread.sleep(100)
            }
            val current=FullUniverseScanRuntime.snapshots.value
            assertEquals(FullUniversePolicy.State.PARTIAL,current.state)
            assertEquals(1,current.counters.triedBist)
            assertNull(tr.borsatakip.v5.scan.BistScanGate.snapshot())
            assertTrue(DiagnosticJournal(context).recent().any { it.code=="SCAN_STARTED_UNIVERSE" })
            assertEquals("PARTIAL",FullUniverseScanSnapshotStore(context).read()!!.status)
            screenshot("04-resumed-service-completed")
        }
    }
    private fun <T:androidx.appcompat.app.AppCompatActivity> capture(name:String,clazz:Class<T>) {
        ActivityScenario.launch(clazz).use { InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(700);screenshot(name) }
    }
    private fun screenshot(name:String) {
        val inst=InstrumentationRegistry.getInstrumentation()
        val dir=File(inst.targetContext.getExternalFilesDir(null),"visual-screenshots").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
