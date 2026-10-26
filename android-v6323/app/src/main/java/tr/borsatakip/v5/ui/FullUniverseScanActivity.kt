package tr.borsatakip.v5.ui

import tr.borsatakip.v5.BuildConfig
import android.content.Intent
import android.graphics.Color
import android.widget.ScrollView
import android.graphics.Typeface
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import tr.borsatakip.v5.data.favorites.BistWatchlistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tr.borsatakip.v5.data.ObservationAnalysis
import tr.borsatakip.v5.data.ObservationCheckpoint
import tr.borsatakip.v5.data.YahooFallbackProvider
import tr.borsatakip.v5.worker.UniverseObservationService
import tr.borsatakip.v5.data.FullUniversePolicy
import tr.borsatakip.v5.data.UserSymbolListStore
import tr.borsatakip.v5.data.FundamentalsBatchClient
import tr.borsatakip.v5.scan.FundamentalValuationEngine as Valuation
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.BackendUrlPolicy
import tr.borsatakip.v5.data.ViopWatchlistStore
import tr.borsatakip.v5.data.FullUniverseScanSnapshotStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conservative full-universe discovery and observation UI, NOT a valuation/trading engine.
 * No arbitrary 30-symbol limit. Backend has to return a legitimate symbol universe.
 * In the absence of provider availability, discovery fails visibly and no result is invented.
 */
