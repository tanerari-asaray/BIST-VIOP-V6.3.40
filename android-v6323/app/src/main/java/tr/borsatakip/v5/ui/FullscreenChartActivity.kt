package tr.borsatakip.v5.ui

import android.content.Context
import android.content.ActivityNotFoundException
import android.net.Uri
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ChartIndicators
import tr.borsatakip.v5.analysis.ChartSupportResistance
import tr.borsatakip.v5.analysis.ChartTimeframe
import tr.borsatakip.v5.analysis.OhlcvResampler
import tr.borsatakip.v5.analysis.ReadableStockAnalysis
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.ChartHistoryResult
import tr.borsatakip.v5.data.ChartHistoryRepository
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.data.YahooFallbackProvider
import tr.borsatakip.v5.model.Candle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Independent chart workspace. Every display and calculation belongs to one requested series. */
class FullscreenChartActivity : BaseActivity() {
    companion object {
        fun open(context: Context, symbol: String, market: String, candles: List<Candle>,
                 source: String, delayed: Boolean, timeframe: ChartTimeframe = ChartTimeframe.ONE_DAY,
                 viewState: Bundle? = null, companyName: String? = null) {
            context.startActivity(Intent(context, FullscreenChartActivity::class.java).apply {
                putExtra("symbol", symbol); putExtra("market", market)
                putExtra("candles", ArrayList(candles.takeLast(2000)))
                putExtra("source", source); putExtra("delayed", delayed)
                putExtra("timeframe", timeframe.name)
                putExtra("viewState", viewState)
                putExtra("verifiedCompanyName", companyName?.takeIf { it.isNotBlank() })
            })
        }
    }

