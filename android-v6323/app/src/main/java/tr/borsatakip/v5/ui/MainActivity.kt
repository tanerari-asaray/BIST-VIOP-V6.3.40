package tr.borsatakip.v5.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tr.borsatakip.v5.BuildConfig
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.ProviderRouter
import tr.borsatakip.v5.data.RealtimeScannerClient
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.YahooFallbackProvider
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.analysis.OpportunityTrendPresentation
import tr.borsatakip.v5.model.Stock
import tr.borsatakip.v5.worker.RealtimeAlertService
import tr.borsatakip.v5.worker.AutoScanScheduler
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : BaseActivity() {
    private enum class TodayMode { ALL, LONG, SHORT }
    private var todayMode: TodayMode = TodayMode.ALL
    private val trLocale = Locale("tr", "TR")
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30_000L)
        }
    }

    private data class TileViews(val value: TextView, val change: TextView, val spark: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupBottomNav()

        findViewById<TextView>(R.id.navHome)?.apply {
            setTextColor(getColor(R.color.blue))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        findViewById<TextView>(R.id.txtVersionBadge).text = "V${BuildConfig.VERSION_NAME}"
        findViewById<View>(R.id.actionCheapStock).setOnClickListener { startActivity(Intent(this, CheapStockScanActivity::class.java)) }
        findViewById<View>(R.id.actionBist).setOnClickListener { startActivity(Intent(this, BistScanActivity::class.java)) }
        findViewById<View>(R.id.actionOpportunity).setOnClickListener { startActivity(Intent(this, OpportunityActivity::class.java)) }
        findViewById<View>(R.id.actionSignal).setOnClickListener { startActivity(Intent(this, SignalHistoryActivity::class.java)) }
        findViewById<View>(R.id.actionViop).setOnClickListener { startActivity(Intent(this, ViopActivity::class.java)) }
        findViewById<View>(R.id.actionWatchlist).setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        findViewById<View>(R.id.actionNotifications).setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }
        findViewById<View>(R.id.btnHeaderSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnMarketDetails).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnSeeAllOpportunities).setOnClickListener { startActivity(Intent(this, OpportunityActivity::class.java)) }
        bindMarketTileClicks()
        applyNeonMotion()
        bindTodayTabs()

        updateClock()
        refreshMarketSummary()
        renderTodayOpportunities()
        resetMarketTiles()

        handleRealtimeAlertIntent(intent)
        val startupSettings = SettingsStore(this)
        if (startupSettings.notifications) RealtimeAlertService.start(this)
        if (startupSettings.autoScanEnabled) AutoScanScheduler.reconcile(this, allowForegroundStart = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRealtimeAlertIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
        refreshMarketSummary()
        renderTodayOpportunities()
        refreshMarketTiles()
    }

    override fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        listOf(R.id.actionBist,R.id.actionOpportunity,R.id.actionSignal,R.id.actionViop,
            R.id.actionWatchlist,R.id.actionNotifications,R.id.actionCheapStock).forEach { id ->
            findViewById<View>(id)?.apply { animate().cancel();alpha=1f }
        }
        super.onPause()
    }

    /** Short decorative entrance only; respects disabled system animator scales. */
    private fun applyNeonMotion() {
        val animationsAllowed = android.provider.Settings.Global.getFloat(
            contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        if (!animationsAllowed) return
        val cards = listOf(R.id.actionBist, R.id.actionOpportunity, R.id.actionSignal,
            R.id.actionViop, R.id.actionWatchlist, R.id.actionNotifications, R.id.actionCheapStock)
        cards.forEachIndexed { index, id ->
            findViewById<View>(id)?.apply {
                alpha = 0.85f
                animate().alpha(1f).setStartDelay(index * 35L).setDuration(220L).start()
            }
        }
    }

    private fun handleRealtimeAlertIntent(sourceIntent: Intent?) {
        val symbol = sourceIntent?.getStringExtra(RealtimeAlertService.EXTRA_ALERT_SYMBOL)
            ?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (symbol.isBlank()) return
        sourceIntent?.removeExtra(RealtimeAlertService.EXTRA_ALERT_SYMBOL)
        lifecycleScope.launch {
            val snapshot = RealtimeScannerClient(this@MainActivity).load(limit = 1, symbols = listOf(symbol)).getOrNull()
            val opportunity = snapshot?.opportunities?.firstOrNull { it.symbol.equals(symbol, true) }
            if (opportunity != null) {
                AppSession.selected = opportunity
                startActivity(Intent(this@MainActivity, StockDetailActivity::class.java).putExtra("opportunity", opportunity))
            }
        }
    }

    private fun bindMarketTileClicks() {
        listOf(
            R.id.marketBist100Card to "BIST100",
            R.id.marketBist30Card to "BIST30",
            R.id.marketUsdCard to "USDTRY",
            R.id.marketGoldCard to "XAUUSD",
            R.id.marketViopCard to "VIOP30"
        ).forEach { (viewId, instrument) ->
            findViewById<View>(viewId).setOnClickListener {
                startActivity(Intent(this, MarketInstrumentDetailActivity::class.java).putExtra(MarketInstrumentDetailActivity.EXTRA_INSTRUMENT, instrument))
            }
        }
    }

    private fun bindTodayTabs() {
        val all = findViewById<TextView>(R.id.todayAllTab)
        val long = findViewById<TextView>(R.id.todayLongTab)
        val short = findViewById<TextView>(R.id.todayShortTab)
        all.setOnClickListener { todayMode = TodayMode.ALL; updateTodayTabStyles(); renderTodayOpportunities() }
        long.setOnClickListener { todayMode = TodayMode.LONG; updateTodayTabStyles(); renderTodayOpportunities() }
        short.setOnClickListener { todayMode = TodayMode.SHORT; updateTodayTabStyles(); renderTodayOpportunities() }
        updateTodayTabStyles()
    }

    private fun updateTodayTabStyles() {
        listOf(
            Triple(R.id.todayAllTab, TodayMode.ALL, R.color.blue),
            Triple(R.id.todayLongTab, TodayMode.LONG, R.color.green),
            Triple(R.id.todayShortTab, TodayMode.SHORT, R.color.red)
        ).forEach { (id, mode, colorRes) ->
            findViewById<TextView>(id).apply {
                val selected = todayMode == mode
                setTextColor(getColor(if (selected) colorRes else R.color.text_secondary))
                background = androidx.appcompat.content.res.AppCompatResources.getDrawable(this@MainActivity, if (selected) R.drawable.bg_chip_selected_blue else R.drawable.bg_chip_outline)
            }
        }
    }

    private fun updateClock() {
        val df = SimpleDateFormat("dd MMM yyyy • HH:mm", trLocale)
        findViewById<TextView>(R.id.txtHeaderDateTime).text = df.format(Date())
    }

    private fun refreshMarketSummary() {
        val s = SettingsStore(this)
        val label = s.lastProviderLabel.takeIf { it.isNotBlank() } ?: "Kaynak doğrulanmadı"
        val timeText = if (s.lastProviderTimestamp > 0L) {
            SimpleDateFormat("HH:mm:ss", trLocale).format(Date(s.lastProviderTimestamp))
        } else {
            "veri zamanı yok"
        }
        findViewById<TextView>(R.id.txtMarketSummary).text = "$label • $timeText • gecikme sonucu ilgili ekranda doğrulanır"
    }

    private fun marketTile(valueId: Int, changeId: Int, sparkId: Int) = TileViews(
        findViewById(valueId), findViewById(changeId), findViewById(sparkId)
    )

    private fun resetMarketTiles() {
        listOf(
            marketTile(R.id.marketBist100Value, R.id.marketBist100Change, R.id.marketBist100Spark),
            marketTile(R.id.marketBist30Value, R.id.marketBist30Change, R.id.marketBist30Spark),
            marketTile(R.id.marketUsdValue, R.id.marketUsdChange, R.id.marketUsdSpark),
            marketTile(R.id.marketGoldValue, R.id.marketGoldChange, R.id.marketGoldSpark),
            marketTile(R.id.marketViopValue, R.id.marketViopChange, R.id.marketViopSpark)
        ).forEach {
            it.value.text = "—"
            it.change.text = "Veri bekleniyor"
            it.change.setTextColor(getColor(R.color.text_muted))
            it.spark.text = "—"
            it.spark.setTextColor(getColor(R.color.text_muted))
        }
    }

    /**
     * Home cards never invent a quote. A tile is populated only when the configured provider returns
     * a validated result. Missing provider/data deliberately stays as an em dash.
     */
    private fun refreshMarketTiles() {
        val strict = ProviderRouter(this, experimentalFallbackOverride = false)
        val delayed = YahooFallbackProvider(this)
        val settings = SettingsStore(this)
        lifecycleScope.launch {
            coroutineScope {
                val bist100Deferred = async {
                    runCatching { strict.fetchOne("XU100") }.getOrNull()
                        ?: delayed.fetchExternalQuote("XU100", listOf("XU100.IS", "^XU100")).stock
                }
                val bist30Deferred = async {
                    runCatching { strict.fetchOne("XU030") }.getOrNull()
                        ?: delayed.fetchExternalQuote("XU030", listOf("XU030.IS", "^XU030")).stock
                }
                val usdDeferred = async {
                    delayed.fetchExternalQuote("USDTRY", listOf("TRY=X")).stock
                }
                val goldDeferred = async {
                    // Spot kartında vadeli altın yedeği kullanılmaz.
                    delayed.fetchExternalQuote("XAUUSD", listOf("XAUUSD=X")).stock
                }
                val viop30Deferred = async {
                    if (!tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl)) return@async null
                    val backend = BackendProvider(this@MainActivity)
                    val contracts = backend.loadViop().getOrNull().orEmpty()
                    val contract = contracts
                        .filter { it.underlying.equals("XU030", true) }
                        .filter { runCatching { java.time.YearMonth.parse(it.expiry) >= java.time.YearMonth.now() }.getOrDefault(true) }
                        .sortedBy { it.expiry }
                        .firstOrNull() ?: return@async null
                    backend.loadViopQuote(contract.symbol).getOrNull()?.takeIf { it.price.isFinite() && it.price > 0.0 }
                }

                bindStockTile(
                    marketTile(R.id.marketBist100Value, R.id.marketBist100Change, R.id.marketBist100Spark),
                    bist100Deferred.await()
                )
                bindStockTile(
                    marketTile(R.id.marketBist30Value, R.id.marketBist30Change, R.id.marketBist30Spark),
                    bist30Deferred.await()
                )
                bindStockTile(
                    marketTile(R.id.marketUsdValue, R.id.marketUsdChange, R.id.marketUsdSpark),
                    usdDeferred.await()
                )
                bindStockTile(
                    marketTile(R.id.marketGoldValue, R.id.marketGoldChange, R.id.marketGoldSpark),
                    goldDeferred.await()
                )
                bindQuoteTile(
                    marketTile(R.id.marketViopValue, R.id.marketViopChange, R.id.marketViopSpark),
                    viop30Deferred.await()
                )
            }
        }
    }

    private fun bindStockTile(tile: TileViews, stock: Stock?) {
        if (stock == null) {
            tile.value.text="—";tile.change.text="Veri alınamadı";tile.spark.text="Kaynak doğrulanmadı"
            return
        }
        val price = stock.quotePrice ?: stock.candles.lastOrNull()?.close ?: return
        val previous = stock.previousClose ?: stock.candles.dropLast(1).lastOrNull()?.close
        val change = previous?.takeIf { it > 0.0 }?.let { ((price - it) / it) * 100.0 }
        bindMarketTile(tile, price, change, stock.candles)
        tile.spark.text = (if(stock.isRealtime) "Güncellik doğrulanmalı" else "Gecikmeli") + " • " +
            (if(stock.dataTimestamp>0) java.text.SimpleDateFormat("HH:mm",trLocale).format(java.util.Date(stock.dataTimestamp)) else "Saat yok")
    }

    private fun bindQuoteTile(tile: TileViews, quote: tr.borsatakip.v5.model.ViopQuote?) {
        if (quote == null) {
            tile.value.text = "—"
            tile.change.text = "Veri yok"
            tile.change.setTextColor(getColor(R.color.text_muted))
            tile.spark.text = "—"
            tile.spark.setTextColor(getColor(R.color.text_muted))
            return
        }
        bindMarketTile(tile, quote.price, quote.dailyChangePct, emptyList())
    }

    private fun bindMarketTile(tile: TileViews, price: Double, changePct: Double?, candles: List<Candle>) {
        tile.value.text = NumberFormat.getNumberInstance(trLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(price)

        if (changePct == null || !changePct.isFinite()) {
            tile.change.text = "Değişim —"
            tile.change.setTextColor(getColor(R.color.text_muted))
        } else {
            val up = changePct > 0
            tile.change.text = "${if (up) "▲" else "▼"} ${String.format(trLocale, "%.2f%%", kotlin.math.abs(changePct))}"
            tile.change.setTextColor(getColor(if (up) R.color.green else R.color.red))
            tile.spark.setTextColor(getColor(if (up) R.color.green else R.color.red))
        }
        if(changePct==0.0) {
            tile.change.text="→ %0,00";tile.change.setTextColor(getColor(R.color.text_secondary))
            tile.spark.setTextColor(getColor(R.color.text_secondary))
        }
        tile.spark.text = sparkline(candles.map { it.close })
    }

    private fun renderTodayOpportunities() {
        val empty = findViewById<TextView>(R.id.todayEmpty)
        val horizontal = findViewById<HorizontalScrollView>(R.id.todayHorizontal)
        val container = findViewById<LinearLayout>(R.id.todayOpportunityRows)
        container.removeAllViews()

        // Only fresh, validated signals qualify as today's trade opportunities.
        // Delayed/unverified observations are never ranked as actionable trades.
        val all = AppSession.lastOpportunities
        val verified = all.filter { OpportunityTrendPresentation.from(it).verifiedSignal }
        val filtered = when (todayMode) {
            TodayMode.ALL -> verified
            TodayMode.LONG -> verified.filter { it.direction.equals("LONG", true) }
            TodayMode.SHORT -> verified.filter { it.direction.equals("SHORT", true) }
        }.sortedWith(compareByDescending<Opportunity> { it.rankingScore }.thenBy { it.riskScore }).take(8)

        // Monitoring is purposefully separate; LONG/SHORT tabs never display delayed rows.
        val observations = if (todayMode == TodayMode.ALL) all.filterNot {
            OpportunityTrendPresentation.from(it).verifiedSignal
        }.take(8) else emptyList()
        val noTrades = if (all.isEmpty()) "TARAMA SONUCU YOK • Tarama yapılmadı veya sonuçlar yüklenemedi; fırsat bulunamadı denemez." else if (verified.isEmpty()) "CANLI SİNYAL DOĞRULANAMADI • ${all.size} gözlem var; veri kaynağı, güncellik ve sinyal koşullarını kontrol edin." else when (todayMode) {
            TodayMode.ALL -> "Doğrulanmış canlı işlem fırsatı yok. Gecikmeli analizler işlem sinyali değildir."
            TodayMode.LONG -> "Doğrulanmış canlı LONG fırsatı yok."
            TodayMode.SHORT -> "Doğrulanmış canlı SHORT fırsatı yok."
        }
        empty.text = noTrades
        empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty() && observations.isEmpty()) {
            horizontal.visibility = View.GONE
            return
        }
        horizontal.visibility = View.VISIBLE
        if (filtered.isNotEmpty()) {
            container.addView(sectionHeading("DOĞRULANMIŞ CANLI FIRSATLAR (${filtered.size})"))
            container.addView(buildOpportunityHeader())
            filtered.forEachIndexed { index, x -> container.addView(buildOpportunityRow(index + 1, x, index)) }
        }
        if (observations.isNotEmpty()) {
            container.addView(sectionHeading("GECİKMELİ / DOĞRULANMAMIŞ TEKNİK İZLEME • İŞLEM SİNYALİ DEĞİL"))
            container.addView(buildOpportunityHeader())
            observations.forEachIndexed { index, x -> container.addView(buildOpportunityRow(index + 1, x, index)) }
        }
    }

    private fun sectionHeading(message: String): View = TextView(this).apply {
        text = message
        setTextColor(getColor(R.color.text_secondary))
        textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(10), dp(12), dp(8), dp(8))
    }

    private fun buildOpportunityHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.rgb(3, 34, 53))
        }
        listOf(
            "#" to 30, "Hisse" to 82, "Puan" to 58, "Sinyal" to 150,
            "Risk" to 72, "Son Fiyat" to 94, "Değişim" to 92, "Mini Grafik" to 112
        ).forEach { (label, width) -> row.addView(tableText(label, width, true)) }
        return row
    }

    private fun buildOpportunityRow(rank: Int, x: Opportunity, index: Int): View {
        val visual = OpportunityTrendPresentation.from(x)
        val trend = visual.trend
        val trendColor = Color.rgb(trend.red, trend.green, trend.blue)
        val verified = visual.verifiedSignal
        val directionLabel = visual.signalLabel
        val signalColor = if (verified) {
            getColor(if (x.direction.equals("SHORT", true)) R.color.red else R.color.green)
        } else getColor(R.color.text_secondary)
        val base = if (index % 2 == 0) Color.rgb(3, 29, 46) else Color.rgb(4, 37, 57)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(4), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(androidx.core.graphics.ColorUtils.blendARGB(base, trendColor, 0.13f))
                setStroke(dp(1), Color.argb(165, Color.red(trendColor), Color.green(trendColor), Color.blue(trendColor)))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "${x.symbol} • ${visual.dataLabel} • teknik trend ${trend.label} • ${directionLabel} • puan ${visual.scoreLabel}"
            setOnClickListener {
                AppSession.selected = x
                startActivity(Intent(this@MainActivity, StockDetailActivity::class.java).putExtra("opportunity", x))
            }
        }
        row.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(39)).apply { rightMargin = dp(4) }
            setBackgroundColor(trendColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        row.addView(tableText(rank.toString(), 23))
        row.addView(tableText(x.symbol.uppercase(Locale.ROOT), 82, true, trendColor))
        row.addView(tableText(visual.scoreLabel, 58, true, signalColor))
        row.addView(tableText(directionLabel, 150, true, signalColor).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.argb(30, Color.red(signalColor), Color.green(signalColor), Color.blue(signalColor)))
                setStroke(dp(1), Color.argb(130, Color.red(signalColor), Color.green(signalColor), Color.blue(signalColor)))
            }
            maxLines = 2
            textSize = 10f
            minHeight = dp(36)
            gravity = Gravity.CENTER
        })
        row.addView(tableText(if (verified) "${x.riskScore}/100" else "—", 72, true, signalColor))
        row.addView(tableText(String.format(trLocale, "%.2f", x.price), 94))
        val change = x.dailyChangePct?.takeIf { it.isFinite() }
        val movementText = change?.let { value ->
            "${if (value >= 0) "▲" else "▼"} ${String.format(trLocale, "%.2f%%", kotlin.math.abs(value))}"
        } ?: "—"
        // Price change and trading signal direction are different things.
        // Movement has its own colour; it does not change trend or signal eligibility.
        val movementColor = when {
            change == null -> getColor(R.color.text_secondary)
            change > 0.0 -> getColor(R.color.green)
            change < 0.0 -> getColor(R.color.red)
            else -> getColor(R.color.text_secondary)
        }
        row.addView(tableText(movementText, 92, true, movementColor))
        row.addView(tableText(sparkline(x.candles.map { it.close }), 112, true, trendColor))
        return row
    }

    private fun tableText(text: String, widthDp: Int, bold: Boolean = false, color: Int = getColor(R.color.text_primary)): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
            this.text = text
            setTextColor(color)
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            setPadding(dp(4), 0, dp(4), 0)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun sparkline(raw: List<Double>): String {
        val values = raw.filter { it.isFinite() }.takeLast(14)
        if (values.size < 2) return "—"
        val min = values.minOrNull() ?: return "—"
        val max = values.maxOrNull() ?: return "—"
        if (max <= min) return "▅".repeat(values.size)
        val chars = charArrayOf('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')
        return values.joinToString("") { value ->
            val i = (((value - min) / (max - min)) * (chars.size - 1)).roundToInt().coerceIn(0, chars.lastIndex)
            chars[i].toString()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
