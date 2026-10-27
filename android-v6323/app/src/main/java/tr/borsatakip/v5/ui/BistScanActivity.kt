package tr.borsatakip.v5.ui

import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.OpportunityRankingPolicy
import tr.borsatakip.v5.data.BackendPreflightClient
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.data.ProviderException
import tr.borsatakip.v5.data.ProviderFailureCode
import tr.borsatakip.v5.data.IntervalMarketDataProvider
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.ScanTimeframe
import tr.borsatakip.v5.data.SignalHistoryRecorder
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.ScanRunStatus
import tr.borsatakip.v5.model.ScanMode
import tr.borsatakip.v5.scan.BistScanMode
import tr.borsatakip.v5.data.ScanPhaseTracker
import tr.borsatakip.v5.data.DiagnosticJournal
import tr.borsatakip.v5.scan.BistScanner
import tr.borsatakip.v5.scan.ScanStatus
import tr.borsatakip.v5.scan.ScanPhase
import tr.borsatakip.v5.worker.AutoScanScheduler
import tr.borsatakip.v5.worker.AutoScanUiPolicy
import java.util.Locale

class BistScanActivity : BaseActivity() {
    private enum class QuickMode { POPULAR, FAVORITES, CUSTOM }

    private var scanJob: Job? = null
    private lateinit var source: TextView
    private lateinit var settings: SettingsStore
    private lateinit var search: EditText
    private lateinit var heroSubtitle: TextView
    private lateinit var detailPanel: View
    private lateinit var quickPopular: TextView
    private lateinit var quickFavorites: TextView
    private lateinit var quickCustom: TextView
    private lateinit var scanTimeButton: View
    private lateinit var scanTimeArrow: TextView
    private lateinit var activeScanTime: TextView
    private lateinit var autoScanSwitch: SwitchCompat
    private lateinit var autoScanStatus: TextView
    private var suppressAutoScanListener = false
    private val autoScanPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(this, if (granted) "Otomatik tarama durum bildirimi izni verildi."
            else "Android bildirim izni verilmedi. Fırsat bildirimleri için Ayarlar → Bildirimler bölümünü açın.", Toast.LENGTH_LONG).show()
        refreshAutoScanUi()
    }
    private val autoUiHandler = Handler(Looper.getMainLooper())
    private val autoUiTick = object : Runnable {
        override fun run() {
            if (::settings.isInitialized && ::autoScanSwitch.isInitialized) {
                refreshAutoScanUi()
                refreshScanTimeUi()
            }
            autoUiHandler.postDelayed(this, 2_500L)
        }
    }
    private var scanTimePopup: PopupWindow? = null
    private var quickMode = QuickMode.POPULAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bist_scan)
        setupBottomNav()
        setupScanBottomNav()

        val progress = findViewById<ProgressBar>(R.id.progress)
        val txt = findViewById<TextView>(R.id.txtProgress)
        val status = findViewById<TextView>(R.id.txtStatus)
        val btn = findViewById<Button>(R.id.btnStartScan)
        val configure = findViewById<Button>(R.id.btnConfigureProvider)
        source = findViewById(R.id.txtSource)
        val debug = findViewById<TextView>(R.id.txtDebugState)
        search = findViewById(R.id.scanSearch)
        heroSubtitle = findViewById(R.id.txtScanHeroSubtitle)
        detailPanel = findViewById(R.id.scanDetailPanel)
        quickPopular = findViewById(R.id.quickPopular)
        quickFavorites = findViewById(R.id.quickFavorites)
        quickCustom = findViewById(R.id.quickCustom)
        scanTimeButton = findViewById(R.id.btnScanTime)
        scanTimeArrow = findViewById(R.id.txtScanTimeArrow)
        activeScanTime = findViewById(R.id.txtActiveScanTime)
        autoScanSwitch = findViewById(R.id.switchAutoScan)
        autoScanStatus = findViewById(R.id.txtAutoScanStatus)
        settings = SettingsStore(this)

        refreshSourceLabel()
        refreshScanTimeUi()
        refreshAutoScanUi()
        scanTimeButton.setOnClickListener { toggleScanTimeMenu() }
        autoScanSwitch.setOnCheckedChangeListener { _, enabled ->
            if (suppressAutoScanListener) return@setOnCheckedChangeListener
            settings.autoScanEnabled = enabled
            if (enabled) {
                settings.autoScanMonitoringStartedAt = System.currentTimeMillis()
                settings.autoScanLastStatus = "STARTING"
                settings.autoScanLastMessage = "İlk otomatik tarama doğrulanıyor."
                settings.autoScanLastHeartbeatAt = 0L
            } else {
                settings.autoScanLastStatus = "STOPPED"
                settings.autoScanLastMessage = "Kullanıcı tarafından kapatıldı."
            }
            val selected = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
            val result = runCatching { AutoScanScheduler.reconcile(this, allowForegroundStart = true) }
                .getOrElse { AutoScanScheduler.ScheduleResult(false, it.message ?: "Zamanlayıcı başlatılamadı") }
            if (enabled && !result.accepted) {
                settings.autoScanEnabled = false
                settings.autoScanLastStatus = "CONFIG_ERROR"
                settings.autoScanLastMessage = result.message
                status.text = "BAŞLATILAMADI"
                heroSubtitle.text = result.message
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            } else if (enabled) {
                status.text = "BAŞLAMA BEKLENİYOR"
                heroSubtitle.text = "${selected.label} zamanlayıcı kabul edildi. İlk gerçek taramanın başlaması bekleniyor; Yahoo yalnız GECİKMELİ analizdir."
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    autoScanPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                status.text = "DURDURULDU"
                heroSubtitle.text = "Otomatik tarama kapatıldı."
            }
            refreshAutoScanUi()
            refreshScanTimeUi()
        }
        btn.text = defaultScanButtonLabel()
        status.text = "READY"
        txt.text = "TARAMA BAŞLAMADI"
        heroSubtitle.text = initialHeroMessage()
        progress.progress = 0
        progress.visibility = View.GONE
        debug.text = "Tarama ilerlemesi ile başarılı analiz sayısı ayrı gösterilir."

        findViewById<View>(R.id.btnHowItWorks).setOnClickListener { showHowItWorks() }
        findViewById<View>(R.id.btnStatusDetail).setOnClickListener { toggleDetails() }
        findViewById<View>(R.id.btnScanFilter).setOnClickListener {
            selectQuickMode(QuickMode.CUSTOM)
            focusSearch()
        }
        findViewById<View>(R.id.btnCheapStockScan).setOnClickListener {
            startActivity(Intent(this, CheapStockScanActivity::class.java))
        }
        quickPopular.setOnClickListener { selectQuickMode(QuickMode.POPULAR) }
        quickFavorites.setOnClickListener { selectQuickMode(QuickMode.FAVORITES) }
        quickCustom.setOnClickListener {
            selectQuickMode(QuickMode.CUSTOM)
            focusSearch()
        }

        findViewById<View>(R.id.quickSectors).setOnClickListener { startActivity(Intent(this, StocksActivity::class.java)) }
        findViewById<View>(R.id.quickCriteria).setOnClickListener {
            selectQuickMode(QuickMode.CUSTOM)
            focusSearch()
        }
        findViewById<View>(R.id.quickHistory).setOnClickListener { startActivity(Intent(this, SignalHistoryActivity::class.java)) }
        findViewById<View>(R.id.quickSaved).setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        findViewById<View>(R.id.btnAllTools).setOnClickListener { startActivity(Intent(this, OpportunityActivity::class.java)) }

        configure.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btn.setOnClickListener {
            if (scanJob?.isActive == true) {
                scanJob?.cancel()
                return@setOnClickListener
            }
            if (settings.autoScanEnabled) {
                autoScanSwitch.isChecked = false
                return@setOnClickListener
            }

            val timeframe = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
            val analysisTimeframeMinutes = timeframe.storedMinutes
            settings.scanMode = ScanMode.MANUAL
            val productionReady = productionBackendConfigured()

            progress.progress = 0
            progress.visibility = View.VISIBLE
            btn.text = "■\nDURDUR"
            status.text = "Doğrulanıyor"
            txt.text = "TARAMA BAŞLAMADI"
            heroSubtitle.text = "Veri sağlayıcısı ve BIST sembol evreni doğrulanıyor."
            debug.text = "Kontrol: Backend → Health → Authentication → BIST Symbols → Quote → History"
            refreshSourceLabel()

            scanJob = lifecycleScope.launch {
                try {
                    var announcedTotal = 0
                    var preflightSampleSymbol: String? = null
                    var universeVerified = false

                    // Manuel tarama her basışta gerçek tarama zincirini çalıştırır.
                    // Hazır/cached realtime snapshot manuel taramanın yerine geçmez.

                    if (productionReady) {
                        val preflight = BackendPreflightClient(this@BistScanActivity).checkBist()
                        if (!preflight.ok) {
                            progress.progress = 0
                            progress.visibility = View.GONE
                            txt.text = "PROVIDER HAZIR DEĞİL"
                            status.text = "Hazır değil"
                            heroSubtitle.text = preflight.message
                            debug.text = "${preflight.failureKind} • ScanRun oluşturulmadı • son başarılı tarama korunuyor.\nAyarlar → Veri Sağlayıcı bölümünden gerçek HTTPS backend adresini yapılandırın."
                            return@launch
                        }
                        announcedTotal = preflight.symbolCount
                        preflightSampleSymbol = preflight.sampleSymbol
                        // Tam-evren etiketi yüzde eşiğinden türetilmez. Yalnız BIST symbols
                        // endpointinin başarılı, pozitif ve örnek sembollü sözleşmesi doğrulanınca kullanılır.
                        universeVerified = preflight.symbolsOk && preflight.symbolCount > 0 && !preflight.sampleSymbol.isNullOrBlank()
                        txt.text = "0 / $announcedTotal  •  %0"
                        status.text = "STARTING"
                        heroSubtitle.text = "$announcedTotal gerçek BIST sembolü doğrulandı • ${timeframe.label} veri kontrolü başlıyor."
                        debug.text = "Health ✓ • Authentication ✓ • Symbols ✓ (${preflight.symbolCount}) • Quote ✓ • History ✓ • Evren doğrulandı=$universeVerified • seçili timeframe=${timeframe.apiInterval}"
                    } else {
                        status.text = "Yedek/gecikmeli analiz"
                        heroSubtitle.text = "Yahoo Finance ${timeframe.label} OHLCV ile gecikmeli teknik analiz • doğrulanmış AL/SAT sinyali üretilmez."
                        debug.text = "Yahoo Finance • YEDEK/GECİKMELİ • ${timeframe.apiInterval} OHLCV • doğrulanmış işlem sinyali kapalı"
                    }

                    val scanMode = if (productionReady) BistScanMode.REALTIME_ONLY else BistScanMode.DELAYED_ANALYSIS
                    val baseProvider = ProviderRouter(
                        this@BistScanActivity,
                        experimentalFallbackOverride = !productionReady
                    )
                    // Seçili timeframe hem Production Backend hem de Yahoo gecikmeli yolda
                    // aynı adapter üzerinden uygulanır. 3 DK gerçek 1 DK OHLCV'den üretilir.
                    val scanProvider = IntervalMarketDataProvider(baseProvider, analysisTimeframeMinutes)
                    if (scanMode == BistScanMode.REALTIME_ONLY) {
                        val sample = preflightSampleSymbol
                        if (sample.isNullOrBlank()) {
                            throw IllegalStateException("BIST HİSSE LİSTESİ ALINAMADI: örnek sembol yok.")
                        }
                        status.text = "LOADING_DATA"
                        heroSubtitle.text = "$sample için ${timeframe.label} OHLCV desteği doğrulanıyor."
                        val sampleStock = try {
                            scanProvider.fetchOne(sample)
                        } catch (pe: ProviderException) {
                            DiagnosticJournal(this@BistScanActivity).record("PREFLIGHT", "FETCH", pe.code.name)
                            progress.visibility = View.GONE
                            txt.text = "TARAMA BAŞLAMADI"
                            status.text = providerStatusLabel(pe.code, timeframe)
                            heroSubtitle.text = providerUserMessage(pe.code, timeframe, pe.message)
                            debug.text = "SYMBOL=$sample • TIMEFRAME=${timeframe.apiInterval} • ENDPOINT=history-window/${timeframe.apiInterval} • ERROR_CODE=${pe.code.name} • ERROR_MESSAGE=${pe.message ?: "unknown"}"
                            return@launch
                        }
                        if (sampleStock == null || sampleStock.candles.size < tr.borsatakip.v5.data.RealTimeIntegrityPolicy.MIN_HISTORY_BARS) {
                            progress.visibility = View.GONE
                            txt.text = "TARAMA BAŞLAMADI"
                            status.text = "${timeframe.label} VERİ SERVİSİ HAZIR DEĞİL"
                            heroSubtitle.text = "$sample için yeterli ${timeframe.label} OHLCV alınamadı. Tüm BIST taraması başlatılmadı."
                            DiagnosticJournal(this@BistScanActivity).record("PREFLIGHT", "OHLCV", "INSUFFICIENT_HISTORY")
                            debug.text = "SYMBOL=$sample • TIMEFRAME=${timeframe.apiInterval} • ERROR_CODE=INSUFFICIENT_HISTORY • sahte sonuç üretilmedi."
                            return@launch
                        }
                        debug.text = "Timeframe preflight ✓ • SYMBOL=$sample • TIMEFRAME=${timeframe.apiInterval} • interval=${sampleStock.interval} • mum=${sampleStock.candles.size} • gerçek OHLCV doğrulandı"
                    }
                    val diagnosticTracker = ScanPhaseTracker(this@BistScanActivity, "MANUAL")
                    val scanner = BistScanner(
                        scanProvider,
                        SignalHistoryRecorder(this@BistScanActivity),
                        scanMode,
                        "MANUAL"
                    )
                    val finalState = scanner.scan { state ->
                        diagnosticTracker.observe(state)
                        runOnUiThread {
                            progress.visibility = View.VISIBLE
                            progress.progress = state.progress
                            val visibleTotal = if (state.total > 0) state.total else announcedTotal
                            if (visibleTotal > 0) {
                                val visibleProcessed = state.processed.coerceIn(0, visibleTotal)
                                txt.text = "$visibleProcessed / $visibleTotal  •  %${state.progress}"
                            } else if (state.status == ScanStatus.RUNNING) {
                                txt.text = if (visibleTotal > 0) "${state.processed.coerceIn(0, visibleTotal)} / $visibleTotal" else "0 / ?"
                            }

                            status.text = state.phase.name
                            when (state.phase) {
                                ScanPhase.IDLE, ScanPhase.READY -> heroSubtitle.text = "Kriterlerinizi seçip taramayı başlatın."
                                ScanPhase.STARTING -> heroSubtitle.text = "Geçici olarak tarama başlatılıyor..."
                                ScanPhase.LOADING_SYMBOLS -> heroSubtitle.text = "BIST hisse evreni gerçek veri kaynağından alınıyor."
                                ScanPhase.LOADING_DATA -> heroSubtitle.text = "${timeframe.label} OHLCV verileri hazırlanıyor."
                                ScanPhase.SCANNING -> heroSubtitle.text = "BIST hisseleri ${timeframe.label} verileri üzerinden taranıyor."
                                ScanPhase.CALCULATING -> heroSubtitle.text = "${timeframe.label} teknik göstergeler, trend, momentum, hacim ve kırılım hesaplanıyor."
                                ScanPhase.RANKING -> heroSubtitle.text = "LONG/SHORT, risk, veri güveni ve fırsat skorları sıralanıyor."
                                ScanPhase.COMPLETED -> {
                                    heroSubtitle.text = "TARAMA TAMAMLANDI • ${timeframe.label}"
                                    val historyText = state.historyError?.let { " • History hata: $it" }
                                        ?: if (state.scanMode == BistScanMode.REALTIME_ONLY) " • Geçmişe ${state.historyPersisted} yeni kayıt" else ""
                                    debug.text = "Taranan ${state.total} • Başarılı ${state.successful} • Veri yok ${state.noData} • Hata ${state.errors} • Fırsat ${state.results.size}$historyText"
                                }
                                ScanPhase.NO_DATA -> heroSubtitle.text = "SEÇİLEN PERİYOT İÇİN VERİ KULLANILAMIYOR"
                                ScanPhase.ERROR -> {
                                    heroSubtitle.text = state.errorMessage ?: "VERİ ALINAMADI"
                                    if (state.total <= 0) txt.text = "TARAMA BAŞLAMADI"
                                }
                                ScanPhase.STOPPED -> heroSubtitle.text = "Tarama durduruldu; yarım tarama tamamlandı sayılmadı."
                            }
                            if (state.status == ScanStatus.RUNNING) {
                                debug.text = "Aşama ${state.phase.name} • İşlenen ${state.processed}/${visibleTotal.coerceAtLeast(state.total)} • Başarılı ${state.successful} • Veri yok ${state.noData} • Hata ${state.errors}"
                            }
                        }
                    }

                    if (finalState.errorMessage == "SCAN_ALREADY_RUNNING") {
                        status.text = "Mevcut tarama devam ediyor"
                        txt.text = "YENİ İSTEK ATLANDI"
                        heroSubtitle.text = "Başka bir BIST taraması devam ettiği için ikinci istek başlatılmadı. Mevcut tarama durdurulmadı; ilerlemeyi başladığı ekrandan izleyin."
                        debug.text = "SCAN_ALREADY_RUNNING • İkinci istek atlandı. Bu mesaj mevcut taramanın hata verdiği veya tamamlandığı anlamına gelmez."
                        progress.visibility = View.GONE
                        return@launch
                    }
                    AppSession.lastScanState = finalState
                    val runStatus = finalState.scanRun?.status
                    val sortedResults = OpportunityRankingPolicy.sort(finalState.results)
                    val visibleResults = filterResults(sortedResults)

                    when {
                        finalState.status == ScanStatus.COMPLETED && runStatus == ScanRunStatus.COMPLETE -> {
                            AppSession.lastOpportunities = visibleResults
                            progress.progress = 100
                            txt.text = "${finalState.processed}/${finalState.total}  •  %100"
                            status.text = "Tamamlandı"
                            heroSubtitle.text = resultModeSummary(finalState.successful, visibleResults.size) + " • ${timeframe.label}"
                            debug.text = if (finalState.scanMode == BistScanMode.REALTIME_ONLY) {
                                "ScanRun=COMPLETE • Hata/atlanan 0 • merkezi history kaydı ${finalState.historyPersisted}."
                            } else {
                                "ScanRun=COMPLETE • GECİKMELİ TEKNİK ANALİZ • AL/SAT YOK • sinyal geçmişine yazılmadı."
                            }
                            val intent = Intent(this@BistScanActivity, ScanResultsActivity::class.java)
                            if (finalState.scanMode == BistScanMode.DELAYED_ANALYSIS) {
                                intent.putExtra(OpportunityActivity.EXTRA_SCAN_WARNING, "YAHOO FINANCE • YEDEK/GECİKMELİ • ${timeframe.label} TEKNİK GÖZLEM • DOĞRULANMIŞ AL/SAT SİNYALİ DEĞİLDİR")
                            }
                            startActivity(intent)
                        }

                        finalState.status == ScanStatus.COMPLETED &&
                            runStatus == ScanRunStatus.PARTIAL &&
                            finalState.successful > 0 -> {
                            AppSession.lastOpportunities = visibleResults
                            progress.progress = 100
                            txt.text = "${finalState.processed}/${finalState.total}  •  %100"
                            status.text = "Kısmi"
                            heroSubtitle.text = resultModeSummary(finalState.successful, visibleResults.size) + " • ${timeframe.label}"
                            debug.text = "ScanRun=PARTIAL • Veri güvenilirliği uyarısı ${finalState.integrityRejected} • merkezi history kaydı ${finalState.historyPersisted} • son COMPLETE tarama kaydı ezilmedi."
                            startActivity(
                                Intent(this@BistScanActivity, ScanResultsActivity::class.java)
                                    .putExtra(OpportunityActivity.EXTRA_SCAN_WARNING, "KISMİ TARAMA • ${timeframe.label} • Başarılı ${finalState.successful}/${finalState.total} • Veri yok ${finalState.noData} • Hata ${finalState.errors}")
                            )
                        }

                        finalState.status == ScanStatus.COMPLETED && finalState.successful == 0 -> {
                            val pct = if (finalState.total > 0) finalState.progress else 0
                            txt.text = if (finalState.total > 0) "${finalState.processed}/${finalState.total}  •  %$pct" else "TARAMA BAŞLAMADI"
                            status.text = "Sonuç yok"
                            heroSubtitle.text = finalState.errorMessage ?: "Tarama tamamlandı ancak başarılı analiz oluşmadı."
                            debug.text = "ScanRun=${runStatus ?: "?"} • Başarılı 0 • Hatalı/atlanan ${finalState.skipped} • Anlık bütünlükten geçmeyen ${finalState.integrityRejected} • sonuç ekranına geçilmedi."
                        }

                        finalState.status == ScanStatus.COMPLETED -> {
                            status.text = "Sonuç yok"
                            heroSubtitle.text = "Yayınlanabilir sonuç oluşmadı."
                            debug.text = "ScanRun=${runStatus ?: "?"} • son başarılı tarama korunuyor."
                        }
                    }
                } catch (_: CancellationException) {
                    DiagnosticJournal(this@BistScanActivity).record("SCAN", "CANCEL", "USER_CANCELLED")
                    status.text = "STOPPED"
                    heroSubtitle.text = "Tarama durduruldu; yarım kalan tarama tamamlandı sayılmadı. Son başarılı tarama korunuyor."
                } catch (t: Throwable) {
                    DiagnosticJournal(this@BistScanActivity).record("SCAN", "ERROR", "UI_SCAN_EXCEPTION")
                    progress.visibility = View.GONE
                    txt.text = "TARAMA BAŞLAMADI"
                    status.text = "Hata"
                    heroSubtitle.text = t.message ?: "Beklenmeyen hata"
                    debug.text = "Sahte/demo veriye geçilmedi • son başarılı tarama korunuyor."
                } finally {
                    btn.text = defaultScanButtonLabel()
                    refreshSourceLabel()
                    refreshAutoScanUi()
                }
            }
        }
    }

    private fun toggleScanTimeMenu() {
        if (scanTimePopup?.isShowing == true) {
            scanTimePopup?.dismiss()
        } else {
            showScanTimeMenu()
        }
    }

    private fun showScanTimeMenu() {
        val selected = settings.analysisTimeframeMinutes
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundResource(R.drawable.bg_card)
        }

        QUICK_TIMEFRAMES.forEach { tf ->
            panel.addView(scanTimeRow(tf.label, tf.storedMinutes == selected) {
                applyScanInterval(tf.storedMinutes)
                scanTimePopup?.dismiss()
            })
        }

        val customSelected = selected !in QUICK_TIMEFRAMES.map { it.storedMinutes }
        val customLabel = if (customSelected) "ÖZEL  •  ${ScanTimeframe.displayLabel(selected)}" else "ÖZEL  ›"
        panel.addView(scanTimeRow(customLabel, customSelected) {
            scanTimePopup?.dismiss()
            showCustomScanTimeDialog()
        })

        val popup = PopupWindow(
            panel,
            scanTimeButton.width.coerceAtLeast(dp(240)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(12).toFloat()
            setOnDismissListener {
                scanTimeArrow.text = "˅"
                scanTimePopup = null
            }
        }
        scanTimeArrow.text = "˄"
        scanTimePopup = popup
        popup.showAsDropDown(scanTimeButton, 0, dp(4))
    }

    private fun scanTimeRow(label: String, selected: Boolean, click: () -> Unit): TextView = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(2)
        }
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        text = if (selected) "$label    ✓" else label
        textSize = 13f
        setTextColor(getColor(if (selected) R.color.white else R.color.text_primary))
        setBackgroundResource(if (selected) R.drawable.bg_chip_selected_blue else R.drawable.bg_chip_outline)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun showCustomScanTimeDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1 - 239"
            setText(settings.analysisTimeframeMinutes.toString())
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("ÖZEL TARAMA ZAMANI")
            .setMessage("Dakika değerini 1 ile 239 arasında girin. 1 GÜN ayrı seçenektir.")
            .setView(input)
            .setNegativeButton("İPTAL", null)
            .setPositiveButton("UYGULA", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString()?.trim()?.toIntOrNull()
                if (value == null || value !in 1..239) {
                    input.error = "1 ile 239 arasında tam sayı girin. 240 DK kullanılmaz."
                    return@setOnClickListener
                }
                applyScanInterval(value)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun applyScanInterval(minutes: Int) {
        if (scanJob?.isActive == true) scanJob?.cancel()
        settings.analysisTimeframeMinutes = ScanTimeframe.normalizeStoredMinutes(minutes)
        val tf = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
        // Analysis interval changes must preserve the independently configured cadence.
        if (settings.autoScanEnabled) {
            val scheduled = runCatching { AutoScanScheduler.reconcile(this, allowForegroundStart = true) }
            if (scheduled.isFailure || scheduled.getOrNull()?.accepted == false) {
                settings.autoScanLastStatus = "CONFIG_ERROR"
                settings.autoScanLastMessage = scheduled.exceptionOrNull()?.message ?: scheduled.getOrNull()?.message.orEmpty()
            }
        }
        AppSession.lastOpportunities = emptyList()
        refreshScanTimeUi()
        refreshAutoScanUi()
        heroSubtitle.text = "${tf.label} seçildi. Eski timeframe sonuçları kullanılmayacak; yeni taramada ${tf.label} OHLCV baştan alınacak."
        findViewById<TextView>(R.id.txtProgress)?.text = "TARAMA BAŞLAMADI"
        findViewById<ProgressBar>(R.id.progress)?.apply { progress = 0; visibility = View.GONE }
    }

    private fun refreshScanTimeUi() {
        if (!::settings.isInitialized || !::activeScanTime.isInitialized) return
        val tf = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
        activeScanTime.text = "AKTİF TARAMA: ${tf.label}"
        activeScanTime.contentDescription = if (settings.autoScanEnabled) {
            "Aktif teknik analiz periyodu ${tf.label}; otomatik tarama sıklığı ${settings.scanCadenceMinutes} dakika"
        } else {
            "Aktif teknik analiz periyodu ${tf.label}; otomatik tarama kapalı"
        }
        val autoSnapshot = autoSnapshot()
        val autoLine = "\nOtomatik tarama: ${autoSnapshot.label} • ${settings.scanCadenceMinutes} DK sıklık"
        findViewById<TextView>(R.id.txtActiveTimeframeDetails)?.text =
            "BIST hisseleri ${tf.label} verileri üzerinden taranacak\n" +
            "OHLCV: ${tf.label}   •   Teknik analiz: ${tf.label}\n" +
            "Trend: ${tf.label}   •   Momentum: ${tf.label}\n" +
            "Hacim: ${tf.label}   •   Kırılım: ${tf.label}" + autoLine
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupScanBottomNav() {
        findViewById<TextView>(R.id.navViop)?.apply {
            text = "⌕\nBIST Tarama"
            setTextColor(getColor(R.color.blue))
            setTypeface(typeface, Typeface.BOLD)
            // Current destination: do not expose an inert tappable control.
            isClickable = false
            isFocusable = false
            isSelected = true
            contentDescription = "BIST Tarama • mevcut ekran"
        }
    }

    private fun selectQuickMode(mode: QuickMode) {
        quickMode = mode
        val pairs = listOf(
            quickPopular to QuickMode.POPULAR,
            quickFavorites to QuickMode.FAVORITES,
            quickCustom to QuickMode.CUSTOM
        )
        pairs.forEach { (view, itemMode) ->
            view.setBackgroundResource(if (mode == itemMode) R.drawable.bg_chip_selected_blue else R.drawable.bg_chip_outline)
            view.setTextColor(getColor(if (mode == itemMode) R.color.white else R.color.text_primary))
        }
        heroSubtitle.text = when (mode) {
            QuickMode.POPULAR -> "Tüm doğrulanmış BIST sonuçları sıralanır."
            QuickMode.FAVORITES -> "Tarama tamamlandığında yalnız favorileriniz gösterilir."
            QuickMode.CUSTOM -> "Arama metni sembol, şirket ve mevcut analiz etiketlerinde uygulanır."
        }
    }

    private fun focusSearch() {
        // Bring the search field into view when the bottom shortcut is tapped.
        val scroll = findViewById<android.widget.ScrollView>(R.id.scanScroll)
        scroll.post {
            scroll.smoothScrollTo(0, 0)
            search.requestFocus()
            search.postDelayed({
                if (!isFinishing && !isDestroyed && search.hasFocus()) {
                    (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
                }
            }, 220L)
        }
    }

    private suspend fun filterResults(items: List<Opportunity>): List<Opportunity> {
        val query = search.text?.toString().orEmpty().trim().uppercase(Locale("tr", "TR"))
        val queryFiltered = if (query.isBlank()) items else items.filter { x ->
            listOf(
                x.symbol,
                x.companyName.orEmpty(),
                x.direction,
                x.technicalLabel,
                x.volumeLabel,
                x.kapLabel,
                x.setupType,
                x.marketRegime,
                x.mtfConsensusLabel
            ).any { it.uppercase(Locale("tr", "TR")).contains(query) }
        }
        if (quickMode != QuickMode.FAVORITES) return queryFiltered
        val favoriteSymbols = FavoriteRepository.get(this).getAll()
            .filter { it.market.equals("BIST", true) }
            .map { it.symbol.uppercase(Locale.ROOT) }
            .toSet()
        return queryFiltered.filter { it.symbol.uppercase(Locale.ROOT) in favoriteSymbols }
    }

    private fun resultModeSummary(allResults: Int, visibleResults: Int): String = when (quickMode) {
        QuickMode.POPULAR -> "$allResults analiz sonucu üretildi."
        QuickMode.FAVORITES -> "$allResults analiz içinden $visibleResults favori sonucu gösteriliyor."
        QuickMode.CUSTOM -> "$allResults analiz içinden $visibleResults arama/kriter sonucu gösteriliyor."
    }

    private fun showHowItWorks() {
        AlertDialog.Builder(this)
            .setTitle("BIST Tarama nasıl çalışır?")
            .setMessage(
                "1. Veri sağlayıcısı ve gerçek sembol evreni doğrulanır.\n\n" +
                    "2. Tarama Zamanı seçilen gerçek OHLCV periyodudur. Otomatik Tarama açılırsa aynı seçim tekrar cadence'i olur: 5 DK = 5 DK OHLCV + her 5 DK yeni tarama; 1 GÜN = gerçek 1d OHLCV + yaklaşık 24 saatte bir arka plan taraması.\n\n" +
                    "3. Quote + OHLCV verileri teknik analiz, MTF, piyasa rejimi ve ensemble katmanlarından geçirilir.\n\n" +
                    "4. İlerleme yalnız gerçek işlenen sembol sayısından hesaplanır.\n\n" +
                    "5. Provider/veri doğrulanamazsa sahte sonuç üretilmez."
            )
            .setPositiveButton("Kapat", null)
            .show()
    }

    private fun toggleDetails() {
        val show = detailPanel.visibility != View.VISIBLE
        detailPanel.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.btnStatusDetail).text = if (show) "Detay  ˄" else "Detay  ˅"
    }

    private fun productionBackendConfigured(): Boolean =
        tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl) && settings.apiKey.isNotBlank()

    private fun autoSnapshot(): AutoScanUiPolicy.Snapshot {
        val tf = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
        return AutoScanUiPolicy.evaluate(
            enabled = settings.autoScanEnabled,
            lastStatus = settings.autoScanLastStatus,
            lastMessage = settings.autoScanLastMessage,
            heartbeatAt = settings.autoScanLastHeartbeatAt,
            startedAt = settings.autoScanMonitoringStartedAt,
            cadenceMinutes = tf.storedMinutes
        )
    }

    private fun refreshAutoScanUi() {
        if (!::settings.isInitialized || !::autoScanSwitch.isInitialized || !::autoScanStatus.isInitialized) return
        val tf = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
        val snapshot = autoSnapshot()
        suppressAutoScanListener = true
        // The switch means scheduling is requested. It must NEVER claim an actual completed scan.
        autoScanSwitch.isChecked = settings.autoScanEnabled
        suppressAutoScanListener = false
        val provider = if (productionBackendConfigured()) "Production Backend" else "Yahoo YEDEK/GECİKMELİ • AL/SAT YOK"
        autoScanStatus.text = "${snapshot.label} • ${tf.label}\n$provider\n${snapshot.detail}"
        if (scanJob?.isActive != true) {
            findViewById<Button>(R.id.btnStartScan)?.apply {
                text = if (settings.autoScanEnabled) "■\nDURDUR" else "▶\nBAŞLAT"
                contentDescription = if (settings.autoScanEnabled) "Otomatik taramayı durdur" else "BIST manuel ${tf.label} taramayı başlat"
            }
            if (settings.autoScanEnabled) {
                findViewById<TextView>(R.id.txtStatus)?.text = snapshot.label
                findViewById<TextView>(R.id.txtProgress)?.text = when (snapshot.state) {
                    AutoScanUiPolicy.State.RUNNING -> "TARAMA ÇALIŞIYOR"
                    AutoScanUiPolicy.State.WAITING -> "SON TARAMA KAYITLI"
                    AutoScanUiPolicy.State.FAILED -> "TARAMA BAŞARISIZ"
                    AutoScanUiPolicy.State.INTERRUPTED -> "TARAMA KESİLMİŞ OLABİLİR"
                    else -> "İLK TARAMA BEKLENİYOR"
                }
                heroSubtitle.text = snapshot.detail
            }
        }
    }

    private fun providerStatusLabel(code: ProviderFailureCode, timeframe: ScanTimeframe): String = when (code) {
        ProviderFailureCode.BACKEND_URL_MISSING, ProviderFailureCode.API_KEY_MISSING, ProviderFailureCode.INVALID_HTTPS -> "VERİ SERVİSİ YAPILANDIRILMAMIŞ"
        ProviderFailureCode.AUTH_ERROR -> "KİMLİK DOĞRULAMA HATASI"
        ProviderFailureCode.RATE_LIMIT -> "İSTEK SINIRI"
        ProviderFailureCode.NETWORK_TIMEOUT, ProviderFailureCode.NETWORK_ERROR, ProviderFailureCode.DNS_ERROR, ProviderFailureCode.TLS_ERROR, ProviderFailureCode.SERVER_ERROR -> "VERİ SERVİSİNE ULAŞILAMIYOR"
        ProviderFailureCode.EMPTY_DATA, ProviderFailureCode.BIST_HISTORY_ERROR, ProviderFailureCode.STALE_DATA -> "${timeframe.label} VERİ SERVİSİ HAZIR DEĞİL"
        else -> "VERİ ALINAMADI"
    }

    private fun providerUserMessage(code: ProviderFailureCode, timeframe: ScanTimeframe, detail: String?): String = when (code) {
        ProviderFailureCode.BACKEND_URL_MISSING, ProviderFailureCode.API_KEY_MISSING, ProviderFailureCode.INVALID_HTTPS -> "${timeframe.label} taraması için Production Backend bağlantısı gerekli."
        ProviderFailureCode.AUTH_ERROR -> "Production Backend kimlik doğrulaması başarısız. API anahtarını kontrol edin."
        ProviderFailureCode.RATE_LIMIT -> "${timeframe.label} verisi için istek sınırı aşıldı. Tarama sahte sonuç üretmeden durduruldu."
        ProviderFailureCode.EMPTY_DATA, ProviderFailureCode.BIST_HISTORY_ERROR, ProviderFailureCode.STALE_DATA -> "${timeframe.label} periyodu seçildi ancak gerçek OHLCV hazır değil. ${detail.orEmpty()}"
        else -> "${timeframe.label} veri servisine ulaşılamadı. ${detail.orEmpty()}"
    }

    private fun refreshSourceLabel() {
        source.text = if (productionBackendConfigured()) {
            "Kaynak: Production Backend • gerçek BIST OHLCV • TradingView veri kaynağı değildir"
        } else {
            "Kaynak: Yahoo Finance • YEDEK/GECİKMELİ • seçili timeframe OHLCV • doğrulanmış AL/SAT sinyali değildir"
        }
        findViewById<Button>(R.id.btnConfigureProvider)?.setTextColor(
            getColor(if (productionBackendConfigured()) R.color.text_secondary else R.color.white)
        )
    }

    private fun defaultScanButtonLabel(): String = if (scanJob?.isActive == true ||
        (::settings.isInitialized && settings.autoScanEnabled)) "■\nDURDUR" else "▶\nBAŞLAT"

    private fun initialHeroMessage(): String {
        if (!::settings.isInitialized) return "Kriterlerinizi seçip taramayı başlatın."
        val tf = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
        return if (productionBackendConfigured()) {
            "${tf.label} gerçek OHLCV ile manuel tarama hazır. Otomatik tarama ${if (settings.autoScanEnabled) "açık" else "kapalı"}. TradingView piyasa veri kaynağı değildir."
        } else {
            "Yahoo Finance ${tf.label} yedek/gecikmeli teknik analiz hazır • doğrulanmış AL/SAT sinyali üretilmez."
        }
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized && ::source.isInitialized) {
            refreshSourceLabel()
            refreshScanTimeUi()
            refreshAutoScanUi()
            if (settings.autoScanEnabled) {
                val outcome = runCatching { AutoScanScheduler.reconcile(this, allowForegroundStart = true) }
                if (outcome.isFailure || outcome.getOrNull()?.accepted == false) {
                    settings.autoScanLastStatus = "CONFIG_ERROR"
                    settings.autoScanLastMessage = outcome.exceptionOrNull()?.message ?: outcome.getOrNull()?.message.orEmpty()
                }
            }
            refreshAutoScanUi()
            if (!settings.autoScanEnabled && scanJob?.isActive != true) heroSubtitle.text = initialHeroMessage()
        }
    }

    companion object {
        private val QUICK_TIMEFRAMES = ScanTimeframe.quick()
    }

    override fun onStart() {
        super.onStart()
        autoUiHandler.removeCallbacks(autoUiTick)
        autoUiHandler.post(autoUiTick)
    }

    override fun onStop() {
        autoUiHandler.removeCallbacks(autoUiTick)
        super.onStop()
    }

    override fun onDestroy() {
        scanTimePopup?.dismiss()
        scanTimePopup = null
        scanJob?.cancel()
        scanJob = null
        super.onDestroy()
    }
}
