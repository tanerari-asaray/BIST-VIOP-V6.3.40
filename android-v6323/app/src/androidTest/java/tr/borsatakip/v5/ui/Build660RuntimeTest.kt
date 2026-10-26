package tr.borsatakip.v5.ui

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.data.FullUniversePolicy as P

/** Fixture-driven UI tests, not proof of a live provider. */
@RunWith(AndroidJUnit4::class)
class Build660RuntimeTest {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    @android.annotation.TargetApi(31)
    private fun capture(name:String, scene:ActivityScenario<FullUniverseScanActivity>) {
        val inst=InstrumentationRegistry.getInstrumentation();inst.waitForIdleSync()
        // Force a full display-list redraw before screen capture on the headless emulator.
        scene.onActivity { activity ->
            views(activity.window.decorView).forEach { it.invalidate() }
            activity.window.decorView.requestLayout()
        }
        // Idle main queue does not imply that the GPU has presented the RecyclerView frame.
        android.os.SystemClock.sleep(1000)
        inst.waitForIdleSync()
        val bitmap=inst.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        java.io.File(context.filesDir,"build660-screens").mkdirs()
        java.io.File(context.filesDir,"build660-screens/$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
        // UTP uninstalls the target after connected tests. Export while it still exists.
        val command="mkdir -p /data/local/tmp/build660-screens && run-as ${context.packageName} cat files/build660-screens/$name.png > /data/local/tmp/build660-screens/$name.png && echo EXPORTED\n"
        val streams=inst.uiAutomation.executeShellCommandRw("sh")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use { it.write(command.toByteArray()) }
        val result=android.os.ParcelFileDescriptor.AutoCloseInputStream(streams[0]).bufferedReader().use { it.readText().trim() }
        assertEquals("EXPORTED",result)
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun tabsAndPartialCountersWithExplicitTestFixtures() {
        ActivityScenario.launch<FullUniverseScanActivity>(Intent(context,FullUniverseScanActivity::class.java)
            .putExtra(FullUniverseScanActivity.EXTRA_AUTO_START,false)
            .putExtra(FullUniverseScanActivity.EXTRA_LOCAL_DELAYED_MODE,false)).use { scene ->
            scene.onActivity { activity ->
                val rows=activity.javaClass.getDeclaredField("entries").apply { isAccessible=true }.get(activity) as MutableList<P.Entry>
                rows.clear()
                fun row(m:P.Market,s:String,p:Double?)=P.Entry(m,s,"TEST VERİSİ • dayanak TEST • 2026-12 • Vadeli",p,"TRY",P.Trend.UNKNOWN,if(p==null)P.Quality.NO_DATA else P.Quality.DELAYED,"TEST FIXTURE",System.currentTimeMillis()-900000,System.currentTimeMillis(),"Bildirilen gecikme: 900 sn",reasonCode=if(p==null)"SERVICE_UNAVAILABLE" else null)
                rows.add(row(P.Market.BIST,"TEST",10.0));rows.add(row(P.Market.VIOP,"F_TEST1226",20.0));rows.add(row(P.Market.VIOP,"F_NODATA1226",null))
                activity.javaClass.getDeclaredField("counts").apply { isAccessible=true }.set(activity,P.Counters(discoveredBist=1,triedBist=1,successfulBist=1,discoveredViop=2,triedViop=2,successfulViop=1,errorsViop=1))
                activity.javaClass.getDeclaredField("state").apply { isAccessible=true }.set(activity,P.State.PARTIAL)
                activity.javaClass.getDeclaredMethod("refreshUI").apply { isAccessible=true }.invoke(activity)
                assertEquals(3,views(activity.window.decorView).filterIsInstance<RecyclerView>().single().adapter!!.itemCount)
                assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("VİOP başarılı: 1")&&it.text.contains("KISMİ") })
            }
            capture("01-all-fixture-partial", scene)
            for((tab,count) in listOf("HİSSELER" to 1,"VİOP" to 2,"TÜMÜ" to 3)) {
                scene.onActivity { activity ->
                    val all=views(activity.window.decorView)
                    assertTrue(all.filterIsInstance<Button>().single { it.text.toString()==tab }.performClick())
                    assertEquals(count,all.filterIsInstance<RecyclerView>().single().adapter!!.itemCount)
                }
                capture(if(tab=="HİSSELER")"02-stocks" else if(tab=="VİOP")"03-viop" else "04-all", scene)
            }
        }
    }
    @Test fun delayedModeOpensWithoutCredentialsAndShowsNoInventedViop() {
        ActivityScenario.launch<FullUniverseScanActivity>(Intent(context,FullUniverseScanActivity::class.java)
            .putExtra(FullUniverseScanActivity.EXTRA_AUTO_START,false)
            .putExtra(FullUniverseScanActivity.EXTRA_LOCAL_DELAYED_MODE,true)).use { scene ->
            scene.onActivity { activity ->
                val all=views(activity.window.decorView)
                assertTrue(all.filterIsInstance<TextView>().any { it.text.contains("GECİKMELİ") })
                all.filterIsInstance<Button>().single { it.text.toString()=="VİOP" }.performClick()
                assertEquals(0,all.filterIsInstance<RecyclerView>().single().adapter!!.itemCount)
            }
            capture("05-delayed-no-viop", scene)
        }
    }
}
