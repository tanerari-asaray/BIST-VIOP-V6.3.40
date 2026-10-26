package tr.borsatakip.v5.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tr.borsatakip.v5.scan.FundamentalValuationEngine as Engine
import java.util.concurrent.TimeUnit

/** Authenticated, opt-in fundamentals feed. No Yahoo price inference. */
class FundamentalsBatchClient(private val context:Context, private val connectionOverride: BackendConnectionConfig? = null) {
 private val localName="bist-financial-import-v1.json"
 private val maxBytes=8*1024*1024
 fun hasLocalImport():Boolean=File(context.filesDir,localName).isFile
 fun deleteLocalImport():Boolean=File(context.filesDir,localName).delete()
 suspend fun loadLocal():Batch=withContext(Dispatchers.IO){
  val f=File(context.filesDir,localName)
  require(f.isFile && f.length() in 1..maxBytes.toLong()) { "Kayıtlı finansal JSON yok veya dosya çok büyük" }
  parseJson(f.readText(Charsets.UTF_8),"KULLANICI JSON • KAP rakamları ve kaynaklar bağımsız doğrulanmadı; canlı veri değildir")
 }
 suspend fun importJson(uri:Uri):Batch=withContext(Dispatchers.IO){
  val bytes=context.contentResolver.openInputStream(uri)?.use{stream->
   val out=ByteArrayOutputStream()
   val buffer=ByteArray(16384)
   while(true){
    val count=stream.read(buffer)
    if(count<0)break
    require(out.size()+count<=maxBytes){"Finansal tablo dosyası 8 MB sınırını aşıyor"}
    out.write(buffer,0,count)
   }
   out.toByteArray()
  }?:error("Dosya okunamadı")
  require(bytes.isNotEmpty()){ "Finansal dosya boş" }
  val batch=parseJson(String(bytes,StandardCharsets.UTF_8),
   "YEREL JSON • Bildirilen KAP kaynağı ve sayılar bağımsız teyit edilmedi")
  require(batch.items.isNotEmpty()){ "İçe aktarılabilir finansal tablo satırı bulunamadı" }
  val tmp=File(context.filesDir,localName+".tmp")
  tmp.writeBytes(bytes)
  val stored=File(context.filesDir,localName)
  require(tmp.renameTo(stored)){"İçe aktarım dosyası kaydedilemedi"}
  batch
 }

 private val settings=SettingsStore(context)
 private val client=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS)
  .readTimeout(25,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
 data class Batch(val items:List<Engine.Snapshot>,val reportedCount:Int,val invalidItems:Int,val sourceNote:String)
 suspend fun load():Batch = withContext(Dispatchers.IO) {
  val connection=connectionOverride ?: settings.connectionSnapshot()
  val base=connection.baseUrl
  require(BackendUrlPolicy.isValid(base)&&connection.apiKey.isNotBlank()){
   "Lisanslı HTTPS finansal tablo servisi ve API anahtarı gerekir. Yahoo bilanço kaynağı değildir."
  }
  val request=Request.Builder().url(BackendUrlPolicy.endpoint(base,"/v1/bist/fundamentals"))
   .header("Authorization","Bearer "+connection.apiKey)
   .header("Accept","application/json").build()
  val body = try {
   client.newCall(request).awaitBody("BIST temel finansallar")
  } catch (e: IllegalArgumentException) {
   val explanation = FundamentalsEndpointDiagnostic.explain(e.message.orEmpty())
   if (explanation != null) throw IllegalStateException(explanation, e)
   throw e
  }
  require(body.toByteArray(Charsets.UTF_8).size<=maxBytes) { "Finansal yanıt çok büyük" }
  val batch=parseJson(body, "HTTPS backend • Finansal tablolar")
  require(batch.items.isNotEmpty()) { "Finansal yanıt boş" }
  val file=android.util.AtomicFile(File(context.filesDir,"financial-cache-v1.json"))
  val out=file.startWrite()
  try {
   val cached=JSONObject().put("origin",base).put("savedAt",System.currentTimeMillis()).put("body",JSONObject(body))
   out.write(cached.toString().toByteArray(Charsets.UTF_8));file.finishWrite(out)
  } catch(e:Exception) { file.failWrite(out); throw e }
  batch
 }
 suspend fun loadCached():Pair<Batch,Long>?=withContext(Dispatchers.IO) {
  try {
   val f=File(context.filesDir,"financial-cache-v1.json")
   if(!f.isFile || f.length()>maxBytes+4096) return@withContext null
   val root=JSONObject(android.util.AtomicFile(f).openRead().bufferedReader().use { it.readText() })
   val saved=root.getLong("savedAt")
   if(root.getString("origin")!=settings.baseUrl.trimEnd('/') || saved>System.currentTimeMillis() ||
      System.currentTimeMillis()-saved>24L*60*60*1000) return@withContext null
   parseJson(root.getJSONObject("body").toString(),"Kayıtlı finansal tablolar") to saved
  } catch(c:CancellationException) { throw c } catch(_:Exception) { null }
 }
 private fun parseJson(raw:String,source:String):Batch {
  val root=JSONObject(raw)
  require(root.optInt("schemaVersion",0)==1){"Finansal tablo şeması desteklenmiyor; schemaVersion=1 gerekli"}
  val entries=root.optJSONArray("items")?:error("items listesi yok")
  require(entries.length()<=10000){"Finansal tablo satır limiti aşıldı"}
  fun num(o:JSONObject,n:String):Double?=(o.opt(n) as? Number)?.toDouble()?.takeIf{it.isFinite()}
  val good=mutableListOf<Engine.Snapshot>()
  var bad=0
  for(i in 0 until entries.length()) {
   val x=entries.optJSONObject(i)
   if(x==null){bad++;continue}
   try {
    good.add(Engine.Snapshot(
     symbol=x.getString("symbol").trim().uppercase(java.util.Locale.ROOT),
     sector=x.getString("sector").trim(),
     businessType=Engine.BusinessType.valueOf(x.getString("businessType")),
     accountingBasis=x.getString("accountingBasis"),
     financialCurrency=x.getString("financialCurrency"),
     financialPeriodEndMs=x.getLong("financialPeriodEndMs"),
     disclosurePublishedAtMs=x.getLong("disclosurePublishedAtMs"),
     marketObservedAtMs=x.getLong("marketObservedAtMs"),
     marketCapTry=num(x,"marketCapTry")?:Double.NaN,
     priceTry=num(x,"priceTry")?:Double.NaN,
     sharesOutstanding=num(x,"sharesOutstanding")?:Double.NaN,
     netIncomeTtmTry=num(x,"netIncomeTtmTry"),
     equityTry=num(x,"equityTry"),
     ebitdaTtmTry=num(x,"ebitdaTtmTry"),
     netDebtTry=num(x,"netDebtTry"),
     freeCashFlowTtmTry=num(x,"freeCashFlowTtmTry"),
     disclosureUrl=x.getString("disclosureUrl"),
     companyName=x.optString("companyName").takeIf{it.isNotBlank()},
     priorNetIncomeTtmTry=num(x,"priorNetIncomeTtmTry"),
     priorFinancialPeriodEndMs=x.optLong("priorFinancialPeriodEndMs",0).takeIf{it>0},
     priorDisclosurePublishedAtMs=x.optLong("priorDisclosurePublishedAtMs",0).takeIf{it>0},
     priorAccountingBasis=x.optString("priorAccountingBasis").takeIf{it.isNotBlank()}
    ))
   } catch(ce:CancellationException){throw ce}
     catch(_:Exception){bad++}
  }
  return Batch(good,entries.length(),bad,source)
 }
}
