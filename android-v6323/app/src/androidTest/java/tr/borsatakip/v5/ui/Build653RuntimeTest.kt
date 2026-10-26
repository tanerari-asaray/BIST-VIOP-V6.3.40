package tr.borsatakip.v5.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import tr.borsatakip.v5.data.FullUniversePolicy as P
import tr.borsatakip.v5.data.favorites.BistWatchlistStore

@RunWith(AndroidJUnit4::class)
class Build653RuntimeTest {
 private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
 @Test fun detailWatchButtonWritesAndRemovesPersistentSymbol() {
  val store=BistWatchlistStore(context);store.remove("TEST")
  ActivityScenario.launch<FullUniverseScanActivity>(Intent(context,FullUniverseScanActivity::class.java)
   .putExtra(FullUniverseScanActivity.EXTRA_AUTO_START,false)).use { scene ->
   scene.onActivity { activity ->
    val item=P.Entry(P.Market.BIST,"TEST","Test",10.0,"TRY",P.Trend.FLAT,P.Quality.DELAYED,"Recorded",0,0,"")
    activity.javaClass.getDeclaredMethod("showDetail",P.Entry::class.java).apply { isAccessible=true }.invoke(activity,item)
   }
   scene.onActivity { activity ->
    val dialog=activity.activeDetailDialog ?: error("Analiz penceresi açılmadı")
    fun findButton(view:View, label:String):android.widget.Button? {
     if(view is android.widget.Button && view.text.toString()==label) return view
     if(view is ViewGroup) for(i in 0 until view.childCount) {
      findButton(view.getChildAt(i),label)?.let { return it }
     }
     return null
    }
    val root=dialog.window?.decorView ?: error("Analiz penceresinin görünümü yok")
    val add=findButton(root,"☆ TAKİBE EKLE") ?: error("Takibe ekle düğmesi yok")
    assertTrue(add.performClick())
    assertTrue(BistWatchlistStore(context).symbols().contains("TEST"))
    val remove=findButton(root,"★ TAKİPTEN ÇIKAR") ?: error("Takipten çıkar düğmesi yok")
    assertTrue(remove.performClick())
    assertFalse(BistWatchlistStore(context).symbols().contains("TEST"))
   }
  }
 }
 @Test fun financialCacheRestoresAfterClientRecreationAndRejectsOtherOrigin() {
  val settings=tr.borsatakip.v5.data.SettingsStore(context)
  val file=java.io.File(context.filesDir,"financial-cache-v1.json")
  val now=System.currentTimeMillis()
  val row=org.json.JSONObject().put("symbol","TEST").put("sector","industry").put("businessType","BANK")
   .put("accountingBasis","TMS29").put("financialCurrency","TRY").put("financialPeriodEndMs",now-70L*86400000)
   .put("disclosurePublishedAtMs",now-60L*86400000).put("marketObservedAtMs",now)
   .put("marketCapTry",1000).put("priceTry",10).put("sharesOutstanding",100).put("netIncomeTtmTry",100)
   .put("equityTry",500).put("disclosureUrl","https://www.kap.org.tr/tr/Bildirim/123")
  val root=org.json.JSONObject().put("origin",settings.baseUrl.trimEnd('/')).put("savedAt",now)
   .put("body",org.json.JSONObject().put("schemaVersion",1).put("items",org.json.JSONArray().put(row)))
  try {
   file.writeText(root.toString())
   val restored=kotlinx.coroutines.runBlocking { tr.borsatakip.v5.data.FundamentalsBatchClient(context).loadCached() }
   assertEquals("TEST",restored!!.first.items.single().symbol)
   root.put("origin","https://different.invalid");file.writeText(root.toString())
   assertNull(kotlinx.coroutines.runBlocking { tr.borsatakip.v5.data.FundamentalsBatchClient(context).loadCached() })
  } finally { file.delete() }
 }
 @Test fun systemReportDefaultsToShortSummary() {
  ActivityScenario.launch<SettingsDetailActivity>(Intent(context,SettingsDetailActivity::class.java)
   .putExtra(SettingsDetailActivity.EXTRA_MODE,SettingsDetailActivity.MODE_SYSTEM_STATUS)).use { scene ->
   scene.onActivity { activity ->
    fun texts(v:View):List<String> = when(v) {
     is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
     is TextView -> if(v.visibility==View.VISIBLE) listOf(v.text.toString()) else emptyList()
     else -> emptyList()
    }
    val all=texts(activity.window.decorView)
    val summary=all.first { it.startsWith("SİSTEM RAPORU • V") }
    assertTrue(summary.length<3000)
    assertFalse(all.any { it.contains("[21]") || it.contains("[16]") })
    assertTrue(summary.contains("GÜNCEL SORUNLAR"))
   }
  }
 }
}
