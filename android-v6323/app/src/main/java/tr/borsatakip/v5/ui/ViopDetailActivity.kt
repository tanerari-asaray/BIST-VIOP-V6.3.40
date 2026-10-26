package tr.borsatakip.v5.ui

import android.content.Intent
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import tr.borsatakip.v5.data.ViopManualPositionStore
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import tr.borsatakip.v5.analysis.ViopScanner
import kotlinx.coroutines.launch
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.LinearTrendAnalyzer
import tr.borsatakip.v5.analysis.OhlcvResampler
import tr.borsatakip.v5.analysis.TechnicalAnalyzer
import tr.borsatakip.v5.analysis.ViopContractCategoryPolicy
import tr.borsatakip.v5.analysis.ViopV6Policy
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.ProviderReadinessService
import tr.borsatakip.v5.data.ProviderState
import tr.borsatakip.v5.data.ViopWatchlistStore
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.model.ViopQuote
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViopDetailActivity : BaseActivity() {
    private val freshnessHandler = Handler(Looper.getMainLooper())
    private val freshnessRefresh = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                refreshQuoteOnly()
                renderSignal()
                renderQuoteCard()
                renderSummaries()
                freshnessHandler.postDelayed(this, 10_000L)
            }
        }
    }
    private var quoteRefreshInFlight = false

    /** Lifecycle-bound REST quote polling; the quote is never used to approve an old bar signal. */
    private fun refreshQuoteOnly() {
        val c = contract ?: return
        if (quoteRefreshInFlight || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
            c.isManual || c.validity != SignalValidity.VALID ||
            ProviderReadinessService(this).localConfigState().state != ProviderState.PROVIDER_READY) return
        quoteRefreshInFlight = true
        lifecycleScope.launch {
            try {
                val result = BackendProvider(this@ViopDetailActivity).loadViopQuote(c.symbol)
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    result.onSuccess { newest ->
                        // Reject a regression (older tick) even if it passes a broad freshness window.
                        if (newest.exchangeTimestamp >= (quote?.exchangeTimestamp ?: 0L)) {
                            quote = newest
                            opportunity = opportunity?.copy(
                                quote = newest,
                                dataAgeMs = (System.currentTimeMillis() - newest.exchangeTimestamp).coerceAtLeast(0L)
                            )
                            renderQuoteCard()
                            renderDepth()
                            renderSignal()
                            renderSummaries()
                            if (signalJob?.isActive != true && android.os.SystemClock.elapsedRealtime() - lastSignalRefreshElapsed >= 60_000L) {
                                reanalyzeSignal()
                            }
                        }
                    }.onFailure { e ->
                        opportunity = opportunity?.copy(validity = SignalValidity.WATCH,
                            decisionState = tr.borsatakip.v5.model.DecisionState.WATCH,
                            signalReason = "QUOTE_REFRESH_FAILED")
                        renderSignal()
                        setStatusMessage("VİOP quote güncellenemedi • ${e.message ?: "bağlantı hatası"}", R.color.yellow)
                    }
                }
            } finally {
                quoteRefreshInFlight = false
            }
        }
    }

    private enum class DetailTab { CHART, DEPTH, SIGNAL, CONTRACT }

    private lateinit var chart: PriceChartView
    private lateinit var watchlistStore: ViopWatchlistStore
    private lateinit var manualJournal: ViopManualPositionStore
    private var manualJournalLabel: TextView? = null

    private var contract: ViopContract? = null
    private var opportunity: ViopOpportunity? = null
    private var quote: ViopQuote? = null
    private var candles: List<Candle> = emptyList()
    private var technical: TechnicalSnapshot? = null
    private var timeframe: ViopDetailTimeframe = ViopDetailTimeframe.FIVE_MIN
    private var historyRequestGeneration = 0L
    private var signalJob: kotlinx.coroutines.Job? = null
    private var lastSignalRefreshElapsed = 0L
    private var signalLoader: suspend (String) -> Result<ViopOpportunity> = { symbol ->
        ViopScanner(BackendProvider(this), tr.borsatakip.v5.data.ProviderRouter(this)).scanOne(symbol)
    }
    private var pendingChartViewState: Bundle? = null
    private fun reanalyzeSignal() {
        val c = contract ?: return
        signalJob?.cancel()
        lastSignalRefreshElapsed = android.os.SystemClock.elapsedRealtime()
        opportunity = opportunity?.copy(validity = SignalValidity.WATCH,
            decisionState = tr.borsatakip.v5.model.DecisionState.WATCH,
            signalReason = "Yeniden değerlendirme sürüyor")
        renderSignal()
        signalJob = lifecycleScope.launch {
            val result = try { signalLoader(c.symbol) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { Result.failure(error) }
            result.onSuccess { opportunity = it; AppSession.selectedViopOpportunity = it }
                .onFailure { error -> opportunity = opportunity?.copy(signalReason = error.message ?: "NO_VERIFIED_ANALYSIS") }
            renderSignal(); renderSummaries()
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) openFullscreenChart()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("detailTimeframe", timeframe.name); outState.putString("detailTab", activeTab.name)
        if (::chart.isInitialized) outState.putBundle("chartViewState", chart.saveDisplayState())
        super.onSaveInstanceState(outState)
    }
    private var activeTab: DetailTab = DetailTab.CHART

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viop_detail)
        setupBottomNav()
        watchlistStore = ViopWatchlistStore(this)
        manualJournal = ViopManualPositionStore(this)
        chart = findViewById(R.id.viopPriceChart)

        resolveSelection()
        bindTabs()
        bindTimeframes()
        bindActions()
        installManualJournalControls()
        renderKnownState()
        timeframe = savedInstanceState?.getString("detailTimeframe")?.let {
            runCatching { ViopDetailTimeframe.valueOf(it) }.getOrNull() } ?: timeframe
        pendingChartViewState = savedInstanceState?.getBundle("chartViewState")
        showTab(savedInstanceState?.getString("detailTab")?.let { runCatching { DetailTab.valueOf(it) }.getOrNull() } ?: DetailTab.CHART)
        refreshVerifiedMarketData()
    }

    override fun onResume() {
        super.onResume()
        renderWatchState()
        renderManualJournalStatus()
        freshnessHandler.removeCallbacks(freshnessRefresh)
        freshnessHandler.post(freshnessRefresh)
    }

    override fun onPause() {
        freshnessHandler.removeCallbacks(freshnessRefresh)
        super.onPause()
    }

    private fun installManualJournalControls() {
        val section = findViewById<LinearLayout>(R.id.viopSignalSection)
        manualJournalLabel = TextView(this).apply {
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            text = "MANUEL İŞLEM DEFTERİ • yalnız kullanıcı beyanı"
            setPadding(0, 16, 0, 12)
        }.also { section.addView(it) }
        section.addView(Button(this).apply {
            text = "GERÇEKLEŞTİRDİĞİM İŞLEMİ KAYDET"
            isAllCaps = false
            setOnClickListener { showRecordManualDialog() }
        })
        section.addView(Button(this).apply {
            text = "AÇIK İŞLEMİ KAPAT / GERÇEKLEŞEN FİYATI YAZ"
            isAllCaps = false
            setOnClickListener { showCloseManualDialog() }
        })
    }

    private fun renderManualJournalStatus() {
        val value = try {
            val s = manualJournal.state()
            "Kayıtlı açık pozisyon: ${s.count} • Planlanan açık risk: %.2f TL • Teminat: %.2f TL • Günlük gerçekleşen zarar: %.2f TL\n" +
                "Yalnız bu telefona girilen işlemleri kapsar. Gerçek broker teminatı ve diğer pozisyonlar doğrulanmadı."
        } catch (_: Exception) {
            "Pozisyon defteri okunamadı • risk doğrulanamadığı için yeni kayıt engellenebilir."
        }
        manualJournalLabel?.text = try {
            val s = manualJournal.state()
            value.format(java.util.Locale("tr","TR"), s.openRiskTry, s.marginTry, s.dailyLossTry) +
                "\nVİOP takibi: " +
                getSharedPreferences("viop_position_monitor_v2", android.content.Context.MODE_PRIVATE)
                    .getString("state", "HENÜZ KONTROL EDİLMEDİ") +
                " • Bu bilgi anlık takip garantisi değildir."
        } catch (_: Exception) { value }
    }

    private fun showRecordManualDialog() {
        val c = contract ?: run {
            Toast.makeText(this, "Önce VİOP sözleşmesi seçin", Toast.LENGTH_LONG).show()
            return
        }
        val now = System.currentTimeMillis()
        if (c.multiplier == null || !c.multiplier.isFinite() || c.multiplier <= 0 ||
            c.lastTradingAt == null) {
            Toast.makeText(this, "Aktif sözleşme ve doğrulanmış çarpan yok • kayıt engellendi", Toast.LENGTH_LONG).show()
            return
        }
        val direction = opportunity?.direction?.takeIf { it == "LONG" || it == "SHORT" } ?: "LONG"
        val askBid = if (direction == "LONG") quote?.ask else quote?.bid
        val fields = listOf(
            "Yön (LONG/SHORT)" to direction,
            "Gerçekleşen giriş fiyatı" to "",
            "Stop fiyatı" to "",
            "Hedef fiyatı" to "",
            "Gerçek kontrat adedi" to "1",
            "Tek kontrat toplam işlem maliyeti (TL)" to "",
            "Tek kontrat güncel teminatı (TL)" to "",
            "Aracı kurum benzersiz gerçekleşme/işlem numarası" to "",
            "Gerçekleşen giriş zamanı yyyy-MM-dd HH:mm" to java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Istanbul")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        )
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 10, 28, 0)
        }
        val inputs = fields.mapIndexed { idx, (label, default) ->
            val entry = EditText(this).apply {
                hint = label
                setSingleLine(true)
                setText(default)
                inputType = if (idx == 0 || idx == 7 || idx == 8) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            container.addView(entry)
            entry
        }
        AlertDialog.Builder(this)
            .setTitle("Manuel işlem defteri – EMİR GÖNDERİLMEZ")
            .setMessage("Tüm tutarları gerçekleşmiş işlem fişinizden girin. Fiyat/teminat otomatik doğrulanmaz. Anlık fiyat sadece bağlamdır: ${askBid ?: "—"}.")
            .setView(android.widget.ScrollView(this).apply { addView(container) })
            .setNegativeButton("İPTAL", null)
            .setPositiveButton("GERÇEKLEŞME KAYDINI İNCELE") { _, _ ->
                fun number(i:Int):Double? = inputs[i].text.toString().trim().replace(",", ".").toDoubleOrNull()
                val side = inputs[0].text.toString().trim().uppercase(java.util.Locale.ROOT)
                val price = number(1); val stop = number(2); val target = number(3)
                val count = number(4)?.takeIf { it.isFinite() && it == it.toInt().toDouble() }?.toInt()
                val cost = number(5); val margin = number(6)
                val brokerRef = inputs[7].text.toString().trim()
                val executionTime = try {
                    java.time.LocalDateTime.parse(inputs[8].text.toString().trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(java.time.ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()
                } catch (_: Exception) { null }
                if (price==null || stop==null || target==null || count==null || cost==null || margin==null || brokerRef.isBlank() || executionTime == null) {
                    Toast.makeText(this, "Tüm tutarlar ve aracı kurum gerçekleşme numarası zorunlu", Toast.LENGTH_LONG).show()
                } else {
                    val result = try {
                        val state = manualJournal.state()
                        tr.borsatakip.v5.analysis.ViopV6Policy.size(price, stop, c.multiplier, cost, margin,
                            state.openRiskTry, state.dailyLossTry, state.count, state.marginTry, side)
                    } catch (_: Exception) { null }
                    if (count < 1 || result == null) {
                        Toast.makeText(this, "RİSK HESAPLANAMADI • Depo veya meta veri hatası. Defteri kontrol edin.", Toast.LENGTH_LONG).show()
                    } else {
                        // New plans are BLOCKED by size(); completed broker executions must be recorded.
                        val breach = result.contracts < count
                        val message = if (breach) "⚠ RİSK LİMİTİ AŞILMIŞ • Bu, yeni bir işlem önerisi değildir. Gerçek işlem defterde saklanacak ve yeni planlar engellenecek."
                            else "Risk limitlerine göre kayıt edilen miktar uygun görünüyor."
                        val exactRisk = count * (kotlin.math.abs(price-stop) * c.multiplier + cost)
                        AlertDialog.Builder(this)
                            .setTitle(if (breach) "GERÇEKLEŞMİŞ İŞLEM • RİSK İHLALİ" else "Gerçekleşmiş işlem kaydı")
                            .setMessage("${c.symbol} ${side} • ${count} kontrat\nStop ${stop} • Hedef ${target}\nBeyan edilen risk: %.2f TL\nAracı kurum no: ${brokerRef}\n${message}\nEmir gönderilmez.".format(
                                java.util.Locale("tr","TR"), exactRisk
                            ))
                            .setNegativeButton("VAZGEÇ", null)
                            .setPositiveButton("GERÇEKLEŞMİŞ İŞLEMİ KAYDET") { _, _ ->
                                val save = manualJournal.record(c.symbol, side, count, price, stop, target, c.multiplier, cost, margin,
                                    brokerExecutionId = brokerRef, now = executionTime)
                                val feedback = when {
                                    !save.accepted -> "Kayıt reddedildi: ${save.reason}"
                                    save.reason == "RISK_BREACH_RECORDED" -> "⚠ İşlem kaydedildi; risk limiti aşıldı. Yeni işlem planlamayın."
                                    else -> "Gerçekleşmiş işlem kaydedildi"
                                }
                                Toast.makeText(this, feedback, Toast.LENGTH_LONG).show()
                                renderManualJournalStatus()
                            }.show()
                    }
                }
            }.show()
    }

    private fun showCloseManualDialog() {
        val positions = try { manualJournal.openPositions() } catch (_: Exception) {
            Toast.makeText(this, "Pozisyon defteri bozuk; kapama kaydı durduruldu.", Toast.LENGTH_LONG).show()
            return
        }
        if (positions.isEmpty()) {
            Toast.makeText(this, "Kayıtlı açık işlem yok", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = positions.map { "${it.symbol} ${it.side} • açık ${it.remainingQuantity} kontrat • ${it.entry}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Kısmi veya tam gerçekleşmiş çıkışı seçin")
            .setItems(labels) { _, index ->
                val pos = positions[index]
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,4,24,4) }
                val quantity = EditText(this).apply {
                    hint = "Çıkılan kontrat adedi (azami ${pos.remainingQuantity})"
                    setText(pos.remainingQuantity.toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                val priceInput = EditText(this).apply {
                    hint = "Gerçekleşen çıkış fiyatı"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                val extraFee = EditText(this).apply {
                    hint = "Ek toplam kapanış masrafı (TL)"
                    setText("0")
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                val dateInput = EditText(this).apply {
                    hint = "Gerçekleşme zamanı yyyy-MM-dd HH:mm"
                    setText(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Istanbul"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    inputType = InputType.TYPE_CLASS_DATETIME
                }
                box.addView(quantity); box.addView(priceInput); box.addView(extraFee); box.addView(dateInput)
                AlertDialog.Builder(this).setTitle("${pos.symbol} • gerçekleşen çıkış")
                    .setMessage("İşlem aracı kurumda gerçekleştikten sonra doldurun. EMİR VERİLMEZ.")
                    .setView(android.widget.ScrollView(this).apply { addView(box) })
                    .setNegativeButton("İPTAL", null)
                    .setPositiveButton("ÇIKIŞI KAYDET") { _, _ ->
                        val exitQty = quantity.text.toString().trim().toIntOrNull()
                        val exitPrice = priceInput.text.toString().replace(",", ".").toDoubleOrNull()
                        val cost = extraFee.text.toString().replace(",", ".").toDoubleOrNull()
                        val executedAt = try {
                            java.time.LocalDateTime.parse(dateInput.text.toString().trim(),
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                .atZone(java.time.ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()
                        } catch (_: Exception) { null }
                        val result = if (exitQty == null || exitPrice == null || cost == null || executedAt == null)
                            ViopManualPositionStore.Result(false,"EXIT_FIELDS_REQUIRED")
                        else manualJournal.closePartial(pos.id, exitQty, exitPrice, executedAt, cost)
                        Toast.makeText(this, if (result.accepted)
                            "Gerçekleşen çıkış kaydedildi • kalan ${result.position?.remainingQuantity ?: 0} kontrat"
                            else result.reason,Toast.LENGTH_LONG).show()
                        renderManualJournalStatus()
                    }.show()
            }.setNegativeButton("İPTAL", null).show()
    }
    private fun resolveSelection() {
        val selectedContract = AppSession.selectedViopContract
        val selectedOpportunity = AppSession.selectedViopOpportunity
        opportunity = selectedOpportunity?.takeIf { selectedContract == null || it.contract.symbol.equals(selectedContract.symbol, true) }
        contract = selectedContract ?: opportunity?.contract
        quote = opportunity?.quote
        candles = opportunity?.candles.orEmpty()
        technical = opportunity?.technical ?: candles.takeIf { it.isNotEmpty() }?.let(TechnicalAnalyzer::analyze)
        if (candles.isNotEmpty()) timeframe = ViopDetailTimeframe.ONE_DAY
    }

    private fun bindTabs() {
        findViewById<Button>(R.id.viopTabChart).setOnClickListener { showTab(DetailTab.CHART) }
        findViewById<Button>(R.id.viopTabDepth).setOnClickListener { showTab(DetailTab.DEPTH) }
        findViewById<Button>(R.id.viopTabSignal).setOnClickListener { showTab(DetailTab.SIGNAL) }
        findViewById<Button>(R.id.viopTabContract).setOnClickListener { showTab(DetailTab.CONTRACT) }
    }

    private fun bindTimeframes() {
        timeframeButtons().forEach { (id, spec) ->
            findViewById<Button>(id).setOnClickListener { loadTimeframe(spec) }
        }
    }

    private fun bindActions() {
        findViewById<TextView>(R.id.viopActionLong).setOnClickListener { showTab(DetailTab.SIGNAL) }
        findViewById<TextView>(R.id.viopActionShort).setOnClickListener { showTab(DetailTab.SIGNAL) }
        findViewById<TextView>(R.id.viopActionWatch).setOnClickListener { toggleWatchlist() }
        chart.onRequestFullscreen = { openFullscreenChart() }
    }

    private fun renderKnownState() {
        val c = contract
        findViewById<TextView>(R.id.viopDetailSubtitle).text = c?.let {
            "${it.underlying.uppercase(Locale.ROOT)} • ${it.expiry} VİOP"
        } ?: "Seçili sözleşme yok"

        renderQuoteCard()
        renderChart()
        renderTechnical()
        renderSignal()
        renderContract()
        renderDepth()
        renderSummaries()
        renderWatchState()
        renderManualJournalStatus()
        updateTimeframeButtons()

        if (c == null) {
            setStatusMessage("VİOP sözleşmeleri ekranından bir sözleşme seçin.", R.color.text_secondary)
        }
    }

    private fun refreshVerifiedMarketData() {
        val c = contract ?: return
        val request = ++historyRequestGeneration
        opportunity = opportunity?.copy(validity = SignalValidity.WATCH,
            decisionState = tr.borsatakip.v5.model.DecisionState.WATCH,
            signalReason = "Fiyat ve analiz yeniden doğrulanıyor")
        clearChartData()
        if (c.symbol.isBlank() || c.isManual || c.validity != SignalValidity.VALID) {
            setStatusMessage("Gerçek VİOP kontratı doğrulanamadı • canlı veri gösterilmedi.", R.color.red)
            return
        }
        val readiness = ProviderReadinessService(this).localConfigState()
        if (readiness.state != ProviderState.PROVIDER_READY) {
            setStatusMessage("Production VİOP provider bağlı değil • grafik ve teknik analiz temizlendi.", R.color.yellow)
            return
        }

        reanalyzeSignal()
        lifecycleScope.launch {
            val provider = BackendProvider(this@ViopDetailActivity)
            try {
                provider.loadViopQuote(c.symbol).getOrThrow().also {
                    if (request != historyRequestGeneration) return@launch
                    quote = it
                    // This refreshed price does not refresh the closed-bar technical signal.
                    // Invalidate old signal until a complete new opportunity assessment arrives.
                    opportunity = opportunity?.copy(
                        quote = it,
                        dataAgeMs = (System.currentTimeMillis() - it.exchangeTimestamp).coerceAtLeast(0L),
                        signalReason = opportunity?.signalReason.orEmpty()
                    )
                    renderSignal()
                    renderChart()
                    renderQuoteCard()
                    renderDepth()
                    renderSummaries()
                }
                setStatusMessage("VİOP fiyatı doğrulandı • 5 dk grafik yükleniyor…", R.color.green)
                try {
                    loadTimeframeInternal(provider, timeframe, userInitiated = false, request = request)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (historyError: Exception) {
                    if (request != historyRequestGeneration) return@launch
                    clearChartData()
                    setStatusMessage("FİYAT BAĞLI / ANALİZ VERİSİ YETERSİZ • ${historyError.message ?: "mum bulunamadı"}", R.color.yellow)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (request != historyRequestGeneration) return@launch
                clearChartData()
                val hasVerified = ViopDetailPresentationPolicy.dataState(quote) == ViopDetailDataState.LIVE
                setStatusMessage(
                    if (hasVerified) "Grafik yenilenemedi • doğrulanmış mevcut quote korunuyor."
                    else "VİOP quote/history doğrulanamadı • ${error.message ?: "veri alınamadı"}",
                    if (hasVerified) R.color.yellow else R.color.red
                )
            }
        }
    }

    private fun loadTimeframe(spec: ViopDetailTimeframe) {
        val c = contract ?: return
        val request = ++historyRequestGeneration
        timeframe = spec
        clearChartData()
        updateTimeframeButtons()
        val readiness = ProviderReadinessService(this).localConfigState()
        if (readiness.state != ProviderState.PROVIDER_READY) {
            setStatusMessage("${spec.displayLabel} için provider gerekli • grafik ve analiz temizlendi.", R.color.yellow)
            return
        }
        if (c.validity != SignalValidity.VALID || c.isManual) {
            Toast.makeText(this, "Doğrulanmış aktif kontrat gerekli", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                loadTimeframeInternal(BackendProvider(this@ViopDetailActivity), spec, userInitiated = true, request = request)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (request != historyRequestGeneration) return@launch
                clearChartData()
                setStatusMessage("${spec.displayLabel} grafiği alınamadı • ${error.message ?: "veri yok"}", R.color.red)
            }
        }
    }

    private fun clearChartData() {
        candles = emptyList()
        technical = null
        renderChart()
        renderTechnical()
        renderSignal()
    }

    private suspend fun loadTimeframeInternal(provider: BackendProvider, spec: ViopDetailTimeframe, userInitiated: Boolean, request: Long) {
        val c = contract ?: return
        if (userInitiated) setStatusMessage("${spec.displayLabel} OHLCV verisi yükleniyor…", R.color.text_secondary)
        val raw = provider.loadViopHistoryFlexible(c.symbol, spec.range, spec.requestInterval, forDisplay = true).getOrThrow()
        if (request != historyRequestGeneration) return
        val next = spec.aggregateMinutes?.let { target ->
            OhlcvResampler.sessionAggregate(raw, spec.sourceMinutes, target)
        } ?: raw
        require(next.isNotEmpty()) { "${spec.displayLabel} için kullanılabilir OHLCV verisi yok." }

        timeframe = spec
        candles = next
        technical = TechnicalAnalyzer.analyze(next)
        renderChart()
        pendingChartViewState?.let { chart.restoreDisplayState(it) }; pendingChartViewState = null
        renderTechnical()
        updateTimeframeButtons()
        setStatusMessage("${spec.displayLabel} • ${next.size} OHLCV mumu • son mum oluşuyor olabilir; sinyal kapalı mumlardan", R.color.green)
    }

    private fun renderQuoteCard() {
        val q = quote
        val state = ViopDetailPresentationPolicy.dataState(q)
        findViewById<TextView>(R.id.viopDetailPrice).text = q?.price?.takeIf { it.isFinite() && it > 0.0 }?.let(::formatPrice) ?: "—"

        val changeView = findViewById<TextView>(R.id.viopDetailChange)
        val change = q?.dailyChangePct
        changeView.text = change?.let { "%+.2f%%".format(Locale.US, it) } ?: "—"
        changeView.setTextColor(getColor(when {
            change == null -> R.color.text_secondary
            change > 0.0 -> R.color.green
            change < 0.0 -> R.color.red
            else -> R.color.text_secondary
        }))

        val stateView = findViewById<TextView>(R.id.viopDataState)
        stateView.text = state.label
        stateView.setTextColor(getColor(when (state) {
            ViopDetailDataState.LIVE -> R.color.green
            ViopDetailDataState.DELAYED -> R.color.yellow
            ViopDetailDataState.STALE -> R.color.red
            ViopDetailDataState.UNAVAILABLE -> R.color.text_secondary
        }))

        findViewById<TextView>(R.id.viopVolume).text = "Hacim ${q?.volume?.let(::formatCompact) ?: "—"}"
        findViewById<TextView>(R.id.viopOpenInterest).text = "Açık Poz. ${q?.openInterest?.let(::formatLong) ?: "—"}"
        findViewById<TextView>(R.id.viopDataAge).text = q?.exchangeTimestamp?.takeIf { it > 0L }?.let {
            "Veri ${formatAge(System.currentTimeMillis() - it)}"
        } ?: "Veri zamanı —"
    }

    private fun renderChart() {
        val c = contract
        chart.candles = candles
        chart.timeframeLabel = timeframe.displayLabel
        chart.trendAnalysis = LinearTrendAnalyzer.analyze(candles)
        chart.signalPrice = opportunity?.decisionPrice?.takeIf { ViopDetailPresentationPolicy.verifiedDirection(opportunity) != null }
        chart.signalTime = null
        findViewById<TextView>(R.id.viopChartTitle).text = if (candles.isEmpty()) {
            "${c?.underlying?.uppercase(Locale.ROOT) ?: "VİOP"} • ${timeframe.displayLabel} • VERİ YOK"
        } else {
            "${c?.underlying?.uppercase(Locale.ROOT) ?: "VİOP"} • ${timeframe.displayLabel}"
        }
    }

    private fun renderTechnical() {
        val t = technical
        findViewById<TextView>(R.id.viopRsi).text = t?.rsi14?.let { "%.1f".format(Locale.US, it) } ?: "—"
        findViewById<TextView>(R.id.viopMacd).text = t?.macd?.let { "%+.3f".format(Locale.US, it) } ?: "—"
        findViewById<TextView>(R.id.viopEma20).text = t?.ema20?.let(::formatPrice) ?: "—"
        findViewById<TextView>(R.id.viopAtr).text = t?.atr14?.let { "%.3f".format(Locale.US, it) } ?: "—"
    }

    private fun renderSignal() {
        val o = opportunity
        val verifiedDirection = o?.takeIf { ViopV6Policy.assess(it).eligible }?.let { ViopDetailPresentationPolicy.verifiedDirection(it) }
        val directionView = findViewById<TextView>(R.id.viopSignalDirection)
        directionView.text = verifiedDirection ?: if (o == null) "SİNYAL YOK" else "İZLE"
        directionView.setTextColor(getColor(when (verifiedDirection) {
            "LONG" -> R.color.green
            "SHORT" -> R.color.red
            else -> R.color.text_secondary
        }))
        findViewById<TextView>(R.id.viopSignalScore).text = "Sinyal skoru: ${if (verifiedDirection != null) o?.finalScore?.let { "$it/100" } else "—"}"
        findViewById<TextView>(R.id.viopSignalConfidence).text = "Veri güveni: ${o?.dataConfidenceScore?.let { "$it/100" } ?: "—"}"
        findViewById<TextView>(R.id.viopSignalRisk).text = "Risk: ${o?.riskScore?.let { "$it/100" } ?: "—"}"
        findViewById<TextView>(R.id.viopSignalReason).text = when {
            o == null -> "Quote ve strict history doğrulanmadan gerçek VİOP LONG/SHORT sinyali üretilmez."
            verifiedDirection == null -> "V6 risk/veri kapısı: ${ViopV6Policy.assess(o).reason} • ${o.signalReason}"
            else -> "${o.signalReason}\nKarar fiyatı: ${formatPrice(o.decisionPrice)} • MTF: ${o.mtfConsensusLabel} • Rejim: ${o.marketRegime}"
        }

        findViewById<TextView>(R.id.viopActionLong).alpha = if (verifiedDirection == "LONG") 1f else 0.55f
        findViewById<TextView>(R.id.viopActionShort).alpha = if (verifiedDirection == "SHORT") 1f else 0.55f
    }

    private fun renderContract() {
        val c = contract
        findViewById<TextView>(R.id.viopContractBody).text = c?.let {
            buildString {
                append("Kontrat: ${it.symbol}\n")
                append("Dayanak: ${it.underlying}\n")
                append("Vade: ${it.expiry}\n")
                append("Tür: ${it.contractType}\n")
                append("Çarpan: ${it.multiplier?.let(::formatPlain) ?: "—"}\n")
                append("Minimum adım: ${it.tickSize?.let(::formatPlain) ?: "—"}\n")
                append("Uzlaşma: ${it.settlementType ?: "—"}\n")
                append("Son işlem: ${it.lastTradingAt?.let(::formatTimestamp) ?: "—"}\n")
                append("Kaynak: ${it.providerLabel}\n")
                append("Doğrulama: ${it.validity} • ${it.validityReason}")
            }
        } ?: "Sözleşme seçilmedi."
    }

    private fun renderDepth() {
        val q = quote
        val bid = q?.bid?.takeIf { it.isFinite() && it > 0.0 }
        val ask = q?.ask?.takeIf { it.isFinite() && it > 0.0 }
        findViewById<TextView>(R.id.viopDepthBid).text = "Alış ${bid?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.viopDepthAsk).text = "Satış ${ask?.let(::formatPrice) ?: "—"}"
        findViewById<TextView>(R.id.viopDepthSpread).text = "Spread ${if (bid != null && ask != null && ask >= bid) formatPrice(ask - bid) else "—"}"
        findViewById<TextView>(R.id.viopDepthNote).text = if (bid != null || ask != null) {
            "En iyi alış/satış quote verisi gösteriliyor. Kademe bazlı tam derinlik endpoint'i mevcut olmadığı için seviye/miktar uydurulmadı."
        } else {
            "Kademe bazlı piyasa derinliği ve doğrulanmış alış/satış verisi yok; değer üretilmedi."
        }
    }

    private fun renderSummaries() {
        val c = contract
        findViewById<TextView>(R.id.viopSummaryContract).text = c?.let {
            "Dayanak  ${it.underlying}\nVade  ${it.expiry}\nÇarpan  ${it.multiplier?.let(::formatPlain) ?: "—"}\nAdım  ${it.tickSize?.let(::formatPlain) ?: "—"}"
        } ?: "Veri yok"

        val o = opportunity
        val direction = o?.takeIf { ViopV6Policy.assess(it).eligible }?.let { ViopDetailPresentationPolicy.verifiedDirection(it) } ?: "İZLE"
        val dataLabel = ViopDetailPresentationPolicy.dataState(quote).label
        findViewById<TextView>(R.id.viopSummarySignal).text =
            "Trend  $direction\nGüven  ${o?.dataConfidenceScore?.let { "$it/100" } ?: "—"}\nVeri  $dataLabel\nSkor  ${o?.finalScore?.let { "$it/100" } ?: "—"}"
    }

    private fun renderWatchState() {
        val c = contract ?: return
        val category = ViopContractCategoryPolicy.classify(c)
        val watched = watchlistStore.symbols(category).any { it.equals(c.symbol, true) }
        findViewById<TextView>(R.id.viopActionWatch).text = if (watched) "★ TAKİP" else "☆ TAKİP"
        findViewById<TextView>(R.id.viopActionWatch).setTextColor(getColor(if (watched) R.color.yellow else R.color.text_primary))
    }

    private fun toggleWatchlist() {
        val c = contract ?: return
        val category = ViopContractCategoryPolicy.classify(c)
        val watched = watchlistStore.symbols(category).any { it.equals(c.symbol, true) }
        if (watched) {
            watchlistStore.remove(category, c.symbol)
            Toast.makeText(this, "${c.symbol} takip listesinden çıkarıldı", Toast.LENGTH_SHORT).show()
        } else {
            when (watchlistStore.add(category, c.symbol)) {
                ViopWatchlistStore.AddResult.ADDED -> Toast.makeText(this, "${c.symbol} takip listesine eklendi", Toast.LENGTH_SHORT).show()
                ViopWatchlistStore.AddResult.ALREADY_EXISTS -> Unit
                ViopWatchlistStore.AddResult.LIMIT_REACHED -> Toast.makeText(this, "Bu VİOP takip listesi dolu", Toast.LENGTH_LONG).show()
                ViopWatchlistStore.AddResult.INVALID -> Toast.makeText(this, "Kontrat sembolü takip için uygun değil", Toast.LENGTH_SHORT).show()
            }
        }
        renderWatchState()
    }

    private fun showTab(tab: DetailTab) {
        activeTab = tab
        findViewById<LinearLayout>(R.id.viopGraphSection).visibility = if (tab == DetailTab.CHART) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.viopDepthSection).visibility = if (tab == DetailTab.DEPTH) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.viopSignalSection).visibility = if (tab == DetailTab.SIGNAL) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.viopContractSection).visibility = if (tab == DetailTab.CONTRACT) View.VISIBLE else View.GONE
        tabButtons().forEach { (id, value) ->
            val button = findViewById<Button>(id)
            button.setBackgroundResource(if (value == activeTab) R.drawable.bg_viop_terminal_tab_selected else R.drawable.bg_viop_terminal_tab)
            button.setTextColor(getColor(if (value == activeTab) R.color.white else R.color.text_primary))
        }
    }

    private fun openFullscreenChart() {
        val symbol = contract?.symbol ?: return
        val chartTimeframe = tr.borsatakip.v5.analysis.ChartTimeframe.valueOf(timeframe.name)
        FullscreenChartActivity.open(this, symbol, "VIOP", candles, quote?.source ?: "VİOP backend",
            false, chartTimeframe, chart.saveDisplayState())
    }

    private fun updateTimeframeButtons() {
        timeframeButtons().forEach { (id, value) ->
            val button = findViewById<Button>(id)
            button.setBackgroundResource(if (value == timeframe) R.drawable.bg_viop_terminal_tab_selected else R.drawable.bg_viop_terminal_tab)
            button.setTextColor(getColor(if (value == timeframe) R.color.white else R.color.text_primary))
        }
    }

    private fun timeframeButtons(): Map<Int, ViopDetailTimeframe> = linkedMapOf(
        R.id.viopTf1m to ViopDetailTimeframe.ONE_MIN,
        R.id.viopTf3m to ViopDetailTimeframe.THREE_MIN,
        R.id.viopTf5m to ViopDetailTimeframe.FIVE_MIN,
        R.id.viopTf15m to ViopDetailTimeframe.FIFTEEN_MIN,
        R.id.viopTf1h to ViopDetailTimeframe.ONE_HOUR,
        R.id.viopTf4h to ViopDetailTimeframe.FOUR_HOUR,
        R.id.viopTf1d to ViopDetailTimeframe.ONE_DAY
    )

    private fun tabButtons(): Map<Int, DetailTab> = linkedMapOf(
        R.id.viopTabChart to DetailTab.CHART,
        R.id.viopTabDepth to DetailTab.DEPTH,
        R.id.viopTabSignal to DetailTab.SIGNAL,
        R.id.viopTabContract to DetailTab.CONTRACT
    )

    private fun setStatusMessage(message: String, colorRes: Int) {
        findViewById<TextView>(R.id.viopDetailStatusMessage).apply {
            text = message
            setTextColor(getColor(colorRes))
        }
    }

    private fun formatPrice(value: Double): String = "%.2f".format(Locale.US, value).replace('.', ',')

    private fun formatPlain(value: Double): String = if (value % 1.0 == 0.0) {
        "%.0f".format(Locale.US, value)
    } else {
        "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
    }

    private fun formatCompact(value: Double): String = when {
        value >= 1_000_000_000 -> "%.2f Mr".format(Locale.US, value / 1_000_000_000)
        value >= 1_000_000 -> "%.2f Mn".format(Locale.US, value / 1_000_000)
        value >= 1_000 -> "%.1f B".format(Locale.US, value / 1_000)
        else -> "%.0f".format(Locale.US, value)
    }

    private fun formatLong(value: Long): String = String.format(Locale.US, "%,d", value).replace(',', '.')

    private fun formatAge(ageMs: Long): String = when {
        ageMs < 0L -> "saat doğrulaması bekleniyor"
        ageMs < 60_000L -> "${ageMs / 1000L} sn önce"
        ageMs < 3_600_000L -> "${ageMs / 60_000L} dk önce"
        else -> "${ageMs / 3_600_000L} sa önce"
    }

    private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(timestamp))
}

