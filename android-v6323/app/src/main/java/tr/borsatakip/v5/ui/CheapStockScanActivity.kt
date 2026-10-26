package tr.borsatakip.v5.ui

import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ProgressBar
import tr.borsatakip.v5.scan.ValueOpportunityScoreEngine as Scores
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.data.FundamentalsBatchClient
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.SymbolUniverseTools
import tr.borsatakip.v5.data.UserSymbolListStore
import tr.borsatakip.v5.data.ValuationSourceGate
import tr.borsatakip.v5.scan.FundamentalValuationEngine as Engine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Auditable cross-sectional valuation: NO price ceiling and NO unverified BUY signals. */
class CheapStockScanActivity:BaseActivity() {
 private val tr=Locale("tr","TR")
 private var job:Job?=null
 private val importPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null && job?.isActive!=true){
   job=lifecycleScope.launch {
    status.text="Yerel dosya doğrulanıyor…"
    try{
     val batch=FundamentalsBatchClient(this@CheapStockScanActivity).importJson(uri)
     status.text="Dosya kaydedildi: "+batch.reportedCount+" kayıt; "+batch.items.size+
      " okunabilir; "+batch.invalidItems+" bozuk satır. Değerleme otomatik başlatılıyor. "+
      "Rakamlar bağımsız KAP doğrulamasından geçmedi."
     AppSession.lastCheapScanReport=status.text.toString()
     updateSourceStatus()
     runScan()
    }catch(e:Exception){
     status.text="İçe aktarma başarısız: "+e.message.orEmpty().take(220)+". Önceki veri korundu."
    }
   }
  }
 }
 private val symbolPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null && job?.isActive!=true){
   job=lifecycleScope.launch {
    try {
     val n=UserSymbolListStore(this@CheapStockScanActivity).importSymbols(uri)
     symbolStatus.text="Sembol listesi içe aktarıldı: $n kayıt. Yalnız sembol kodları; fiyat, bilanço ve güncellik doğrulanmadı."
     refreshSymbolList(force=true)
    }catch(e:Exception){
     symbolStatus.text="Sembol içe aktarılamadı: "+e.message.orEmpty().take(180)
    }
   }
  }
 }
 private var cachedDisplaySymbols:List<String> = emptyList()
 private lateinit var symbolInput:EditText
 private lateinit var symbolStatus:TextView
 private lateinit var symbolResults:TextView
 private var rows:List<Engine.Assessment> = emptyList()
 private var selected="ÖNE"
 private var page=5
 private var strategy=Scores.Strategy.BALANCED
 private lateinit var advancedArea:LinearLayout
 private lateinit var strategyRow:LinearLayout
 private lateinit var status:TextView
 private lateinit var sourceStatus:TextView
 private lateinit var list:LinearLayout
 private lateinit var tabs:LinearLayout
 private lateinit var go:Button
 private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
 private fun fmt(x:Double)=NumberFormat.getNumberInstance(tr).apply {
   maximumFractionDigits=2;minimumFractionDigits=2
 }.format(x)
 private fun time(x:Long)=SimpleDateFormat("dd.MM.yyyy",tr).format(Date(x))
 private fun text(v:LinearLayout,s:String,sz:Float=13f)=TextView(this).apply {
   text=s;textSize=sz;setTextColor(Color.rgb(61,77,100))
   setPadding(dp(4),dp(8),dp(4),dp(8))
   v.addView(this,LinearLayout.LayoutParams(-1,-2))
 }
 private fun button(v:LinearLayout,s:String,click:()->Unit)=Button(this).apply {
  text=s;isAllCaps=false;setOnClickListener{click()}
  minHeight=dp(48);setPadding(dp(8),dp(10),dp(8),dp(10))
  v.addView(this,LinearLayout.LayoutParams(-1,-2))
 }
 private fun shape(bg:Int,border:Int=Color.TRANSPARENT,corner:Int=16):GradientDrawable =
  GradientDrawable().apply { setColor(bg);cornerRadius=dp(corner).toFloat();if(border!=Color.TRANSPARENT)setStroke(dp(1),border) }
 private fun block(parent:LinearLayout,bg:Int=Color.WHITE,border:Int=Color.TRANSPARENT):LinearLayout =
  LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12));background=shape(bg,border)
   val lp=LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(this,lp)
  }
 private fun title(parent:LinearLayout,words:String,size:Float=19f,color:Int=Color.rgb(14,31,57)):TextView =
  text(parent,words,size).apply{setTextColor(color);setTypeface(null,Typeface.BOLD)}
 private fun coloredButton(parent:LinearLayout,words:String,bg:Int,color:Int=Color.WHITE,action:()->Unit):Button =
  button(parent,words,action).apply{background=shape(bg);setTextColor(color);setTypeface(null,Typeface.BOLD)}
 private fun strategyChoices(){
  strategyRow.removeAllViews()
  for(candidate in Scores.Strategy.values()) {
   val chosen=candidate==strategy
   val tint=if(chosen)Color.rgb(228,241,255) else Color.WHITE
   val card=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER
    setPadding(dp(5),dp(12),dp(5),dp(12))
    background=shape(tint,if(chosen)Color.rgb(24,109,255) else Color.rgb(216,226,238),13)
   }
   val emoji=when(candidate){Scores.Strategy.BALANCED->"⚖";Scores.Strategy.QUALITY->"✓";Scores.Strategy.DISCOUNT->"◇"}
   title(card,emoji,24f,if(chosen)Color.rgb(23,107,248) else Color.rgb(30,126,138)).gravity=android.view.Gravity.CENTER
   title(card,candidate.display,12f).gravity=android.view.Gravity.CENTER
   text(card,when(candidate){
    Scores.Strategy.BALANCED->"Değer + kalite + sağlamlık"
    Scores.Strategy.QUALITY->"Finansal kalite öncelikli"
    Scores.Strategy.DISCOUNT->"Göreli iskonto öncelikli"
   },10f).apply{gravity=android.view.Gravity.CENTER}
   card.setOnClickListener {strategy=candidate;strategyChoices();if(rows.isNotEmpty())render()}
   strategyRow.addView(card,LinearLayout.LayoutParams(0,dp(120),1f).apply{marginEnd=dp(4)})
  }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val scroll=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(Color.rgb(246,249,253))}
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(15),dp(15),dp(25))
  }
  scroll.addView(root);setContentView(scroll)
  ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
   val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
   view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
  }
  ViewCompat.requestApplyInsets(scroll)
  text(root,"← BIST TARAMAYA DÖN",13f).apply {setTextColor(Color.rgb(22,103,240));setOnClickListener{finish()}}
  title(root,"Akıllı Değer ve Fırsat Avcısı",23f)
  coloredButton(root,"▣  BIST + VİOP • TÜM EVREN DURUMU",Color.rgb(23,94,182)) {
   startActivity(Intent(this,FullUniverseScanActivity::class.java))
  }
  text(root,"BIST • Finansal kalite, göreli değer ve risk analizi",13f)
  val chooser=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  root.addView(chooser,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
  val valueTab=Button(this).apply{text="⚖ TEMEL DEĞER";isAllCaps=false}
  val delayedTab=Button(this).apply{text="◷ GECİKMELİ TARAMA";isAllCaps=false}
  chooser.addView(valueTab,LinearLayout.LayoutParams(0,dp(54),1f))
  chooser.addView(delayedTab,LinearLayout.LayoutParams(0,dp(54),1f))
  val valueSection=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val delayedSection=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE}
  root.addView(valueSection);root.addView(delayedSection)
  fun selectPanel(delayed:Boolean){
   valueSection.visibility=if(delayed)android.view.View.GONE else android.view.View.VISIBLE
   delayedSection.visibility=if(delayed)android.view.View.VISIBLE else android.view.View.GONE
   valueTab.background=shape(if(delayed)Color.rgb(231,239,250) else Color.rgb(21,107,228),corner=12)
   delayedTab.background=shape(if(delayed)Color.rgb(21,107,228) else Color.rgb(231,239,250),corner=12)
   valueTab.setTextColor(if(delayed)Color.rgb(31,71,126) else Color.WHITE)
   delayedTab.setTextColor(if(delayed)Color.WHITE else Color.rgb(31,71,126))
  }
  valueTab.setOnClickListener{selectPanel(false)}
  delayedTab.setOnClickListener{launchDelayedObservation()}
  val delayedHero=block(delayedSection,Color.rgb(231,241,254))
  title(delayedHero,"Gecikmeli teknik tarama",20f)
  text(delayedHero,"Kullanıcı listesindeki bütün erişilebilir semboller gecikmeli/deneysel gözlemlenir. Tam BIST evreni olduğu iddia edilmez.",13f)
  val caution=block(delayedSection,Color.rgb(255,245,222))
  title(caution,"ÖNEMLİ • CANLI AL/SAT DEĞİLDİR",14f,Color.rgb(148,83,20))
  text(caution,"Yahoo Finance yedeği gecikmeli, eksik veya erişilemez olabilir. Kaynağın kullanım koşullarına uymalısınız. Giriş, stop, hedef, canlı fiyat veya işlem uygunluğu onayı üretilmez. Bilanço ve 'ucuz hisse' puanı hesaplanmaz.",12f)
  coloredButton(delayedSection,"▶  GECİKMELİ TARAMAYI BAŞLAT",Color.rgb(21,107,228)) {
   launchDelayedObservation()
  }
  text(delayedSection,"Kapsam: kullanıcı ve önbellek listesindeki bütün geçerli semboller. TÜMÜ / HİSSELER / VİOP yeni ekranda ayrı gösterilir. Tam BIST kapsamı kanıtlanmadan iddia edilmez.",12f)
  selectPanel(false)
  val hero=block(valueSection,Color.rgb(228,247,245))
  title(hero,"BIST genelinde değer fırsatlarını araştır",18f)
  text(hero,"Şirketleri yalnız fiyatlarına göre değil; finansal tablo, emsal karşılaştırması, kârlılık ve bilanço riskiyle değerlendirir.",13f)
  text(hero,"Yalnız yasal erişimi bulunan kaynaklar kullanılır. Veri yoksa sonuç uydurulmaz.",11f)
  title(valueSection,"Tarama stratejini seç",17f)
  strategyRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};valueSection.addView(strategyRow)
  strategyChoices()
  text(valueSection,"Dengeli: %50 değer / %30 kalite / %20 sağlamlık. Diğer modlar analiz önceliğini değiştirir; kanıt kapılarını gevşetmez.",11f)
  go=coloredButton(valueSection,"▶  BIST GENELİNİ DEĞERLE",Color.rgb(29,112,241)) {
   if(job?.isActive==true)job?.cancel()else beginValuation()
  }
  go.minHeight=dp(58)
  sourceStatus=text(valueSection,"Finansal kaynak kontrol ediliyor.",12f)
  val warning=block(root,Color.rgb(255,247,229))
  title(warning,"Veri güvenliği",14f,Color.rgb(143,88,21))
  text(warning,"Fiyat listesi bilanço değildir. Kaynak ve dönem doğrulanmadan 'güçlü fırsat' veya AL/SAT kararı gösterilmez. Lisanslı canlı veri bağlantısı bu sürümde mevcut değildir.",12f)
  val how=block(root)
  title(how,"Nasıl çalışır?",16f)
  text(how,"1  Finansalları doğrula    2  Emsalleri karşılaştır    3  İskontoyu ve riskleri açıkla",12f)
  coloredButton(valueSection,"⚙  GELİŞMİŞ • SEMBOL ARAMA VE VERİ İÇE AKTARMA",Color.rgb(228,238,252),Color.rgb(30,71,132)) {
   advancedArea.visibility=if(advancedArea.visibility==android.view.View.GONE)android.view.View.VISIBLE else android.view.View.GONE
  }
  advancedArea=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE}
  valueSection.addView(advancedArea)
  val advanced=block(advancedArea)
  title(advanced,"Çevrimdışı BIST sembol arama",16f)
  text(advanced,"Yalnız telefona kaydedilmiş veya kullanıcı tarafından aktarılmış semboller aranır. Sembolün bulunması finansal veri bulunduğu anlamına gelmez.",11f)
  symbolInput=EditText(this).apply{
   hint="Sembol ara: THYAO, EREGL...";setSingleLine(true);textSize=15f
   setTextColor(Color.rgb(15,33,54));setHintTextColor(Color.GRAY)
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
  }
  advanced.addView(symbolInput,LinearLayout.LayoutParams(-1,-2))
  button(advanced,"KAYITLI SEMBOLLERİ GÖSTER / ARA") {refreshSymbolList()}
  button(advanced,"SEMBOL LİSTESİ (.TXT / .CSV) SEÇ") {
   symbolPicker.launch(arrayOf("text/plain","text/csv","application/octet-stream"))
  }
  symbolStatus=text(advanced,"Yerel sembol listesi henüz kontrol edilmedi.",11f)
  symbolResults=text(advanced,"Önce kayıtlı listeyi açın; fiyat/bilanço uydurulmaz.",11f)
  symbolInput.addTextChangedListener(object:TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){renderSymbols()}
   override fun afterTextChanged(s:Editable?){}
  })
  title(advanced,"Finansal veri ve kaynak",16f)
  button(advanced,"FİNANSAL TABLO JSON DOSYASI SEÇ") {
   importPicker.launch(arrayOf("application/json","text/plain","application/octet-stream"))
  }
  button(advanced,"KAP RESMÎ FİNANSAL TABLOLARI") {
   startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.kap.org.tr/tr/")))
  }
  text(advanced,"KAP PDF/.rar bu sürümde otomatik ayrıştırılmaz. İçe aktarılan JSON'un KAP aslıyla bire bir uyumu bağımsız olarak teyit edilemez.",11f)
  button(advanced,"YEREL FİNANSAL VERİYİ SİL") {
   if(job?.isActive!=true){
    val deleted=FundamentalsBatchClient(this@CheapStockScanActivity).deleteLocalImport()
    status.text=if(deleted)"Yerel finansal veriler silindi." else "Silinecek yerel finansal veri yok."
    rows=emptyList();list.removeAllViews();tabs.removeAllViews()
    AppSession.lastCheapScanReport=status.text.toString();updateSourceStatus()
   }
  }
  title(valueSection,"Tarama sonuçları",19f)
  status=text(valueSection,"Henüz değerleme yapılmadı. Kaynak yoksa aday veya yapay puan üretilmez.",12f)
  tabs=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};valueSection.addView(tabs)
  list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};valueSection.addView(list)
 }
 override fun onResume() {
  super.onResume()
  if(::sourceStatus.isInitialized) updateSourceStatus()
 }
 private fun sourceMode():ValuationSourceGate.Mode {
  val financial=FundamentalsBatchClient(this)
  val config=SettingsStore(this)
  return ValuationSourceGate.decide(financial.hasLocalImport(),config.baseUrl,config.apiKey.isNotBlank())
 }
 private fun updateSourceStatus(){
  sourceStatus.text=when(sourceMode()){
   ValuationSourceGate.Mode.LOCAL_JSON ->
    "Yerel finansal JSON var • Kaynak bağımsız teyitsiz • Canlı veri değil."
   ValuationSourceGate.Mode.CONFIGURED_HTTPS ->
    "HTTPS finansal kaynak yapılandırılmış; yetki, veri ve güncellik henüz doğrulanmadı."
   ValuationSourceGate.Mode.MISSING_API_KEY ->
    "Finansal veriler yok: API anahtarı eksik. Gelişmiş alandan kendi verini yükleyebilirsin."
   ValuationSourceGate.Mode.MISSING_FINANCIAL_SOURCE ->
    "Finansal veri yok: JSON yükle veya yetkili finansal kaynak yapılandır; bu koşulda hisse puanlanmaz."
  }
 }
 private fun refreshSymbolList(force:Boolean=false){
  if(!force && job?.isActive==true){symbolStatus.text="Başka işlem devam ediyor, tekrar deneyin.";return}
  job=lifecycleScope.launch {
   symbolStatus.text="Yerel semboller okunuyor (internet erişimi kullanılmaz)…"
   try {
    val store=SettingsStore(this@CheapStockScanActivity)
    val manual=withContext(Dispatchers.IO){UserSymbolListStore(this@CheapStockScanActivity).loadSymbols()}
    val finance=FundamentalsBatchClient(this@CheapStockScanActivity)
    val financialSymbols=if(finance.hasLocalImport())try{finance.loadLocal().items.map{it.symbol}}catch(_:Exception){emptyList()}else emptyList()
    cachedDisplaySymbols=SymbolUniverseTools.normalize(store.cachedBistSymbols+manual+financialSymbols)
    symbolStatus.text="Kayıtlı sembol: "+cachedDisplaySymbols.size+
     " • Finansal JSON sembolü: "+financialSymbols.size+
     " • Kaynak: yerel önbellek/kullanıcı dosyası (tamlık ve güncellik doğrulanmadı)."
    renderSymbols()
   }catch(e:Exception){symbolStatus.text="Yerel sembol listesi okunamadı: "+e.message.orEmpty().take(140)}
  }
 }
 private fun renderSymbols(){
  if(!::symbolResults.isInitialized||!::symbolInput.isInitialized)return
  val found=SymbolUniverseTools.search(cachedDisplaySymbols,symbolInput.text.toString(),120)
  symbolResults.text=when{
   cachedDisplaySymbols.isEmpty()->"Kayıtlı sembol yok. Kullanıcıya ait .TXT/.CSV liste seçin ya da KAP kaynaklı finansal şema-v1 JSON içe aktarın."
   found.isEmpty()->"Bu aramayla eşleşen kayıtlı sembol yok. Sembol listesi eksik olabilir."
   else->"EŞLEŞEN: "+SymbolUniverseTools.count(cachedDisplaySymbols,symbolInput.text.toString())+
    " • İlk "+found.size+" sembol\n"+found.chunked(6).joinToString("\n"){it.joinToString(" • ")}+
    "\nBu liste fiyat, bilanço veya yatırım sinyali içermez."
  }
 }
 /** Opens the existing delayed/experimental price observation without claiming fundamental valuation. */
 private fun launchDelayedObservation(){
  startActivity(Intent(this,FullUniverseScanActivity::class.java)
   .putExtra(FullUniverseScanActivity.EXTRA_LOCAL_DELAYED_MODE,true)
   .putExtra(FullUniverseScanActivity.EXTRA_AUTO_START,true))
 }
 private fun beginValuation(){
  val mode=sourceMode()
  if(ValuationSourceGate.canCalculate(mode)){runScan();return}
  rows=emptyList();tabs.removeAllViews();list.removeAllViews()
  val msg=if(mode==ValuationSourceGate.Mode.MISSING_API_KEY)
   "HTTPS finansal servis adresi var ancak API anahtarı eksik. Temel değerleme için " +
    "yetkili finansal tablo verisi gerekir."
  else
   "Temel değerleme için gerçek bilanço ve finansal tablo verisi gerekiyor. " +
    "Şu anda JSON dosyası veya yetkili finansal HTTPS kaynağı bulunamadı. " +
    "Finansal ucuzluk puanı hesaplanmadı."
  status.text="TEMEL DEĞERLEME BAŞLATILAMADI • FİNANSAL VERİ KAYNAĞI YOK\n"+msg+
   "\nAlternatif: Gecikmeli teknik gözlem (kayıtlı listenin tümü; temel değerleme değildir)."
  AppSession.lastCheapScanReport=status.text.toString()
  updateSourceStatus()
  // Dialog keeps the three existing actions, and adds a prominent fourth action
  // inside the body rather than overloading Android's three-button footer.
  val alternative=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(16),dp(5),dp(16),dp(7))
  }
  val notice=block(alternative,Color.rgb(230,241,255))
  title(notice,"ALTERNATİF • GECİKMELİ TEKNİK TARAMA",13f,Color.rgb(22,85,158))
  text(notice,
   "Kayıtlı listedeki bütün geçerli sembollerin fiyatı ve mevcutsa 10 günlük eğilimi incelenir. " +
   "Veri gecikmeli, eksik veya erişilemez olabilir. " +
   "Bilanço, ucuzluk puanı, AL/SAT veya işlem sinyali üretilmez.",12f)
  val delayedButton=coloredButton(alternative,"▶  GECİKMELİ VERİYLE TARA",Color.rgb(14,113,195)) {}
  val dialog=android.app.AlertDialog.Builder(this)
   .setTitle("Finansal tablo verisi gerekli")
   .setMessage(msg)
   .setView(alternative)
   .setPositiveButton("JSON DOSYASI SEÇ"){_,_->
    importPicker.launch(arrayOf("application/json","text/plain","application/octet-stream"))
   }
   .setNeutralButton("VERİ BAĞLANTISI"){_,_->
    startActivity(Intent(this,SettingsDetailActivity::class.java)
     .putExtra(SettingsDetailActivity.EXTRA_MODE,SettingsDetailActivity.MODE_DATA_CONNECTION))
   }
   .setNegativeButton("KAPAT",null)
   .create()
  delayedButton.setOnClickListener {
   dialog.dismiss()
   launchDelayedObservation()
  }
  dialog.show()
 }
 private fun ranked():List<Scores.Ranked> = Scores.rank(rows,strategy)
 private fun filtered():List<Scores.Ranked> = ranked().filter { item ->
  when(selected){
   "ÖNE" -> item.score?.group==Scores.Group.FEATURED
   "İZLE" -> item.score?.group==Scores.Group.WATCH
   "RİSK" -> item.score?.group==Scores.Group.VALUE_TRAP
   "EKSİK" -> item.score==null
   else -> true
  }
 }
 private fun label(s:Engine.Status)=when(s){
  Engine.Status.RELATIVE_DISCOUNT->"MODEL: GÖRELİ İSKONTO • BAĞIMSIZ TEYİTSİZ"
  Engine.Status.FINANCIAL_RISK->"FİNANSAL RİSK / DEĞER TUZAĞI"
  Engine.Status.NOT_DISCOUNT->"İSKONTO KANITI YOK"
  Engine.Status.INSUFFICIENT_PEERS->"YETERSİZ EMSAL"
  Engine.Status.INVALID_OR_STALE->"GEÇERSİZ / ESKİ VERİ"
  Engine.Status.NO_FINANCIALS->"BİLANÇO VERİSİ YOK"
 }
 private fun runScan(){
  go.text="TARAMAYI DURDUR";rows=emptyList();list.removeAllViews()
  status.text="BIST evreni ve finansal veriler kontrol ediliyor…"
  job=lifecycleScope.launch {
   try {
    val fundamentals=FundamentalsBatchClient(this@CheapStockScanActivity)
    val local=fundamentals.hasLocalImport()
    val batch=try { if(local) fundamentals.loadLocal() else fundamentals.load() }
       catch(c:CancellationException){throw c}
       catch(e:Exception){
        status.text="Değerleme yapılamadı: "+e.message.orEmpty().take(220)+
         "\nFinansal tablo olmadan ucuz hisse sonucu oluşturulmadı. Sembol listesini çevrimdışı arayabilirsiniz."
        AppSession.lastCheapScanReport=status.text.toString();return@launch
       }
    // Yerel kullanıcı finansalları, lisanslı çevrimiçi sembol API'sine bağlı kalmadan değerlendirilebilir.
    // Yerel JSON'da bulunmayan semboller için kapsam iddiası yapılmaz.
    val universe=if(local) SymbolUniverseTools.normalize(batch.items.map{it.symbol})
     else {
      try { SymbolUniverseTools.normalize(
       ProviderRouter(this@CheapStockScanActivity,experimentalFallbackOverride=false).listSymbols()) }
      catch(e:Exception){
       status.text="Finansal veri alındı, ancak yetkili BIST sembol evreni doğrulanamadı: "+
        e.message.orEmpty().take(180)+". Yanlış kapsamla değerleme yapılmadı."
       AppSession.lastCheapScanReport=status.text.toString();return@launch
      }
     }
    if(universe.isEmpty()){status.text="Değerlenebilir sembol yok. Finansal dosya/evren boş; sonuç üretilmedi.";return@launch}
    status.text=""+(if(local)"YEREL FİNANSAL JSON EVRENİ: " else "BIST API EVRENİ: ")+
      universe.size+" sembol • Finansal kayıtlar kontrol ediliyor"
    rows=withContext(Dispatchers.Default) { Engine.scan(universe,batch.items,System.currentTimeMillis()) }
    val counts=Engine.Status.values().associateWith{s->rows.count{it.status==s}}
    val summary=(if(local)"KULLANICI JSON EVRENİ • Tüm BIST kapsamı iddia edilmez" else "BIST API EVRENİ")+
      " • Evren "+universe.size+
       " • Sektörel göreli iskontolu: "+counts[Engine.Status.RELATIVE_DISCOUNT]+
       " • Risk: "+counts[Engine.Status.FINANCIAL_RISK]+
       " • İskonto kanıtı yok: "+counts[Engine.Status.NOT_DISCOUNT]+
       " • Emsal yetersiz: "+counts[Engine.Status.INSUFFICIENT_PEERS]+
       " • Finansal veri yok: "+counts[Engine.Status.NO_FINANCIALS]+
       " • Geçersiz/eski: "+counts[Engine.Status.INVALID_OR_STALE]+
       " • Geçersiz finansal satırlar: "+batch.invalidItems+
       "\n"+batch.sourceNote+
       "\nFiyat limiti uygulanmadı. KAP bağlantıları ayrıca kontrol edilmelidir. Gerçek değer ve AL/SAT sonucu doğrulanmadı."
    status.text=summary;AppSession.lastCheapScanReport=summary
    selected="ÖNE";page=5;render()
   }catch(_:CancellationException){
     rows=emptyList();list.removeAllViews();status.text="Tarama iptal edildi; kısmi sonuç sunulmadı"
   }catch(e:Exception){
     rows=emptyList();list.removeAllViews()
     status.text="Finansal tarama hatası: "+e.message.orEmpty().take(180)
   }finally{go.text="▶  BIST GENELİNİ DEĞERLE"}
  }
 }
 private fun metricBar(parent:LinearLayout,name:String,value:Int,color:Int){
  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
  title(row,name,11f).apply{layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
  text(row,"$value/100",11f).setTextColor(Color.rgb(25,43,72))
  parent.addView(row)
  val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
   max=100;progress=value;progressTintList=android.content.res.ColorStateList.valueOf(color)
   progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(227,235,242))
  }
  parent.addView(bar,LinearLayout.LayoutParams(-1,dp(6)).apply{bottomMargin=dp(6)})
 }
 private fun card(parent:LinearLayout,item:Scores.Ranked){
  val a=item.assessment
  val data=item.score
  val tint=when(data?.group){
   Scores.Group.FEATURED->Color.rgb(19,156,89)
   Scores.Group.WATCH->Color.rgb(234,163,32)
   Scores.Group.VALUE_TRAP->Color.rgb(217,64,72)
   else->Color.rgb(90,116,152)
  }
  val box=block(parent)
  val heading=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
  title(heading,a.symbol,20f).apply{layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
  title(heading,if(data!=null)"${data.total}/100" else "—",18f,tint)
  box.addView(heading)
  text(box,(a.snapshot?.companyName ?: "Şirket adı yok")+" • "+(a.snapshot?.sector?:"Sektör bilinmiyor"),12f)
  val label=when(data?.group){
   Scores.Group.FEATURED->"● MODELDE ÖNE ÇIKAN • TEYİTSİZ"
   Scores.Group.WATCH->"● İZLENECEK / İSKONTO TEYİDİ YETERSİZ"
   Scores.Group.VALUE_TRAP->"▲ DEĞER TUZAĞI RİSKİ"
   else->"● VERİ YETERSİZ / HESAPLANAMADI"
  }
  title(box,label,11f,tint)
  if(data!=null){
   metricBar(box,"Göreli değer",data.value,Color.rgb(18,162,93))
   metricBar(box,"Finansal kalite",data.quality,Color.rgb(43,138,203))
   metricBar(box,"Finansal sağlamlık",data.safety,Color.rgb(226,162,43))
   text(box,"Zamanlama: ÖLÇÜLMEDİ • Yeterli ve lisanslı OHLCV yok.",11f)
   text(box,data.explanation,12f)
  }
  text(box,"Kanıt: "+a.reason+"\nEmsal sayısı: "+a.peerCount+" • Kaynak bağımsız teyitsiz.",11f)
  title(box,"DETAY VE KAP KAYNAĞINI GÖR  ›",11f,Color.rgb(32,108,215))
  box.setOnClickListener{details(a)}
 }
 private fun render(){
  tabs.removeAllViews();list.removeAllViews()
  val data=ranked()
  val summary=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val featured=data.count{it.score?.group==Scores.Group.FEATURED}
  val watch=data.count{it.score?.group==Scores.Group.WATCH}
  val risk=data.count{it.score?.group==Scores.Group.VALUE_TRAP}
  val unscored=data.count{it.score==null}
  val c=block(summary,Color.rgb(235,244,252))
  title(c,"Öne çıkan $featured   •   İzlenecek $watch   •   Riskli $risk",14f)
  text(c,"Veri yetersiz / ölçülemeyen: $unscored • Toplam sembol: ${data.size} • Strateji: ${strategy.display}",11f)
  tabs.addView(summary)
  for((id,name) in listOf("ÖNE" to "ÖNE ÇIKAN", "İZLE" to "İZLENECEK", "RİSK" to "DEĞER TUZAĞI", "EKSİK" to "VERİ YETERSİZ", "TÜMÜ" to "TÜM KAYITLAR")) {
   val count=when(id){"ÖNE"->featured;"İZLE"->watch;"RİSK"->risk;"EKSİK"->unscored;else->rows.size}
   coloredButton(tabs,"$name ($count)"+(if(selected==id)" ✓" else ""),
    if(selected==id)Color.rgb(34,108,226) else Color.rgb(228,238,251),
    if(selected==id)Color.WHITE else Color.rgb(28,61,115)) {
    selected=id;page=if(id=="ÖNE")5 else 20;render()
   }
  }
  val items=filtered()
  title(list,"Sonuçlar: ${items.size} kayıt",16f)
  if(items.isEmpty()) {
   text(list,"Bu grupta kanıtlı aday yok. Eksik veri başarı veya başarısız değerleme değildir. Gelişmiş alandan finansal tablo ekleyebilirsin.",12f)
   return
  }
  items.take(page).forEach{card(list,it)}
  if(items.size>page)coloredButton(list,"DAHA FAZLA GÖSTER (${items.size-page})",Color.rgb(231,240,252),Color.rgb(32,83,151)){
   page+=20;render()
  }
  text(list,"PUAN SINIRI: 0–100 yalnızca denetlenebilir model kıyasıdır. Fiyat hedefi, doğrulanmış içsel değer veya AL/SAT sinyali değildir. Finansal tablolar ve KAP bağlantıları bağımsız doğrulanmadı.",11f)
 }
 private fun details(a:Engine.Assessment) {
  val s=a.snapshot
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14))}
  text(panel,a.symbol+" • "+label(a.status)+"\n"+a.reason)
  val ranking=Scores.score(a,strategy)
  if(ranking!=null){
   title(panel,"${strategy.display} model: ${ranking.total}/100",17f)
   text(panel,"Değer ${ranking.value}/100 • Kalite ${ranking.quality}/100 • Sağlamlık ${ranking.safety}/100 • Risk ${ranking.risk}/100\n"+
    "Değer tuzağı/iskonto durumu: ${ranking.group}\n"+ranking.explanation+"\n"+ranking.limitations,12f)
  }else text(panel,"Bu kayıt için doğrulanabilir puan yok: veri/emsal veya risk girdileri yetersiz.",12f)
  text(panel,"NASIL HESAPLANIR?\n"+
   "1 • F/K = Piyasa Değeri / Son 12 Ay Net Kâr (veya Hisse Fiyatı / Pay Başına Kâr). "+
   "Pozitif kârda, sektördeki benzerlerinden düşük olması görece iskontoyu gösterebilir; tek başına yeterli değildir.\n\n"+
   "2 • PD/DD = Piyasa Değeri / Ana ortaklık özkaynağı. "+
   "Özkaynak kalitesi, enflasyon muhasebesi, aktif kârlılığı ve sektör farkları değerlendirilmeden düşük PD/DD 'ucuz' değildir.\n\n"+
   "3 • Net Borç/FAVÖK = (Finansal Borçlar − Nakit Benzerleri) / Son 12 Ay FAVÖK. "+
   "Borcun faaliyet kazancına göre yükünü yaklaşık gösterir; gerçek geri ödeme süresi değildir. "+
   "Banka ve finans kuruluşlarında sanayi şirketi borç ölçütü uygulanmaz.\n\n"+
   "4 • FD/FAVÖK = (Piyasa Değeri + Net Borç) / Son 12 Ay FAVÖK. "+
   "İşletme değerini faaliyet kârlılığına göre kıyaslar; finans dışı şirketlerde kullanılır.\n\n"+
   "5 • SEKTÖR KIYASI = (1 − Şirket Çarpanı / Emsal Medyanı) × 100. "+
   "Şirket, yalnız aynı sektördeki, benzer iş modelindeki ve aynı muhasebe bazındaki geçerli en az 5 emsale göre incelenir. "+
   "Medyan tek bir aşırı değerden daha az etkilenir. İki çarpanda en az %20 iskonto + kârlılık ve borç/nakit akışı filtresi gerekir.\n\n"+
   "ÖNEMLİ: Çarpan iskontosu gelecekteki kârı, adil değeri veya hisse fiyatının yükseleceğini ispatlamaz.",12f)
  if(s!=null){
   val m=a.multiples
   text(panel,"Şirket: "+(s.companyName?:"")+"\nSektör: "+s.sector+
    "\nİş türü: "+s.businessType+" • Muhasebe: "+s.accountingBasis+
    "\nDönem: "+time(s.financialPeriodEndMs)+" • KAP yayın: "+time(s.disclosurePublishedAtMs)+
    "\nFiyat ölçümü: "+time(s.marketObservedAtMs)+
    "\nPiyasa değeri: "+fmt(s.marketCapTry)+" TRY"+
    "\nTTM net kâr: "+(s.netIncomeTtmTry?.let{fmt(it)}?:"YOK")+
    "\nÖzkaynak: "+(s.equityTry?.let{fmt(it)}?:"YOK")+
    "\nFAVÖK: "+(s.ebitdaTtmTry?.let{fmt(it)}?:"YOK")+
    "\nNet borç: "+(s.netDebtTry?.let{fmt(it)}?:"YOK")+
    "\nSerbest nakit akımı: "+(s.freeCashFlowTtmTry?.let{fmt(it)}?:"YOK")+
    "\nF/K: "+(m?.pe?.let{fmt(it)}?:"—")+
    "\nPD/DD: "+(m?.pb?.let{fmt(it)}?:"—")+
    "\nFD/FAVÖK: "+(m?.evEbitda?.let{fmt(it)}?:"—")+
    "\nROE: "+(m?.roe?.let{fmt(it*100)}?:"—")+"%"+
    "\nNet borç/FAVÖK: "+(m?.netDebtToEbitda?.let{fmt(it)}?:"—")+
    "\nEmsal sayısı: "+a.peerCount)
   if(s.disclosureUrl.startsWith("https://www.kap.org.tr/")||s.disclosureUrl.startsWith("https://kap.org.tr/"))
    button(panel,"KAP KAYNAK BAĞLANTISINI AÇ"){
     startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(s.disclosureUrl)))
    }
  }
  text(panel,"KAP rakamları bağımsız doğrulanmadıkça sonuç kesin ucuzluk, içsel değer veya AL/SAT sinyali değildir.",12f)
  val scroll=ScrollView(this);scroll.addView(panel)
  android.app.AlertDialog.Builder(this).setTitle("DEĞERLEME KANITI").setView(scroll).setPositiveButton("Kapat",null).show()
 }
 override fun onDestroy(){job?.cancel();super.onDestroy()}
}