class FullUniverseScanActivity : BaseActivity() {
    companion object {
        const val EXTRA_LOCAL_DELAYED_MODE = "tr.borsatakip.v5.EXTRA_LOCAL_DELAYED_MODE"
        const val EXTRA_AUTO_START = "tr.borsatakip.v5.EXTRA_AUTO_START_UNIVERSE_SCAN"
    }
    private var localMode = false
    private var startedAt = 0L
    private val symbolPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (FullUniverseScanRuntime.isRunning) {
                AlertDialog.Builder(this).setMessage("Listeyi değiştirmeden önce devam eden taramayı tamamlayın veya durdurun.")
                    .setPositiveButton("TAMAM", null).show()
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                try {
                    val count = UserSymbolListStore(this@FullUniverseScanActivity).importSymbols(uri)
                    localMode = true
                    state = FullUniversePolicy.State.IDLE
                    entries.clear()
                    counts = FullUniversePolicy.Counters()
                    discoveryFailure = "Kullanıcı sembol listesi: $count kayıt içe aktarıldı. Taramayı başlatın."
                    refreshUI()
                } catch (e: Exception) {
                    discoveryFailure = "İÇE AKTARMA HATASI: " + (e.message ?: e.javaClass.simpleName)
                    updateStatus()
                }
            }
        }
    }
    private lateinit var modeStatus: TextView
    private lateinit var modeButton: Button
    private fun refreshUI() {
        modeStatus.text = if (localMode) "MOD: GECİKMELİ TEKNİK GÖZLEM • Tam borsa kapsamı değildir"
            else "MOD: YETKİLİ SAĞLAYICI • Veri bağlantısı gerekli"
        modeButton.text = if (localMode) "⚙ YETKİLİ BIST/VİOP BAĞLANTISI"
            else "↔ GECİKMELİ GÖZLEME GEÇ"
        startButton.text = if (localMode) "▶ GECİKMELİ TARAMAYI BAŞLAT"
            else "▶ YETKİLİ BIST / VİOP TARAMASI"
        updateStatus()
        updateList()
    }
    private val tr = Locale("tr", "TR")
    private val clock = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", tr)
    private val entries = mutableListOf<FullUniversePolicy.Entry>()
    private lateinit var status: TextView
    private lateinit var sourceNote: TextView
    private lateinit var startButton: Button
    private lateinit var adapter: EntryAdapter
    private var state = FullUniversePolicy.State.IDLE
    private var counts = FullUniversePolicy.Counters()
    private var selected: FullUniversePolicy.Market? = null
    private var query: String = ""
    private var scanId = ""
    private var valuationBySymbol: Map<String, Valuation.Assessment> = emptyMap()
    private var valuationSourceNote = "Finansal veri bekleniyor"
    private var valuationLoadedAt = 0L
    private var valuationLoading = false
    private val valuationCallbacks = mutableListOf<() -> Unit>()
    private lateinit var valuationToggleLabel: Button
    private var valuationMode = false
    private var ordering = ValuationResultSort.Order.PRICE_ASC
    private val financialCacheMs = 15L * 60L * 1000L
    private var discoveryFailure = ""
    private fun px(v: Int) = (resources.displayMetrics.density * v).toInt()

    private fun text(parent: LinearLayout, value: String, size: Float, strong: Boolean = false): TextView {
        val result = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.rgb(234, 250, 247))
            setPadding(px(5), px(3), px(5), px(3))
            if (strong) setTypeface(null, Typeface.BOLD)
        }
        parent.addView(result, LinearLayout.LayoutParams(-1, -2))
        return result
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit): Button {
        val b = Button(this).apply { text = title; isAllCaps = false; setOnClickListener { action() } }
        b.minimumHeight = px(48)
        b.setTextColor(Color.WHITE)
        b.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(0, 118, 96))
        b.maxLines = 3
        b.setPadding(px(6), px(7), px(6), px(7))
        parent.addView(b, LinearLayout.LayoutParams(-1, -2))
        return b
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fresh side-by-side installations cannot read Keystore secrets of older package IDs.
        // Make a no-credential installation useful via user-triggered delayed observation.
        val store = SettingsStore(this)
        val configured = BackendUrlPolicy.isValid(store.baseUrl) && store.apiKey.isNotBlank()
        localMode = intent.getBooleanExtra(EXTRA_LOCAL_DELAYED_MODE, !configured)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10), px(8), px(10), px(8))
            setBackgroundColor(Color.rgb(3, 23, 33))
        }
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(3,23,33)) }
        val controls=ScrollView(this).apply { addView(root);isFillViewport=false }
        outer.addView(controls,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(outer)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(60_000)
                    if (valuationMode) ensureValuations()
                }
            }
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(px(10)+bars.left,px(8)+bars.top,px(10)+bars.right,px(8)+bars.bottom)
            insets
        }
        text(root, "← AKILLI DEĞER EKRANINA DÖN", 12f)
            .apply { setTextColor(Color.rgb(18, 215, 215)); setOnClickListener { finish() } }
        text(root, "BIST + VİOP • TARAMA", 18f, true)
        text(root, "V${BuildConfig.VERSION_NAME} • Gecikmeli teknik gözlem • İşlem sinyali değildir", 10f)
        modeStatus = text(root, "", 12f, true)
        modeButton = button(root, "", {})
        modeButton.setOnClickListener {
            if (FullUniverseScanRuntime.isRunning) {
                AlertDialog.Builder(this).setTitle("Tarama devam ediyor")
                    .setMessage("Önce durdurun veya tamamlanmasını bekleyin. Sonuçlar raporda saklanır.")
                    .setPositiveButton("TAMAM", null).show()
            } else if (localMode && (SettingsStore(this).apiKey.isBlank() || !BackendUrlPolicy.isValid(SettingsStore(this).baseUrl))) {
                AlertDialog.Builder(this).setTitle("Yetkili sağlayıcı bağlantısı gerekli")
                    .setMessage("Yetkili bağlantı için HTTPS sunucu adresi ve API anahtarı gereklidir. Gecikmeli mod yalnız BIST gözlemidir; VİOP sonuçları üretmez. Anahtar APK içine gömülmez.")
                    .setPositiveButton("BAĞLANTI AYARLARI") { _, _ ->
                        openConnectionSettings()
                    }.setNegativeButton("GECİKMELİ MODDA KAL", null).show()
            } else {
                localMode = !localMode
                entries.clear()
                counts = FullUniversePolicy.Counters()
                state = FullUniversePolicy.State.IDLE
                scanId = ""
                discoveryFailure = ""
                refreshUI()
            }
        }
        val secondary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(secondary, LinearLayout.LayoutParams(-1, px(51)))
        val importButton = Button(this).apply {
            text = "📄 LİSTE YÜKLE"
            textSize = 12f
            isAllCaps = false
            maxLines = 2
            setOnClickListener { symbolPicker.launch(arrayOf("text/plain", "text/csv", "application/octet-stream")) }
        }
        secondary.addView(importButton, LinearLayout.LayoutParams(0, -2, 1f))
        val settingsButton = Button(this).apply {
            text = "⚙ BAĞLANTI AYARLARI"
            textSize = 12f
            isAllCaps = false
            maxLines = 2
            setOnClickListener {
                openConnectionSettings()
            }
        }
        secondary.addView(settingsButton, LinearLayout.LayoutParams(0, -2, 1f))
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabs, LinearLayout.LayoutParams(-1, px(48)))
        listOf("TÜMÜ", "HİSSELER", "VİOP").forEachIndexed { index, label ->
            val b = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setOnClickListener {
                    selected = when (index) {
                        1 -> FullUniversePolicy.Market.BIST
                        2 -> FullUniversePolicy.Market.VIOP
                        else -> null
                    }
                    updateList()
                }
            }
            tabs.addView(b, LinearLayout.LayoutParams(0, px(48), 1f))
        }
        val valuationToggle = button(root,"GÖRÜNÜM: FİYATA GÖRE") {
            valuationMode = !valuationMode
            valuationToggleLabel.text = if (valuationMode) "GÖRÜNÜM: TEMEL DEĞERLEMEYE GÖRE" else "GÖRÜNÜM: FİYATA GÖRE"
            updateList()
            if (valuationMode) ensureValuations()
        }
        valuationToggleLabel = valuationToggle
        val sortOptions = ValuationResultSort.Order.entries
        val sortSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@FullUniverseScanActivity,android.R.layout.simple_spinner_dropdown_item,
                sortOptions.map { it.label })
            onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                    ordering = sortOptions[position]
                    updateList()
                }
            }
        }
        root.addView(sortSpinner,LinearLayout.LayoutParams(-1,px(56)))
        val search = EditText(this).apply {
            hint = "Sembol veya isim ara"
            setSingleLine(true)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    updateList()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(Color.rgb(132, 150, 168))
        root.addView(search, LinearLayout.LayoutParams(-1, px(48)))
        startButton = button(root, if (localMode) "▶ GECİKMELİ TARAMAYI BAŞLAT"
            else "▶ YETKİLİ BIST / VİOP TARAMASI") {
            if (FullUniverseScanRuntime.isRunning) FullUniverseScanRuntime.cancel() else startScan()
        }
        button(root, "↻ KAYITLI TARAMAYA DEVAM ET") {
            if (!FullUniverseScanRuntime.isRunning) startScan(true)
        }
        button(root, "↻ BAŞARISIZ VE EKSİKLERİ YENİDEN DENE") { if (!FullUniverseScanRuntime.isRunning) startScan(resume = true, retryFailuresOnly = true) }
        status = text(root, "Henüz tarama yapılmadı.", 12f, true)
        sourceNote = text(outer,
            if (configured) "Yetkili modun fiyat kaynağı ayrıca doğrulanmalıdır."
            else "VİOP: lisanslı sözleşme verisi yok. BIST: deneysel gecikmeli gözlem.",
            10f)
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@FullUniverseScanActivity)
            setHasFixedSize(false)
        }
        adapter = EntryAdapter()
        recycler.adapter = adapter
        outer.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        // A process killed by Android cannot continue in this process-scoped runner.
        // Show its last recorded checkpoint as interrupted rather than silently reporting success.
        if (!FullUniverseScanRuntime.isRunning) {
            FullUniverseScanSnapshotStore(this).read()?.let { last ->
                if (last.runId.isNotBlank()) {
                    scanId = last.runId
                    localMode = last.mode == "LOCAL_DELAYED"
                    startedAt = last.startedAt
                    counts = FullUniversePolicy.Counters(
                        discoveredBist = last.discoveredBist, triedBist = last.triedBist,
                        successfulBist = last.successfulBist, noDataBist = last.noDataBist,
                        errorsBist = last.errorsBist, discoveredViop = last.discoveredViop,
                        triedViop = last.triedViop, successfulViop = last.successfulViop,
                        noDataViop = last.noDataViop, errorsViop = last.errorsViop
                    )
                    state = FullUniversePolicy.State.entries.firstOrNull { it.name == last.status }
                        ?: FullUniversePolicy.State.PARTIAL
                    if (state == FullUniversePolicy.State.RUNNING) {
                        state = FullUniversePolicy.State.PARTIAL
                        discoveryFailure = "ÖNCEKİ UYGULAMA SÜRECİ KESİLDİ • Kayıtlı ilerleme: " +
                            "${last.triedBist}/${last.discoveredBist}. Tarama yeniden başlatılabilir; tamamlandı sayılmaz."
                    } else discoveryFailure = last.failure
                }
            }
        }
        refreshUI()
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { ObservationCheckpoint(applicationContext).read() }
            if (!FullUniverseScanRuntime.isRunning && saved != null && entries.isEmpty()) {
                entries.addAll(saved.rows)
                updateList()
            }
        }
        lifecycleScope.launch {
            FullUniverseScanRuntime.snapshots.collect { snapshot ->
                if (snapshot.runId.isNotBlank()) {
                    // Do not mislabel a continuation as a new scan when returning to this screen.
                    localMode = snapshot.localMode
                    state = snapshot.state
                    counts = snapshot.counters
                    discoveryFailure = snapshot.failure
                    scanId = snapshot.runId
                    startedAt = snapshot.startedAt
                    entries.clear()
                    entries.addAll(snapshot.entries)
                    modeStatus.text = if (localMode) "MOD: GECİKMELİ TEKNİK GÖZLEM • Tam borsa kapsamı değildir"
                        else "MOD: YETKİLİ SAĞLAYICI • Veri bağlantısı gerekli"
                    modeButton.text = if (localMode) "⚙ YETKİLİ BIST/VİOP BAĞLANTISI" else "↔ GECİKMELİ GÖZLEME GEÇ"
                    startButton.text = if (snapshot.state == FullUniversePolicy.State.RUNNING) "■ TARAMAYI DURDUR" else
                        if (localMode) "▶ GECİKMELİ TARAMAYI BAŞLAT" else "▶ YETKİLİ BIST / VİOP TARAMASI"
                    updateStatus()
                    updateList()
                    if (valuationMode && snapshot.state != FullUniversePolicy.State.RUNNING) ensureValuations()
                }
            }
        }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            startScan()
        }
    }

    private fun updateList() {
        if (!::adapter.isInitialized) return
        val filtered = FullUniversePolicy.visible(entries, selected, query)
        adapter.replace(ValuationResultSort.sort(filtered, valuationBySymbol, ordering, valuationMode))
        sourceNote.text = when {
            localMode && selected == FullUniversePolicy.Market.VIOP -> "VİOP TARANMADI • Gecikmeli modda yetkili VİOP sözleşme/fiyat sağlayıcısı yok. Bağlantı ayarlarından yetkili sağlayıcı tanımlayın."
            localMode -> "YALNIZCA BIST TEKNİK GÖZLEMİ • VİOP sözleşmeleri taranmadı. TÜMÜ sekmesi gerçek VİOP kapsamı değildir."
            selected == FullUniversePolicy.Market.VIOP && filtered.isEmpty() -> "VİOP sonucu yok • Bağlantı/keşif durumunu kontrol edin; boş liste tam tarama kanıtı değildir."
            filtered.isEmpty() -> "Gösterilecek sonuç yok • Tarama ve filtre durumunu kontrol edin."
            else -> "${filtered.size} sonuç • Veri zamanı ve kaynak her kayıtta gösterilir; canlı işlem sinyali değildir."
        }
    }
    private fun updateStatus() {
        val progress = if (counts.discoveredBist > 0)
            "BIST: ${counts.triedBist}/${counts.discoveredBist} (%${counts.triedBist * 100 / counts.discoveredBist})"
            else "BIST: 0 / 0"
        val viop = if (localMode) "VİOP: veri kaynağı yok" else
            "VİOP: ${counts.triedViop}/${counts.discoveredViop}"
        val label = when(state) {
            FullUniversePolicy.State.RUNNING -> "TARANIYOR"
            FullUniversePolicy.State.LOCAL_LIST_FINISHED -> "YEREL LİSTE TAMAMLANDI (canlı değil)"
            FullUniversePolicy.State.CANCELLED -> "DURDURULDU"
            FullUniversePolicy.State.PARTIAL -> "KISMİ"
            FullUniversePolicy.State.FAILED -> "VERİ ALINAMADI"
            else -> state.name
        }
        status.text = "$label • $progress • $viop\nBIST başarılı: ${counts.successfulBist} • Verisiz: ${counts.noDataBist} • Hata: ${counts.errorsBist}\nVİOP başarılı: ${counts.successfulViop} • Verisiz: ${counts.noDataViop} • Hata: ${counts.errorsViop}\nTam borsa kapsamı doğrulanmadı." +
            if (discoveryFailure.isNotBlank()) "\nBilgi: " + discoveryFailure.trim().trimStart('|', ' ') else ""
    }

    private fun openConnectionSettings() {
        startActivity(Intent(this, SettingsDetailActivity::class.java)
            .putExtra(SettingsDetailActivity.EXTRA_MODE, SettingsDetailActivity.MODE_DATA_CONNECTION))
    }
    private fun startScan(resume: Boolean = false, retryFailuresOnly: Boolean = false) {
        if (!localMode && (SettingsStore(this).apiKey.isBlank() || !BackendUrlPolicy.isValid(SettingsStore(this).baseUrl))) {
            AlertDialog.Builder(this).setTitle("Yetkili tarama başlatılamadı")
                .setMessage("Eksik API anahtarı veya HTTPS adresi. Gecikmeli teknik gözlem ayrı çalışır. Lisanslı piyasa akışı doğrulanmadan yetkili tarama yapılamaz.")
                .setPositiveButton("BAĞLANTI AYARLARI") { _, _ -> openConnectionSettings() }
                .setNegativeButton("VAZGEÇ", null).show()
            return
        }
        try { UniverseObservationService.start(this, localMode, resume, retryFailuresOnly) }
        catch (e: IllegalStateException) {
            discoveryFailure = "Tarama servisi başlatılamadı. Uygulamayı ön planda açıp tekrar deneyin."
            updateStatus()
        }
    }

    internal var activeDetailDialog: AlertDialog? = null
        private set

    private fun showDetail(item: FullUniversePolicy.Entry) {
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3,23,33))
            setPadding(px(14),px(12),px(14),px(14))
        }
        scroll.addView(panel)
        fun number(v: Double?) = v?.let { String.format(tr,"%.2f",it) } ?: "Yetersiz veri"
        text(panel,"${item.symbol} • ${item.subtitle}",18f,true)
        text(panel,"${number(item.price)} ${item.currency.orEmpty()} • ${ObservationAnalysis.trendLabel(item.trend)}",14f)
        button(panel,"PROFESYONEL GRAFİK VE TEKNİK ANALİZ") { openProfessionalChart(item) }
        text(panel,"Grafik ve teknik hesaplamalar bağımsız tam ekran sayfasında, seçili döneme göre gösterilir.",12f)
        text(panel,"TEMEL DEĞERLEME VE SEKTÖR KARŞILAŞTIRMASI",15f,true)
        val valuationArea=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        panel.addView(valuationArea)
        val cached = valuationBySymbol[item.symbol]
        addValuationCards(valuationArea,cached,valuationSourceNote)
        if (item.market==FullUniversePolicy.Market.BIST) ensureValuations { 
            if (valuationArea.isAttachedToWindow) addValuationCards(valuationArea,valuationBySymbol[item.symbol],valuationSourceNote)
        }
        text(panel,"BU SONUÇ NEDEN OLUŞTU?",14f,true)
        text(panel,"Deneme: ${item.attempts} • Süre: ${item.durationMs} ms" +
            "\nHata geçmişi: ${item.attemptErrors.joinToString().ifBlank { "Yok" }}",12f)
        val evidence = valuationBySymbol[item.symbol]
        val reasonText=text(panel,"Teknik eğilim: ${ObservationAnalysis.trendLabel(item.trend)} • ${item.reasonCode ?: "Neden kodu bulunmuyor"}."+
            "\nTemel değerleme: ${evidence?.reason ?: "Finansal değerlendirme için veri bekleniyor."}",12f)
        if(item.market==FullUniversePolicy.Market.BIST) ensureValuations {
            if(reasonText.isAttachedToWindow) reasonText.text="Teknik eğilim: ${ObservationAnalysis.trendLabel(item.trend)}\nTemel değerleme: ${valuationBySymbol[item.symbol]?.reason ?: "Finansal veri bekleniyor"}"
        }
        button(panel,"FİNANSALLARI YENİLE / YENİDEN DENE") {
            ensureValuations(force=true) {
                if (valuationArea.isAttachedToWindow) addValuationCards(valuationArea,valuationBySymbol[item.symbol],valuationSourceNote)
            }
        }
        if(item.market==FullUniversePolicy.Market.BIST) {
            val watch=BistWatchlistStore(this)
            val watchButton=button(panel,if(item.symbol in watch.symbols()) "★ TAKİPTEN ÇIKAR" else "☆ TAKİBE EKLE",{})
            watchButton.setOnClickListener {
                val message=if(item.symbol in watch.symbols()) {
                    watch.remove(item.symbol); "Takip listesinden çıkarıldı"
                } else when(watch.add(item.symbol)) {
                    BistWatchlistStore.AddResult.ADDED -> "Takip listesine eklendi"
                    BistWatchlistStore.AddResult.ALREADY_EXISTS -> "Zaten takip ediliyor"
                    BistWatchlistStore.AddResult.LIMIT_REACHED -> "Takip listesi dolu (20 hisse)"
                    BistWatchlistStore.AddResult.INVALID -> "Geçersiz sembol"
                }
                watchButton.text=if(item.symbol in watch.symbols()) "★ TAKİPTEN ÇIKAR" else "☆ TAKİBE EKLE"
                android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_SHORT).show()
            }
        } else button(panel,"☆ VİOP TAKİBİNİ DÜZENLE") {
            val watch=ViopWatchlistStore(this)
            val categories=ViopWatchlistStore.WATCHLIST_CATEGORIES
            AlertDialog.Builder(this).setTitle("Takip kategorisi seçin")
                .setItems(categories.map { it.label+if(item.symbol in watch.symbols(it)) " • Takipte (çıkarmak için seçin)" else "" }.toTypedArray()) { _, index ->
                    val category=categories[index]
                    val message=if(item.symbol in watch.symbols(category)) {
                        watch.remove(category,item.symbol); "Takipten çıkarıldı"
                    } else when(watch.add(category,item.symbol)) {
                        ViopWatchlistStore.AddResult.ADDED -> "${category.label} takibine eklendi"
                        ViopWatchlistStore.AddResult.ALREADY_EXISTS -> "Zaten takipte"
                        ViopWatchlistStore.AddResult.LIMIT_REACHED -> "Kategori listesi dolu (20 sözleşme)"
                        ViopWatchlistStore.AddResult.INVALID -> "Geçersiz sözleşme"
                    }
                    android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_SHORT).show()
                }.setNegativeButton("VAZGEÇ",null).show()
        }
        val dialog=AlertDialog.Builder(this).setTitle("${item.symbol} • Grafik ve analiz")
            .setView(scroll).setPositiveButton("KAPAT",null).create()
        dialog.setOnDismissListener { if (activeDetailDialog === dialog) activeDetailDialog = null }
        activeDetailDialog = dialog
        dialog.show()
    }

    private fun openProfessionalChart(item: FullUniversePolicy.Entry) {
        activeDetailDialog?.dismiss()
        FullscreenChartActivity.open(this, item.symbol, item.market.name, item.candles, item.source,
            item.market == FullUniversePolicy.Market.BIST && item.source.contains("Yahoo", true))
    }

    private fun ensureValuations(force:Boolean=false, after: (() -> Unit)? = null) {
        val now=System.currentTimeMillis()
        if (!force && valuationLoadedAt>0 && now-valuationLoadedAt in 0 until financialCacheMs) { after?.invoke(); return }
        after?.let { valuationCallbacks.add(it) }
        if (valuationLoading) return
        valuationLoading=true
        lifecycleScope.launch {
            try {
                val client=FundamentalsBatchClient(applicationContext)
                if(valuationBySymbol.isEmpty() && !client.hasLocalImport()) {
                    client.loadCached()?.let { (batch,saved) ->
                        valuationBySymbol=withContext(Dispatchers.Default) {
                            Valuation.scan(batch.items.map { it.symbol },batch.items,System.currentTimeMillis()).associateBy { it.symbol }
                        }
                        valuationSourceNote=batch.sourceNote+" • "+clock.format(Date(saved))
                        updateList()
                    }
                }
                val batch=withTimeout(40_000) { if(client.hasLocalImport()) client.loadLocal() else client.load() }
                val results=withContext(Dispatchers.Default) {
                    Valuation.scan(batch.items.map { it.symbol },batch.items,System.currentTimeMillis())
                }
                valuationBySymbol=results.associateBy { it.symbol }
                valuationLoadedAt=System.currentTimeMillis()
                valuationSourceNote=batch.sourceNote+" • "+clock.format(Date(valuationLoadedAt))
            } catch(_:TimeoutCancellationException) {
                valuationSourceNote="Süre doldu; kayıtlı veriler korundu. Yeniden deneyin."
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:Exception) {
                valuationSourceNote="Finansal veri yenilenemedi; kayıtlı veriler korundu. Bağlantıyı kontrol edip yeniden deneyin."
            } finally {
                valuationLoading=false
                if(!isDestroyed) {
                    updateList()
                    val callbacks=valuationCallbacks.toList();valuationCallbacks.clear()
                    callbacks.forEach { it() }
                } else valuationCallbacks.clear()
            }
        }
    }

    private fun addValuationCards(parent:LinearLayout, assessment:Valuation.Assessment?, sourceNote:String) {
        parent.removeAllViews()
        val snapshot=assessment?.snapshot?.takeIf { Valuation.validate(it,System.currentTimeMillis())==null }
        val mult=if(snapshot!=null) Valuation.multiples(snapshot) else null
        fun num(v:Double?):String=v?.takeIf { it.isFinite() }?.let { String.format(tr,"%.2f",it) } ?: "Finansal veri bekleniyor"
        fun card(title:String,value:String,meaning:String,compare:String,comment:String) {
            val box=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(px(14),px(12),px(14),px(12))
                background=GradientDrawable().apply {
                    setColor(Color.rgb(10,34,48)); cornerRadius=px(14).toFloat()
                    setStroke(px(1),Color.rgb(139,112,52))
                }
            }
            text(box,title,15f,true).setTextColor(Color.rgb(235,193,99))
            text(box,"Değer: $value",14f,true)
            text(box,"Ne anlama gelir? $meaning",12f)
            text(box,"Sektör karşılaştırması: $compare",12f)
            text(box,"Yorum: $comment",12f)
            parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=px(10) })
        }
        fun comparison(name:String):Valuation.Comparison?=assessment?.comparisons?.firstOrNull { it.metric==name }
        fun sector(name:String):String {
            val c=comparison(name) ?: return "Karşılaştırılabilir en az ${Valuation.MIN_PEERS} emsal ve geçerli dönem verisi bekleniyor"
            return "Şirket ${num(c.own)} • sektör medyanı ${num(c.median)} • ${c.peerCount} emsal (${c.peerSymbols.joinToString()}) • fark ${String.format(tr,"%+.1f",(c.own/c.median-1)*100)}%"
        }
        val peComment=when {
            snapshot==null -> "Finansal veri bekleniyor; doğrulanmamış oran hesaplanmaz."
            (snapshot.netIncomeTtmTry?:0.0)<=0 -> "Pozitif kâr olmadığı için F/K karşılaştırması yapılamıyor."
            comparison("F/K")!=null -> "Sektör medyanına göre konum, kâr büyümesi ve tek seferlik gelirler doğrulanmadan ucuzluk kanıtı sayılmaz."
            else -> "Kâr büyümesi ve tek seferlik gelir etkisi için yeterli emsal/veri yok."
        }
        card("F/K — Kâra göre fiyatlama",num(mult?.pe),
            "Hisse fiyatı / hisse başına son 12 aylık net kâr. Hisse başına 1 TL net kâr için ödenen fiyatı gösterir.",sector("F/K"),peComment)
        val pbComment=when {
            snapshot==null -> "Finansal veri bekleniyor."
            (snapshot.equityTry?:0.0)<=0 -> "Özkaynak sıfır veya negatif; oran değerleme puanına katılmaz."
            else -> "Özkaynak kârlılığı, varlık kalitesi ve sektör farklarıyla birlikte yorumlanmalı; 1 altı otomatik ucuzluk değildir."
        }
        card("PD/DD — Özkaynağa göre fiyatlama",num(mult?.pb),
            "Piyasa değeri / son yayımlanan bilanço özkaynağı.",sector("PD/DD"),pbComment)
        val debtValue=when {
            snapshot==null -> "Finansal veri bekleniyor"
            snapshot.businessType!=Valuation.BusinessType.NON_FINANCIAL -> "Bu sektör için uygulanmaz"
            (snapshot.ebitdaTtmTry?:0.0)<=0 -> "FAVÖK sıfır veya negatif"
            (snapshot.netDebtTry?:0.0)<0 -> "${num(mult?.netDebtToEbitda)} • Net nakit pozisyonu"
            else -> num(mult?.netDebtToEbitda)
        }
        card("Net Borç/FAVÖK — Borçluluk",debtValue,
            "Net borç / son 12 aylık FAVÖK; kesin borç ödeme süresi değildir.",
            "Bu veri modelinde dönemsel borç geçmişi ve bu oranın sektör medyanı bulunmuyor.",
            if(snapshot?.businessType==Valuation.BusinessType.NON_FINANCIAL && (snapshot.ebitdaTtmTry?:0.0)>0)
                "Borç eğilimi doğrulanamadı; yalnız geçerli güncel oran gösterilir." else "Uygun veri/sektör karşılaştırması bekleniyor.")
        if(snapshot!=null) card("Kâr büyümesi",Valuation.profitGrowthPct(snapshot)?.let { num(it)+"%" } ?: "Hesaplanamadı",
            "Son 12 aylık kârın, önceki yılın karşılaştırılabilir son 12 aylık kârına göre değişimi.",
            "Aynı muhasebe bazı ve bir yıl aralıklı dönemler kullanılır.",
            if(Valuation.profitGrowthPct(snapshot)!=null) "Önceki dönem kârı: ${num(snapshot.priorNetIncomeTtmTry)} TL"
            else "Karşılaştırılabilir dönem verisi eksik veya önceki kâr pozitif değil.")
        val peers=assessment?.comparisons.orEmpty()
        val peerText=if(peers.isEmpty()) "Finansal veri bekleniyor veya emsal sayısı yetersiz"
            else peers.joinToString("\n") { "${it.metric}: şirket ${num(it.own)} / medyan ${num(it.median)} • ${it.peerCount} emsal" }
        val period=snapshot?.let { "Finansal dönem: ${clock.format(Date(it.financialPeriodEndMs))} • Fiyat tarihi: ${clock.format(Date(it.marketObservedAtMs))}" }
            ?: "Finansal dönem ve fiyat tarihi: veri bekleniyor"
        card("Sektörüne Göre Kıyasla",snapshot?.sector?:"Finansal veri bekleniyor",
            "$period • ${snapshot?.accountingBasis?:"Muhasebe standardı bekleniyor"}",peerText,
            "Kıyas hedef fiyat veya kesin yükseliş potansiyeli değildir. ${assessment?.reason?:"Kıyas için doğrulanabilir veri gerekli."}")
        if(snapshot!=null) {
            assessment?.comparisons.orEmpty().forEach { c ->
                text(parent,"${c.metric} • Şirket ${num(c.own)} / Medyan ${num(c.median)}",11f)
                val bar=android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                    max=1000
                    progress= ((c.own/(maxOf(c.own,c.median)*1.15))*1000).toInt().coerceIn(0,1000)
                    progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(77,202,162))
                }
                parent.addView(bar,LinearLayout.LayoutParams(-1,px(8)).apply { bottomMargin=px(8) })
            }
        }
        text(parent,sourceNote,10f)
    }

    private inner class EntryAdapter : RecyclerView.Adapter<EntryAdapter.Holder>() {
        private var shown: List<FullUniversePolicy.Entry> = emptyList()
        inner class Holder(val box: LinearLayout, val label: TextView, val chart: ObservedPriceChart, val details: Button) : RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val label = TextView(this@FullUniverseScanActivity).apply {
                textSize = 13f
                setTextColor(Color.rgb(245, 252, 255))
                setPadding(px(12), px(10), px(12), px(10))
            }
            val box=LinearLayout(this@FullUniverseScanActivity).apply { orientation=LinearLayout.VERTICAL }
            box.addView(label)
            val chart=ObservedPriceChart(this@FullUniverseScanActivity,true)
            box.addView(chart,LinearLayout.LayoutParams(-1,px(48)))
            val details = Button(this@FullUniverseScanActivity).apply { text = "FİNANSAL / TAKİP"; minimumHeight = px(48) }
            box.addView(details)
            box.layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                bottomMargin = px(5)
            }
            return Holder(box,label,chart,details)
        }
        override fun getItemCount(): Int = shown.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = shown[position]
            val color = when (item.quality) {
                FullUniversePolicy.Quality.NO_DATA -> Color.rgb(32, 43, 53)
                FullUniversePolicy.Quality.DELAYED -> Color.rgb(20, 42, 47)
                FullUniversePolicy.Quality.VERIFIED -> Color.rgb(3, 60, 47)
                FullUniversePolicy.Quality.UNVERIFIED -> Color.rgb(9, 36, 56)
            }
            val label = when (item.quality) {
                FullUniversePolicy.Quality.DELAYED -> "GECİKMELİ"
                FullUniversePolicy.Quality.NO_DATA -> "VERİ YOK"
                FullUniversePolicy.Quality.VERIFIED -> "DOĞRULANMIŞ"
                FullUniversePolicy.Quality.UNVERIFIED -> "DOĞRULANMAMIŞ"
            }
            val priceText = item.price?.let { String.format(tr, "%.2f", it) } ?: "—"
            val timeText = if (item.dataTimeMs > 0L) clock.format(Date(item.dataTimeMs)) else "—"
            val arrow = when(item.trend) {
                FullUniversePolicy.Trend.UP -> "↗"
                FullUniversePolicy.Trend.DOWN -> "↘"
                FullUniversePolicy.Trend.FLAT -> "→"
                FullUniversePolicy.Trend.UNKNOWN -> "?"
            }
            val valuation=valuationBySymbol[item.symbol]
            val financial = if (item.market==FullUniversePolicy.Market.BIST) {
                val m=valuation?.multiples
                fun fmt(v:Double?)=v?.takeIf { it.isFinite() }?.let { String.format(tr,"%.2f",it) } ?: "Hesaplanamadı"
                "\nSektör: ${valuation?.snapshot?.sector ?: "Finansal veri bekleniyor"} • F/K: ${fmt(m?.pe)} • PD/DD: ${fmt(m?.pb)}"+
                "\nNet Borç/FAVÖK: ${if(valuation?.snapshot?.businessType!=Valuation.BusinessType.NON_FINANCIAL && valuation?.snapshot!=null) "Uygulanmaz" else if((valuation?.snapshot?.netDebtTry?:0.0)<0) "Net nakit" else fmt(m?.netDebtToEbitda)}"+
                "\nSektör kıyası: ${valuation?.status?.name ?: "Hesaplanamadı"} • ${valuation?.reason?.take(125) ?: "Finansal veri bekleniyor"}"
            } else "\nVİOP: hisse temel değerlemesi uygulanmaz"
            holder.label.text = (if (item.market == FullUniversePolicy.Market.BIST) "HİSSE" else "VİOP") +
                " • " + item.symbol + "  " + arrow + " " + item.trend +
                "\n" + item.subtitle +
                "\n" + priceText + " " + (item.currency ?: "BİRİM DOĞRULANMADI") + " • " + label +
                "\nKaynak: " + item.source + " • " + timeText +
                "\nVeri yaşı: " + (if(item.dataTimeMs>0L) "${((System.currentTimeMillis()-item.dataTimeMs).coerceAtLeast(0L))/1000} sn" else "bilinmiyor") +
                (if(item.market==FullUniversePolicy.Market.VIOP) "\n"+item.detail else "") +
                "\nDeğişim: " + (item.changePct?.let { String.format(tr,"%+.2f%%",it) } ?: "—") + " • " + (item.reasonCode ?: "Gecikmeli gözlem") + financial
            holder.chart.setBars(item.candles)
            holder.box.background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = px(13).toFloat()
                setStroke(px(2), when(item.trend) {
                    FullUniversePolicy.Trend.UP -> Color.rgb(24, 165, 87)
                    FullUniversePolicy.Trend.DOWN -> Color.rgb(218, 63, 72)
                    FullUniversePolicy.Trend.FLAT -> Color.rgb(220, 174, 64)
                    FullUniversePolicy.Trend.UNKNOWN -> Color.rgb(120, 130, 148)
                })
            }
            holder.box.setOnClickListener { openProfessionalChart(item) }
            holder.details.setOnClickListener { showDetail(item) }

        }
        fun replace(items: List<FullUniversePolicy.Entry>) {
            shown = items
            notifyDataSetChanged()
        }
    }

}


