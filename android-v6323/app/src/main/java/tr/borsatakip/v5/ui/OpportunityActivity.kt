package tr.borsatakip.v5.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.OpportunityDiscoveryPresentation
import tr.borsatakip.v5.analysis.OpportunityTrendPresentation
import tr.borsatakip.v5.analysis.OpportunityFilter
import tr.borsatakip.v5.analysis.OpportunityFilterPolicy
import tr.borsatakip.v5.data.LastSuccessfulScanStore
import tr.borsatakip.v5.data.IntervalMarketDataProvider
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.data.RealtimeScannerClient
import tr.borsatakip.v5.data.RealtimeScannerSocket
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.ScanTimeframe
import tr.borsatakip.v5.data.SignalHistoryRecorder
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.ScanMode
import tr.borsatakip.v5.model.ScanRun
import tr.borsatakip.v5.model.ScanRunStatus
import tr.borsatakip.v5.scan.BistScanMode
import tr.borsatakip.v5.data.ScanPhaseTracker
import tr.borsatakip.v5.scan.BistScanner
import tr.borsatakip.v5.scan.ScanStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiscoveryFilter { ALL, STRONG_BUY, STRONG_SELL, WATCH, INSUFFICIENT, REJECTED, CATALYST, LOW_RISK, HIGH_VOLUME }
enum class DiscoverySort { SCORE, DATA_QUALITY, LOW_RISK, HIGH_VOLUME, CHEAPEST }

