package tr.borsatakip.v5.ui

import android.content.res.ColorStateList
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tr.borsatakip.v5.analysis.ChartIndicators
import tr.borsatakip.v5.analysis.ChartTimeframe
import tr.borsatakip.v5.analysis.ExtendedStockEvidence
import tr.borsatakip.v5.analysis.OhlcvResampler
import tr.borsatakip.v5.data.FundamentalsBatchClient
import tr.borsatakip.v5.scan.FundamentalValuationEngine
import android.widget.FrameLayout
import tr.borsatakip.v5.analysis.LinearTrendAnalyzer
import tr.borsatakip.v5.analysis.ChartSupportResistance
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.data.ChartHistoryRepository
import tr.borsatakip.v5.data.YahooFallbackProvider
import tr.borsatakip.v5.data.LiveMarketSocket
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.SignalValidity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockDetailActivity : BaseActivity() {
    private val freshnessHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastDisplayTimestamp = 0L
    private var lastDisplayRealtime = false
    private var quoteFailure: String? = null
    private val freshnessTick = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                refreshFreshnessLabels()
                freshnessHandler.postDelayed(this, 1_000L)
            }
        }
    }
    private fun refreshFreshnessLabels() {
        if (!::x.isInitialized || !::liveConnectionStatus.isInitialized) return
        val fresh = lastDisplayRealtime && System.currentTimeMillis() - lastDisplayTimestamp in 0L..60_000L
        findViewById<TextView>(R.id.validity).text = "SİNYAL GEÇERLİLİĞİ: ${validityLabel()}\n${x.signalValidityReason}"
        findViewById<TextView>(R.id.dataStatus).text = dataStatusText()
        findViewById<TextView>(R.id.scoreSummary).text = summaryText()
        if (!tr.borsatakip.v5.analysis.OpportunityTrendPresentation.from(x).verifiedSignal) {
            liveDecision.text = "SİNYAL KARARI  İZLEME • İŞLEM ONAYI YOK"
            liveDecision.setTextColor(getColor(R.color.yellow))
            chart.signalPrice = null; chart.signalTime = null
        }
        if (quoteFailure != null || (lastDisplayRealtime && !fresh)) {
            findViewById<TextView>(R.id.dataBadge).text = "GÜNCEL DEĞİL"
            findViewById<TextView>(R.id.quoteSource).text = "⚠ ESKİ VERİ\nYenileme gerekli"
            liveConnectionStatus.text = quoteFailure ?: "ESKİ VERİ • fiyat yenilenmeli • işlem onayı yok"
            findViewById<TextView>(R.id.expLastPrice).text = "Son Fiyat    —  Güncel veri doğrulanamadı"
            liveQuote.text = liveQuote.text.toString().replace("CANLI/DOĞRULANMIŞ", "ESKİ/DOĞRULANMAMIŞ")
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        configureResponsiveChart()
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && ::x.isInitialized)
            openFullscreenChart()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("detailTimeframe", selectedTimeframe.name)
        outState.putString("detailTab", selectedDetailTab)
        if (::chart.isInitialized) outState.putBundle("chartViewState", chart.saveDisplayState())
        super.onSaveInstanceState(outState)
    }

    private lateinit var x: Opportunity
    private lateinit var chart: PriceChartView
    private lateinit var chartStatus: TextView
    private lateinit var trendInfoPanel: TextView
    private lateinit var chartRepository: ChartHistoryRepository
    private lateinit var quoteProvider: ProviderRouter
    private val delayedDisplaySource by lazy { YahooFallbackProvider(applicationContext) }
    private var chartUsingDelayedData = false
    private var chartLoadJob: Job? = null
    private var chartRequestVersion = 0L
    private var pendingChartViewState: Bundle? = null
    private val chartToggleRefreshers = mutableListOf<() -> Unit>()
    private var selectedTimeframe: ChartTimeframe = ChartTimeframe.ONE_DAY
    private lateinit var liveSocket: LiveMarketSocket
    private lateinit var liveConnectionStatus: TextView
    private lateinit var liveQuote: TextView
    private lateinit var liveCandle: TextView
    private lateinit var liveMomentum: TextView
    private lateinit var liveVolume: TextView
    private lateinit var liveTrend: TextView
    private lateinit var liveV5Score: TextView
    private lateinit var liveDecision: TextView
    private lateinit var liveTradingViewSignal: TextView
    private val favoriteRepository by lazy { FavoriteRepository.get(this) }
    private var selectedDetailTab = "Grafik"
    private var expandedFinancialPanel: ChartFinancialPanel? = null
    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr","TR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_detail)
        setupBottomNav()
        x = (if (android.os.Build.VERSION.SDK_INT >= 33) intent.getSerializableExtra("opportunity", Opportunity::class.java) else @Suppress("DEPRECATION") (intent.getSerializableExtra("opportunity") as? Opportunity))
            ?: AppSession.selected ?: run { finish(); return }
        AppSession.selected = x
        chart = findViewById(R.id.chart)
        chartStatus = findViewById(R.id.chartStatus)
        trendInfoPanel = findViewById(R.id.trendInfoPanel)
        quoteProvider = ProviderRouter(this)
        // Fallback is scoped to BIST stock-detail display; trading/scanner routing remains unchanged.
        chartRepository = ChartHistoryRepository(quoteProvider, delayedDisplaySource)
        liveSocket = LiveMarketSocket(this)
        liveConnectionStatus = findViewById(R.id.liveConnectionStatus)
        liveQuote = findViewById(R.id.liveQuote)
        liveCandle = findViewById(R.id.liveCandle)
        liveMomentum = findViewById(R.id.liveMomentum)
        liveVolume = findViewById(R.id.liveVolume)
        liveTrend = findViewById(R.id.liveTrend)
        liveV5Score = findViewById(R.id.liveV5Score)
        liveDecision = findViewById(R.id.liveDecision)
        liveTradingViewSignal = findViewById(R.id.liveTradingViewSignal)

        findViewById<TextView>(R.id.title).text = "${x.symbol} · Karar kapanışı ${money(x.price)}"
        findViewById<TextView>(R.id.subtitle).text = buildString {
            append(x.companyName ?: "Şirket adı yok")
            append(" • Günlük ").append(x.dailyChangePct?.let { "%+.2f%%".format(it) } ?: "VERİ YOK")
        }
        bindCompactHeader(x.price, x.dailyChangePct, x.source, x.isRealtime, x.exchangeTimestamp, true)
        bindEasyInfoCards()
        bindExtendedPanels()
        loadExtendedDailyMetrics()
        loadExtendedFinancialMetrics()
        bindDetailTabs(savedInstanceState?.getString("detailTab") ?: "Grafik")
        bindDetailedNavigation()
        configureResponsiveChart()
        bindIndicatorDisclosure()
        bindFavorites()
        bindQuickTimeframes()
        findViewById<TextView>(R.id.btnBackDetail).setOnClickListener { finish() }
        findViewById<TextView>(R.id.scoreSummary).text = summaryText()
        findViewById<TextView>(R.id.overview).text = overviewText()
        bindFactorCards()
        findViewById<TextView>(R.id.technicalDetails).text = technicalText()
        findViewById<TextView>(R.id.dataQuality).text = qualityText()
        findViewById<TextView>(R.id.dataStatus).text = dataStatusText()
        bindLiveDecisionSnapshot()
        x.displayQuote?.let { q -> renderDisplayQuote(q.price, tr.borsatakip.v5.data.StockQuotePolicy.change(q),
            q.source, tr.borsatakip.v5.data.StockQuotePolicy.live(q), q.timestamp) }
        renderIntentDisplayQuote()
        findViewById<TextView>(R.id.validity).text = if (isDelayedObservation()) {
            "VERİ DURUMU: GECİKMELİ TEKNİK İZLEME\n${x.signalValidityReason}"
        } else {
            "SİNYAL GEÇERLİLİĞİ: ${validityLabel()}\n${x.signalValidityReason}"
        }

        if (isDelayedObservation()) {
            chart.signalPrice = null
            chart.signalTime = null
        } else {
            chart.signalPrice = x.price
            chart.signalTime = x.signalGeneratedAt.takeIf { it > 0L } ?: x.exchangeTimestamp.takeIf { it > 0L }
        }
        // Reference design emphasizes EMA9/EMA21; other averages remain opt-in.
        chart.showEma20 = false
        chart.showEma50 = false
        chart.showEma200 = false
        chart.showBollinger = false
        chart.showMacd = false
        bindTimeframeMenu()
        bindChartToggles()
        bindReferenceIndicators()
        SupportResistanceControl.bind(findViewById(R.id.toggleSupportResistance), chart)
        findViewById<Button>(R.id.chartZoomIn).setOnClickListener { chart.zoomIn() }
        findViewById<Button>(R.id.chartZoomOut).setOnClickListener { chart.zoomOut() }
        findViewById<Button>(R.id.chartFullscreen).setOnClickListener { openFullscreenChart() }
        chart.onRequestFullscreen = { openFullscreenChart() }
        selectedTimeframe = savedInstanceState?.getString("detailTimeframe")?.let {
            runCatching { ChartTimeframe.valueOf(it) }.getOrNull() } ?: ChartTimeframe.FIVE_MIN
        pendingChartViewState = savedInstanceState?.getBundle("chartViewState")
        pendingChartViewState?.let { chart.restoreDisplayState(it) }
        chartToggleRefreshers.forEach { it() }
        loadTimeframe(selectedTimeframe)
        loadMultiTimeframeSummary()
        findViewById<Button>(R.id.btnChartRetry).setOnClickListener {
            chartRepository.invalidate(x.symbol)
            findViewById<View>(R.id.chartUnavailablePanel).visibility = View.GONE
            loadTimeframe(selectedTimeframe)
            loadMultiTimeframeSummary()
        }
        findViewById<Button>(R.id.btnConnectionSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnTechnicalScreen).setOnClickListener { startActivity(Intent(this, TechnicalAnalysisActivity::class.java).putExtra("opportunity", x)) }
        findViewById<Button>(R.id.btnNewsScreen).setOnClickListener { startActivity(Intent(this, NewsActivity::class.java).putExtra("opportunity", x)) }
        findViewById<Button>(R.id.btnSignalScreen).setOnClickListener { startActivity(Intent(this, SignalHistoryActivity::class.java)) }
        findViewById<View>(R.id.btnTradingViewChart).apply {
            contentDescription = "${this@StockDetailActivity.x.symbol} TradingView grafiğini aç"
            setOnClickListener { openTradingViewChart() }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshDisplayQuote()
        startUnifiedLiveChannel()
        freshnessHandler.removeCallbacks(freshnessTick)
        freshnessHandler.post(freshnessTick)
    }

    override fun onStop() {
        freshnessHandler.removeCallbacks(freshnessTick)
        liveSocket.close()
        super.onStop()
    }

    private fun renderIntentDisplayQuote() {
        val p = intent.getDoubleExtra("displayPrice", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
        val c = intent.getDoubleExtra("displayChangePct", Double.NaN).takeIf { it.isFinite() }
        if (p != null) {
            renderDisplayQuote(
                price = p,
                changePct = c,
                source = intent.getStringExtra("displaySource") ?: x.source,
                realtime = intent.getBooleanExtra("displayRealtime", x.isRealtime),
                timestamp = intent.getLongExtra("displayTimestamp", x.exchangeTimestamp)
            )
        }
    }

    private fun refreshDisplayQuote() {
        lifecycleScope.launch {
            val stock = try {
                quoteProvider.fetchOne(x.symbol) ?: delayedDisplaySource.fetchDisplayQuoteOnly(x.symbol).stock
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // When a configured primary fails, a delayed display-only observation may
                // still be available. This never changes the trade-signal provider.
                runCatching { delayedDisplaySource.fetchDisplayQuoteOnly(x.symbol).stock }.getOrNull()
            }
            if (stock == null) {
                quoteFailure = "Fiyat güncellenemedi • gecikmeli veri kaynağı da yanıt vermedi"
                refreshFreshnessLabels()
                return@launch
            }
            val quote = tr.borsatakip.v5.data.StockQuotePolicy.from(stock)
            if (quote == null) {
                quoteFailure = "Fiyat güncellenemedi • geçerli fiyat/zaman çifti yok"
                refreshFreshnessLabels()
                return@launch
            }
            renderDisplayQuote(quote.price, tr.borsatakip.v5.data.StockQuotePolicy.change(quote), quote.source,
                tr.borsatakip.v5.data.StockQuotePolicy.live(quote), quote.timestamp)
        }
    }

    private fun renderDisplayQuote(price: Double, changePct: Double?, source: String, realtime: Boolean, timestamp: Long) {
        lastDisplayTimestamp = timestamp
        lastDisplayRealtime = realtime
        quoteFailure = null
        val fresh = realtime && System.currentTimeMillis() - timestamp in 0L..60_000L
        findViewById<TextView>(R.id.title).text = "${x.symbol}  ${money(price)}"
        findViewById<TextView>(R.id.subtitle).text = buildString {
            append(x.companyName ?: "Şirket adı yok")
            append(" • Günlük ").append(changePct?.let { "%+.2f%%".format(it) } ?: "VERİ YOK")
        }
        liveQuote.text = buildString {
            append("SON FİYAT  ").append(money(price))
            append("\nGÜNLÜK DEĞİŞİM  ").append(changePct?.let { "%+.2f%%".format(it) } ?: "VERİ YOK")
            append("\n").append(if (fresh) "CANLI/DOĞRULANMIŞ" else if (realtime) "ESKİ/DOĞRULANMAMIŞ" else "YEDEK/GECİKMELİ")
            append(" • ").append(source)
            if (timestamp > 0L) append(" • ").append(df.format(Date(timestamp)))
            append("\nV5 puanı kapanmış mum karar anına aittir; güncel quote puanı geriye dönük değiştirmez.")
        }
        liveQuote.setTextColor(directionColor(changePct))
        bindCompactHeader(price, changePct, source, realtime, timestamp, false)
        renderExpandedQuote(price, source, timestamp)
    }

    private fun startUnifiedLiveChannel() {
        liveConnectionStatus.text = "WSS canlı kanalına bağlanılıyor…"
        liveSocket.connect(
            symbols = listOf(x.symbol),
            includeSignals = true,
            includeCandles = true,
            candleInterval = "1m",
            listener = object : LiveMarketSocket.Listener {
                override fun onConnected() = runOnUiThread {
                    liveConnectionStatus.text = "WebSocket bağlandı • doğrulanmış piyasa verisi bekleniyor"
                }

                override fun onSubscribed(message: String) = runOnUiThread {
                    liveConnectionStatus.text = "CANLI KANAL HAZIR • $message"
                }

                override fun onTick(tick: LiveMarketSocket.Tick) = runOnUiThread {
                    lastDisplayTimestamp = tick.timestamp; lastDisplayRealtime = true; quoteFailure = null
                    findViewById<TextView>(R.id.title).text = "${tick.symbol}  ${money(tick.price)}"
                    val bid = tick.bid?.let(::money) ?: "—"
                    val ask = tick.ask?.let(::money) ?: "—"
                    val change = tick.changePct
                    val changeText = change?.let { "%+.2f%%".format(it) } ?: "—"
                    val volume = tick.volume?.let { String.format(Locale.US, "%,.0f", it) } ?: "—"
                    liveQuote.text = buildString {
                        append("SON FİYAT  ").append(money(tick.price))
                        append("\nDEĞİŞİM  ").append(changeText)
                        append("   •   ALIŞ  ").append(bid).append("   SATIŞ  ").append(ask)
                        append("\nHACİM  ").append(volume)
                        append("\n").append(df.format(Date(tick.timestamp)))
                        append(" • ").append(tick.source ?: tick.providerId ?: "lisanslı sağlayıcı")
                        append(" • gecikme ").append(tick.delaySeconds).append(" sn")
                    }
                    liveQuote.setTextColor(directionColor(change))
                    val instant = tick.delaySeconds <= 0 && System.currentTimeMillis() - tick.timestamp in 0L..60_000L
                    lastDisplayRealtime = instant
                    bindCompactHeader(tick.price, change, tick.source ?: tick.providerId ?: "WSS sağlayıcısı", instant, tick.timestamp, false)
                    liveConnectionStatus.text = if (instant) "ANLIK ✓ • WebSocket" else "GECİKMELİ / GÜNCEL DEĞİL • ${tick.delaySeconds} sn"
                }

                override fun onCandle(candle: LiveMarketSocket.CandleUpdate) = runOnUiThread {
                    val momentumPct = if (candle.open > 0.0) ((candle.close / candle.open) - 1.0) * 100.0 else null
                    liveMomentum.text = momentumPct?.let { "1 DK MOMENTUM  ${"%+.2f".format(it)}%" } ?: "1 DK MOMENTUM  —"
                    liveMomentum.setTextColor(directionColor(momentumPct))
                    liveCandle.text = buildString {
                        append(candle.interval).append(" MUM • ")
                        append("O ").append(money(candle.open)).append("  ")
                        append("H ").append(money(candle.high)).append("  ")
                        append("L ").append(money(candle.low)).append("  ")
                        append("C ").append(money(candle.close))
                        append("\nHacim ").append(String.format(Locale.US, "%,.0f", candle.volume))
                        append(" • ").append(df.format(Date(candle.timestamp)))
                    }
                }

                override fun onTradingViewSignal(signal: LiveMarketSocket.TradingViewSignalEvent) = runOnUiThread {
                    liveTradingViewSignal.text = buildString {
                        append(if (signal.freshSignal) "TRADINGVIEW DOĞRULAMASI • " else "TRADINGVIEW ALARMI • ")
                        append(signal.action)
                        signal.price?.let { append(" • ").append(money(it)) }
                        signal.interval?.let { append(" • ").append(it) }
                        signal.score?.let { append(" • SİNYAL SKORU ").append(String.format(Locale.US, "%.0f/100", it)) }
                        append("\n").append(df.format(Date(signal.signalTime)))
                        signal.strategy?.let { append(" • ").append(it) }
                        val metrics = buildList {
                            signal.rsi?.let { add("RSI ${String.format(Locale.US, "%.1f", it)}") }
                            signal.macdHistogram?.let { add("MACD Δ ${String.format(Locale.US, "%.4f", it)}") }
                            signal.volumeRatio?.let { add("Hacim x${String.format(Locale.US, "%.2f", it)}") }
                            signal.trend?.let { add("Trend $it") }
                        }
                        if (metrics.isNotEmpty()) append("\n").append(metrics.joinToString(" • "))
                        signal.message?.let { append("\n").append(it) }
                        append("\nBu bir TradingView alarm/doğrulama bilgisidir; emir gerçekleşme kaydı değildir.")
                    }
                }

                override fun onDisconnected(reason: String) = runOnUiThread {
                    liveConnectionStatus.text = "Canlı bağlantı kapandı • $reason"
                }

                override fun onError(message: String) = runOnUiThread {
                    liveConnectionStatus.text = "Canlı kanal: $message"
                }
            }
        )
    }

    private fun openTradingViewChart() {
        val tradingViewSymbol = TradingViewChartLink.toBistSymbol(x.symbol) ?: run {
            Toast.makeText(this, "TradingView sembolü oluşturulamadı", Toast.LENGTH_SHORT).show()
            return
        }
        val encoded = Uri.encode(tradingViewSymbol)
        val uri = Uri.parse("https://www.tradingview.com/chart/?symbol=$encoded")
        // Implicit ACTION_VIEW is deliberate: if TradingView owns the URL on the device,
        // Android opens the TradingView app directly. Otherwise a browser/Custom Tab is used.
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        runCatching { startActivity(intent) }
            .recoverCatching { CustomTabsIntent.Builder().build().launchUrl(this, uri) }
            .onFailure {
                Toast.makeText(this, "TradingView grafiği açılamadı", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bindLiveDecisionSnapshot() {
        val realtimeDecision = tr.borsatakip.v5.analysis.OpportunityTrendPresentation.from(x).verifiedSignal
        val ratio = x.technical.volumeRatio
        liveVolume.text = "HACİM  ${ratio?.let { "x%.2f".format(it) } ?: "—"}"
        liveVolume.setTextColor(when { ratio == null -> getColor(R.color.text_secondary); ratio >= 1.5 -> getColor(R.color.green); ratio < 0.8 -> getColor(R.color.red); else -> getColor(R.color.text_primary) })

        val normalizedDirection = x.direction.trim().uppercase(Locale.ROOT)
        val trendLabel = when (normalizedDirection) {
            "LONG" -> "↗ YÜKSELİŞ"
            "SHORT" -> "↘ DÜŞÜŞ"
            else -> "→ NÖTR / KARIŞIK"
        }
        liveTrend.text = "TREND  $trendLabel"
        liveTrend.setTextColor(when (normalizedDirection) { "LONG" -> getColor(R.color.green); "SHORT" -> getColor(R.color.red); else -> getColor(R.color.text_secondary) })

        liveV5Score.text = "SİNYAL SKORU  ${x.finalSignalScore.coerceIn(0, 100)} / 100   •   RİSK ${x.riskScore.coerceIn(0, 100)} / 100"
        val decision = when {
            !realtimeDecision -> "TEKNİK İZLEME • CANLI KARAR YOK"
            normalizedDirection == "LONG" && x.finalSignalScore >= 85 -> "GÜÇLÜ AL ADAYI"
            normalizedDirection == "LONG" && x.finalSignalScore >= 70 -> "AL ADAYI"
            normalizedDirection == "SHORT" && x.finalSignalScore >= 85 -> "GÜÇLÜ SAT ADAYI"
            normalizedDirection == "SHORT" && x.finalSignalScore >= 70 -> "SAT ADAYI"
            else -> "İZLE"
        }
        liveDecision.text = "SİNYAL KARARI  $decision"
        liveDecision.setTextColor(when {
            decision.contains("AL ADAYI") -> getColor(R.color.green)
            decision.contains("SAT ADAYI") -> getColor(R.color.red)
            else -> getColor(R.color.yellow)
        })
        liveQuote.text = buildString {
            append("SON FİYAT  ").append(money(x.price))
            append("\nGÜNLÜK DEĞİŞİM  ").append(x.dailyChangePct?.let { "%+.2f%%".format(it) } ?: "VERİ YOK")
        }
        liveQuote.setTextColor(directionColor(x.dailyChangePct))
    }

    private fun directionColor(changePct: Double?): Int = when {
        changePct == null || !changePct.isFinite() || changePct == 0.0 -> getColor(R.color.text_primary)
        changePct > 0.0 -> getColor(R.color.green)
        else -> getColor(R.color.red)
    }

    private fun bindTimeframeMenu() {
        val button = findViewById<Button>(R.id.btnTimeframeMenu)
        button.text = "◷ Zaman ▼"
        button.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            val options = listOf(
                ChartTimeframe.ONE_MIN,
                ChartTimeframe.THREE_MIN,
                ChartTimeframe.FIVE_MIN,
                ChartTimeframe.TEN_MIN,
                ChartTimeframe.FIFTEEN_MIN,
                ChartTimeframe.THIRTY_MIN,
                ChartTimeframe.ONE_HOUR,
                ChartTimeframe.FOUR_HOUR,
                ChartTimeframe.ONE_DAY,
                ChartTimeframe.ONE_WEEK,
                ChartTimeframe.ONE_MONTH,
                ChartTimeframe.ALL_TIME
            )
            options.forEachIndexed { index, timeframe ->
                popup.menu.add(0, index + 1, index, "◷  ${timeframe.label}")
            }
            popup.setOnMenuItemClickListener { item ->
                val timeframe = options.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
                button.text = "◷ ${timeframe.label} ▼"
                loadTimeframe(timeframe)
                true
            }
            popup.show()
        }
    }

    private fun bindChartToggles() {
        fun bind(id: Int, activeText: String, inactiveText: String, current: () -> Boolean, apply: (Boolean) -> Unit) {
            val button = findViewById<Button>(id)
            fun refresh() {
                val active = current()
                button.text = if (active) activeText else inactiveText
                button.alpha = if (active) 1.0f else 0.78f
                button.backgroundTintList = ColorStateList.valueOf(
                    getColor(if (active) R.color.green else R.color.chip_bg))
                button.setTextColor(getColor(if (active) R.color.chip_bg else R.color.white))
            }
            chartToggleRefreshers += ::refresh
            button.setOnClickListener {
                apply(!current())
                refresh()
            }
            refresh()
        }
        bind(R.id.toggleEma20, "EMA20 ✓", "EMA20", { chart.showEma20 }, { chart.showEma20 = it })
        bind(R.id.toggleEma50, "EMA50 ✓", "EMA50", { chart.showEma50 }, { chart.showEma50 = it })
        bind(R.id.toggleEma200, "EMA200 ✓", "EMA200", { chart.showEma200 }, { chart.showEma200 = it })
        bind(R.id.toggleBollinger, "BOLLINGER ✓", "BOLLINGER", { chart.showBollinger }, { chart.showBollinger = it })
        bind(R.id.toggleTrend, "TREND ✓", "TREND", { chart.showTrendLines }, { chart.showTrendLines = it })
        bind(R.id.toggleChannel, "KANAL ✓", "KANAL", { chart.showChannel }, { chart.showChannel = it })
    }

    private data class TimeframeEvidence(
        val trend: LinearTrendAnalyzer.Result,
        val emaExplanation: String,
        val candleTimestamp: Long?,
        val delayed: Boolean
    )

    private fun loadMultiTimeframeSummary() {
        val specs = listOf(
            Triple(R.id.trend5m, ChartTimeframe.FIVE_MIN, "5 DK"),
            Triple(R.id.trend15m, ChartTimeframe.TEN_MIN, "10 DK"),
            Triple(R.id.trend1h, ChartTimeframe.ONE_HOUR, "1 SAAT"),
            Triple(R.id.trend1d, ChartTimeframe.ONE_DAY, "GÜNLÜK"),
            Triple(R.id.expTrendWeekly, ChartTimeframe.ONE_WEEK, "HAFTALIK"),
            Triple(R.id.expTrendMonthly, ChartTimeframe.ONE_MONTH, "AYLIK")
        )
        specs.forEach { (id, _, label) -> findViewById<TextView>(id).text = "$label • Veri bekleniyor" }
        for (id in listOf(R.id.expTrendFive, R.id.expTrendTen, R.id.expTrendHour, R.id.expTrendDaily))
            findViewById<TextView>(id).text = "Veri bekleniyor • —"
        findViewById<TextView>(R.id.timeframeConflict).text = "Zaman dilimleri doğrulanmış mum verisiyle karşılaştırılıyor; eksik veri için tahmin yapılmaz."
        lifecycleScope.launch {
            val results = specs.map { (id, timeframe, label) ->
                async(Dispatchers.IO) {
                    val evidence = runCatching {
                        val history = withTimeout(25_000) { chartRepository.loadWithSource(x.symbol, timeframe, x.candles) }
                        val candles = history.candles
                        val clean = tr.borsatakip.v5.analysis.OhlcvResampler.sanitize(candles)
                        // Do not treat an unconfirmed final candle as a closed trend.
                        val confirmed = clean.dropLast(1)
                        val trend = LinearTrendAnalyzer.analyze(confirmed)
                        val closes = confirmed.map { it.close }
                        val e20 = ChartIndicators.ema(closes, 20).lastOrNull()
                        val e50 = ChartIndicators.ema(closes, 50).lastOrNull()
                        val close = closes.lastOrNull()
                        val emaExplanation = when {
                            close == null || e20 == null || e50 == null -> "EMA20/EMA50 için yeterli mum yok."
                            close > e20 && close > e50 -> "Fiyat EMA20 ve EMA50 üzerinde; kısa ortalamalara göre güçlü."
                            close < e20 && close < e50 -> "Fiyat EMA20 ve EMA50 altında; kısa ortalamalara göre zayıf."
                            else -> "EMA20 ve EMA50 arasında; ortalamalar yönü teyit etmiyor."
                        }
                        TimeframeEvidence(trend, emaExplanation, confirmed.lastOrNull()?.timestamp, history.delayed)
                    }.getOrNull()
                    Triple(id, label, evidence)
                }
            }.awaitAll()
            if (isFinishing || isDestroyed) return@launch
            results.forEach { (id, label, evidence) ->
                val result = evidence?.trend
                val card = findViewById<TextView>(id)
                card.text = when {
                    result?.main == null || result.candleCount < 8 -> "$label\n— Yeterli veri yok"
                    else -> {
                        val glyph = when (result.main.direction) {
                            LinearTrendAnalyzer.Direction.UP -> "↗"
                            LinearTrendAnalyzer.Direction.DOWN -> "↘"
                            else -> "→"
                        }
                        val filled = ((result.main.strengthScore.coerceIn(0, 100) + 12) / 25).coerceIn(1, 4)
                        val bars = "▰".repeat(filled) + "▱".repeat(4 - filled)
                        "$label  $glyph ${result.main.direction.label}\n${if (evidence?.delayed == true) "Gecikmeli • " else ""}${result.main.strength.label}    $bars"
                    }
                }
                val extendedId = when (id) {
                    R.id.trend5m -> R.id.expTrendFive
                    R.id.trend15m -> R.id.expTrendTen
                    R.id.trend1h -> R.id.expTrendHour
                    R.id.trend1d -> R.id.expTrendDaily
                    else -> id
                }
                findViewById<TextView>(extendedId).apply {
                    text = card.text.toString().replace("\n", "   •   ")
                    contentDescription = "${text}. Dokunarak $label grafiğini aç."
                    setTextColor(when (result?.main?.direction) {
                        LinearTrendAnalyzer.Direction.UP -> getColor(R.color.green)
                        LinearTrendAnalyzer.Direction.DOWN -> getColor(R.color.red)
                        else -> getColor(R.color.text_primary)
                    })
                }
                card.contentDescription = "${card.text}. Dokunarak $label grafiğini aç."
                card.setBackgroundResource(when (result?.main?.direction) {
                    LinearTrendAnalyzer.Direction.UP -> R.drawable.bg_stock_green
                    LinearTrendAnalyzer.Direction.DOWN -> R.drawable.bg_stock_red
                    else -> R.drawable.bg_stock_outline
                })
                val detailId = when (id) {
                    R.id.trend5m -> R.id.tech5m
                    R.id.trend15m -> R.id.tech10m
                    R.id.trend1h -> R.id.tech1h
                    R.id.trend1d -> R.id.tech1d
                    else -> null
                }
                val explanation = when {
                    result?.main == null || result.candleCount < 8 -> "$label • Yeterli, doğrulanmış mum verisi yok. Yön yorumu yapılamıyor."
                    else -> "$label • ${result.main.direction.label} (${result.main.strength.label.lowercase(Locale("tr", "TR"))}). ${result.candleCount} mum üzerinden eğim hesaplandı. ${evidence?.emaExplanation.orEmpty()} ${evidence?.candleTimestamp?.let { time -> if (System.currentTimeMillis() - time > 30 * 60_000L) "Son mum güncel değil. " else "" } ?: ""}Trend puanı ${result.main.strengthScore}/100; bu puan kazanç olasılığı değildir."
                }
                detailId?.let { detail -> findViewById<TextView>(detail).apply {
                    text = explanation
                    contentDescription = "$explanation. Dokunarak $label grafiğini aç."
                }
                findViewById<TextView>(detail).setBackgroundResource(when (result?.main?.direction) {
                    LinearTrendAnalyzer.Direction.UP -> R.drawable.bg_stock_green
                    LinearTrendAnalyzer.Direction.DOWN -> R.drawable.bg_stock_red
                    else -> R.drawable.bg_stock_outline
                })
                }
                findViewById<TextView>(id).setTextColor(when {
                    result?.main?.direction == LinearTrendAnalyzer.Direction.UP -> getColor(R.color.green)
                    result?.main?.direction == LinearTrendAnalyzer.Direction.DOWN -> getColor(R.color.red)
                    else -> getColor(R.color.text_primary)
                })
            }
            val validDirections = results.mapNotNull { it.third?.trend?.takeIf { r -> r.candleCount >= 8 }?.main?.direction }
            val conflict = validDirections.distinct().size > 1
            findViewById<TextView>(R.id.timeframeConflict).text = when {
                validDirections.isEmpty() -> "TEKNİK ANALİZ BEKLENİYOR • Doğrulanmış mum verisi bulunamadı."
                conflict -> "ZAMAN DİLİMLERİ KARIŞIK • Kısa ve uzun vadeli yönler aynı değil; tek başına AL/SAT sonucu çıkarılmaz."
                validDirections.size < specs.size -> "KISMİ VERİ • Bazı zaman dilimleri alınamadı; genel teyit eksik."
                else -> "ZAMAN DİLİMİ UYUMU • ${validDirections.first().label}; diğer risk ve veri kontrolleri ayrıca geçerlidir."
            }
            findViewById<TextView>(R.id.technicalConflict).text = findViewById<TextView>(R.id.timeframeConflict).text.toString()
        }
    }

    private fun loadTimeframe(timeframe: ChartTimeframe) {
        selectedTimeframe = timeframe
        chartLoadJob?.cancel()
        val request = ++chartRequestVersion
        chart.candles = emptyList()
        chart.trendAnalysis = null
        chartUsingDelayedData = false
        clearSelectedAnalysis()
        refreshQuickSelection()
        chart.intraday = !timeframe.daily
        chartStatus.text = "${timeframe.label} gerçek OHLCV verisi yükleniyor..."
        findViewById<View>(R.id.chartUnavailablePanel).visibility = View.GONE
        trendInfoPanel.text = "Trend analizi hazırlanıyor..."
        chartLoadJob = lifecycleScope.launch {
            runCatching {
                val history = withTimeout(35_000) { chartRepository.loadWithSource(x.symbol, timeframe, x.candles) }
                val candles = history.candles
                val trend = withContext(Dispatchers.Default) { LinearTrendAnalyzer.analyze(candles.dropLast(1)) }
                Triple(candles, trend, history)
            }.onSuccess { (candles, trend, history) ->
                if (request != chartRequestVersion || selectedTimeframe != timeframe) return@onSuccess
                chartUsingDelayedData = history.delayed
                if (chartUsingDelayedData) { chart.signalPrice = null; chart.signalTime = null }
                chart.timeframeLabel = timeframe.label
                chart.candles = candles
                chart.supportResistance = ChartSupportResistance.fromConfirmed(candles.dropLast(1))
                chart.trendAnalysis = trend
                pendingChartViewState?.let { chart.restoreDisplayState(it) }; pendingChartViewState = null
                chartToggleRefreshers.forEach { it() }
                chartStatus.text = when {
                    candles.isEmpty() -> "${timeframe.label}: gerçek OHLCV verisi bulunamadı. Sahte veri üretilmedi."
                    else -> "${if (history.delayed) "GECİKMELİ / YEDEK · SADECE TEKNİK İZLEME · AL/SAT YOK" else "SAĞLAYICI VERİSİ"}\nKaynak: ${history.source} • Zaman Dilimi: ${timeframe.label} • ${candles.size} gerçek mum • ${dateRange(candles.first().timestamp, candles.last().timestamp)}"
                }
                val unavailable = candles.isEmpty()
                findViewById<View>(R.id.chartUnavailablePanel).visibility = if (unavailable) View.VISIBLE else View.GONE
                findViewById<TextView>(R.id.chartUnavailableReason).text = if (unavailable) "${timeframe.label} için doğrulanmış grafik verisi alınamadı. Sunucu erişimini kontrol edin veya yeniden deneyin." else ""
                trendInfoPanel.text = trendInfo(timeframe, trend)
                bindSelectedAnalysis(timeframe, candles, trend)
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) return@onFailure
                if (request != chartRequestVersion || selectedTimeframe != timeframe) return@onFailure
                chartUsingDelayedData = false
                chart.timeframeLabel = timeframe.label
                chart.candles = emptyList()
                chart.trendAnalysis = null
                chartStatus.text = "${timeframe.label}: doğrulanmış grafik verisi alınamadı."
                findViewById<View>(R.id.chartUnavailablePanel).visibility = View.VISIBLE
                findViewById<TextView>(R.id.chartUnavailableReason).text = "Veri kaynağına ulaşılamadı veya mum bilgisi doğrulanamadı. Bağlantınızı kontrol edip tekrar deneyin."
                trendInfoPanel.text = "Teknik analiz bekleniyor • Doğrulanmış mum verisi olmadan gösterge değeri üretilmez."
                clearSelectedAnalysis()
            }
        }
    }



    private fun bindCompactHeader(price: Double, changePct: Double?, source: String, realtime: Boolean, timestamp: Long, snapshot: Boolean) {
        findViewById<TextView>(R.id.title).text = x.symbol
        findViewById<TextView>(R.id.quoteCompany).text = x.companyName ?: "Şirket adı yok"
        findViewById<TextView>(R.id.quotePrice).text = price.takeIf { it.isFinite() && it > 0.0 }?.let(::money) ?: "— TL"
        findViewById<TextView>(R.id.quoteChange).apply {
            text = "Günlük değişim  " + (changePct?.takeIf { it.isFinite() }?.let { "%+.2f%%".format(it) } ?: "—")
            setTextColor(directionColor(changePct))
        }
        val fresh = !snapshot && realtime && timestamp > 0L && System.currentTimeMillis() - timestamp in 0L..60_000L
        val state = when {
            fresh -> "ANLIK VERİ"
            snapshot -> "KARAR / TARAMA VERİSİ"
            timestamp > 0L -> "GECİKMELİ / ESKİ"
            else -> "ZAMAN DOĞRULANAMADI"
        }
        findViewById<TextView>(R.id.dataBadge).text = state
        // The freshness badge already conveys delay. Repeating it in this narrow column
        // wrapped the provider name mid-word on 360dp / 1.3x-font Android screens.
        val compactProvider = source.substringBefore("•").substringBefore(" / ").trim()
            .take(24).ifBlank { "Kaynak bilinmiyor" }
        findViewById<TextView>(R.id.quoteSource).text = compactProvider +
            if (timestamp > 0L) "\nSon veri ${SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(timestamp))}" else "\nZaman bilinmiyor"
    }

    private fun showDetailTab(name: String) {
        selectedDetailTab = name
        for ((label, tabId, panelId) in listOf(
            Triple("Grafik", R.id.detailTabChart, R.id.detailChartSection),
            Triple("Teknik", R.id.detailTabTechnical, R.id.detailTechnicalSection),
            Triple("Plan", R.id.detailTabPlan, R.id.detailPlanSection)
        )) {
            val active = name == label
            findViewById<View>(panelId).visibility = if (active) View.VISIBLE else View.GONE
            findViewById<TextView>(tabId).apply {
                setTextColor(getColor(if (active) R.color.white else R.color.text_secondary))
                typeface = android.graphics.Typeface.create("sans-serif-medium", if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setBackgroundResource(if (active) R.drawable.bg_stock_tab_selected_line else android.R.color.transparent)
            }
        }
    }

    private fun bindDetailTabs(initialTab: String) {
        findViewById<TextView>(R.id.detailTabChart).setOnClickListener { showDetailTab("Grafik") }
        findViewById<TextView>(R.id.detailTabTechnical).setOnClickListener { showDetailTab("Teknik") }
        findViewById<TextView>(R.id.detailTabPlan).setOnClickListener { showDetailTab("Plan") }
        showDetailTab(initialTab.takeIf { it in setOf("Grafik", "Teknik", "Plan") } ?: "Grafik")
    }

    private fun focusDetailTabs() {
        // Scroll target is in the ScrollView's direct content; preserves orientation and TalkBack focus.
        val scroll = findViewById<android.widget.ScrollView>(R.id.detailScrollView)
        val tabs = findViewById<View>(R.id.detailTabChart)
        scroll.post { scroll.smoothScrollTo(0, (tabs.top - (12 * resources.displayMetrics.density).toInt()).coerceAtLeast(0)) }
    }

    private fun bindDetailedNavigation() {
        findViewById<Button>(R.id.btnDetailedCommentary).setOnClickListener {
            showDetailTab("Teknik")
            focusDetailTabs()
        }
        // Right-chevron card action: show the selected timeframe's real chart, never generate data.
        for ((id, timeframe) in listOf(
            R.id.tech5m to ChartTimeframe.FIVE_MIN,
            R.id.tech10m to ChartTimeframe.TEN_MIN,
            R.id.tech1h to ChartTimeframe.ONE_HOUR,
            R.id.tech1d to ChartTimeframe.ONE_DAY,
            R.id.trend5m to ChartTimeframe.FIVE_MIN,
            R.id.trend15m to ChartTimeframe.TEN_MIN,
            R.id.trend1h to ChartTimeframe.ONE_HOUR,
            R.id.trend1d to ChartTimeframe.ONE_DAY,
            R.id.expTrendFive to ChartTimeframe.FIVE_MIN,
            R.id.expTrendTen to ChartTimeframe.TEN_MIN,
            R.id.expTrendHour to ChartTimeframe.ONE_HOUR,
            R.id.expTrendDaily to ChartTimeframe.ONE_DAY,
            R.id.expTrendWeekly to ChartTimeframe.ONE_WEEK,
            R.id.expTrendMonthly to ChartTimeframe.ONE_MONTH
        )) {
            findViewById<TextView>(id).setOnClickListener {
                showDetailTab("Grafik")
                loadTimeframe(timeframe)
                focusDetailTabs()
            }
        }
    }

    private fun bindIndicatorDisclosure() {
        val advanced = findViewById<View>(R.id.advancedIndicatorRow)
        val disclosure = findViewById<TextView>(R.id.otherIndicatorsToggle)
        disclosure.setOnClickListener {
            val expand = advanced.visibility != View.VISIBLE
            advanced.visibility = if (expand) View.VISIBLE else View.GONE
            disclosure.text = if (expand) "Diğer göstergeler  ▴" else "Diğer göstergeler  ▾"
            disclosure.contentDescription = if (expand) "Diğer göstergeleri kapat" else "Diğer göstergeleri aç"
        }
    }

    private fun configureResponsiveChart() {
        val screenHeightDp = resources.configuration.screenHeightDp.coerceAtLeast(480)
        val chartHeightDp = (screenHeightDp * 0.43f).toInt().coerceIn(260, 360)
        val frame = findViewById<View>(R.id.chartFrame)
        frame.layoutParams = frame.layoutParams.apply {
            height = (chartHeightDp * resources.displayMetrics.density + 0.5f).toInt()
        }
    }

    private fun bindFavorites() {
        val star = findViewById<TextView>(R.id.btnFavoriteDetail)
        fun showFavoriteIcon(favorite: Boolean) {
            star.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, if (favorite) R.drawable.ic_sd_favorite_filled else R.drawable.ic_sd_favorite_outline, 0, 0
            )
            star.contentDescription = if (favorite) "Favorilerden çıkar" else "Favorilere ekle"
        }
        lifecycleScope.launch {
            val isFavorite = favoriteRepository.isFavorite(x.symbol)
            if (!isFinishing && !isDestroyed) showFavoriteIcon(isFavorite)
        }
        star.setOnClickListener {
            lifecycleScope.launch {
                runCatching { favoriteRepository.toggle(x.symbol, x.companyName) }
                    .onSuccess { added ->
                        showFavoriteIcon(added)
                        Toast.makeText(this@StockDetailActivity, if (added) "Favorilere eklendi" else "Favorilerden çıkarıldı", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(this@StockDetailActivity, "Favoriler güncellenemedi", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun bindQuickTimeframes() {
        for ((id, timeframe) in listOf(
            R.id.quick5m to ChartTimeframe.FIVE_MIN,
            R.id.quick10m to ChartTimeframe.TEN_MIN,
            R.id.quick1h to ChartTimeframe.ONE_HOUR,
            R.id.quick1d to ChartTimeframe.ONE_DAY
        )) {
            findViewById<Button>(id).setOnClickListener { loadTimeframe(timeframe) }
        }
    }

    private fun refreshQuickSelection() {
        for ((id, timeframe) in listOf(
            R.id.quick5m to ChartTimeframe.FIVE_MIN,
            R.id.quick10m to ChartTimeframe.TEN_MIN,
            R.id.quick1h to ChartTimeframe.ONE_HOUR,
            R.id.quick1d to ChartTimeframe.ONE_DAY
        )) {
            findViewById<Button>(id).backgroundTintList = ColorStateList.valueOf(
                getColor(if (selectedTimeframe == timeframe) R.color.green else R.color.chip_bg))
            findViewById<Button>(id).setTextColor(getColor(if (selectedTimeframe == timeframe) R.color.chip_bg else R.color.white))
        }
        findViewById<Button>(R.id.btnTimeframeMenu).text = "◷ ${selectedTimeframe.label} ▼"
    }

    private fun bindReferenceIndicators() {
        fun toggle(id: Int, name: String, enabled: () -> Boolean, set: (Boolean) -> Unit) {
            val button = findViewById<Button>(id)
            fun render() {
                val active = enabled()
                button.text = if (active) "$name ✓" else name
                button.backgroundTintList = ColorStateList.valueOf(getColor(if (active) R.color.green else R.color.chip_bg))
                button.setTextColor(getColor(if (active) R.color.chip_bg else R.color.white))
            }
            chartToggleRefreshers += ::render
            button.setOnClickListener { set(!enabled()); render() }
            render()
        }
        chart.showEma9 = true
        chart.showEma21 = true
        chart.showVwap = true
        toggle(R.id.toggleEma9, "EMA9", { chart.showEma9 }, { chart.showEma9 = it })
        toggle(R.id.toggleEma21, "EMA21", { chart.showEma21 }, { chart.showEma21 = it })
        toggle(R.id.toggleSma50, "SMA50", { chart.showSma50 }, { chart.showSma50 = it })
        toggle(R.id.toggleSma200, "SMA200", { chart.showSma200 }, { chart.showSma200 = it })
        toggle(R.id.toggleVwap, "VWAP", { chart.showVwap }, { chart.showVwap = it })
    }

    private fun bindEasyInfoCards() {
        findViewById<TextView>(R.id.easyCompany).text = x.companyName?.takeIf { it.isNotBlank() }?.let {
            "Şirket adı: $it. Faaliyet alanına ilişkin doğrulanmış KAP açıklaması bu ekrana bağlı olmadığından tahmin yapılmıyor."
        } ?: "Bu hissenin faaliyet alanına ilişkin doğrulanmış KAP açıklaması henüz alınamadı."
        findViewById<TextView>(R.id.easyWhyInterest).text =
            "Güncel KAP/haber verisi bu grafikle eşleştirilmedi. Fiyat veya hacim artışı, şirketin neden ilgi gördüğünü tek başına kanıtlamaz."
        findViewById<TextView>(R.id.easyRisks).text =
            "Piyasa hareketleri, şirket haberleri, bilanço sonuçları ve dalgalanmalar fiyatı değiştirebilir. Geçmiş grafik kâr veya zarar garantisi vermez."
        resetEasyMarketCards()
    }

    private fun resetEasyMarketCards() {
        findViewById<TextView>(R.id.easyPriceMovement).text = "Yeterli ve kapanmış olduğu teyit edilmiş mum olmadan fiyat yönü açıklanmaz."
        findViewById<TextView>(R.id.easyImportantLevels).text = "Geçerli destek/direnç verisi bekleniyor; tahmini fiyat seviyesi gösterilmiyor."
        findViewById<TextView>(R.id.easyChangeConditions).text = "Geçerli veri olmadan kırılma veya yön değişimi tahmini yapılmaz."
        findViewById<TextView>(R.id.easyDataSource).text = "Grafik verisi henüz doğrulanmadı. Önceki hisseye ait değerler kullanılmıyor."
    }

    private fun clearSelectedAnalysis() {
        for (id in listOf(R.id.graphSupport, R.id.supportDetail)) findViewById<TextView>(id).text = "Destek\n— • veri bekleniyor"
        for (id in listOf(R.id.graphResistance, R.id.resistanceDetail)) findViewById<TextView>(id).text = "Direnç\n— • veri bekleniyor"
        findViewById<TextView>(R.id.breakoutUp).text = "↗ Direnç için doğrulanmış kapanış bekleniyor."
        findViewById<TextView>(R.id.breakoutDown).text = "↘ Destek için doğrulanmış kapanış bekleniyor."
        findViewById<TextView>(R.id.metricRsi).text = "RSI14\n—"
        findViewById<TextView>(R.id.metricVolume).text = "Hacim\n—"
        findViewById<TextView>(R.id.metricAtr).text = "ATR%\n—"
        findViewById<TextView>(R.id.plainExplanation).text = "Geçerli mum verileri bekleniyor. Yeterli veri gelmeden yön, destek ve direnç yorumu yapılmaz."
        resetEasyMarketCards()
        clearExpandedTechnical()
    }

    private fun bindSelectedAnalysis(timeframe: ChartTimeframe, candles: List<tr.borsatakip.v5.model.Candle>, result: LinearTrendAnalyzer.Result) {
        val last = candles.dropLast(1).lastOrNull()
        if (last == null || result.main == null) { clearSelectedAnalysis(); return }
        val price = last.close
        val verified = chart.supportResistance
        val support = verified?.support?.price?.takeIf { it < price }
        val resistance = verified?.resistance?.price?.takeIf { it > price }
        fun distance(level: Double): String = String.format(Locale("tr", "TR"), "%%%.1f", kotlin.math.abs((price - level) / price * 100))
        fun evidenceLabel(evidence: ChartSupportResistance.Evidence?): String = when (evidence) {
            ChartSupportResistance.Evidence.CONFIRMED_PIVOT -> "Doğrulanmış dönüş seviyesi"
            ChartSupportResistance.Evidence.ROLE_REVERSAL -> "İki kapanışla doğrulanan rol değişimi"
            ChartSupportResistance.Evidence.RECENT_RANGE -> "Önceki 40 mumun sınırı • pivot değil"
            null -> "Seviye doğrulanamadı"
        }
        val sText = support?.let { "Destek\n${money(it)} • ${distance(it)} mesafe\n${evidenceLabel(verified?.support?.evidence)}" }
            ?: "Destek\nBu dönemde doğrulanamadı"
        val rText = resistance?.let { "Direnç\n${money(it)} • ${distance(it)} mesafe\n${evidenceLabel(verified?.resistance?.evidence)}" }
            ?: "Direnç\nBu dönemde doğrulanamadı"
        for (id in listOf(R.id.graphSupport, R.id.supportDetail)) findViewById<TextView>(id).text = sText
        for (id in listOf(R.id.graphResistance, R.id.resistanceDetail)) findViewById<TextView>(id).text = rText
        findViewById<TextView>(R.id.breakoutUp).text = if (resistance != null)
            "↗ ${money(resistance)} üzerinde kapanış: yukarı yön araştırılır; hacim ve ikinci mum teyidi gerekir." else
            "↗ Direnç doğrulanmadı. Yukarı kırılım eşiği verilemiyor."
        findViewById<TextView>(R.id.breakoutDown).text = if (support != null)
            "↘ ${money(support)} altında kapanış: aşağı yön riski artabilir; hacim ve ikinci mum izlenir." else
            "↘ Destek doğrulanmadı. Aşağı kırılım eşiği verilemiyor."
        val indicators = ChartIndicators.calculate(candles.dropLast(1), !timeframe.daily)
        val rsi = indicators.rsi.lastOrNull()
        val atr = indicators.atr.lastOrNull()
        val avgVolume = indicators.volume20.lastOrNull()
        val volumeRatio = avgVolume?.takeIf { it > 0.0 }?.let { last.volume / it }
        findViewById<TextView>(R.id.metricRsi).text = "RSI14\n" + (rsi?.let { "%.0f".format(it) } ?: "—")
        findViewById<TextView>(R.id.metricVolume).text = "Hacim\n" + (volumeRatio?.let { "%.1fx".format(it) } ?: "—")
        findViewById<TextView>(R.id.metricAtr).text = "ATR%\n" + (atr?.let { "%.1f".format(it / price * 100) } ?: "—")
        renderExpandedTechnical(timeframe, candles.dropLast(1), price)
        val direction = result.main.direction
        val understanding = when (direction) {
            LinearTrendAnalyzer.Direction.UP -> "Bu dönemin son alınan mumlarında fiyatın genel yönü yukarı."
            LinearTrendAnalyzer.Direction.DOWN -> "Bu dönemin son alınan mumlarında fiyatın genel yönü aşağı."
            else -> "Bu dönemde fiyatın yönü net değil; yatay hareket öne çıkıyor."
        }
        findViewById<TextView>(R.id.plainExplanation).text = buildString {
            append("Fiyat nasıl hareket ediyor?\n$understanding ")
            append("Bu, ${timeframe.label} dönemindeki ${candles.size - 1} kapanmış olduğu varsayılan geçmiş muma dayanır; son mum teyitsiz bırakılır. Bir garanti değildir.\n\n")
            append("Hangi seviyeler önemli?\n")
            append(support?.let { "${money(it)} civarı doğrulanmış destek eğrisi izleniyor. " } ?: "Güvenilir destek eğrisi bulunamadı. ")
            append(resistance?.let { "${money(it)} civarı doğrulanmış direnç eğrisi izleniyor. " } ?: "Güvenilir direnç eğrisi bulunamadı. ")
            append("Destek, alıcıların güçlenebildiği; direnç, satışların artabildiği seviyedir.\n\n")
            append("Bu yorum ne zaman değişir?\n")
            append("Fiyat destek veya direnç sınırını kapanış ve hacimle aşarsa tablo yeniden hesaplanır. ")
            append("Üstteki 5 dk, 10 dk, saatlik ve günlük kartlar farklıysa kısa süreli hareket ana trendi değiştirmemiş olabilir. ")
            append("Canlı ve güncel veri doğrulanmadan işlem kararı vermeyin.")
        }
        findViewById<TextView>(R.id.easyPriceMovement).text = understanding + " Son mum kesinleşmediği için yorumda kullanılmaz."
        findViewById<TextView>(R.id.easyImportantLevels).text =
            (support?.let { "Destek: ${money(it)}. " } ?: "Destek doğrulanamadı. ") +
            (resistance?.let { "Direnç: ${money(it)}. " } ?: "Direnç doğrulanamadı. ") +
            "Bu seviyeler sabit fiyat garantisi değildir."
        findViewById<TextView>(R.id.easyChangeConditions).text =
            "Doğrulanmış kapanış ve hacim, destek veya direnç sınırını aşarsa görünüm yeniden değerlendirilir."
        findViewById<TextView>(R.id.easyDataSource).text =
            "Tarama kaynağı: ${x.source} • Grafik: ${timeframe.label} • İncelenen son geçmiş mum: ${df.format(Date(last.timestamp))}. " +
            "En yeni mum teyitsiz; yatırım tavsiyesi değildir."
        findViewById<TextView>(R.id.techDataCaution).text = "${timeframe.label} • ${candles.size} sağlayıcı mumu • Son mum kapanmış olmayabilir • Teknik izleme, yatırım tavsiyesi değildir."
    }


    /** Wide ASELS reference panels, adapted to portrait scrolling without changing existing tabs. */
    private fun bindExtendedPanels() {
        // No claim of an analyst consensus until a validated, dated provider is connected.
        findViewById<TextView>(R.id.expAnalystSource).text =
            "Analist konsensüsü kaynağı bağlı değil; 1–3, 3–6 ve 6–12 ay hedefleri tahmin edilmez."
        for ((id, label) in listOf(
            R.id.expTargetShort to "Kısa Vadeli (1–3 ay)",
            R.id.expTargetMedium to "Orta Vadeli (3–6 ay)",
            R.id.expTargetLong to "Uzun Vadeli (6–12 ay)"
        )) findViewById<TextView>(id).apply {
            text = "$label\n— • Doğrulanmış analist verisi yok"
            contentDescription = text
        }
        findViewById<TextView>(R.id.expNavGraph).setOnClickListener {
            showDetailTab("Grafik"); focusDetailTabs()
        }
        fun navMessage(message: String) {
            findViewById<TextView>(R.id.expNavigationInfo).apply { text = message; requestFocus() }
        }
        fun openOfficial(url: String) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { navMessage("Tarayıcı açılamadı. Resmî site adresi: $url") }
        }
        findViewById<TextView>(R.id.expNavCompany).setOnClickListener {
            navMessage("Şirket: ${x.companyName ?: "Ad doğrulanamadı"} • Sembol: ${x.symbol}. Faaliyet alanı ve şirket bildirimleri resmî KAP'tan doğrulanmalıdır.")
            openOfficial("https://www.kap.org.tr/tr/bist-sirketler")
        }
        findViewById<TextView>(R.id.expNavFinancial).setOnClickListener {
            val frame = findViewById<FrameLayout>(R.id.expFinancialContainer)
            frame.visibility = View.VISIBLE
            if (expandedFinancialPanel == null) {
                expandedFinancialPanel = ChartFinancialPanel(this, x.symbol, "BIST", lifecycleScope)
                frame.addView(expandedFinancialPanel, FrameLayout.LayoutParams(-1, -2))
            }
            expandedFinancialPanel?.load()
            navMessage("Finansal tablolar: doğrulanmış KAP kayıtları varsa aşağıdaki finansal panelde gösterilir.")
        }
        findViewById<TextView>(R.id.expNavNews).setOnClickListener {
            startActivity(Intent(this, NewsActivity::class.java).putExtra("opportunity", x))
        }
        findViewById<TextView>(R.id.expNavDividend).setOnClickListener {
            navMessage("Temettü: Bu sembol için doğrulanmış temettü takvimi bağlı değil. KAP bildirimleri açılıyor.")
            openOfficial("https://www.kap.org.tr/tr/bist-sirketler")
        }
        findViewById<TextView>(R.id.expNavOwnership).setOnClickListener {
            navMessage("Ortaklık yapısı: Tarihli ve doğrulanmış pay oranları henüz bağlı değil. KAP sayfasını açın.")
            openOfficial("https://www.kap.org.tr/tr/bist-sirketler")
        }
        findViewById<TextView>(R.id.expNavLegislation).setOnClickListener {
            navMessage("Resmî mevzuat için devletin mevzuat portalı açılıyor.")
            openOfficial("https://www.mevzuat.gov.tr/")
        }
        findViewById<TextView>(R.id.expNavAnalyst).setOnClickListener {
            navMessage("Analist raporları: Kaynağı, yayın tarihi ve analist sayısı doğrulanmadığından hedef fiyatlar gösterilmiyor.")
            val target = findViewById<View>(R.id.expTargetShort)
            findViewById<android.widget.ScrollView>(R.id.detailScrollView).post {
                findViewById<android.widget.ScrollView>(R.id.detailScrollView).smoothScrollTo(0, target.top)
            }
        }
    }

    private fun renderExpandedQuote(price: Double, source: String, timestamp: Long) {
        val valid = price.takeIf { it.isFinite() && it > 0.0 && timestamp in 1_000_000_000_000L..System.currentTimeMillis() }
        findViewById<TextView>(R.id.expLastPrice).text = "Son Fiyat  " + (valid?.let(::money) ?: "—")
        findViewById<TextView>(R.id.expLevelSource).text = if (valid == null)
            "Son fiyat doğrulanamadı; referans seviyeler kapanmış mumlara dayanır." else
            "Son fiyat: ${source.take(36)} • ${df.format(Date(timestamp))}. Destek/direnç ayrı seçili mum dönemine aittir."
    }

    private fun loadExtendedDailyMetrics() {
        for ((id, label) in listOf(
            R.id.expDailyLow to "Günlük En Düşük", R.id.expDailyHigh to "Günlük En Yüksek",
            R.id.expDailyOpen to "Açılış", R.id.expPrevClose to "Önceki Kapanış", R.id.expDailyVolume to "Hacim"
        )) findViewById<TextView>(id).text = "$label\n—"
        findViewById<TextView>(R.id.expMetricSource).text = "Günlük veriler doğrulanıyor; eksik veri tahmin edilmiyor."
        lifecycleScope.launch {
            val confirmed = try {
                val bars = withTimeout(30_000) { chartRepository.load(x.symbol, ChartTimeframe.ONE_DAY, x.candles) }
                OhlcvResampler.sanitize(bars).dropLast(1)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
              catch (_: Exception) { emptyList() }
            if (isFinishing || isDestroyed) return@launch
            val last = confirmed.lastOrNull()
            if (last == null) {
                findViewById<TextView>(R.id.expMetricSource).text = "Doğrulanmış kapanmış günlük mum yok; fiyat kutuları boş bırakıldı."
                return@launch
            }
            fun set(id: Int, name: String, value: String) { findViewById<TextView>(id).text = "$name\n$value" }
            set(R.id.expDailyLow, "Günlük En Düşük", money(last.low))
            set(R.id.expDailyHigh, "Günlük En Yüksek", money(last.high))
            set(R.id.expDailyOpen, "Açılış", money(last.open))
            set(R.id.expPrevClose, "Önceki Kapanış", last.close.let(::money))
            set(R.id.expDailyVolume, "Hacim", String.format(Locale("tr", "TR"), "%,.0f", last.volume))
            findViewById<TextView>(R.id.expMetricSource).text =
                "Kapanmış günlük mum: ${df.format(Date(last.timestamp))} • Grafik kaynağı, finansal oranlardan farklı olabilir."
        }
    }

    private fun loadExtendedFinancialMetrics() {
        for ((id, label) in listOf(R.id.expMarketCap to "Piyasa Değeri", R.id.expPe to "F/K", R.id.expPb to "PD/DD"))
            findViewById<TextView>(id).text = "$label\n—"
        lifecycleScope.launch {
            val assessment = try {
                val client = FundamentalsBatchClient(applicationContext)
                val batch = withTimeout(40_000) { if (client.hasLocalImport()) client.loadLocal() else client.load() }
                withContext(Dispatchers.Default) {
                    FundamentalValuationEngine.scan(
                        (batch.items.map { it.symbol } + x.symbol).distinct(), batch.items, System.currentTimeMillis()
                    ).firstOrNull { it.symbol == x.symbol }
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
              catch (_: Exception) { null }
            if (isFinishing || isDestroyed) return@launch
            val snapshot = assessment?.snapshot?.takeIf { it.symbol == x.symbol &&
                FundamentalValuationEngine.validate(it, System.currentTimeMillis()) == null }
            val multiples = snapshot?.let(FundamentalValuationEngine::multiples)
            val fmt = { d: Double? -> d?.takeIf { it.isFinite() && it > 0 }?.let { String.format(Locale("tr", "TR"), "%.2f", it) } ?: "—" }
            findViewById<TextView>(R.id.expMarketCap).text = "Piyasa Değeri\n" +
                (snapshot?.marketCapTry?.let { String.format(Locale("tr", "TR"), "%.2f Mr TL", it / 1_000_000_000) } ?: "—")
            findViewById<TextView>(R.id.expPe).text = "F/K\n${fmt(multiples?.pe)}"
            findViewById<TextView>(R.id.expPb).text = "PD/DD\n${fmt(multiples?.pb)}"
            if (snapshot != null) findViewById<TextView>(R.id.expMetricSource).append(
                "\nKAP finansal dönemi: ${df.format(Date(snapshot.financialPeriodEndMs))} • Piyasa gözlemi: ${df.format(Date(snapshot.marketObservedAtMs))}."
            )
        }
    }

    private fun clearExpandedTechnical() {
        for ((id,label) in listOf(R.id.expRsi to "RSI (14)", R.id.expMacd to "MACD", R.id.expStochastic to "Stokastik (14)",
            R.id.expCci to "CCI (20)", R.id.expAdx to "ADX (14)", R.id.expMomentum to "Momentum (10)"))
            findViewById<TextView>(id).apply { text = "$label    —"; contentDescription = text }
        for ((id,label) in listOf(R.id.expRes1 to "Direnç 1",R.id.expRes2 to "Direnç 2",R.id.expRes3 to "Direnç 3",
            R.id.expSup1 to "Destek 1",R.id.expSup2 to "Destek 2",R.id.expSup3 to "Destek 3"))
            findViewById<TextView>(id).apply { text = "$label    —"; contentDescription = text }
        findViewById<TextView>(R.id.expIndicatorSource).text = "Seçili zaman diliminde doğrulanmış mum verisi bekleniyor."
    }

    private fun renderExpandedTechnical(timeframe: ChartTimeframe, closed: List<tr.borsatakip.v5.model.Candle>, close: Double) {
        val data = ExtendedStockEvidence.indicators(closed, !timeframe.daily)
        fun metric(id: Int, label: String, value: Double?, suffix: String = "") {
            findViewById<TextView>(id).apply {
                text = "$label    " + (value?.takeIf { it.isFinite() }?.let { String.format(Locale("tr", "TR"), "%.2f", it) + suffix } ?: "—")
                contentDescription = text
            }
        }
        metric(R.id.expRsi, "RSI (14)", data.rsi14)
        metric(R.id.expMacd, "MACD (12,26,9)", data.macd)
        metric(R.id.expStochastic, "Stokastik %K (14)", data.stochastic14)
        metric(R.id.expCci, "CCI (20)", data.cci20)
        metric(R.id.expAdx, "ADX (14)", data.adx14)
        metric(R.id.expMomentum, "Momentum (10)", data.momentum10Pct, "%")
        val ladder = ExtendedStockEvidence.ladder(closed, close)
        for (index in 0 until 3) {
            findViewById<TextView>(listOf(R.id.expRes1,R.id.expRes2,R.id.expRes3)[index]).text =
                "Direnç ${index+1}    " + (ladder.resistances.getOrNull(index)?.let(::money) ?: "—")
            findViewById<TextView>(listOf(R.id.expSup1,R.id.expSup2,R.id.expSup3)[index]).text =
                "Destek ${index+1}    " + (ladder.supports.getOrNull(index)?.let(::money) ?: "—")
        }
        findViewById<TextView>(R.id.expIndicatorSource).text =
            "${timeframe.label} • ${closed.size} kapanmış/son teyitsiz mum dışlanmış OHLCV gözlemi. Hesaplanamayan gösterge boş bırakılır."
    }

    private fun openFullscreenChart() {
        FullscreenChartActivity.open(this, x.symbol, "BIST", chart.candles, if (chartUsingDelayedData) delayedDisplaySource.displayName else x.source,
            chartUsingDelayedData || x.source.contains("Yahoo", true), selectedTimeframe, chart.saveDisplayState(), companyName = x.companyName)
    }

    private fun trendInfo(timeframe: ChartTimeframe, r: LinearTrendAnalyzer.Result): String {
        val main = r.main
        if (main == null) return "Zaman Dilimi: ${timeframe.label}\n${r.message ?: "Trend analizi için yeterli veri yok."}"
        val strength = sequenceOf(r.support, r.resistance, r.main).filterNotNull().maxByOrNull { it.strengthScore } ?: main
        val breakoutDetail = when (r.breakout) {
            LinearTrendAnalyzer.Breakout.UP -> "YUKARI KIRILIM • ${r.volumeConfirmation.label} hacim teyidi"
            LinearTrendAnalyzer.Breakout.DOWN -> "AŞAĞI KIRILIM • ${r.volumeConfirmation.label} hacim teyidi"
            LinearTrendAnalyzer.Breakout.WAITING -> "TEYİT BEKLENİYOR • ${r.volumeConfirmation.label} hacim"
            LinearTrendAnalyzer.Breakout.NONE -> "KIRILIM YOK"
        }
        return buildString {
            append("Zaman Dilimi: ${timeframe.label}\n")
            append("Ana Trend: ${main.direction.label}\n")
            append("Trend Gücü: ${strength.strength.label} (${strength.strengthScore}/100)\n")
            append("Ana Eğim: ${"%.6f".format(main.slope)} • R²: ${"%.3f".format(main.r2)}\n")
            append("Temas: ${strength.contactCount} • Son temas: ${strength.lastContactIndex?.let { "${r.candleCount - 1 - it} mum önce" } ?: "yok"}\n")
            append("Destek Trend: ${r.supportNow?.let { "%.2f".format(it) } ?: "yok"}\n")
            append("Direnç Trend: ${r.resistanceNow?.let { "%.2f".format(it) } ?: "yok"}\n")
            append("Trend Kırılımı: $breakoutDetail")
            r.breakoutPct?.let { append(" • ${"%+.2f".format(it)}%") }
        }
    }

    private fun dateRange(first: Long, last: Long): String = "${df.format(Date(first))} – ${df.format(Date(last))}"

    private fun summaryText():String {
        if (isDelayedObservation()) {
            return "GECİKMELİ TEKNİK İZLEME • AL/SAT YOK\nTeknik Skor ${x.score}/100 • İşlem sıralaması: — (gecikmeli) • Risk ${x.riskScore}/100\nVeri Güveni ${x.dataConfidenceScore}/100 (${x.dataConfidenceLabel})\n${x.setupType} • ${x.signalValidityReason}"
        }
        val strength = when {
            x.finalSignalScore >= 85 -> "YÜKSEK GÜÇ"
            x.finalSignalScore >= 70 -> "İZLE"
            x.finalSignalScore >= 60 -> "ZAYIF SİNYAL"
            else -> "DÜŞÜK GÜÇ"
        }
        return "${x.direction} • LONG ${x.longScore}/100 • SHORT ${x.shortScore}/100\nNihai Sinyal ${x.finalSignalScore}/100 • Sıralama ${x.rankingScore}/100 • $strength\nTeknik Skor ${x.score}/100 • Risk ${x.riskScore}/100 • Veri Güveni ${x.dataConfidenceScore}/100 (${x.dataConfidenceLabel})\n${validityLabel()} • ${x.setupType}"
    }

    private fun overviewText():String {
        val t = x.technical
        val emaState = when {
            listOf(t.ema20,t.ema50,t.ema200).all { it != null } && x.price < t.ema20!! && t.ema20!! < t.ema50!! && t.ema50!! < t.ema200!! ->
                "Fiyat EMA20, EMA50 ve EMA200 seviyelerinin altında; mevcut trend aşağı yönlü."
            listOf(t.ema20,t.ema50,t.ema200).all { it != null } && x.price > t.ema20!! && t.ema20!! > t.ema50!! && t.ema50!! > t.ema200!! ->
                "Fiyat EMA20, EMA50 ve EMA200 seviyelerinin üzerinde; mevcut trend yukarı yönlü."
            else -> "EMA dizilimi tek yönlü güçlü trend teyidi vermiyor."
        }
        val volume = x.technical.volumeRatio?.let {
            if (it < 0.8) "İşlem hacmi zayıf olduğu için sinyal teyidi azalıyor." else "Hacim sinyal değerlendirmesine veri sağlıyor."
        } ?: "Hacim teyidi doğrulanamadı."
        return if (isDelayedObservation()) {
            "$emaState $volume Bu kayıt gecikmeli/günlük teknik analizdir; AL/SAT sinyali veya anlık işlem kararı değildir."
        } else {
            "$emaState $volume Nihai Sinyal ${x.finalSignalScore}/100 bir başarı olasılığı değildir; veri ve teknik koşulların mevcut sürümdeki birleşik skorudur."
        }
    }

    private fun bindFactorCards() {
        val t = x.technical
        val trend = when {
            t.ema20 != null && t.ema50 != null && t.ema200 != null && x.price < t.ema20 && t.ema20 < t.ema50 && t.ema50 < t.ema200 -> "NEGATİF\nFiyat EMA20/50/200 altında. SHORT yönünü destekliyor."
            t.ema20 != null && t.ema50 != null && t.ema200 != null && x.price > t.ema20 && t.ema20 > t.ema50 && t.ema50 > t.ema200 -> "POZİTİF\nFiyat EMA20/50/200 üzerinde. LONG yönünü destekliyor."
            else -> "KARIŞIK\nEMA dizilimi güçlü tek yön teyidi vermiyor."
        }
        findViewById<TextView>(R.id.trendCard).text = "TREND\n$trend"

        val rsi = t.rsi14
        val momentum = when {
            rsi == null -> "YETERSİZ VERİ\nRSI14 hesaplanamadı."
            rsi < 30 -> "AŞIRI SATIM BÖLGESİNE YAKIN/ALTINDA\nRSI14 ${fmt(rsi)}. Tepki hareketi riski izlenmeli."
            rsi < 48 -> "ZAYIF\nRSI14 ${fmt(rsi)}. Alıcı momentumu zayıf."
            rsi <= 68 -> "DENGELİ/POZİTİF\nRSI14 ${fmt(rsi)}. Momentum aşırı bölge dışında."
            else -> "YÜKSEK\nRSI14 ${fmt(rsi)}. Aşırı alım/geri çekilme riski izlenmeli."
        }
        findViewById<TextView>(R.id.momentumCard).text = "MOMENTUM\n$momentum"

        val macd = if (t.macd != null && t.macdSignal != null) {
            val state = if (t.macd >= t.macdSignal) "POZİTİF" else "NEGATİF"
            "$state\nMACD ${fmt(t.macd)} / Signal ${fmt(t.macdSignal)}. Kısa vadeli momentum ${if (state=="POZİTİF") "yukarı" else "aşağı"} yönde."
        } else "YETERSİZ VERİ\nMACD/Signal hesaplanamadı."
        findViewById<TextView>(R.id.macdCard).text = "MACD\n$macd"

        val volume = t.volumeRatio?.let { ratio ->
            val state = when { ratio >= 1.5 -> "GÜÇLÜ TEYİT"; ratio >= 1.0 -> "ORTA TEYİT"; else -> "ZAYIF TEYİT" }
            "$state\nHacim ${"%.2f".format(ratio)}x. ${if (ratio < 1.0) "Düşük katılım sinyal teyidini azaltıyor." else "Hacim katılımı sinyal değerlendirmesini destekliyor."}"
        } ?: "YETERSİZ VERİ\nHacim oranı hesaplanamadı."
        findViewById<TextView>(R.id.volumeCard).text = "HACİM\n$volume"

        findViewById<TextView>(R.id.extraCard).text = buildString {
            append("EK FAKTÖRLER\n")
            append("VWMA20: ${fmt(t.vwma20 ?: t.vwap)} • ${if ((t.vwma20 ?: t.vwap) != null) if (x.price >= (t.vwma20 ?: t.vwap)!!) "fiyat üzerinde" else "fiyat altında" else "veri yok"}\n")
            append("Session VWAP: ${fmt(t.sessionVwap)} • ${if (t.sessionVwap != null) if (x.price >= t.sessionVwap) "fiyat üzerinde" else "fiyat altında" else "intraday veri yok"}\n")
            append("VWMA: ${fmt(t.vwma)}\n")
            append("ATR14: ${fmt(t.atr14)} • volatilite/risk girdisi\n")
            append("Destek: ${fmt(t.support)} • Direnç: ${fmt(t.resistance)}\n")
            append("Hacim anomalisi: ${x.volumeAnomalyPct?.let { "%+.1f%%".format(it) } ?: "Veri yok"}\n")
            x.rsiRiskMessage?.let { append("RSI Risk Uyarısı: $it\n") }
            x.riskPlan?.let { p ->
                append("Giriş: ${fmt(p.entry)} • Stop: ${fmt(p.stop)} • Hedef1: ${fmt(p.target1)} • Hedef2: ${fmt(p.target2)}\n")
                append("RR1: ${p.rr1?.let { "1:%.2f".format(it) } ?: "—"} • RR2: ${p.rr2?.let { "1:%.2f".format(it) } ?: "—"}")
            }
        }
    }

    private fun technicalText():String {
        val t=x.technical
        return "EMA20: ${fmt(t.ema20)}\nEMA50: ${fmt(t.ema50)}\nEMA200: ${fmt(t.ema200)}\nRSI14: ${fmt(t.rsi14)}\nMACD: ${fmt(t.macd)}\nSignal: ${fmt(t.macdSignal)}\nATR14: ${fmt(t.atr14)}\nVWMA20: ${fmt(t.vwma20 ?: t.vwap)}\nSession VWAP: ${fmt(t.sessionVwap)}\nVWMA(legacy): ${fmt(t.vwma)}\nHacim oranı: ${t.volumeRatio?.let { "%.2fx".format(it) } ?: "Veri yok"}\nBollinger alt/üst: ${fmt(t.bbLower)} / ${fmt(t.bbUpper)}\nDestek/Direnç: ${fmt(t.support)} / ${fmt(t.resistance)}"
    }

    private fun qualityText():String {
        fun mark(ok:Boolean)=if(ok)"✓" else "⚠ Veri yok"
        val hasPrice=x.price.isFinite()&&x.price>0
        val hasVol=x.technical.volumeRatio!=null
        val hasOhlcv=x.candles.size>=220
        val hasKap=!x.kapLabel.equals("Veri yok",true)&&x.kapLabel.isNotBlank()
        val hasLevels=x.support!=null&&x.resistance!=null
        val hasVwap=(x.technical.sessionVwap ?: x.technical.vwma20 ?: x.technical.vwap)!=null
        val footer = if (isDelayedObservation()) {
            "Eksik alanlar uydurulmaz. Gecikmeli veri yalnız teknik izleme içindir; AL/SAT sinyali üretilmez."
        } else {
            "Eksik alanlar uydurulmaz ve Veri Güveni ile Nihai Sinyal birbirinin yerine kullanılmaz."
        }
        return "VERİ GÜVENİ ${x.dataConfidenceScore}/100 (${x.dataConfidenceLabel})\nFiyat ${mark(hasPrice)}\nHacim ${mark(hasVol)}\nOHLCV ${mark(hasOhlcv)}\nKAP ${mark(hasKap)}\nDestek/Direnç ${mark(hasLevels)}\nVWMA20/Session VWAP ${mark(hasVwap)}\n$footer"
    }

    private fun dataStatusText():String {
        val measured = if (x.receivedElapsedRealtime > 0L) {
            val elapsed=(SystemClock.elapsedRealtime()-x.receivedElapsedRealtime).coerceAtLeast(0L)
            val atReceipt=if(x.receivedAt>0&&x.exchangeTimestamp>0)(x.receivedAt-x.exchangeTimestamp).coerceAtLeast(0L) else 0L
            formatAge(atReceipt+elapsed)
        } else "yeniden başlatma sonrası monotonic yaş doğrulanamıyor"
        val decisionLabel = if (isDelayedObservation()) "Karar Durumu: TEKNİK İZLEME • AL/SAT YOK" else "Karar Durumu: ${x.decisionState.name}"
        return "$decisionLabel\nSıralama Skoru: ${x.rankingScore}/100\nKaynak: ${x.source}\nVeri Modu: ${x.dataMode.name}\nPiyasa Veri Zamanı: ${time(x.exchangeTimestamp)}\nUygulamaya Ulaşma: ${time(x.receivedAt)}\nTarama Anındaki Veri Yaşı: ${x.dataAgeMs?.let(::formatAge) ?: "bilinmiyor"}\nŞu An Ölçülen Veri Yaşı: $measured\nSağlayıcı gecikmesi: ${x.delaySeconds?.let { "$it sn" } ?: "bilinmiyor"}\nTarama başladı: ${time(x.scanStartedAt)}\nTarama tamamlandı: ${time(x.scanCompletedAt)}\nScanRun: ${x.scanRunId ?: "yok"}\nNot: Uygulamaya ulaşma süresi, tek başına piyasa gecikmesi olarak yorumlanmaz."
    }

    private fun isDelayedObservation(): Boolean = x.dataMode != DataMode.REALTIME || !x.isRealtime

    private fun validityLabel() = when {
        tr.borsatakip.v5.analysis.OpportunityTrendPresentation.from(x).verifiedSignal -> "DOĞRULANMIŞ FIRSAT"
        x.dataMode == DataMode.REALTIME && System.currentTimeMillis() - x.exchangeTimestamp !in 0L..60_000L -> "ESKİ VERİ • İŞLEM YOK"
        x.signalValidity == SignalValidity.VALID -> "VERİ BÜTÜNLÜĞÜ GEÇERLİ • KARAR İZLEME"
        x.signalValidity == SignalValidity.WATCH -> "İZLEME"
        x.signalValidity == SignalValidity.INSUFFICIENT -> "YETERSİZ VERİ"
        else -> "REDDEDİLDİ"
    }
    private fun time(v:Long)=if(v>0)df.format(Date(v)) else "bilinmiyor"
    private fun money(v:Double)=if(v.isFinite())"%.2f TL".format(v) else "Veri yok"
    private fun fmt(v:Double?)=v?.takeIf{it.isFinite()}?.let{"%.2f".format(it)}?:"Veri yok"
    private fun formatAge(ms:Long)=when { ms<60_000L->"${ms/1000L} sn"; ms<3_600_000L->"${ms/60_000L} dk"; else->"${ms/3_600_000L} sa" }
}

