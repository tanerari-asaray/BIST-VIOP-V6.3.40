package tr.borsatakip.v5.ui

import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.analysis.OpportunityTrendPresentation
import tr.borsatakip.v5.analysis.TrendUiState
import tr.borsatakip.v5.data.StocksReferences
import tr.borsatakip.v5.data.StocksReferenceRepository
import tr.borsatakip.v5.data.SettingsStore
import java.util.Locale

class StocksActivity : BaseActivity() {
    private lateinit var repo: FavoriteRepository
    private lateinit var list: RecyclerView
    private lateinit var status: TextView
    private var query: String = ""
    private var filter: UiFilter = UiFilter.ALL
    private var order = MarketListOrder.SOURCE
    private var renderGeneration = 0
    private var bist30Only = false
    private var sector: String? = null
    private var stockQuoteRequest: Job? = null
    private var stockQuoteLoader: suspend (List<String>, (Int, Int) -> Unit) -> tr.borsatakip.v5.data.StockQuoteRefreshResult = { symbols, progress ->
        StocksReferenceRepository(this).refreshStockQuotes(symbols, progress)
    }
    private var references = StocksReferences()
    private var referenceRequest: Job? = null
    private var referenceLoading = false
    private var referenceLoader: suspend () -> StocksReferences = { StocksReferenceRepository(this).refresh() }
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshDataLabels = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                renderReferenceNotice()
                if (filter == UiFilter.ALL && !bist30Only && sector == null) list.adapter?.notifyDataSetChanged()
                else lifecycleScope.launch {
                    val scroll = list.layoutManager?.onSaveInstanceState()
                    bindList()
                    list.adapter?.notifyDataSetChanged()
                    list.layoutManager?.onRestoreInstanceState(scroll)
                }
                refreshHandler.postDelayed(this, nextLabelRefreshDelay())
            }
        }
    }

    private fun nextLabelRefreshDelay(): Long {
        val now = System.currentTimeMillis()
        val expiry = AppSession.lastOpportunities.asSequence()
            .filter { it.dataMode == DataMode.REALTIME && it.exchangeTimestamp in (now - 60_000L)..now }
            .map { 60_001L - (now - it.exchangeTimestamp) }.minOrNull()
        return minOf(15_000L, expiry ?: 15_000L).coerceAtLeast(1_000L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stocks)
        setupBottomNav()
        MarketScreenUi.bottomNav(this, R.id.navBist)

        filter = UiFilter.entries.firstOrNull { it.name == savedInstanceState?.getString("marketFilter") } ?: UiFilter.ALL
        order = MarketListOrder.entries.firstOrNull { it.name == savedInstanceState?.getString("marketOrder") } ?: MarketListOrder.SOURCE
        query = savedInstanceState?.getString("marketQuery").orEmpty()
        bist30Only = savedInstanceState?.getBoolean("bist30Only") ?: false
        sector = savedInstanceState?.getString("marketSector")
        findViewById<EditText>(R.id.stocksSearch).setText(query)
        repo = FavoriteRepository.get(this)
        list = findViewById(R.id.stocksList)
        status = findViewById(R.id.stocksStatus)
        list.layoutManager = LinearLayoutManager(this)

        findViewById<TextView>(R.id.stocksFavorites).setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        findViewById<TextView>(R.id.stocksSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.stocksNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.stocksScanCard).setOnClickListener {
            startActivity(Intent(this, BistScanActivity::class.java))
        }
        findViewById<TextView>(R.id.stocksScanAction).setOnClickListener {
            startActivity(Intent(this, BistScanActivity::class.java))
        }

        bindSearch()
        bindFilters()
        status.setOnClickListener { StocksScreenPresentation.dialog(this, "Hisse listesi durumu", status.text.toString()) }
        findViewById<View>(R.id.stocksDataRefresh).setOnClickListener { loadReferences() }
        findViewById<View>(R.id.stocksQuoteRefresh).setOnClickListener { refreshStockPrices() }
        findViewById<View>(R.id.stocksIndexNotice).setOnClickListener {
            StocksScreenPresentation.dialog(this, "Kaynak ve güncellik", referenceDetails())
        }
        findViewById<View>(R.id.stocksSort).setOnClickListener { anchor ->
            MarketScreenUi.sortMenu(anchor, order) { selected ->
                order = selected
                lifecycleScope.launch { bindList() }
            }
        }
        findViewById<Button>(R.id.stocksEmptyAction).setOnClickListener {
            if (AppSession.lastOpportunities.isEmpty()) startActivity(Intent(this, BistScanActivity::class.java))
            else {
                filter = UiFilter.ALL
                bist30Only = false; sector = null
                findViewById<EditText>(R.id.stocksSearch).setText("")
                updateFilterColors()
                lifecycleScope.launch {
                    bindList()
                    findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.marketAppBar).setExpanded(true, false)
                }
            }
        }
        renderIndexCards()

        lifecycleScope.launch {
            repo.migrateLegacyIfNeeded()
            bindList()
        }
        val settings = SettingsStore(this)
        if ((settings.experimentalProvidersEnabled && settings.yahooFallbackEnabled) ||
            (tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl) && settings.apiKey.isNotBlank() && settings.lastProviderState == "PROVIDER_READY")) loadReferences()
    }

    private fun bindSearch() {
        findViewById<EditText>(R.id.stocksSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty().trim()
                lifecycleScope.launch { bindList() }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun bindFilters() {
        listOf(
            R.id.stocksFilterAll to UiFilter.ALL,
            R.id.stocksFilterLong to UiFilter.LONG,
            R.id.stocksFilterShort to UiFilter.SHORT,
            R.id.stocksFilterTrendUp to UiFilter.TREND_UP,
            R.id.stocksFilterTrendDown to UiFilter.TREND_DOWN
        ).forEach { (id, selectedFilter) ->
            findViewById<Button>(id).setOnClickListener {
                filter = selectedFilter
                if (selectedFilter == UiFilter.ALL) { bist30Only = false; sector = null }
                updateFilterColors()
                lifecycleScope.launch { bindList() }
            }
        }

        val unsupported = listOf(
            R.id.stocksFilterBank to "Bankacılık",
            R.id.stocksFilterIndustry to "Sanayi",
            R.id.stocksFilterTech to "Teknoloji"
        )
        findViewById<Button>(R.id.stocksFilterBist30).setOnClickListener {
            if (references.directory?.usable() == true) {
                bist30Only = !bist30Only
                updateFilterColors()
                lifecycleScope.launch { bindList() }
            } else {
                status.text = "BIST 30 üyelik verisi doğrulanmadı. Yenile ile KAP üyeliğini alın."
                StocksScreenPresentation.dialog(this, "BIST 30 filtresi kullanılamıyor", status.text.toString())
            }
        }
        unsupported.forEach { (id, label) ->
            findViewById<Button>(id).setOnClickListener {
                val code = when (id) { R.id.stocksFilterBank -> "XUBNK"; R.id.stocksFilterIndustry -> "XUSIN"; else -> "XUTEK" }
                if (references.directory?.takeIf { it.usable() }?.memberships?.get(code)?.isNotEmpty() == true) {
                    sector = if (sector == code) null else code
                    updateFilterColors(); lifecycleScope.launch { bindList() }
                } else {
                    status.text = "$label KAP endeks üyeliği alınamadı. Endeks/üyelik yenile ile tekrar deneyin."
                    StocksScreenPresentation.dialog(this, "$label filtresi kullanılamıyor", status.text.toString())
                }
            }
        }
        updateFilterColors()
    }

    private fun updateFilterColors() {
        val buttons = listOf(
            UiFilter.ALL to findViewById<Button>(R.id.stocksFilterAll),
            UiFilter.LONG to findViewById<Button>(R.id.stocksFilterLong),
            UiFilter.SHORT to findViewById<Button>(R.id.stocksFilterShort),
            UiFilter.TREND_UP to findViewById<Button>(R.id.stocksFilterTrendUp),
            UiFilter.TREND_DOWN to findViewById<Button>(R.id.stocksFilterTrendDown)
        )
        buttons.forEach { (kind, button) ->
            val selected = kind == filter
            MarketScreenUi.chip(button, selected, kind == UiFilter.SHORT)
        }
        listOf(R.id.stocksFilterBank to "XUBNK", R.id.stocksFilterIndustry to "XUSIN", R.id.stocksFilterTech to "XUTEK").forEach { (id, code) ->
            MarketScreenUi.chip(findViewById(id), sector == code)
        }
        findViewById<Button>(R.id.stocksFilterBist30).apply {
            text = if (references.directory?.usable() == true) "BIST 30" else "BIST 30 · ?"
            MarketScreenUi.chip(this, bist30Only)
        }
    }

    private suspend fun bindList() {
        if (!::repo.isInitialized || !::list.isInitialized) return
        val generation = ++renderGeneration
        val directory = references.directory?.takeIf { it.usable() }
        val source = AppSession.lastOpportunities.map { item ->
            if (item.companyName.isNullOrBlank()) item.copy(companyName = directory?.names?.get(item.symbol)) else item
        }
        val normalizedQuery = query.lowercase(Locale.getDefault())
        val filtered = source.filter { item ->
            val searchMatch = normalizedQuery.isBlank() || item.symbol.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                item.companyName.orEmpty().lowercase(Locale.getDefault()).contains(normalizedQuery)
            val filterMatch = when (filter) {
                UiFilter.ALL -> true
                UiFilter.LONG -> OpportunityTrendPresentation.from(item).verifiedSignal && item.direction.equals("LONG", ignoreCase = true)
                UiFilter.SHORT -> OpportunityTrendPresentation.from(item).verifiedSignal && item.direction.equals("SHORT", ignoreCase = true)
                UiFilter.TREND_UP -> OpportunityTrendPresentation.from(item).trend.state in setOf(TrendUiState.BULLISH, TrendUiState.STRONG_BULLISH)
                UiFilter.TREND_DOWN -> OpportunityTrendPresentation.from(item).trend.state in setOf(TrendUiState.BEARISH, TrendUiState.STRONG_BEARISH)
            }
            searchMatch && filterMatch && (!bist30Only || directory?.bist30?.contains(item.symbol) == true) &&
                (sector == null || directory?.memberships?.get(sector)?.contains(item.symbol) == true)
        }
        val sorted = order.apply(filtered, { it.symbol }, { tr.borsatakip.v5.data.StockQuotePolicy.change(it) }, { tr.borsatakip.v5.data.StockQuotePolicy.price(it) })
        val favorites = repo.symbols()
        if (generation != renderGeneration) return
        val existing = list.adapter as? StocksCompactAdapter
        if (existing != null) existing.submit(sorted, favorites)
        else list.adapter = StocksCompactAdapter(
            sorted,
            favorites,
            click = { item ->
                AppSession.selected = item
                startActivity(Intent(this, StockDetailActivity::class.java).putExtra("opportunity", item))
            },
            toggleFavorite = { item ->
                lifecycleScope.launch {
                    repo.toggle(item.symbol, item.companyName)
                    bindList()
                }
            }
        )

        status.text = when {
            source.isEmpty() -> "TARAMA SONUCU YOK • BIST taramasını başlatın; fırsat bulunamadığı henüz doğrulanmadı."
            bist30Only && directory == null -> "BIST 30 üyelik doğrulamasının süresi doldu. Yenile ile üyeliği yeniden alın."
            filtered.isEmpty() && filter in setOf(UiFilter.LONG, UiFilter.SHORT) -> "${source.size} gözlem · 0 doğrulanmış ${filter.label} sinyali. Gecikmeli teknik yön için Teknik ↑ / ↓ seçin."
            filtered.isEmpty() -> "${source.size} gözlem içinde aramaya uyan kayıt yok."
            else -> "${filtered.size} sonuç • ${filter.label}${if (bist30Only) " · BIST 30" else ""} • hisse detayını açmak için dokunun"
        }
        findViewById<View>(R.id.stocksEmptyState).visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.stocksEmptyTitle).text = if (source.isEmpty()) "Hisse listenizi oluşturun" else "Eşleşen kayıt yok"
        findViewById<TextView>(R.id.stocksEmptyMessage).text = when {
            source.isEmpty() -> "BIST taraması tamamlandığında sonuçlar burada görünür."
            filter in setOf(UiFilter.LONG, UiFilter.SHORT) -> "Bu filtrede doğrulanmış canlı sinyal yok. Gecikmeli teknik yönü Teknik ↑ / ↓ ile inceleyin."
            bist30Only && directory == null -> "Üyelik doğrulanmadan BIST 30 listesi gösterilmez."
            filter in setOf(UiFilter.TREND_UP, UiFilter.TREND_DOWN) -> "Seçilen teknik yönde yeterli veriyle hesaplanmış kayıt yok. Bu filtre işlem sinyali üretmez."
            else -> "Aramayı temizleyerek tüm gözlemlere dönün."
        }
        findViewById<Button>(R.id.stocksEmptyAction).text = if (source.isEmpty()) "Taramayı aç  ›" else "Filtreleri temizle"
        renderIndexCards()
        renderReferenceNotice()
        updateFilterColors()
    }

    private fun renderIndexCards() {
        bindIndexCard("XU100", R.id.index100Value, R.id.index100Change, R.id.index100Chart)
        bindIndexCard("XU030", R.id.index30Value, R.id.index30Change, R.id.index30Chart)
        bindIndexCard("XUTUM", R.id.indexAllValue, R.id.indexAllChange, R.id.indexAllChart)
    }

    private fun bindIndexCard(symbol: String, valueId: Int, changeId: Int, chartId: Int) {
        val item = references.indices[symbol]?.takeIf { it.valid(symbol) }
        val value = findViewById<TextView>(valueId)
        val change = findViewById<TextView>(changeId)
        val chart = findViewById<StockSparklineView>(chartId)
        value.setOnClickListener(null)
        val pct = item?.changePct?.takeIf(Double::isFinite)
        if (item == null || !item.price.isFinite() || item.price <= 0.0) {
            value.text = "—"
            value.contentDescription = "Endeks verisi yok"
            change.text = "—"
            change.setTextColor(getColor(R.color.text_secondary))
            chart.setCandles(emptyList(), null)
            return
        }
        value.contentDescription = MarketScreenUi.stamp(item.source, item.exchangeTimestamp)
        value.text = if (item.price >= 1000.0) "%,.2f".format(Locale.getDefault(), item.price) else "%.2f".format(Locale.getDefault(), item.price)
        change.text = pct?.let { "%+.2f%%".format(Locale.getDefault(), it) } ?: "—"
        change.setTextColor(
            when {
                pct == null -> getColor(R.color.text_secondary)
                pct > 0.0 -> getColor(R.color.green)
                pct < 0.0 -> getColor(R.color.red)
                else -> getColor(R.color.text_secondary)
            }
        )
        chart.setCandles(emptyList(), null)
        value.setOnClickListener { StocksScreenPresentation.dialog(this, symbol,
            "${item.source}\nVeri zamanı: ${MarketScreenUi.stamp("", item.exchangeTimestamp)}\nVeri yaşı: ${StocksScreenPresentation.age(item.exchangeTimestamp)}\n${if (item.live()) "Canlı olarak bildirildi" else "Canlı fiyat değildir"}") }
    }

    private fun refreshStockPrices() {
        if (stockQuoteRequest?.isActive == true) return
        val symbols = AppSession.lastOpportunities.map { it.symbol }
        if (symbols.isEmpty()) { status.text = "Önce hisse taramasını çalıştırın."; return }
        stockQuoteRequest = lifecycleScope.launch {
            val button = findViewById<View>(R.id.stocksQuoteRefresh); button.isEnabled = false
            try {
                val result = stockQuoteLoader(symbols) { done, total ->
                    runOnUiThread { status.text = "Hisse fiyatları yenileniyor $done/$total • analiz kararı korunuyor" }
                }
                AppSession.lastOpportunities = AppSession.lastOpportunities.map { item ->
                    result.quotes[item.symbol]?.let { item.copy(displayQuote = it) } ?: item
                }
                bindList()
                status.text = getString(R.string.stock_quote_refresh_summary, result.quotes.size, symbols.size, result.issues.size)
                if (result.issues.isNotEmpty()) StocksScreenPresentation.dialog(this@StocksActivity,
                    getString(R.string.stock_quote_result_title), StocksScreenPresentation.quoteFailureMessage(this@StocksActivity, result))
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                status.text = getString(R.string.stock_quote_refresh_failed, tr.borsatakip.v5.data.StockQuotePolicy.failureCode(error))
            } finally { button.isEnabled = true }
        }
    }

    private fun loadReferences() {
        if (referenceRequest?.isActive == true) return
        referenceLoading = true
        references = StocksReferences(message = "Üyelik ve 3 endeks yükleniyor…")
        renderIndexCards(); renderReferenceNotice()
        lifecycleScope.launch { bindList() }
        referenceRequest = lifecycleScope.launch {
            try { references = referenceLoader() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { references = StocksReferences(message = "Veri alınamadı. Önceki endeks fiyatları temizlendi; Yenile ile tekrar deneyin.") }
            finally {
                referenceLoading = false
                bindList()
            }
        }
    }

    private fun renderReferenceNotice() {
        val missing = 3 - references.indices.count { (symbol, value) -> value.valid(symbol) }
        findViewById<TextView>(R.id.stocksIndexNotice).text = when {
            referenceLoading -> "Üyelik ve 3 endeks yükleniyor…"
            missing == 3 -> "Endeks verisi yok · 3 endeks için fiyat alınamadı. ${references.message}"
            else -> references.message + " · ${references.indices.values.count { !it.live() }} canlı olmayan endeks · ayrıntı için dokunun"
        }
        findViewById<View>(R.id.stocksDataRefresh).apply { isEnabled = !referenceLoading; alpha = if (referenceLoading) 0.4f else 1f }
        findViewById<Button>(R.id.stocksDataRefresh).setTextColor(getColor(R.color.market_mint))
        val records = AppSession.lastOpportunities
        val latest = records.maxOfOrNull { it.exchangeTimestamp } ?: 0L
        findViewById<TextView>(R.id.stocksDataSummary).text = if (records.isEmpty()) "Henüz fiyat gözlemi yok" else
            "${records.count { OpportunityTrendPresentation.from(it).verifiedSignal }} canlı onaylı · ${records.count { it.dataMode == DataMode.DELAYED || it.dataMode == DataMode.EOD }} gecikmeli · en yeni kayıt ${StocksScreenPresentation.age(latest)} önce"
    }

    private fun referenceDetails(): String = references.message + "\n" +
        (references.directory?.let { "KAP üyelik/şirket unvanı alınış: ${MarketScreenUi.stamp("", it.fetchedAt)}; ${if (it.usable()) "geçerli" else "süresi doldu"}" } ?: "KAP üyeliği doğrulanmadı") +
        "\n" + references.indices.values.joinToString("\n") { "${it.symbol}: ${it.source} · ${StocksScreenPresentation.age(it.exchangeTimestamp)} önce · ${if (it.realtime) "canlı bildirimi" else "canlı değildir"}" } +
        "\n" + references.issues.entries.joinToString("\n") { "${it.key}: ${it.value}" } +
        "\nGecikmeli teknik yön, canlı LONG/SHORT sinyalinden ayrıdır."

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("marketFilter", filter.name)
        outState.putString("marketOrder", order.name)
        outState.putString("marketQuery", query)
        outState.putBoolean("bist30Only", bist30Only)
        outState.putString("marketSector", sector)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized && ::list.isInitialized) {
            lifecycleScope.launch { bindList() }
        }
        refreshHandler.removeCallbacks(refreshDataLabels)
        refreshHandler.postDelayed(refreshDataLabels, nextLabelRefreshDelay())
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshDataLabels)
        super.onPause()
    }

    private enum class UiFilter(val label: String) {
        ALL("Tümü"), LONG("Canlı LONG"), SHORT("Canlı SHORT"), TREND_UP("Teknik ↑"), TREND_DOWN("Teknik ↓")
    }
}