class OpportunityActivity : BaseActivity() {
    private var scanJob: Job? = null
    private var selectedFilter = DiscoveryFilter.ALL
    private var selectedSort = DiscoverySort.SCORE
    private var verifiedOnly = false
    private var lastSuccessfulRun: ScanRun? = null
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var historyStore: LastSuccessfulScanStore
    private var favoriteSymbols: Set<String> = emptySet()
    private lateinit var summary: TextView
    private lateinit var list: RecyclerView
    private lateinit var marketRegime: TextView
    private lateinit var watchedCount: TextView
    private lateinit var foundCount: TextView
    private lateinit var dataQuality: TextView
    private lateinit var realtimeSocket: RealtimeScannerSocket
    private val filterButtons = linkedMapOf<DiscoveryFilter, TextView>()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opportunity)
        setupBottomNav()

        favoriteRepository = FavoriteRepository.get(this)
        historyStore = LastSuccessfulScanStore(this)
        summary = findViewById(R.id.txtSummary)
        list = findViewById(R.id.list)
        marketRegime = findViewById(R.id.txtMarketRegime)
        watchedCount = findViewById(R.id.txtWatchedCount)
        foundCount = findViewById(R.id.txtFoundCount)
        dataQuality = findViewById(R.id.txtDataQuality)
        realtimeSocket = RealtimeScannerSocket(this)
        list.layoutManager = LinearLayoutManager(this)

        val ids = listOf(
            DiscoveryFilter.ALL to R.id.btnFilterAll,
            DiscoveryFilter.STRONG_BUY to R.id.btnFilterStrongBuy,
            DiscoveryFilter.STRONG_SELL to R.id.btnFilterStrongSell,
            DiscoveryFilter.WATCH to R.id.btnFilterWatch,
            DiscoveryFilter.INSUFFICIENT to R.id.btnFilterInsufficient,
            DiscoveryFilter.REJECTED to R.id.btnFilterRejected,
            DiscoveryFilter.CATALYST to R.id.btnFilterCatalyst,
            DiscoveryFilter.LOW_RISK to R.id.btnFilterLowRisk,
            DiscoveryFilter.HIGH_VOLUME to R.id.btnFilterHighVolume
        )
        ids.forEach { (filter, id) ->
            val button = findViewById<TextView>(id)
            filterButtons[filter] = button
            button.setOnClickListener {
                selectedFilter = filter
                updateFilterVisuals()
                lifecycleScope.launch { applyDiscoveryFilter() }
            }
        }
        updateFilterVisuals()
        findViewById<TextView>(R.id.btnVerifiedOnly).setOnClickListener {
            verifiedOnly = !verifiedOnly
            lifecycleScope.launch { applyDiscoveryFilter() }
        }
        findViewById<TextView>(R.id.btnCandidatesOnly).setOnClickListener {
            verifiedOnly = false
            selectedFilter = DiscoveryFilter.WATCH
            updateFilterVisuals()
            lifecycleScope.launch { applyDiscoveryFilter() }
        }
        findViewById<TextView>(R.id.btnLowPriceSort).setOnClickListener {
            selectedSort = DiscoverySort.CHEAPEST
            lifecycleScope.launch { applyDiscoveryFilter() }
        }

        findViewById<TextView>(R.id.btnSortOpportunity).setOnClickListener {
            selectedSort = when (selectedSort) {
                DiscoverySort.SCORE -> DiscoverySort.DATA_QUALITY
                DiscoverySort.DATA_QUALITY -> DiscoverySort.LOW_RISK
                DiscoverySort.LOW_RISK -> DiscoverySort.HIGH_VOLUME
                DiscoverySort.HIGH_VOLUME -> DiscoverySort.CHEAPEST
                DiscoverySort.CHEAPEST -> DiscoverySort.SCORE
            }
            Toast.makeText(this, "Sıralama: ${sortLabel(selectedSort)}", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { applyDiscoveryFilter() }
        }

        findViewById<TextView>(R.id.btnSignalHistory).setOnClickListener {
            startActivity(Intent(this, SignalHistoryActivity::class.java))
        }

        lifecycleScope.launch {
            favoriteRepository.migrateLegacyIfNeeded()
            refreshFavoriteSymbols()
            if (AppSession.lastOpportunities.isEmpty()) {
                historyStore.load()?.let { (run, items) ->
                    lastSuccessfulRun = run
                    AppSession.lastOpportunities = OpportunityFilterPolicy.apply(items, OpportunityFilter.ALL)
                }
            }
            showExisting()
        }

        val scanButton = findViewById<TextView>(R.id.btnRealOpportunityScan)
        scanButton.setOnClickListener {
            if (scanJob?.isActive == true) {
                scanJob?.cancel()
                return@setOnClickListener
            }
            scanButton.text = "DURDUR"
            scanJob = lifecycleScope.launch {
                try {
                    val settings = SettingsStore(this@OpportunityActivity)
                    val productionConfigured = productionBackendConfigured(settings)
                    val timeframeLabel = ScanTimeframe.displayLabel(settings.analysisTimeframeMinutes)
                    summary.text = if (productionConfigured) {
                        "Fırsat verileri toplanıyor • Production provider öncelikli • $timeframeLabel"
                    } else {
                        "Fırsat verileri toplanıyor • Yahoo Finance • YEDEK/GECİKMELİ • $timeframeLabel"
                    }
                    // Realtime Market Engine is the primary opportunity source. If the backend has
                    // a ready whole-market snapshot, do not re-query hundreds of symbols from the phone.
                    if (productionConfigured) {
                        val realtimeSnapshot = RealtimeScannerClient(this@OpportunityActivity)
                            .load(
                                limit = 200,
                                analysisTimeframeMinutes = settings.analysisTimeframeMinutes,
                                scanCadenceMinutes = settings.scanCadenceMinutes,
                                scanMode = ScanMode.MANUAL
                            )
                            .getOrNull()
                        if (realtimeSnapshot != null && realtimeSnapshot.readySymbols > 0) {
                            val results = OpportunityFilterPolicy.apply(realtimeSnapshot.opportunities, OpportunityFilter.ALL)
                            AppSession.lastOpportunities = results
                            val total = realtimeSnapshot.expectedSymbols.takeIf { it > 0 } ?: realtimeSnapshot.trackedSymbols
                            val coverage = realtimeSnapshot.readyCoveragePct?.let { "%.1f".format(it) } ?: "?"
                            applyDiscoveryFilter(
                                "CANLI FIRSATLAR • ${realtimeSnapshot.provider} • ${realtimeSnapshot.readySymbols}/$total hazır • kapsam %$coverage"
                            )
                            return@launch
                        }
                    }

                    // Production backend remains the preferred source. When it is absent or a
                    // primary request fails, allow the real Yahoo delayed fallback instead of
                    // aborting before BIST symbols can be loaded. The fallback is never promoted
                    // to realtime/verified output.
                    val baseProvider = ProviderRouter(
                        this@OpportunityActivity,
                        experimentalFallbackOverride = true
                    )
                    val scanProvider = IntervalMarketDataProvider(
                        baseProvider,
                        settings.analysisTimeframeMinutes
                    )
                    val scanner = BistScanner(
                        scanProvider,
                        SignalHistoryRecorder(this@OpportunityActivity),
                        BistScanMode.DELAYED_ANALYSIS,
                        "OPPORTUNITY"
                    )
                    val diagnosticTracker = ScanPhaseTracker(this@OpportunityActivity, "OPPORTUNITY")
                    val finalState = scanner.scan { state ->
                        diagnosticTracker.observe(state)
                        runOnUiThread {
                            summary.text = when (state.status) {
                                ScanStatus.IDLE -> "Hazır"
                                ScanStatus.RUNNING -> "Piyasa verisi toplanıyor • ${state.processed}/${state.total} • %${state.progress}"
                                ScanStatus.COMPLETED -> "Veri toplama tamamlandı • ${state.successful}/${state.total} analiz edildi"
                                ScanStatus.ERROR -> "Fırsat analizi başarısız • ${state.errorMessage ?: "Veri alınamadı"}"
                                ScanStatus.CANCELLED -> if (state.errorMessage == "SCAN_ALREADY_RUNNING") "Mevcut BIST taraması sürüyor; çakışma engellendi" else "Fırsat analizi durduruldu"
                            }
                        }
                    }
                    if (finalState.errorMessage == "SCAN_ALREADY_RUNNING") {
                        summary.text = "Mevcut BIST taraması sürüyor; ikinci fırsat taraması atlandı. Önceki fırsatlar korundu."
                        return@launch
                    }
                    AppSession.lastScanState = finalState
                    val run = finalState.scanRun
                    // This scanner path is deliberately DELAYED_ANALYSIS: it may use Yahoo fallback
                    // and therefore must never overwrite the verified production history/store.
                    val delayedSuffix = " • doğrulanmış sinyal geçmişine yazılmadı"
                    when {
                        finalState.status == ScanStatus.COMPLETED && run?.status == ScanRunStatus.COMPLETE -> {
                            val results = OpportunityFilterPolicy.apply(finalState.results, OpportunityFilter.ALL)
                            AppSession.lastOpportunities = results
                            bindFiltered(
                                results,
                                "YEDEK/GECİKMELİ FIRSAT GÖZLEMİ • ${formatRunTime(run)} • ${results.size} aday$delayedSuffix"
                            )
                        }
                        finalState.status == ScanStatus.COMPLETED && run?.status == ScanRunStatus.PARTIAL && finalState.successful > 0 -> {
                            val partial = OpportunityFilterPolicy.apply(finalState.results, OpportunityFilter.ALL)
                            AppSession.lastOpportunities = partial
                            bindFiltered(
                                partial,
                                "KISMİ YEDEK/GECİKMELİ VERİ • Başarılı ${finalState.successful}/${finalState.total} • Hatalı/atlanan ${finalState.skipped}$delayedSuffix"
                            )
                        }
                        finalState.status == ScanStatus.COMPLETED -> bindFiltered(
                            emptyList(),
                            "YEDEK/GECİKMELİ ANALİZ • Kalite eşiğini geçen fırsat bulunamadı.$delayedSuffix"
                        )
                    }
                } catch (_: CancellationException) {
                    summary.text = "Fırsat analizi durduruldu • son başarılı sonuç korunuyor"
                } catch (t: Throwable) {
                    summary.text = "Fırsat analizi başarısız • ${t.message ?: "Beklenmeyen veri hatası"}"
                } finally {
                    scanButton.text = "FIRSATLARI YENİLE"
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val settings = SettingsStore(this)
        if (!productionBackendConfigured(settings)) {
            if (AppSession.lastOpportunities.isEmpty()) {
                summary.text = "Yahoo Finance • YEDEK/GECİKMELİ hazır • FIRSATLARI YENİLE ile analiz başlat"
            }
            return
        }
        realtimeSocket.connect(
            minScore = 55,
            limit = 50,
            actionableOnly = false,
            minIntervalMs = 1500,
            analysisTimeframeMinutes = settings.analysisTimeframeMinutes,
            scanCadenceMinutes = settings.scanCadenceMinutes,
            scanMode = ScanMode.LIVE,
            listener = object : RealtimeScannerSocket.Listener {
                override fun onSubscribed() = runOnUiThread {
                    if (AppSession.lastOpportunities.isEmpty()) {
                        summary.text = "Canlı BIST scanner bağlandı • fırsat bekleniyor"
                    }
                }

                override fun onSnapshot(snapshot: RealtimeScannerClient.Snapshot) = runOnUiThread {
                    // An empty/not-ready snapshot is authoritative: never keep displaying stale opportunities.
                    AppSession.lastOpportunities = snapshot.opportunities
                    lifecycleScope.launch {
                        if (snapshot.readySymbols <= 0) {
                            bindFiltered(
                                emptyList(),
                                "CANLI AKIŞ • ${snapshot.provider} • hazır sembol yok • önceki fırsatlar geçersizleştirildi"
                            )
                            return@launch
                        }
                        val total = snapshot.expectedSymbols.takeIf { it > 0 } ?: snapshot.trackedSymbols
                        val coverage = snapshot.readyCoveragePct?.let { " • hazır kapsam %${"%.1f".format(it)}" } ?: ""
                        applyDiscoveryFilter(
                            "CANLI AKIŞ • ${snapshot.provider} • ${snapshot.readySymbols}/$total hazır$coverage" +
                                " • analiz ${ScanTimeframe.displayLabel(snapshot.analysisTimeframeMinutes)} • cadence ${snapshot.scanCadenceMinutes} DK"
                        )
                    }
                }

                override fun onDisconnected(reason: String) = runOnUiThread {
                    // Fail closed: a disconnected live feed must not leave old opportunities looking current.
                    AppSession.lastOpportunities = emptyList()
                    lifecycleScope.launch {
                        bindFiltered(emptyList(), "Canlı scanner bağlantısı kapandı • $reason • eski fırsatlar geçersizleştirildi")
                    }
                }

                override fun onError(message: String) = runOnUiThread {
                    // Connection/data errors are always visible, even when the list was previously populated.
                    AppSession.lastOpportunities = emptyList()
                    lifecycleScope.launch {
                        bindFiltered(emptyList(), "Canlı scanner hatası • $message • eski fırsatlar geçersizleştirildi")
                    }
                }
            }
        )
    }

    override fun onStop() {
        realtimeSocket.close()
        super.onStop()
    }

    private suspend fun applyDiscoveryFilter(prefix: String? = null) {
        val base = OpportunityFilterPolicy.apply(AppSession.lastOpportunities, OpportunityFilter.ALL)
        val filtered = base.filter { x ->
            val p = OpportunityDiscoveryPresentation.from(x)
            when (selectedFilter) {
                DiscoveryFilter.ALL -> true
                DiscoveryFilter.STRONG_BUY -> OpportunityTrendPresentation.from(x).verifiedSignal && x.direction.equals("LONG", true) && p.classification in setOf("GÜÇLÜ AL", "AL")
                DiscoveryFilter.STRONG_SELL -> OpportunityTrendPresentation.from(x).verifiedSignal && x.direction.equals("SHORT", true) && p.classification in setOf("GÜÇLÜ SAT", "SAT")
                DiscoveryFilter.WATCH -> !OpportunityTrendPresentation.from(x).verifiedSignal && p.classification !in setOf("YETERSİZ VERİ", "REDDEDİLDİ")
                DiscoveryFilter.INSUFFICIENT -> p.classification == "YETERSİZ VERİ"
                DiscoveryFilter.REJECTED -> p.classification == "REDDEDİLDİ"
                DiscoveryFilter.CATALYST -> p.catalystAvailable
                DiscoveryFilter.LOW_RISK -> p.lowRisk
                DiscoveryFilter.HIGH_VOLUME -> p.highVolume
            }
        }
        val eligible = if (verifiedOnly) filtered.filter { OpportunityTrendPresentation.from(it).verifiedSignal } else filtered
        val sorted = when (selectedSort) {
            DiscoverySort.SCORE -> eligible.sortedWith(compareByDescending<Opportunity> { it.rankingScore }.thenByDescending { it.finalSignalScore }.thenBy { it.riskScore })
            DiscoverySort.DATA_QUALITY -> eligible.sortedByDescending { it.dataConfidenceScore }
            DiscoverySort.LOW_RISK -> eligible.sortedBy { it.riskScore }
            DiscoverySort.HIGH_VOLUME -> eligible.sortedByDescending { it.technical.volumeRatio ?: -1.0 }
            DiscoverySort.CHEAPEST -> eligible.sortedBy { it.price.takeIf { price -> price > 0.0 } ?: Double.MAX_VALUE }
        }
        val baseText = prefix ?: lastSuccessfulRun?.let { "SON GÜNCELLEME • ${formatRunTime(it)}" } ?: "FIRSAT KONTROLÜ"
        val verifiedCount = base.count { OpportunityTrendPresentation.from(it).verifiedSignal }
        val candidates = base.count { !OpportunityTrendPresentation.from(it).verifiedSignal && OpportunityDiscoveryPresentation.from(it).classification !in setOf("YETERSİZ VERİ", "REDDEDİLDİ") }
        val rejected = base.count { OpportunityDiscoveryPresentation.from(it).classification == "REDDEDİLDİ" }
        val insufficient = base.count { OpportunityDiscoveryPresentation.from(it).classification == "YETERSİZ VERİ" }
        val emptySuffix = if (sorted.isEmpty()) " • Bu filtrede kayıt yok veya veri yetersiz" else ""
        bindFiltered(sorted, "$baseText • ${filterLabel(selectedFilter)} • ${sorted.size}/${base.size} ANALİZ • ONAYLI $verifiedCount • ADAY $candidates • RED $rejected • VERİSİZ $insufficient$emptySuffix")
    }

    private suspend fun bindFiltered(items: List<Opportunity>, title: String) {
        refreshFavoriteSymbols()
        summary.text = title
        updateTopMetrics(AppSession.lastOpportunities)
        list.adapter = OpportunityAdapter(
            items,
            favoriteSymbols,
            click = {
                AppSession.selected = it
                startActivity(Intent(this, StockDetailActivity::class.java).putExtra("opportunity", it))
            },
            toggleFavorite = { opportunity ->
                lifecycleScope.launch {
                    val added = favoriteRepository.toggle(
                        opportunity.symbol,
                        opportunity.companyName,
                        analysisIntervalMinutes = opportunity.analysisTimeframeMinutes.takeIf { ScanTimeframe.isSupportedStored(it) }
                    )
                    Toast.makeText(
                        this@OpportunityActivity,
                        if (added) "${opportunity.symbol} favorilere eklendi" else "${opportunity.symbol} favorilerden çıkarıldı",
                        Toast.LENGTH_SHORT
                    ).show()
                    applyDiscoveryFilter()
                }
            }
        )
    }

    private fun updateTopMetrics(items: List<Opportunity>) {
        marketRegime.text = "REJİM DOĞRULANMADI"
        marketRegime.contentDescription = "BIST 100 ve BIST 30 referans verisi doğrulanamadı; piyasa trendi yüzdeyle sunulmuyor."
        marketRegime.setTextColor(getColor(R.color.yellow))
        val watched = AppSession.lastScanState?.total?.takeIf { it > 0 } ?: lastSuccessfulRun?.count?.takeIf { it > 0 } ?: items.size
        watchedCount.text = watched.toString()
        // Count trade opportunities only after independent realtime validity checks.
        val verifiedCount = items.count { OpportunityTrendPresentation.from(it).verifiedSignal }
        val candidateCount = items.count { !OpportunityTrendPresentation.from(it).verifiedSignal && OpportunityDiscoveryPresentation.from(it).classification !in setOf("YETERSİZ VERİ", "REDDEDİLDİ") }
        val rejectedCount = items.count { OpportunityDiscoveryPresentation.from(it).classification == "REDDEDİLDİ" }
        val insufficientCount = items.count { OpportunityDiscoveryPresentation.from(it).classification == "YETERSİZ VERİ" }
        foundCount.text = verifiedCount.toString()
        findViewById<TextView>(R.id.txtScanModeDisclosure).text =
            if (verifiedCount > 0) "CANLI / ONAYLI • $verifiedCount teyitli sinyal" else "GECİKMELİ / DOĞRULANMAMIŞ • İşlem sinyali yok"
        findViewById<TextView>(R.id.txtScanEvidence).text =
            "Toplam ${items.size} analiz • Onaylı $verifiedCount • Aday $candidateCount • RED $rejectedCount • Verisiz $insufficientCount"
        foundCount.contentDescription = "Onaylı $verifiedCount, aday $candidateCount, reddedilen $rejectedCount, veri yetersiz $insufficientCount, toplam analiz ${items.size}"
        findViewById<TextView>(R.id.txtOutcomeBreakdown).text = "ONAYLI $verifiedCount  •  ADAY $candidateCount  •  RED $rejectedCount  •  VERİSİZ $insufficientCount"
        val avgQuality = if (items.isEmpty()) null else items.map { it.dataConfidenceScore }.average().toInt()
        dataQuality.text = avgQuality?.let { "%$it" } ?: "—"
    }

    private fun updateFilterVisuals() {
        filterButtons.forEach { (filter, button) ->
            val active = filter == selectedFilter
            button.alpha = if (active) 1f else 0.62f
            val clean = button.text.toString().removePrefix("✓ ")
            button.text = if (active) "✓ $clean" else clean
        }
    }

    private suspend fun refreshFavoriteSymbols() {
        favoriteSymbols = favoriteRepository.symbols()
    }

    private suspend fun showExisting() {
        val settings = SettingsStore(this)
        if (AppSession.lastOpportunities.isEmpty()) {
            val text = if (productionBackendConfigured(settings)) {
                "Kayıtlı fırsat yok • FIRSATLARI YENİLE ile Production verisini değerlendir"
            } else {
                "Kayıtlı doğrulanmış fırsat yok • Yahoo YEDEK/GECİKMELİ analiz kullanılabilir"
            }
            bindFiltered(emptyList(), text)
        } else {
            applyDiscoveryFilter()
        }
    }

    private fun filterLabel(filter: DiscoveryFilter) = when (filter) {
        DiscoveryFilter.ALL -> "Tüm Analizler"
        DiscoveryFilter.STRONG_BUY -> "Güçlü Al"
        DiscoveryFilter.STRONG_SELL -> "Güçlü Sat"
        DiscoveryFilter.WATCH -> "İzleme"
        DiscoveryFilter.INSUFFICIENT -> "Yetersiz Veri"
        DiscoveryFilter.REJECTED -> "Reddedildi"
        DiscoveryFilter.CATALYST -> "Katalizörlü"
        DiscoveryFilter.LOW_RISK -> "Düşük Risk"
        DiscoveryFilter.HIGH_VOLUME -> "Yüksek Hacim"
    }

    private fun sortLabel(sort: DiscoverySort) = when (sort) {
        DiscoverySort.SCORE -> "Fırsat Skoru"
        DiscoverySort.DATA_QUALITY -> "Veri Kalitesi"
        DiscoverySort.LOW_RISK -> "Düşük Risk"
        DiscoverySort.HIGH_VOLUME -> "Yüksek Hacim"
        DiscoverySort.CHEAPEST -> "Ucuzdan Pahalıya"
    }

    private fun formatRunTime(run: ScanRun): String {
        val ts = run.scanCompletedAt ?: run.scanStartedAt
        return if (ts > 0) dateFormat.format(Date(ts)) else "zaman bilinmiyor"
    }

    private fun productionBackendConfigured(settings: SettingsStore): Boolean =
        tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl) && settings.apiKey.isNotBlank()

    override fun onResume() {
        super.onResume()
        if (::favoriteRepository.isInitialized && ::list.isInitialized) {
            lifecycleScope.launch { showExisting() }
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        scanJob = null
        super.onDestroy()
    }

    companion object { const val EXTRA_SCAN_WARNING = "scan_warning" }
}
