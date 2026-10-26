package tr.borsatakip.v5.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ViopScanner
import tr.borsatakip.v5.analysis.ViopV6Policy
import tr.borsatakip.v5.scan.ScanOrchestrator
import tr.borsatakip.v5.analysis.ViopUnderlyingScanner
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.ProviderReadinessService
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.data.AlertEventStore
import tr.borsatakip.v5.data.ProviderState
import tr.borsatakip.v5.data.ViopContractSelector
import tr.borsatakip.v5.data.ViopRepository
import tr.borsatakip.v5.data.ViopStrategyDecisionService
import tr.borsatakip.v5.data.ViopWatchlistStore
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.model.ViopQuote
import tr.borsatakip.v5.model.ViopScanProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViopActivity : BaseActivity() {
    private lateinit var backend: BackendProvider
    private lateinit var scanner: ScanOrchestrator
    private lateinit var underlyingScanner: ViopUnderlyingScanner
    private lateinit var readiness: ProviderReadinessService
    private lateinit var list: RecyclerView
    private lateinit var status: TextView
    private lateinit var providerStatus: TextView
    private lateinit var scanButton: Button
    private val refreshUiHandler = Handler(Looper.getMainLooper())
    private val refreshUiStatus = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                if (!showingUnderlying && productionScanCompleted) applyDashboardFilter(preserveScroll = true)
                else if (::list.isInitialized) list.adapter?.notifyDataSetChanged()
                refreshProviderState() // no stale price may retain the LIVE label
                refreshPreviewQuote()
                refreshUiHandler.postDelayed(this, 10_000L)
            }
        }
    }
    private var previewContract: ViopContract? = null
    private var previewQuote: ViopQuote? = null
    private var previewCandles: List<Candle> = emptyList()
    private var previewQuoteRefreshInFlight = false

    private fun refreshPreviewQuote() {
        val c = previewContract ?: return
        if (previewQuoteRefreshInFlight || readiness.localConfigState().state != ProviderState.PROVIDER_READY) return
        previewQuoteRefreshInFlight = true
        lifecycleScope.launch {
            try {
                backend.loadViopQuote(c.symbol).onSuccess { newest ->
                    if (newest.exchangeTimestamp >= (previewQuote?.exchangeTimestamp ?: 0L) &&
                        lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        previewQuote = newest
                        renderPreview(c, newest, previewCandles)
                        refreshProviderState()
                    }
                }.onFailure { error ->
                    if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        providerStatus.text = "VİOP fiyat yenileme başarısız • ${error.message ?: "veri yok"}"
                    }
                }
            } finally {
                previewQuoteRefreshInFlight = false
            }
        }
    }
    private var providerAutoTestInFlight = false
    private var lastProviderAutoTestAt = 0L
    private var underlyingScanInFlight = false
    private var opportunityScanInFlight = false
    private var connectionTestInFlight = false
    private var underlyingScanCompleted = false
    private var productionCandidates: List<ViopOpportunity> = emptyList()
    private var productionItems: List<ViopOpportunity> = emptyList()
    private var productionScanCompleted = false
    private var underlyingItems: List<ViopUnderlyingScanner.Candidate> = emptyList()
    private var showingUnderlying = false
    private var uiQuery = ""
    private var uiCategory = UiCategory.ALL
    private var uiDirection = UiDirection.ALL
    private var uiOrder = MarketListOrder.SOURCE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viop)
        setupBottomNav()
        MarketScreenUi.bottomNav(this, R.id.navViop)
        uiCategory = UiCategory.entries.firstOrNull { it.name == savedInstanceState?.getString("marketCategory") } ?: UiCategory.ALL
        uiDirection = UiDirection.entries.firstOrNull { it.name == savedInstanceState?.getString("marketDirection") } ?: UiDirection.ALL
        uiOrder = MarketListOrder.entries.firstOrNull { it.name == savedInstanceState?.getString("marketOrder") } ?: MarketListOrder.SOURCE
        uiQuery = savedInstanceState?.getString("marketQuery").orEmpty()
        findViewById<EditText>(R.id.viopSearch).setText(uiQuery)
        backend = BackendProvider(this)
        scanner = ScanOrchestrator(this)
        underlyingScanner = ViopUnderlyingScanner(ProviderRouter(this, experimentalFallbackOverride = true))
        readiness = ProviderReadinessService(this)
        list = findViewById(R.id.list)
        status = findViewById(R.id.status)
        providerStatus = findViewById(R.id.providerStatus)
        scanButton = findViewById(R.id.refresh)
        list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        findViewById<TextView>(R.id.openSettings).setOnClickListener { openViopSettings() }
        findViewById<TextView>(R.id.openFavorites).setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        findViewById<TextView>(R.id.openNotifications).setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }
        bindDashboardFilters()
        MarketScreenUi.details(status, "VİOP tarama durumu")
        MarketScreenUi.details(providerStatus, "VİOP veri kaynağı")
        MarketScreenUi.details(findViewById(R.id.signalSummary), "Gösterilen sonuçlar")
        findViewById<View>(R.id.providerTitle).setOnClickListener { openViopSettings() }
        findViewById<View>(R.id.viopSort).setOnClickListener { anchor ->
            MarketScreenUi.sortMenu(anchor, uiOrder) { selected -> uiOrder = selected; applyDashboardFilter() }
        }
        findViewById<Button>(R.id.viopEmptyAction).setOnClickListener {
            if (uiQuery.isNotBlank() || uiCategory != UiCategory.ALL || uiDirection != UiDirection.ALL) {
                uiQuery = ""
                uiCategory = UiCategory.ALL
                uiDirection = UiDirection.ALL
                findViewById<EditText>(R.id.viopSearch).setText("")
                updateDashboardFilterColors()
                applyDashboardFilter()
            } else openViopSettings()
        }
        findViewById<Button>(R.id.openContracts).setOnClickListener { startActivity(Intent(this, ViopContractsActivity::class.java)) }
        findViewById<Button>(R.id.openExpiries).setOnClickListener { startActivity(Intent(this, ViopContractsActivity::class.java)) }
        findViewById<Button>(R.id.openAnalysis).setOnClickListener { openAnalysis() }
        findViewById<Button>(R.id.openSignals).setOnClickListener { openSignals() }
        findViewById<TextView>(R.id.summaryLong).setOnClickListener { findViewById<Button>(R.id.filterLong).performClick() }
        findViewById<TextView>(R.id.summaryShort).setOnClickListener { findViewById<Button>(R.id.filterShort).performClick() }
        findViewById<TextView>(R.id.summaryWatch).setOnClickListener { startActivity(Intent(this, ViopContractsActivity::class.java)) }
        scanButton.setOnClickListener { startOrValidateScan() }
        findViewById<View>(R.id.viopScanCard).setOnClickListener { startOrValidateScan() }
        applyDashboardFilter()
        refreshProviderState()
        recoverProviderAndLoad()
        intent.getStringExtra("manualPositionId")?.let { positionId ->
            val pos = try { tr.borsatakip.v5.data.ViopManualPositionStore(this).positions()
                .firstOrNull { it.id == positionId } } catch (_: Exception) { null }
            if (pos != null) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("VİOP KAYITLI POZİSYON • ${pos.symbol}")
                    .setMessage("${pos.side} • ${pos.quantity} kontrat\nStop: ${pos.stop} • Hedef: ${pos.target}\n${pos.riskViolationAtEntry ?: "Risk ihlali etiketi yok"}\nUyarı emir iletmez.")
                    .setPositiveButton("TAMAM", null).show()
            } else {
                status.text = "Uyarıdaki manuel pozisyon bu cihazdaki defterde bulunamadı veya defter bozuk."
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("marketCategory", uiCategory.name)
        outState.putString("marketDirection", uiDirection.name)
        outState.putString("marketOrder", uiOrder.name)
        outState.putString("marketQuery", uiQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshProviderState()
        if (::readiness.isInitialized) recoverProviderAndLoad()
        refreshUiHandler.removeCallbacks(refreshUiStatus)
        refreshUiHandler.post(refreshUiStatus)
    }

    override fun onPause() {
        refreshUiHandler.removeCallbacks(refreshUiStatus)
        super.onPause()
    }


    private fun openViopSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_RETURN_TO_VIOP, true))
    }

    private fun openAnalysis() {
        startActivity(Intent(this, ViopAnalysisActivity::class.java))
    }

    private fun openSignals() {
        val ready = readiness.localConfigState().state == ProviderState.PROVIDER_READY
        val signals = if (ready) productionItems.filter { ViopV6Policy.assess(it).eligible } else emptyList()
        if (signals.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Doğrulanmış VİOP adayları")
                .setItems(signals.map { "${it.contract.symbol} · ${it.direction}" }.toTypedArray()) { _, position ->
                    val selected = signals[position]
                    // Recheck freshness when selected; an open chooser must not freeze eligibility.
                    if (ViopV6Policy.assess(selected).eligible) {
                        AppSession.selectedViopOpportunity = selected
                        AppSession.selectedViopContract = selected.contract
                        startActivity(Intent(this, ViopDetailActivity::class.java))
                    } else MarketScreenUi.notice(status, "Veri yeniden doğrulanmalı", "Seçilen adayın güncelliği sona erdi. VİOP taramasını yenileyin.")
                }.setNegativeButton("Kapat", null).show()
        } else {
            val message = if (ready) "Güncel veri ve kalite kontrolünü geçen VİOP adayı yok. Taramayı yenileyebilirsiniz."
                else "Gerçek VİOP fiyatı ve geçmişi doğrulanmadı. BIST dayanak taraması VİOP sinyali üretmez. Önce VİOP bağlantısını yapılandırın."
            status.text = message
            AlertDialog.Builder(this).setTitle("Doğrulanmış VİOP sinyali yok").setMessage(message)
                .setPositiveButton(if (ready) "Taramayı başlat" else "Bağlantı ayarları") { _, _ ->
                    if (ready) startOrValidateScan() else openViopSettings()
                }.setNegativeButton("Kapat", null).show()
        }
    }

    private fun scanInFlight() = underlyingScanInFlight || opportunityScanInFlight || connectionTestInFlight

    private fun recoverProviderAndLoad() {
        if (!::readiness.isInitialized || providerAutoTestInFlight) return
        when (readiness.localConfigState().state) {
            ProviderState.PROVIDER_READY -> if (previewQuote == null) loadDashboardPreview()
            ProviderState.PROVIDER_CONFIGURED, ProviderState.PROVIDER_STALE_READY, ProviderState.PROVIDER_ERROR -> {
                val now = System.currentTimeMillis()
                if (now - lastProviderAutoTestAt < AUTO_TEST_COOLDOWN_MS) return
                lastProviderAutoTestAt = now
                providerAutoTestInFlight = true
                connectionTestInFlight = true
                status.text = "VİOP provider otomatik doğrulanıyor: HTTPS → Health → Authentication → Contracts → Quote → History"
                refreshProviderState()
                lifecycleScope.launch {
                    try {
                        val result = readiness.test()
                        if (result.state == ProviderState.PROVIDER_READY) loadDashboardPreview()
                        else status.text = "${result.failureCode} • ${result.message}"
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (error: Exception) {
                        status.text = "VİOP bağlantı testi başarısız • ${error.message ?: "Tekrar deneyin"}"
                    } finally {
                        providerAutoTestInFlight = false
                        connectionTestInFlight = false
                        refreshProviderState()
                    }
                }
            }
            ProviderState.PROVIDER_NOT_CONFIGURED -> {
                clearPreview()
                if (!underlyingScanInFlight && !underlyingScanCompleted) {
                    runUnderlyingScan("Production VİOP backend bağlı değil")
                }
            }
            ProviderState.PROVIDER_TESTING -> Unit
        }
    }

    private fun startOrValidateScan() {
        if (scanInFlight()) return
        when (val s = readiness.localConfigState()) {
            else -> when (s.state) {
                ProviderState.PROVIDER_NOT_CONFIGURED -> runUnderlyingScan("Production VİOP backend bağlı değil")
                ProviderState.PROVIDER_READY -> runOpportunityScan()
                ProviderState.PROVIDER_TESTING -> status.text = "Provider bağlantı testi devam ediyor..."
                ProviderState.PROVIDER_CONFIGURED, ProviderState.PROVIDER_STALE_READY, ProviderState.PROVIDER_ERROR -> testThenScan()
            }
        }
    }

    private fun testThenScan() {
        if (scanInFlight()) return
        connectionTestInFlight = true
        status.text = "Provider doğrulanıyor: HTTPS → Health → Authentication → VİOP Contracts → Quote → History"
        refreshProviderState()
        lifecycleScope.launch {
            try {
                val r = readiness.test()
                connectionTestInFlight = false
                if (r.state == ProviderState.PROVIDER_READY) {
                    loadDashboardPreview()
                    runOpportunityScan()
                } else {
                    clearPreview()
                    status.text = "VİOP provider doğrulanamadı • ${r.failureCode} • ${r.message}"
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (error: Exception) {
                status.text = "VİOP bağlantı testi başarısız • ${error.message ?: "Tekrar deneyin"}"
            } finally {
                connectionTestInFlight = false
                refreshProviderState()
            }
        }
    }

    private fun refreshProviderState() {
        val s = readiness.localConfigState()
        val title = findViewById<TextView>(R.id.providerTitle)
        val dot = findViewById<TextView>(R.id.providerDot)
        val live = s.state == ProviderState.PROVIDER_READY &&
            previewQuote?.realtime == true && previewQuote?.delaySeconds == 0 &&
            previewQuote?.currentSessionIncluded == true &&
            previewQuote?.exchangeTimestamp?.let { (System.currentTimeMillis() - it) in 0L..60_000L } == true
        if (live) {
            title.text = "CANLI VERİ"
            title.setTextColor(Color.parseColor("#56F1C1"))
            dot.setTextColor(Color.parseColor("#00F080"))
            providerStatus.text = "Doğrulanmış VİOP quote • ${s.message} • canlı soket henüz teyit edilmedi"
        } else {
            title.text = when (s.state) {
                ProviderState.PROVIDER_NOT_CONFIGURED -> "DAYANAK MODU"
                ProviderState.PROVIDER_ERROR -> "VİOP VERİ HATASI"
                ProviderState.PROVIDER_TESTING -> "BAĞLANTI TEST EDİLİYOR"
                ProviderState.PROVIDER_STALE_READY -> "VERİ YENİDEN DOĞRULANMALI"
                else -> "VİOP VERİSİ BEKLENİYOR"
            }
            val error = s.state == ProviderState.PROVIDER_ERROR
            title.setTextColor(getColor(if (error) R.color.red else R.color.market_amber))
            dot.setTextColor(getColor(if (error) R.color.red else R.color.market_amber))
            providerStatus.text = when {
                s.state == ProviderState.PROVIDER_NOT_CONFIGURED -> "VİOP bağlı değil · BIST dayanakları ayrı gösterilir. Bağlantı için ayarlara dokunun."
                s.state == ProviderState.PROVIDER_READY && previewQuote != null -> "VİOP FİYATI ESKİ / TEKRAR DOĞRULAMA BEKLENİYOR • ${s.message}"
                else -> "${s.failureCode} • ${s.message}"
            }
        }
        val actionDescription = when (s.state) {
            ProviderState.PROVIDER_NOT_CONFIGURED -> if (underlyingScanCompleted) "DAYANAK TARAMASINI YENİLE" else "DAYANAK TARAMASINI BAŞLAT"
            ProviderState.PROVIDER_READY -> "VİOP TARAMASINI BAŞLAT"
            ProviderState.PROVIDER_STALE_READY -> "YENİDEN DOĞRULA"
            ProviderState.PROVIDER_ERROR -> "VİOP BAĞLANTISINI TEKRAR DOĞRULA"
            ProviderState.PROVIDER_TESTING -> "PROVIDER TEST EDİLİYOR"
            ProviderState.PROVIDER_CONFIGURED -> "BAĞLANTIYI TEST ET"
        }
        scanButton.contentDescription = actionDescription
        val busy = scanInFlight() || s.state == ProviderState.PROVIDER_TESTING
        scanButton.text = when {
            underlyingScanInFlight || opportunityScanInFlight -> "Taranıyor…"
            connectionTestInFlight -> "Doğrulanıyor…"
            else -> when (s.state) {
            ProviderState.PROVIDER_NOT_CONFIGURED -> if (underlyingScanCompleted) "Yenile  ›" else "Tara  ›"
            ProviderState.PROVIDER_READY -> "Tara  ›"
            ProviderState.PROVIDER_TESTING -> "Bekleyin"
            else -> "Doğrula  ›"
            }
        }
        findViewById<TextView>(R.id.viopScanTitle).text = if (s.state == ProviderState.PROVIDER_NOT_CONFIGURED) "Dayanak tarama" else "VİOP tarama"
        findViewById<TextView>(R.id.viopScanSubtitle).text = if (s.state == ProviderState.PROVIDER_NOT_CONFIGURED)
            "BIST hisseleri · VİOP sinyali üretmez" else "Doğrulanmış kontratları tara"
        scanButton.isEnabled = !busy
        scanButton.alpha = if (busy) 0.55f else 1f
        findViewById<View>(R.id.viopScanProgress).visibility = if (busy) View.VISIBLE else View.GONE
        findViewById<View>(R.id.viopScanCard).isEnabled = scanButton.isEnabled
    }

    private fun loadDashboardPreview() {
        lifecycleScope.launch {
            status.text = "Gerçek VİOP sözleşme ve yakın vade verisi yükleniyor..."
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val contracts = backend.loadViop().getOrThrow()
                    val c = ViopContractSelector.candidates(contracts, allowWatch = false).firstOrNull()
                        ?: error("NO_CONTRACT: Doğrulanmış aktif VİOP sözleşmesi bulunamadı.")
                    val q = backend.loadViopQuote(c.symbol).getOrThrow()
                    // Quote remains visible even when daily history fails or the expiry is recent.
                    val h = backend.loadViopHistoryFlexible(c.symbol, "1y", "1d", 0)
                        .getOrDefault(emptyList())
                    Triple(c, q, h)
                }
            }
            result.onSuccess { (c, q, h) ->
                previewContract = c
                previewQuote = q
                AppSession.selectedViopContract = c
                renderPreview(c, q, h)
                refreshProviderState()
                status.text = "VİOP FİYAT BAĞLI • ${c.symbol} • ${q.source}" +
                    if (h.size < ViopScanner.MIN_HISTORY_BARS) " • ANALİZ YETERSİZ (${h.size} günlük mum)" else " • Günlük analiz verisi hazır"
            }.onFailure {
                previewContract = null
                previewQuote = null
                clearPreview()
                refreshProviderState()
                status.text = "VİOP önizlemesi yüklenemedi • ${it.message ?: "Bilinmeyen hata"}"
            }
        }
    }

    private fun renderPreview(c: ViopContract, q: ViopQuote, candles: List<Candle>) {
        previewCandles = candles
        val last = candles.lastOrNull()
        findViewById<TextView>(R.id.heroTitle).text = c.underlying.takeIf { it.isNotBlank() && it != "-" } ?: "YAKIN VADE"
        findViewById<TextView>(R.id.heroContract).text = "${c.symbol} • ${c.expiry}"
        findViewById<TextView>(R.id.heroExpiry).text = c.lastTradingAt?.let { "Son işlem ${shortDate(it)}" } ?: "Vade ${c.expiry}"
        findViewById<TextView>(R.id.heroPrice).text = formatPrice(q.price)
        findViewById<TextView>(R.id.heroChange).apply {
            text = q.dailyChangePct?.let { "%+.2f%%".format(it) } ?: "Değişim verisi yok"
            setTextColor(when { q.dailyChangePct == null -> Color.parseColor("#AFC2D4"); q.dailyChangePct!! >= 0 -> Color.parseColor("#00F080"); else -> Color.parseColor("#FF5A66") })
        }
        findViewById<ViopSparklineView>(R.id.heroSparkline).setCandles(candles)
        findViewById<TextView>(R.id.metricOpen).text = "Açılış\n${last?.open?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.metricHigh).text = "En Yüksek\n${last?.high?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.metricLow).text = "En Düşük\n${last?.low?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.metricBid).text = "Alış\n${q.bid?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.metricAsk).text = "Satış\n${q.ask?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.metricVolume).text = "Hacim  ${q.volume?.let(::formatCompact) ?: c.volume?.let(::formatCompact) ?: "—"}"
        findViewById<TextView>(R.id.metricOi).text = "Açık Poz.  ${q.openInterest ?: c.openInterest ?: "—"}"
        findViewById<TextView>(R.id.metricBasis).text = "Basis  —"
        findViewById<TextView>(R.id.providerLastUpdate).text = timeOf(q.exchangeTimestamp)
        val age = (System.currentTimeMillis() - q.exchangeTimestamp).coerceAtLeast(0L)
        findViewById<TextView>(R.id.providerDataAge).text = if (age < 1000) "$age ms" else "%.1f sn".format(age / 1000.0)
        findViewById<TextView>(R.id.providerLatency).text = q.delaySeconds?.let { "$it sn" } ?: "—"
        findViewById<TextView>(R.id.marketContracts).text = "Kontrat\n${c.symbol}"
        findViewById<TextView>(R.id.marketTrend).text = "Veri\n${if (q.realtime) "Canlı" else "Gecikmeli"}"
    }

    private fun clearPreview() {
        previewContract = null
        previewQuote = null
        previewCandles = emptyList()
        findViewById<TextView>(R.id.heroTitle).text = "YAKIN VADE"
        findViewById<TextView>(R.id.heroContract).text = "Canlı sözleşme bekleniyor"
        findViewById<TextView>(R.id.heroExpiry).text = "Vade —"
        findViewById<TextView>(R.id.heroPrice).text = "—"
        findViewById<TextView>(R.id.heroChange).text = "Gerçek fiyat verisi bekleniyor"
        findViewById<ViopSparklineView>(R.id.heroSparkline).setCandles(emptyList())
        listOf(R.id.metricOpen to "Açılış", R.id.metricHigh to "En Yüksek", R.id.metricLow to "En Düşük", R.id.metricBid to "Alış", R.id.metricAsk to "Satış").forEach { (id, label) -> findViewById<TextView>(id).text = "$label\n—" }
        findViewById<TextView>(R.id.metricVolume).text = "Hacim  —"
        findViewById<TextView>(R.id.metricOi).text = "Açık Poz.  —"
        findViewById<TextView>(R.id.metricBasis).text = "Basis  —"
        findViewById<TextView>(R.id.providerLastUpdate).text = "—"
        findViewById<TextView>(R.id.providerDataAge).text = "—"
        findViewById<TextView>(R.id.providerLatency).text = "—"
    }

    private fun runOpportunityScan() {
        if (scanInFlight() || readiness.localConfigState().state != ProviderState.PROVIDER_READY) return
        opportunityScanInFlight = true
        status.text = "Provider READY • aktif sözleşme evreni alınıyor..."
        refreshProviderState()
        lifecycleScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { scanner.runViop { p -> runOnUiThread { status.text = progressText(p) } } }
                AppSession.lastViopOpportunities = r.opportunities
                AlertEventStore(this@ViopActivity).evaluateViop(r.opportunities, origin = "UI")
                renderOpportunities(r.opportunities)
                status.text = buildString {
                    append("${r.status.name} • Production VİOP taraması tamamlandı\n")
                    append(progressText(r.progress))
                    append("\nHam aday: ${r.opportunities.size} • V6 doğrulamasını geçen: ${productionItems.size} • LONG: ${r.progress.longCount} • SHORT: ${r.progress.shortCount}")
                    val rejected = r.opportunities.groupingBy { ViopV6Policy.assess(it).reason }
                        .eachCount().filterKeys { it != "MANUAL_REVIEW_ONLY" }
                    if (rejected.isNotEmpty()) append("\nKalite kapısı: ${rejected.entries.sortedByDescending { it.value }.take(4).joinToString { "${it.key}=${it.value}" }}")
                    if (r.opportunities.isEmpty()) {
                        if (r.errors.isNotEmpty()) {
                            append("\nVERİ / ANALİZ HATASI: fırsat yok sonucuna varılamaz.")
                            append("\nİlk nedenler: ${r.errors.take(3).joinToString { "${it.symbol}: ${it.code}" }}")
                        } else append("\nTarama tamamlandı; doğrulanmış uygun fırsat yok.")
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Exception) {
                status.text = "VİOP taraması başarısız • ${t.message ?: "Beklenmeyen hata"}"
            } finally {
                opportunityScanInFlight = false
                refreshProviderState()
            }
        }
    }

    private fun runUnderlyingScan(reason: String) {
        if (scanInFlight()) return
        underlyingScanInFlight = true
        status.text = "$reason • BIST dayanakları teknik olarak taranıyor..."
        refreshProviderState()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { underlyingScanner.scan() }
                underlyingItems = result.items
                productionCandidates = emptyList()
                productionItems = emptyList()
                showingUnderlying = true
                applyDashboardFilter()
                status.text = buildString {
                    append("DAYANAK TARAMASI TAMAMLANDI • VİOP fiyatı üretilmedi\n")
                    append("Denenen ${result.attempted} • Analiz ${result.resolved} • Veri yok ${result.failed}\n")
                    append("Gerçek VİOP fırsatı için production kontrat quote/history gerekir.")
                    if (result.failures.isNotEmpty()) {
                        append("\nErişilemeyen dayanaklar: ")
                        append(result.failures.entries.take(8).joinToString { "${it.key}=${it.value}" })
                        if (result.failures.size > 8) append(" • +${result.failures.size - 8} diğer")
                        result.failures["PGSUS"]?.let { append("\nPGSUS kontrolü: $it") }
                    }
                }
                underlyingScanCompleted = true
            } catch (ce: kotlinx.coroutines.CancellationException) {
                underlyingScanCompleted = false
                throw ce
            } catch (t: Exception) {
                underlyingScanCompleted = false
                status.text = "Dayanak taraması başarısız • ${t.message ?: "Beklenmeyen hata"}"
            } finally {
                underlyingScanInFlight = false
                scanButton.isEnabled = true
                refreshProviderState()
            }
        }
    }

    private var openingUnderlyingContract = false

    private fun openRealViopContract(candidate: ViopUnderlyingScanner.Candidate) {
        if (openingUnderlyingContract) return
        val local = readiness.localConfigState()
        if (local.state != ProviderState.PROVIDER_READY) {
            status.text = when (local.state) {
                ProviderState.PROVIDER_NOT_CONFIGURED -> "VİOP veri servisi yapılandırılmamış. Gerçek kontrat açmak için HTTPS backend ve API anahtarı gerekir."
                else -> "VİOP veri servisi hazır değil • ${local.failureCode} • ${local.message}"
            }
            openUnderlyingDetail(candidate)
            return
        }
        openingUnderlyingContract = true
        status.text = "${candidate.contract.underlying.uppercase()} için gerçek VİOP kontratı çözülüyor..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val underlying = candidate.contract.underlying.trim().uppercase()
                    val contracts = backend.loadViop().getOrThrow()
                    val candidates = ViopContractSelector.candidates(contracts, underlying = underlying, allowWatch = false)
                    var lastError: Throwable? = null
                    for (contract in candidates) {
                        val attempt = runCatching {
                            val quote = backend.loadViopQuote(contract.symbol).getOrThrow()
                            val history = backend.loadViopHistoryFlexible(contract.symbol, "1y", "1d", 0)
                                .getOrDefault(emptyList())
                            Triple(contract, quote, history)
                        }
                        if (attempt.isSuccess) return@runCatching attempt.getOrThrow()
                        lastError = attempt.exceptionOrNull()
                    }
                    if (candidates.isEmpty()) error("NO_CONTRACT: $underlying için doğrulanmış aktif VİOP kontratı bulunamadı.")
                    throw (lastError ?: IllegalStateException("VİOP quote/history doğrulanamadı."))
                }
            }
            openingUnderlyingContract = false
            result.onSuccess { (contract, quote, history) ->
                AppSession.selectedViopOpportunity = null
                AppSession.selectedViopContract = contract
                previewContract = contract
                previewQuote = quote
                renderPreview(contract, quote, history)
                status.text = "VİOP fiyatı doğrulandı • ${contract.symbol}" +
                    if (history.size < ViopScanner.MIN_HISTORY_BARS) " • Analiz geçmişi yetersiz (${history.size})" else " • Günlük analiz verisi hazır"
                startActivity(Intent(this@ViopActivity, ViopDetailActivity::class.java))
            }.onFailure { t ->
                val msg = t.message.orEmpty()
                status.text = when {
                    msg.contains("NO_CONTRACT") -> "${candidate.contract.underlying.uppercase()} için geçerli VİOP kontratı bulunamadı."
                    msg.contains("QUOTE", true) -> "VİOP kontratı bulundu ancak güncel fiyat verisi alınamadı."
                    msg.contains("HISTORY", true) || msg.contains("INSUFFICIENT_HISTORY", true) -> "VİOP fiyatı mevcut olabilir ancak analiz için doğrulanmış geçmiş veri yok."
                    else -> "VİOP kontrat verisi doğrulanamadı • ${t.message ?: "Bilinmeyen hata"}"
                }
            }
        }
    }

    private fun openUnderlyingDetail(candidate: ViopUnderlyingScanner.Candidate) {
        val underlying = candidate.underlying
        AppSession.selected = underlying
        startActivity(Intent(this, StockDetailActivity::class.java).putExtra("opportunity", underlying))
    }

    private fun renderOpportunities(items: List<ViopOpportunity>) {
        productionCandidates = items
        productionItems = tr.borsatakip.v5.analysis.ViopDashboardPolicy.snapshot(items, System.currentTimeMillis()).items
        productionScanCompleted = true
        underlyingItems = emptyList()
        showingUnderlying = false
        status.text = "V${tr.borsatakip.v5.BuildConfig.VERSION_NAME} ÖN İZLEME • Adaylar güncellik ve kalite kontrolleriyle listelenir. " +
            "İşlem başı risk 250 TL • Otomatik emir KAPALI • Manuel onay gerekli."
        applyDashboardFilter()
    }

    private fun bindDashboardFilters() {
        findViewById<EditText>(R.id.viopSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                uiQuery = s?.toString().orEmpty().trim()
                applyDashboardFilter()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        listOf(
            R.id.filterAll to UiCategory.ALL,
            R.id.filterIndex to UiCategory.INDEX,
            R.id.filterFx to UiCategory.FX,
            R.id.filterCommodity to UiCategory.COMMODITY,
            R.id.filterRate to UiCategory.RATE
        ).forEach { (id, category) ->
            findViewById<Button>(id).setOnClickListener {
                uiCategory = category
                if (category == UiCategory.ALL) uiDirection = UiDirection.ALL
                updateDashboardFilterColors()
                applyDashboardFilter()
            }
        }
        findViewById<Button>(R.id.filterLong).setOnClickListener {
            uiDirection = if (uiDirection == UiDirection.LONG) UiDirection.ALL else UiDirection.LONG
            updateDashboardFilterColors()
            applyDashboardFilter()
        }
        findViewById<Button>(R.id.filterShort).setOnClickListener {
            uiDirection = if (uiDirection == UiDirection.SHORT) UiDirection.ALL else UiDirection.SHORT
            updateDashboardFilterColors()
            applyDashboardFilter()
        }
        updateDashboardFilterColors()
    }

    private fun applyDashboardFilter(preserveScroll: Boolean = false) {
        if (!::list.isInitialized) return
        val scrollState = if (preserveScroll) list.layoutManager?.onSaveInstanceState() else null
        val nowMs = System.currentTimeMillis()
        if (!showingUnderlying)
            productionItems = tr.borsatakip.v5.analysis.ViopDashboardPolicy.snapshot(productionCandidates, nowMs).items
        val query = uiQuery.lowercase(Locale.getDefault())
        if (showingUnderlying) {
            val categoryFiltered = underlyingItems.filter { candidate ->
                val contract = candidate.contract
                val searchOk = query.isBlank() || listOf(contract.underlying, contract.expiry, candidate.underlying.companyName.orEmpty())
                    .any { it.lowercase(Locale.getDefault()).contains(query) }
                searchOk && categoryMatches(contract.underlying)
            }
            val filtered = uiOrder.apply(categoryFiltered.filter { directionMatches(it.underlying.direction) },
                { it.contract.underlying }, { it.underlying.dailyChangePct })
            list.adapter = ViopUnderlyingOpportunityAdapter(filtered) { candidate -> openRealViopContract(candidate) }
            updateDashboardSummary(
                total = filtered.size,
                longs = categoryFiltered.count { it.underlying.direction.equals("LONG", true) },
                shorts = categoryFiltered.count { it.underlying.direction.equals("SHORT", true) },
                average = filtered.map { it.underlying.rankingScore }.takeIf { it.isNotEmpty() }?.average(),
                underlyingMode = true
            )
            findViewById<TextView>(R.id.emptySignals).apply { text = if (uiDirection == UiDirection.LONG) "LONG sinyali yok • ${underlyingItems.size} BIST dayanağı incelendi. Gerçek VİOP fiyat/geçmiş verisi doğrulanmadı." else if (uiDirection == UiDirection.SHORT) "SHORT sinyali yok • ${underlyingItems.size} BIST dayanağı incelendi. Gerçek VİOP fiyat/geçmiş verisi doğrulanmadı." else "Filtreye uygun dayanak yok • ${underlyingItems.size} BIST dayanağı; gerçek VİOP kontratı değildir."; visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE }
        } else {
            val matching = productionItems.filter { opportunity ->
                val c = opportunity.contract
                val searchOk = query.isBlank() || listOf(c.symbol, c.underlying, c.expiry)
                    .any { it.lowercase(Locale.getDefault()).contains(query) }
                searchOk && categoryMatches(c.underlying) && directionMatches(opportunity.direction)
            }
            val filtered = uiOrder.apply(matching, { it.contract.symbol }, { it.quote.dailyChangePct })
            list.adapter = ViopOpportunityAdapter(filtered, assessedAtMs = nowMs) { opportunity ->
                AppSession.selectedViopOpportunity = opportunity
                AppSession.selectedViopContract = opportunity.contract
                startActivity(Intent(this, ViopDetailActivity::class.java))
            }
            updateDashboardSummary(
                total = filtered.size,
                longs = filtered.count { it.direction.equals("LONG", true) },
                shorts = filtered.count { it.direction.equals("SHORT", true) },
                average = filtered.map { it.finalScore }.takeIf { it.isNotEmpty() }?.average(),
                underlyingMode = false
            )
            findViewById<TextView>(R.id.emptySignals).apply {
                text = if (productionItems.isNotEmpty()) "Bu arama veya filtreyle eşleşen doğrulanmış kontrat yok."
                    else "Gerçek VİOP fiyatı ve geçmişi doğrulanmadan sinyal gösterilmez. Bağlantınızı kontrol edin."
                visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        val empty = (list.adapter?.itemCount ?: 0) == 0
        val filtered = uiQuery.isNotBlank() || uiCategory != UiCategory.ALL || uiDirection != UiDirection.ALL
        findViewById<View>(R.id.viopEmptyState).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.viopEmptyTitle).text = if (filtered) "Eşleşen kayıt yok" else "Sözleşme verisi bekleniyor"
        findViewById<Button>(R.id.viopEmptyAction).text = if (filtered) "Filtreleri temizle" else "Bağlantı ayarları  ›"
        if (!empty && scrollState != null) list.layoutManager?.onRestoreInstanceState(scrollState)
    }

    private fun updateDashboardSummary(total: Int, longs: Int, shorts: Int, average: Double?, underlyingMode: Boolean) {
        val contractData = !underlyingMode && productionScanCompleted && readiness.localConfigState().state == ProviderState.PROVIDER_READY
        findViewById<TextView>(R.id.summaryLong).apply {
            text = "LONG\n${if (contractData) longs.toString() else "—"}"
            contentDescription = if (contractData) "$longs doğrulanmış LONG kontrat" else "LONG sayısı yok; gerçek VİOP verisi doğrulanmadı"
        }
        findViewById<TextView>(R.id.summaryShort).apply {
            text = "SHORT\n${if (contractData) shorts.toString() else "—"}"
            contentDescription = if (contractData) "$shorts doğrulanmış SHORT kontrat" else "SHORT sayısı yok; gerçek VİOP verisi doğrulanmadı"
        }
        val watchCount = ViopWatchlistStore(this).symbols().size
        findViewById<TextView>(R.id.summaryWatch).apply {
            text = "İZLEME\n$watchCount"
            contentDescription = "Kayıtlı VİOP izleme listesi: $watchCount"
        }
        findViewById<TextView>(R.id.signalSummary).text = buildString {
            append(if (underlyingMode) "${underlyingItems.size} BIST dayanağı (VİOP verisi değil) • Gösterilen $total • Yönsüz ${underlyingItems.count { !it.underlying.direction.equals("LONG", true) && !it.underlying.direction.equals("SHORT", true) }}" else if (productionScanCompleted) "${productionItems.size} doğrulanmış kontrat • Gösterilen $total" else "Tarama sonucu bekleniyor")
            average?.takeIf { !underlyingMode }?.let { append("  •  Ortalama skor %${it.toInt().coerceIn(0, 100)}") }
        }
        findViewById<TextView>(R.id.signalLastUpdate).text = "Ekran yenileme ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        findViewById<TextView>(R.id.marketSignals).text = if (underlyingMode) "Dayanak\n$total" else "Sinyal\n$total"
        findViewById<TextView>(R.id.marketTrend).text = when {
            total == 0 -> "Durum\nBEKLE"
            longs > shorts -> "Eğilim\nLONG"
            shorts > longs -> "Eğilim\nSHORT"
            else -> "Eğilim\nDENGELİ"
        }
    }

    private fun categoryMatches(underlyingRaw: String): Boolean {
        val underlying = underlyingRaw.trim().uppercase(Locale.ROOT)
        return when (uiCategory) {
            UiCategory.ALL -> true
            UiCategory.INDEX -> underlying.startsWith("XU") || underlying.contains("BIST")
            UiCategory.FX -> underlying.contains("USD") || underlying.contains("EUR") || underlying.contains("TRY")
            UiCategory.COMMODITY -> listOf("GOLD", "SILVER", "BRENT", "XAU", "XAG").any { underlying.contains(it) }
            UiCategory.RATE -> underlying.contains("RATE") || underlying.contains("FAIZ") || underlying.contains("TLREF")
        }
    }


    private fun directionMatches(directionRaw: String): Boolean {
        val direction = directionRaw.trim().uppercase(Locale.ROOT)
        return when (uiDirection) {
            UiDirection.ALL -> true
            UiDirection.LONG -> direction == "LONG"
            UiDirection.SHORT -> direction == "SHORT"
        }
    }

    private fun updateDashboardFilterColors() {
        val allSelected = uiCategory == UiCategory.ALL && uiDirection == UiDirection.ALL
        fun tint(buttonId: Int, selected: Boolean, selectedColor: Int) {
            val button = findViewById<Button>(buttonId)
            MarketScreenUi.chip(button, selected, selectedColor == getColor(R.color.red))
        }
        tint(R.id.filterAll, allSelected, getColor(R.color.green))
        tint(R.id.filterLong, uiDirection == UiDirection.LONG, getColor(R.color.green))
        tint(R.id.filterShort, uiDirection == UiDirection.SHORT, getColor(R.color.red))
        tint(R.id.filterIndex, uiCategory == UiCategory.INDEX, getColor(R.color.blue))
        tint(R.id.filterFx, uiCategory == UiCategory.FX, getColor(R.color.blue))
        tint(R.id.filterCommodity, uiCategory == UiCategory.COMMODITY, getColor(R.color.blue))
        tint(R.id.filterRate, uiCategory == UiCategory.RATE, getColor(R.color.blue))
    }

    private fun progressText(p: ViopScanProgress) = "Toplam ${p.total} • Quote ${p.quoteSuccess} • History ${p.historySuccess} • Analiz ${p.analyzed} • Yetersiz ${p.insufficient} • Elenen ${p.eliminated} • Hata ${p.failed}"
    private fun formatPrice(v: Double) = if (v >= 1000.0) "%,.2f".format(v) else "%.2f".format(v)
    private fun formatCompact(v: Double) = when { v >= 1_000_000 -> "%.1f M".format(v / 1_000_000.0); v >= 1_000 -> "%.1f K".format(v / 1_000.0); else -> "%.0f".format(v) }
    private fun timeOf(ms: Long) = if (ms <= 0) "—" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
    private fun shortDate(ms: Long) = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))

    private enum class UiCategory { ALL, INDEX, FX, COMMODITY, RATE }
    private enum class UiDirection { ALL, LONG, SHORT }

    companion object {
        private const val AUTO_TEST_COOLDOWN_MS = 30_000L
    }

}
