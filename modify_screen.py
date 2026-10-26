from pathlib import Path
p=Path('/mnt/data/bist_build668/android-v6323/app/src/main/java/tr/borsatakip/v5/ui/FullscreenChartActivity.kt')
s=p.read_text()
s=s.replace('import android.content.Context\n','import android.content.Context\nimport android.content.ActivityNotFoundException\nimport android.net.Uri\n')
s=s.replace('import android.widget.TextView\n','import android.widget.TextView\nimport android.widget.Toast\n')
s=s.replace('import tr.borsatakip.v5.analysis.OhlcvResampler\n','import tr.borsatakip.v5.analysis.OhlcvResampler\nimport tr.borsatakip.v5.analysis.ReadableStockAnalysis\n')
s=s.replace('    private lateinit var periodStrip: HorizontalScrollView\n','''    private lateinit var periodStrip: HorizontalScrollView
    private lateinit var trendPanel: LinearLayout
    private lateinit var readableBottom: LinearLayout
    private var trendJob: Job? = null
    private var trendSequence = 0L
    private val trendReadings = mutableMapOf<ChartTimeframe, ReadableStockAnalysis.Snapshot?>()
    private val trendPending = mutableSetOf<ChartTimeframe>()
    private val comparisonPeriods = listOf(ChartTimeframe.FIVE_MIN, ChartTimeframe.TEN_MIN,
        ChartTimeframe.ONE_HOUR, ChartTimeframe.ONE_DAY)
''')
s=s.replace('ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN,','ChartTimeframe.FIVE_MIN, ChartTimeframe.TEN_MIN, ChartTimeframe.FIFTEEN_MIN,')
s=s.replace('''        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {''','''        refreshTrendComparisons()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {''')
s=s.replace('''        root.addView(titleRow)
        notice = label''','''        root.addView(titleRow)
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
        notice = label''')
s=s.replace('''        val chartPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val periods = row()''','''        val chartPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chartScroll = ScrollView(this).apply { isFillViewport = false }
        chartScroll.addView(chartPage)
        trendPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chartPage.addView(trendPanel)
        val periods = row()''')
s=s.replace('''        chartPage.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f))''','''        chartPage.addView(chart, LinearLayout.LayoutParams(-1, dp(390)))''')
s=s.replace('''        chartPage.addView(tools)
        pages.addView(chartPage)''','''        chartPage.addView(tools)
        readableBottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chartPage.addView(readableBottom)
        pages.addView(chartScroll)''')
anchor='''    private fun renderAnalysis() {'''
more='''    private fun openTradingView() {
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
                        val candles = withTimeout(35_000) { fetch(tf) }
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
                val accent = when (value?.direction) {
                    ReadableStockAnalysis.Direction.RISING -> green
                    ReadableStockAnalysis.Direction.FALLING -> red
                    ReadableStockAnalysis.Direction.MIXED -> muted
                    null -> amber
                }
                val status = when {
                    pending -> "Veri kontrol ediliyor"
                    value == null -> "Veri yok · Yorum yapılmadı"
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
                }
                line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
        }
        val resolved = trendReadings.values.filterNotNull()
        if (resolved.size >= 2 && resolved.map { it.direction }.distinct().size > 1) {
            neonCard(trendPanel, "⚠ ZAMAN DİLİMLERİ FARKLI YÖNDE",
                "Kısa ve uzun dönem aynı şeyi söylemiyor. Kısa vadeli toparlanma, günlük düşüşün bittiği anlamına gelmez.", amber)
        }
        trendPanel.addView(label("Her dönem yalnızca kendi gerçek mumlarıyla hesaplanır. Son mum hariç tutulur. Canlı işlem sinyali değildir.", 11f))
    }

    private fun renderEverydayExplanation(parent: LinearLayout, bars: List<Candle>) {
        parent.removeAllViews()
        parent.addView(label("💡 BU HİSSEYİ KOLAYCA ANLAYALIM", 17f).apply {
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
        neonCard(parent, "Teknik kelimeler ne anlama geliyor?", analysis.plainIndicatorNote.ifBlank {
            "Gösterge hesaplamak için yeterli karşılaştırma verisi yok."
        }, Color.rgb(188, 134, 255))
        neonCard(parent, "Dikkat edilmesi gerekenler",
            "Borsada fiyatlar haber, şirket açıklamaları, hacim ve genel piyasa koşullarıyla değişebilir. " +
                "Bu grafik, şirketin ucuz ya da pahalı olduğunu tek başına göstermez. Şirketin finansal sonuçları ayrıca incelenmelidir.", red)
        parent.addView(label("Kaynak: $source · İncelenen mum başlangıcı: ${date.format(Date(analysis.sampleTime))}. " +
            "En yeni mum, kapanışı doğrulanmadığı için dışarıda bırakıldı. " +
            "Veri gecikmeli veya güncelliği doğrulanmamış olabilir. Bu bilgiler yatırım tavsiyesi ya da işlem emri değildir.", 11f))
    }

'''
assert anchor in s
s=s.replace(anchor,more+anchor)
s=s.replace('''        summary.removeAllViews(); details.removeAllViews()
        val bars = state.candles''','''        summary.removeAllViews(); details.removeAllViews()
        val bars = state.candles
        renderEverydayExplanation(readableBottom, bars)''')
s=s.replace('''            card(details, "GÖSTERGELER", "Yeterli veri olmadan değer veya sinyal gösterilmez.")
            return''','''            card(details, "GÖSTERGELER", "Yeterli veri olmadan değer veya sinyal gösterilmez.")
            renderEverydayExplanation(details, bars)
            return''')
s=s.replace('''        card(details, "HESAPLAMA / VERİ", "${bars.size} gerçek OHLCV mumu · ${period.label}\\n" +''','''        card(details, "HESAPLAMA / VERİ", "${bars.size} gerçek OHLCV mumu · ${period.label}\\n" +''')
s=s.replace('''            "4 saatlik mumlar aynı günün kesintisiz dört saatinden oluşur; eksik grup çizilmez. Göstergeler seçili dönemden hesaplanır.")
    }
    override fun onSaveInstanceState''','''            "4 saatlik mumlar aynı günün kesintisiz dört saatinden oluşur; eksik grup çizilmez. Göstergeler seçili dönemden hesaplanır.")
        renderEverydayExplanation(details, bars)
    }
    override fun onSaveInstanceState''')
p.write_text(s)
print('modified',len(s),'characters')