    private val green = Color.rgb(0, 236, 145)
    private val amber = Color.rgb(255, 195, 75)
    private val red = Color.rgb(255, 85, 103)
    private val muted = Color.rgb(167, 194, 200)
    private val state = ChartDisplayState()
    private val bistChartRepository by lazy {
        ChartHistoryRepository(ProviderRouter(applicationContext, experimentalFallbackOverride = false),
            YahooFallbackProvider(applicationContext))
    }
    private val tr = Locale("tr", "TR")
    private val date = SimpleDateFormat("dd.MM.yyyy HH:mm", tr).apply {
        timeZone = TimeZone.getTimeZone("Europe/Istanbul")
    }
    private lateinit var chart: PriceChartView
    private lateinit var notice: TextView
    private lateinit var selection: TextView
    private lateinit var heading: TextView
    private lateinit var pages: ViewFlipper
    private lateinit var summary: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var financial: ChartFinancialPanel
    private lateinit var indicatorScroll: HorizontalScrollView
    private lateinit var pageRoot: LinearLayout
    private lateinit var headerRow: LinearLayout
    private lateinit var navigationTabs: LinearLayout
    private lateinit var periodStrip: HorizontalScrollView
    private lateinit var trendPanel: LinearLayout
    private lateinit var readableBottom: LinearLayout
    private var trendJob: Job? = null
    private var trendSequence = 0L
    private val trendReadings = mutableMapOf<ChartTimeframe, ReadableStockAnalysis.Snapshot?>()
    private val trendPending = mutableSetOf<ChartTimeframe>()
    private val comparisonPeriods = listOf(ChartTimeframe.FIVE_MIN, ChartTimeframe.TEN_MIN,
        ChartTimeframe.ONE_HOUR, ChartTimeframe.ONE_DAY)
    private var requestJob: Job? = null
    private var period = ChartTimeframe.ONE_DAY
    private val periodButtons = mutableMapOf<ChartTimeframe, Button>()
    private var verifiedCompanyName: String? = null
    private var symbol = ""
    private var market = "BIST"
    private var delayedSource = false
    private var source = ""
    private var lastMessage = ""
    private var panelsExpanded = true
    private var pendingViewState: Bundle? = null
    private val indicatorRefreshers = mutableListOf<() -> Unit>()

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun number(v: Double?, digits: Int = 2) = v?.takeIf(Double::isFinite)?.let {
        String.format(tr, "%.${digits}f", it)
    } ?: "—"
    private fun label(value: String, size: Float = 13f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(muted)
        setPadding(dp(10), dp(5), dp(10), dp(5))
    }
    private fun action(value: String, onClick: () -> Unit) = Button(this).apply {
        text = value; textSize = 11f; isAllCaps = false
        setTextColor(green); minWidth = dp(58); minimumHeight = dp(44)
        setPadding(dp(8), 0, dp(8), 0)
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 31, 36)); setStroke(dp(1), Color.rgb(19, 78, 66)); cornerRadius = dp(9).toFloat()
        }
        setOnClickListener { onClick() }
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun scroller(view: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; addView(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        symbol = intent.getStringExtra("symbol").orEmpty()
        verifiedCompanyName = intent.getStringExtra("verifiedCompanyName")?.trim()?.takeIf { it.isNotBlank() }
        market = intent.getStringExtra("market")?.takeIf { it == "VIOP" } ?: "BIST"
        delayedSource = market == "BIST" && intent.getBooleanExtra("delayed", false)
        source = intent.getStringExtra("source").orEmpty().ifBlank { "Kaynak doğrulanmadı" }
        val requested = savedInstanceState?.getString("period") ?: intent.getStringExtra("timeframe")
        period = ChartTimeframe.entries.firstOrNull { it.name == requested || it.label == requested } ?: ChartTimeframe.ONE_DAY
        val initial = if (android.os.Build.VERSION.SDK_INT >= 33) {
            @Suppress("UNCHECKED_CAST")
            intent.getSerializableExtra("candles", ArrayList::class.java) as? ArrayList<Candle>
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            intent.getSerializableExtra("candles") as? ArrayList<Candle>
        }.orEmpty()
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val restoredCandles = savedInstanceState?.getSerializable("chartCandles") as? ArrayList<Candle>
        val visibleCandles = restoredCandles ?: initial
        pendingViewState = savedInstanceState?.getBundle("viewState") ?: intent.getBundleExtra("viewState")
        buildContent()
        pendingViewState?.let { chart.restoreDisplayState(it) }
        indicatorRefreshers.forEach { it() }
        if (visibleCandles.isNotEmpty()) {
            state.complete(state.begin(), visibleCandles.takeLast(2000), recorded = true)
            render()
            refreshTrendComparisons()
        } else load(period)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { updateNotice(); delay(30_000) }
            }
        }
    }

    private fun buildContent() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(1, 14, 22))
        }
        pageRoot = root
        val titleRow = row()
        headerRow = titleRow
        titleRow.addView(action("‹") { finish() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        heading = label("", 17f).apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) }
        heading.contentDescription = "Sembol ve zaman dilimi; dönem seçmek için dokunun"
        heading.setOnClickListener {
            android.widget.PopupMenu(this, heading).apply {
                val choices = listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.TEN_MIN, ChartTimeframe.FIFTEEN_MIN,
                    ChartTimeframe.THIRTY_MIN, ChartTimeframe.ONE_HOUR, ChartTimeframe.FOUR_HOUR,
                    ChartTimeframe.ONE_DAY, ChartTimeframe.ONE_WEEK, ChartTimeframe.ONE_MONTH)
                choices.forEachIndexed { i, tf -> menu.add(0, i, i, tf.label) }
                setOnMenuItemClickListener { load(choices[it.itemId]); true }
                show()
            }
        }
        titleRow.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(titleRow)
        val tradingView = action("↗  TRADINGVIEW GRAFİĞİNİ AÇ") { openTradingView() }.apply {
            textSize = 15f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.rgb(0, 152, 238), Color.rgb(0, 89, 225))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                cornerRadius = dp(12).toFloat()
            }
            contentDescription = "TradingView sitesinde ${symbol} grafik sayfasını aç"
        }
        root.addView(tradingView, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(dp(7), dp(5), dp(7), dp(5))
        })
        notice = label("", 11f).apply { setTextColor(amber) }
        root.addView(notice)
        val tabs = row()
        navigationTabs = tabs
        listOf("GRAFİK", "ÖZET", "AYRINTI", "FİNANSAL").forEachIndexed { index, title ->
            tabs.addView(action(title) {
                pages.displayedChild = index
                if (index == 3) financial.load()
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        tabs.addView(action("YENİLE") {
            if (pages.displayedChild == 3) financial.load(force = true) else {
                if (market == "BIST") bistChartRepository.invalidate(symbol)
                load(period)
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(tabs)
        pages = ViewFlipper(this)
        root.addView(pages, LinearLayout.LayoutParams(-1, 0, 1f))
        val chartHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chartPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chartScroll = ScrollView(this).apply { isFillViewport = false }
        chartScroll.addView(chartPage)
        trendPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val periods = row()
        val choices = listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.TEN_MIN,
            ChartTimeframe.FIFTEEN_MIN, ChartTimeframe.THIRTY_MIN, ChartTimeframe.ONE_HOUR,
            ChartTimeframe.FOUR_HOUR, ChartTimeframe.ONE_DAY, ChartTimeframe.ONE_WEEK, ChartTimeframe.ONE_MONTH)
        (if (period in choices) choices else listOf(period) + choices).forEach { tf ->
            val button = action(tf.label) { load(tf) }.apply { tag = tf.name }
            periodButtons[tf] = button
            periods.addView(button, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(4) })
        }
        periodStrip = scroller(periods)
        chartPage.addView(periodStrip)
        chart = PriceChartView(this).apply {
            id = R.id.chart
            showEma20 = false; showEma50 = false; showEma200 = false
            showEma9 = true; showEma21 = true; showSma50 = true; showSma200 = true
            showTrendLines = false; showChannel = false
            showRecordedPriceLabels = true
            onCandleSelected = { candle -> updateSelection(candle ?: state.candles.lastOrNull()) }
        }
        selection = label("Muma dokunun • basılı tutup sürükleyin: OHLCV • iki parmak: yakınlaştır", 11f)
        chartPage.addView(selection)
        chartPage.addView(chart, LinearLayout.LayoutParams(-1, dp(390)))
        val indicators = row()
        val sd = action("S/D") {}.apply { id = R.id.toggleSupportResistance }
        SupportResistanceControl.bind(sd, chart)
        indicators.addView(sd, LinearLayout.LayoutParams(-2, dp(48)))
        fun toggle(name: String, current: () -> Boolean, change: (Boolean) -> Unit) {
            val button = action(name) {}
            button.setTextColor(when (name) {
                "EMA9" -> Color.rgb(3, 169, 244)
                "EMA21" -> Color.rgb(255, 193, 7)
                "SMA50" -> Color.rgb(186, 104, 200)
                "SMA200" -> Color.rgb(255, 152, 0)
                "Bollinger" -> Color.LTGRAY
                "ATR14" -> red
                else -> green
            })
            fun refresh() { button.text = name + if (current()) " ●" else " ○"; button.alpha = if (current()) 1f else 0.62f }
            indicatorRefreshers += ::refresh
            button.setOnClickListener { change(!current()); refresh() }
            refresh()
            indicators.addView(button, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(4) })
        }
        toggle("EMA9", { chart.showEma9 }) { chart.showEma9 = it }
        toggle("EMA21", { chart.showEma21 }) { chart.showEma21 = it }
        toggle("SMA50", { chart.showSma50 }) { chart.showSma50 = it }
        toggle("SMA200", { chart.showSma200 }) { chart.showSma200 = it }
        toggle("VWAP", { chart.showVwap }) { chart.showVwap = it }
        toggle("Bollinger", { chart.showBollinger }) { chart.showBollinger = it }
        toggle("ATR14", { chart.showAtr }) { chart.showAtr = it }
        indicatorScroll = scroller(indicators)
        // Controls are outside the scrolling, asynchronously resized analysis content.
        chartHost.addView(indicatorScroll, LinearLayout.LayoutParams(-1, dp(48)))
        val tools = row()
        tools.addView(action("+") { chart.zoomIn() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        tools.addView(action("−") { chart.zoomOut() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        tools.addView(action("Sıfırla") { chart.resetZoom() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        tools.addView(action("Göstergeler") {
            indicatorScroll.visibility = if (indicatorScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, LinearLayout.LayoutParams(0, dp(44), 1.7f))
        tools.addView(action("Alt paneller") {
            panelsExpanded = !panelsExpanded; applyPanels()
        }, LinearLayout.LayoutParams(0, dp(44), 1.7f))
        chartPage.addView(tools)
        chartPage.addView(trendPanel)
        readableBottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chartPage.addView(readableBottom)
        chartHost.addView(chartScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        pages.addView(chartHost)
        fun analysisPage(): LinearLayout {
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(6), dp(5), dp(10)) }
            pages.addView(ScrollView(this).apply { addView(panel) })
            return panel
        }
        summary = analysisPage(); details = analysisPage()
        financial = ChartFinancialPanel(this, symbol, market, lifecycleScope)
        pages.addView(ScrollView(this).apply { tag = "chart-financial-scroll"; addView(financial) })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(5), bars.top, bars.right + dp(5), bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        adjustOrientation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); adjustOrientation()
    }
    private fun adjustOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        panelsExpanded = !landscape
        indicatorScroll.visibility = View.VISIBLE
        periodStrip.visibility = if (landscape) View.GONE else View.VISIBLE
        // Keep touch targets stationary when loading/provenance text changes.
        notice.minLines = if (landscape) 1 else 3
        notice.maxLines = if (landscape) 1 else 3
        notice.ellipsize = android.text.TextUtils.TruncateAt.END
        if (landscape && navigationTabs.parent !== headerRow) {
            (navigationTabs.parent as ViewGroup).removeView(navigationTabs)
            headerRow.addView(navigationTabs, LinearLayout.LayoutParams(0, dp(44), 1.5f))
        } else if (!landscape && navigationTabs.parent !== pageRoot) {
            (navigationTabs.parent as ViewGroup).removeView(navigationTabs)
            pageRoot.addView(navigationTabs, 2, LinearLayout.LayoutParams(-1, dp(44)))
        }
        applyPanels()
        updateNotice(); updateSelection(chart.selectedCandle ?: state.candles.lastOrNull())
    }
    private fun applyPanels() {
        chart.showVolume = panelsExpanded; chart.showRsi = panelsExpanded; chart.showMacd = panelsExpanded
    }

    private fun load(tf: ChartTimeframe) {
        period = tf
        requestJob?.cancel()
        val request = state.begin()
        lastMessage = "${tf.label} yükleniyor…"
        render() // Clear chart, price, selection and analysis before starting the request.
        refreshTrendComparisons() // Never retain obsolete trend cards across period changes/refreshes.
        requestJob = lifecycleScope.launch {
            try {
                val history = withTimeout(35_000) { fetch(tf) }
                val clean = OhlcvResampler.sanitize(history.candles).takeLast(2000)
                require(clean.none { it.timestamp > System.currentTimeMillis() + 60_000 }) { "Future timestamp" }
                if (state.complete(request, clean)) {
                    source = history.source
                    delayedSource = history.delayed
                    lastMessage = if (clean.isEmpty()) "Bu dönem için OHLCV verisi alınamadı." else ""
                    render()
                }
            } catch (cancel: CancellationException) {
                if (cancel is kotlinx.coroutines.TimeoutCancellationException && state.fail(request)) {
                    lastMessage = "Veri isteğinin süresi doldu. Yenile ile tekrar deneyin."; render()
                } else throw cancel
            } catch (_: Exception) {
                if (state.fail(request)) {
                    lastMessage = "Veri alınamadı. Bağlantıyı kontrol edip Yenile ile tekrar deneyin."
                    render()
                }
            }
        }
    }

    private suspend fun fetch(tf: ChartTimeframe): ChartHistoryResult = withContext(Dispatchers.IO) {
        require(symbol.isNotBlank()) { "Missing symbol" }
        if (market == "VIOP") {
            // Never route a futures contract to an equity/Yahoo endpoint.
            val interval = if (tf.daily) "1d" else "${tf.requestIntervalMinutes}m"
            val range = if (tf == ChartTimeframe.ONE_WEEK || tf == ChartTimeframe.ONE_MONTH) "5y" else if (tf.daily) "1y" else "120d"
            val raw = BackendProvider(applicationContext).loadViopHistoryFlexible(symbol, range, interval, 0, forDisplay = true).getOrThrow()
            val bars = when {
                tf == ChartTimeframe.ONE_WEEK -> OhlcvResampler.weekly(raw)
                tf == ChartTimeframe.ONE_MONTH -> OhlcvResampler.monthly(raw)
                tf == ChartTimeframe.FOUR_HOUR -> OhlcvResampler.sessionAggregate(raw, 60, 240)
                tf.aggregateMinutes != null -> OhlcvResampler.aggregate(raw, requireNotNull(tf.requestIntervalMinutes), tf.aggregateMinutes)
                else -> raw
            }
            ChartHistoryResult(bars, "VİOP backend", false, bars.lastOrNull()?.timestamp)
        } else {
            bistChartRepository.loadWithSource(symbol, tf, emptyList())
        }
    }

    private fun render() {
        chart.intraday = !period.daily
        chart.timeframeLabel = period.label
        chart.candles = state.candles
        chart.supportResistance = ChartSupportResistance.fromConfirmed(state.candles.dropLast(1))
        chart.signalPrice = null; chart.signalTime = null; chart.trendAnalysis = null
        heading.text = "${symbol.ifBlank { "Grafik" }} · ${if (market == "VIOP") "VİOP" else "BIST"} · ${period.label} ▾"
        periodButtons.forEach { (tf, button) -> button.alpha = if (tf == period) 1f else 0.55f }
        if(state.candles.isNotEmpty()) { pendingViewState?.let { chart.restoreViewportState(it) }; pendingViewState = null }
        indicatorRefreshers.forEach { it() }
        updateSelection(chart.selectedCandle ?: state.candles.lastOrNull())
        updateNotice(); renderAnalysis()
    }

    private fun updateSelection(c: Candle?) {
        selection.text = if (c == null) "Fiyat / OHLCV: —"
            else "${date.format(Date(c.timestamp))} · ${period.label}\nA ${number(c.open)}  Y ${number(c.high)}  D ${number(c.low)}  K ${number(c.close)}  H ${number(c.volume, 0)}"
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            selection.text = selection.text.toString().replace("\n", " · ")
    }
    private fun updateNotice() {
        if (!::notice.isInitialized) return
        val last = state.candles.lastOrNull()
        notice.setTextColor(if (last == null) red else amber)
        notice.text = when {
            state.phase == ChartDisplayState.Phase.LOADING -> lastMessage
            last == null -> "${lastMessage.ifBlank { "OHLCV verisi yok." }} Grafik ve analiz temizlendi."
            else -> {
                val age = ((System.currentTimeMillis() - last.timestamp).coerceAtLeast(0) / 60_000)
                val mode = if (state.phase == ChartDisplayState.Phase.RECORDED) "KAYITLI VERİ" else if (delayedSource) "GECİKMELİ VERİ" else "GÜNCELLİK DOĞRULANMADI"
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    "$mode · ${source.take(45)} · Son mum ${date.format(Date(last.timestamp))} · Canlı fiyat değildir"
                else "$mode · ${if (source.isBlank()) "Kaynak doğrulanmadı" else source}\nSon mum başlangıcı: ${date.format(Date(last.timestamp))} · $age dk önce · Canlı fiyat değildir"
            }
        }
    }
    private fun card(parent: LinearLayout, title: String, value: String) {
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(4, 29, 37)); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.rgb(19, 65, 61))
            }
            addView(label(title, 12f).apply { setTextColor(green); setTypeface(null, Typeface.BOLD) })
            addView(label(value, 14f).apply { setTextColor(Color.rgb(229, 243, 241)) })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    private fun openTradingView() {
        val ticker = symbol.trim().uppercase(Locale.ROOT).removeSuffix(".IS")
        if (!ticker.matches(Regex("[A-Z0-9._-]{1,40}"))) {
            Toast.makeText(this, "TradingView için geçerli bir sembol seçin.", Toast.LENGTH_LONG).show()
            return
        }
        // For VİOP the remote instrument mapping is not verified: open search, not a made-up contract.
        val url = if (market == "VIOP")
            "https://www.tradingview.com/search/?query=${Uri.encode(ticker)}"
        else "https://www.tradingview.com/symbols/BIST-${Uri.encode(ticker)}/"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Bağlantıyı açacak bir tarayıcı bulunamadı.", Toast.LENGTH_LONG).show()
        }
    }

    private fun neonCard(parent: LinearLayout, heading: String, body: String, accent: Int) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(4, 28, 39))
                setStroke(dp(1), accent)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(7), dp(7), dp(7), dp(7))
            contentDescription = "$heading. $body"
            addView(label(heading, 14f).apply { setTextColor(accent); setTypeface(null, Typeface.BOLD) })
            addView(label(body, 13f).apply { setTextColor(Color.WHITE) })
        }
        parent.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(4), dp(4), dp(4), dp(5))
        })
    }

    private fun refreshTrendComparisons() {
        trendJob?.cancel()
        val generation = ++trendSequence
        trendReadings.clear()
        trendPending.clear()
        trendPending.addAll(comparisonPeriods)
        renderTrendCards()
        trendJob = lifecycleScope.launch {
            comparisonPeriods.forEach { tf ->
                launch {
                    val snapshot = try {
                        val candles = withTimeout(35_000) { fetch(tf) }.candles
                        ReadableStockAnalysis.analyze(candles, tf)
                    } catch (cancel: CancellationException) {
                        if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
                        null
                    } catch (_: Exception) { null }
                    if (generation == trendSequence) {
                        trendReadings[tf] = snapshot
                        trendPending.remove(tf)
                        renderTrendCards()
                    }
                }
            }
        }
    }

    private fun renderTrendCards() {
        if (!::trendPanel.isInitialized) return
        trendPanel.removeAllViews()
        val title = row()
        title.addView(label("ÇOKLU ZAMAN DİLİMİ · TREND", 14f).apply {
            setTextColor(green); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(action("↻ Yenile") { refreshTrendComparisons() })
        trendPanel.addView(title)
        comparisonPeriods.chunked(2).forEach { chunk ->
            val line = row()
            trendPanel.addView(line)
            chunk.forEach { tf ->
                val pending = tf in trendPending
                val value = trendReadings[tf]
                // Historical comparison data is never presented as a current trend.
                val aged = value != null && System.currentTimeMillis() - value.sampleTime >
                    if (tf.daily) 14L * 86_400_000 else 7L * 86_400_000
                val accent = when (if (aged) null else value?.direction) {
                    ReadableStockAnalysis.Direction.RISING -> green
                    ReadableStockAnalysis.Direction.FALLING -> red
                    ReadableStockAnalysis.Direction.MIXED -> muted
                    null -> amber
                }
                val status = when {
                    pending -> "Veri kontrol ediliyor"
                    value == null -> "Veri yok · Yorum yapılmadı"
                    aged -> "Eski veri · Yön teyitsiz"
                    value.direction == ReadableStockAnalysis.Direction.RISING -> "↗ Yükseliş eğilimi"
                    value.direction == ReadableStockAnalysis.Direction.FALLING -> "↘ Düşüş eğilimi"
                    else -> "→ Yatay / karışık"
                }
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(3, 32, 41)); setStroke(dp(1), accent); cornerRadius = dp(10).toFloat()
                    }
                    addView(label(tf.label, 14f).apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
                    addView(label(status, 12f).apply { setTextColor(accent) })
                    if (value != null) addView(label(
                        "Mum: ${date.format(Date(value.sampleTime))} · " +
                            if (delayedSource) "GECİKMELİ" else "GÜNCELLİK DOĞRULANMADI", 10f
                    ))
                }
                line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
        }
        val resolved = trendReadings.filter { (tf, snapshot) ->
            snapshot != null && System.currentTimeMillis() - snapshot.sampleTime <=
                if (tf.daily) 14L * 86_400_000 else 7L * 86_400_000
        }.values.filterNotNull()
        if (resolved.size >= 2 && resolved.map { it.direction }.distinct().size > 1) {
            neonCard(trendPanel, "⚠ ZAMAN DİLİMLERİ FARKLI YÖNDE",
                "Kısa ve uzun dönem aynı şeyi söylemiyor. Kısa vadeli toparlanma, günlük düşüşün bittiği anlamına gelmez.", amber)
        }
        trendPanel.addView(label("Her dönem yalnızca kendi gerçek mumlarıyla hesaplanır. Son mum hariç tutulur. Canlı işlem sinyali değildir.", 11f))
    }

    private fun renderEverydayExplanation(parent: LinearLayout, bars: List<Candle>) {
        parent.removeAllViews()
        parent.addView(label(if (market == "VIOP") "💡 BU SÖZLEŞMEYİ KOLAYCA ANLAYALIM" else "💡 BU HİSSEYİ KOLAYCA ANLAYALIM", 17f).apply {
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        val analysis = ReadableStockAnalysis.analyze(bars, period)
        if (analysis == null) {
            neonCard(parent, "Veri olmadan yorum yapmıyoruz",
                "Bu dönem için yeterli ve geçerli mum bilgisi yok. Destek, direnç veya yükseliş/düşüş yorumu gösterilmiyor. " +
                    "Veri bağlantısını kontrol ederek yeniden deneyebilirsiniz.", amber)
            return
        }
        neonCard(parent, "Fiyat nasıl hareket ediyor?", analysis.priceMovement, Color.rgb(43, 187, 255))
        neonCard(parent, "Hangi seviyeler önemli?", analysis.importantLevels, green)
        neonCard(parent, "Bu yorum ne zaman değişir?", analysis.changeConditions, amber)
        neonCard(parent, "Kısaca ne iş yapıyor?",
            if (market == "VIOP")
                "Bu bir VİOP vadeli işlem sözleşmesidir; şirket hissesi değildir. Sözleşme özellikleri doğrulanmadan dayanak varlık veya faaliyet alanı hakkında çıkarım yapılmaz."
            else verifiedCompanyName?.let {
                "Şirket: $it. Faaliyet alanına ilişkin doğrulanmış KAP açıklaması bu grafiğe bağlı değil; yalnız şirket adından iş kolu tahmin edilmez."
            } ?: "Şirketin faaliyet alanına ait doğrulanmış açıklama alınamadı. Şirket bilgileri ve KAP açıklamaları incelenmelidir.",
            Color.rgb(188, 134, 255))
        neonCard(parent, "Neden ilgi görüyor?",
            "İlginin sebebini doğrulayan güncel şirket haberi veya KAP açıklaması bu ekranda yok. " +
                "Fiyat ve hacimdeki hareket tek başına bir haberin ya da alıcı ilgisinin nedenini kanıtlamaz. " +
                "Haberler ve resmi duyurular ayrı ayrı kontrol edilmelidir.", Color.rgb(33, 206, 237))
        neonCard(parent, "Dikkat edilmesi gerekenler",
            "Borsada fiyatlar haber, şirket açıklamaları, hacim ve genel piyasa koşullarıyla değişebilir. " +
                (if (market == "VIOP")
                    "VİOP sözleşmelerinde kaldıraç, teminat ve vade sonu riskleri vardır. Fiyat yönü olumlu görünse de zarar büyüyebilir. Bu bir işlem tavsiyesi değildir."
                else "Bu grafik, şirketin ucuz ya da pahalı olduğunu tek başına göstermez. Şirketin finansal sonuçları ayrıca incelenmelidir."), red)
        parent.addView(label("Teknik terimler: ${analysis.plainIndicatorNote.ifBlank { "Hesaplama için yeterli doğrulanmış veri yok." }}", 11f))
        parent.addView(label("Kaynak: $source · İncelenen mum başlangıcı: ${date.format(Date(analysis.sampleTime))}. " +
            "En yeni mum, kapanışı doğrulanmadığı için dışarıda bırakıldı. " +
            "Veri gecikmeli veya güncelliği doğrulanmamış olabilir. Bu bilgiler yatırım tavsiyesi ya da işlem emri değildir.", 11f))
    }

    private fun renderAnalysis() {
        summary.removeAllViews(); details.removeAllViews()
        val bars = state.candles.dropLast(1) // The latest candle may still be forming.
        renderEverydayExplanation(readableBottom, state.candles)
        if (bars.isEmpty()) {
            card(summary, "ANALİZ YOK", "Seçili döneme ait OHLCV verisi bekleniyor. Önceki hesaplamalar temizlendi.")
            card(details, "GÖSTERGELER", "Yeterli veri olmadan değer veya sinyal gösterilmez.")
            renderEverydayExplanation(details, state.candles)
            return
        }
        val s = ChartIndicators.calculate(bars, !period.daily)
        val last = bars.last()
        val previous = bars.dropLast(1).takeLast(20)
        val support = previous.takeIf { it.size == 20 }?.minOf { it.low }
        val resistance = previous.takeIf { it.size == 20 }?.maxOf { it.high }
        val average = previous.takeIf { it.size == 20 }?.map { it.volume }?.average()
        val ratio = average?.takeIf { it > 0 }?.let { last.volume / it }
        val atrPct = s.atr.lastOrNull()?.let { 100 * it / last.close }
        val fast = s.ema9.lastOrNull(); val slow = s.ema21.lastOrNull()
        val trend = when {
            fast == null || slow == null -> "EMA9/21 için yetersiz geçmiş"
            fast > slow && last.close > fast -> "Yükseliş eğilimi · Kapanış > EMA9 > EMA21"
            fast < slow && last.close < fast -> "Düşüş eğilimi · Kapanış < EMA9 < EMA21"
            else -> "Karışık / yatay · EMA sıralaması teyitli değil"
        }
        card(summary, "ÖNCEKİ MUM REFERANSI · ${period.label}", "${number(last.close)} · Kapanış kesinliği ve güncellik ayrıca doğrulanmalıdır; anlık alış/satış fiyatı değildir")
        card(summary, "TREND", trend)
        card(summary, "REFERANS DESTEK / DİRENÇ", "${number(support)} / ${number(resistance)}\nSon mum hariç önceki 20 mumun alt / üst seviyesi")
        card(summary, "HACİM", "Son mum: ${number(last.volume, 0)}\nÖnceki 20 mum ortalaması: ${number(average, 0)} · Oran: ${number(ratio)}×")
        card(summary, "RİSK / OYNAKLIK", "ATR14: ${number(s.atr.lastOrNull(), 4)} · ATR/Fiyat: %${number(atrPct)}" +
            if (market == "VIOP") "\nSözleşme çarpanı, teminat ve kaldıraç ayrıca değerlendirilir." else "")
        card(summary, "KOŞULLU SENARYOLAR", if (support == null || resistance == null) "20 önceki mum gerekli."
            else "${number(resistance)} üzerinde kapanış ve hacim artışı: yukarı kırılım izlenir.\n${number(support)} altında kapanış: aşağı kırılım izlenir. Arada: bant içi hareket. Bunlar işlem emri değildir.")
        card(summary, "İŞLEM SİNYALİ: YOK", "Verinin güncelliği ve son mumun kapanışı birlikte doğrulanmadı. Hesaplanan eğilim alış/satış onayı değildir.")
        val metrics = listOf("EMA9" to fast, "EMA21" to slow, "SMA50" to s.sma50.lastOrNull(),
            "SMA200" to s.sma200.lastOrNull(), "VWAP (gün içi)" to s.vwap.lastOrNull(),
            "Bollinger20 üst" to s.upper.lastOrNull(), "Bollinger20 alt" to s.lower.lastOrNull(),
            "RSI14 (Wilder)" to s.rsi.lastOrNull(), "MACD12/26" to s.macd.lastOrNull(),
            "MACD sinyal9" to s.signal.lastOrNull(), "MACD histogram" to s.histogram.lastOrNull(),
            "ATR14 (Wilder)" to s.atr.lastOrNull())
        metrics.forEach { (name, value) -> card(details, name, value?.let { number(it, 4) } ?: "Hesaplanamadı / yetersiz veri") }
        card(details, "HESAPLAMA / VERİ", "${bars.size} incelenen OHLCV mumu (en yeni mum hesap dışında) · ${period.label}\n" +
            "EMA ilk dönem SMA ile başlatılır. RSI/ATR Wilder düzeltmesi kullanır. VWAP İstanbul tarihine göre gün içinde sıfırlanır; günlük/haftalıkta gösterilmez. Haftalık son mum oluşuyor olabilir.\n" +
            "4 saatlik mumlar aynı günün kesintisiz dört saatinden oluşur; eksik grup çizilmez. Göstergeler seçili dönemden hesaplanır.")
        renderEverydayExplanation(details, state.candles)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable("chartCandles", ArrayList(state.candles.takeLast(2000)))
        outState.putString("period", period.name); outState.putBundle("viewState", chart.saveDisplayState()); super.onSaveInstanceState(outState)
    }
}

