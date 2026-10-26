package tr.borsatakip.v5.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tr.borsatakip.v5.BuildConfig
import tr.borsatakip.v5.data.FundamentalsBatchClient
import tr.borsatakip.v5.scan.FundamentalValuationEngine as Engine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Financial data is requested independently from OHLCV, using the existing source and engine. */
class ChartFinancialPanel(context: Context, symbol: String, private val market: String,
                          private val scope: CoroutineScope) : LinearLayout(context) {
    private val symbol = symbol.trim().uppercase(Locale.ROOT).removeSuffix(".IS")
    private val tr = Locale("tr", "TR")
    private val green = Color.rgb(0, 236, 145)
    private val amber = Color.rgb(255, 195, 75)
    private val muted = Color.rgb(167, 194, 200)
    private var started = false
    private var generation = 0L
    private var job: Job? = null
    private var assessment: Engine.Assessment? = null
    private var sourceNote = "Finansal kaynak henüz yüklenmedi."
    private var status = "FİNANSAL VERİ BEKLENİYOR"
    private val date = SimpleDateFormat("dd.MM.yyyy HH:mm", tr).apply { timeZone = TimeZone.getTimeZone("Europe/Istanbul") }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun number(value: Double?) = value?.takeIf(Double::isFinite)?.let { String.format(tr, "%.2f", it) } ?: "—"
    private fun text(parent: LinearLayout, value: String, size: Float = 13f, color: Int = muted, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value; textSize = size; setTextColor(color); setPadding(dp(10), dp(5), dp(10), dp(5))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            parent.addView(this, LayoutParams(-1, -2))
        }
    private fun box(parent: LinearLayout): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 29, 37)); setStroke(dp(1), Color.rgb(19, 65, 61)); cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(3), dp(5), dp(3), dp(7))
        parent.addView(this, LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }
    init {
        orientation = VERTICAL; tag = "chart-financial-panel"; setPadding(dp(5), dp(6), dp(5), dp(10))
        render()
    }

    fun load(force: Boolean = false) {
        if (started && !force) { render(); return }
        started = true; job?.cancel(); val request = ++generation
        assessment = null // Never retain an earlier ratio or peer chart across a refresh/error.
        sourceNote = "Grafik fiyatları finansal tablo yerine kullanılmaz."
        status = "FİNANSAL VERİ YÜKLENİYOR…"
        if (market != "BIST") {
            status = "VİOP · ŞİRKET ORANLARI UYGULANMAZ"
            sourceNote = "Sözleşme fiyatı, şirket piyasa değeri veya bilanço verisi değildir."
            render(); return
        }
        render()
        job = scope.launch {
            try {
                require(symbol.isNotBlank()) { "Missing symbol" }
                val client = FundamentalsBatchClient(context.applicationContext)
                val batch = withTimeout(40_000) { if (client.hasLocalImport()) client.loadLocal() else client.load() }
                val result = withContext(Dispatchers.Default) {
                    Engine.scan((batch.items.map { it.symbol } + symbol).distinct(), batch.items, System.currentTimeMillis())
                        .first { it.symbol == symbol }
                }
                if (request != generation) return@launch
                assessment = result
                sourceNote = batch.sourceNote + "\nFinansal kaynak fiyatı ile grafikteki mum fiyatı farklı tarihlere ait olabilir."
                status = "FİNANSAL KAYNAK · CANLI FİYAT DEĞİLDİR"
                render()
            } catch (cancel: CancellationException) {
                if (cancel is TimeoutCancellationException && request == generation) {
                    assessment = null; status = "FİNANSAL VERİ ALINAMADI"
                    sourceNote = "İstek zaman aşımına uğradı. Önceki oranlar ve sektör grafikleri temizlendi."; render()
                } else throw cancel
            } catch (_: Exception) {
                if (request != generation) return@launch
                assessment = null; status = "FİNANSAL VERİ ALINAMADI"
                sourceNote = "Geçerli finansal JSON veya yetkili HTTPS finansal kaynak gerekli. Önceki oranlar ve sektör grafikleri temizlendi."
                render()
            }
        }
    }

    private fun comparison(parent: LinearLayout, c: Engine.Comparison) {
        val panel = LinearLayout(context).apply { orientation = VERTICAL; tag = "financial-comparison" }
        parent.addView(panel, LayoutParams(-1, -2))
        text(panel, "${c.metric} · ${c.peerCount} karşılaştırılabilir emsal", 13f, green, true)
        val ceiling = maxOf(c.own, c.median) * 1.1
        listOf(Triple("Şirket", c.own, green), Triple("Sektör medyanı", c.median, Color.rgb(133, 159, 170))).forEach { (label, value, color) ->
            text(panel, "$label: ${number(value)}×", 12f)
            panel.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = (value / ceiling * 1000).toInt().coerceIn(0, 1000)
                progressTintList = android.content.res.ColorStateList.valueOf(color)
                contentDescription = "$label ${c.metric}: ${number(value)}"
            }, LayoutParams(-1, dp(8)).apply { leftMargin = dp(10); rightMargin = dp(10); bottomMargin = dp(5) })
        }
        val difference = (c.own / c.median - 1) * 100
        text(panel, "Medyana göre fark: ${String.format(tr, "%+.1f", difference)}%\nEmsaller: ${c.peerSymbols.joinToString()}", 11f)
    }

    private fun render() {
        removeAllViews()
        val model = ChartFinancialCards.build(symbol, market, assessment, System.currentTimeMillis())
        text(this, "FİNANSAL ANALİZ · $symbol", 17f, green, true)
        text(this, "${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}", 10f)
        text(this, status, 12f, amber, true)
        text(this, sourceNote, 11f)
        val tiles = model.metrics.map { Triple(it.key, it.value?.let { value -> number(value) + "×" } ?: "—", "financial-value-${it.key}") } +
            Triple("SEKTÖR KIYASI", model.snapshot?.sector ?: if (model.applicable) "Veri bekleniyor" else "Uygulanmaz", "financial-sector")
        tiles.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            addView(row, LayoutParams(-1, -2))
            pair.forEach { (title, value, valueTag) ->
                val tile = LinearLayout(context).apply {
                    orientation = VERTICAL; minimumHeight = dp(88)
                    background = GradientDrawable().apply { setColor(Color.rgb(4, 29, 37)); setStroke(dp(1), green); cornerRadius = dp(10).toFloat() }
                }
                row.addView(tile, LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(5)) })
                text(tile, title, 12f, green, true)
                text(tile, value, if (valueTag == "financial-sector") 14f else 23f, Color.WHITE, true).apply {
                    tag = valueTag; contentDescription = "$title değeri: $value"
                }
            }
        }
        val conclusion = box(this)
        text(conclusion, model.conclusion, 14f, green, true)
        text(conclusion, model.reason, 12f)
        model.metrics.forEach { metric ->
            val card = box(this)
            text(card, metric.title, 14f, green, true)
            text(card, metric.formula, 13f, Color.WHITE)
            text(card, metric.meaning, 12f)
            text(card, metric.comment, 12f)
            model.comparisons.firstOrNull { it.metric == metric.key }?.let { comparison(card, it) }
        }
        val sector = box(this).apply { tag = "financial-sector-card" }
        text(sector, "SEKTÖRÜNE GÖRE KIYASLA", 15f, green, true)
        text(sector, model.snapshot?.let { "${it.sector} · ${it.accountingBasis} · ${it.financialCurrency}\nAynı sektör, işletme türü ve finansal dönem." }
            ?: if (model.applicable) "Sektör ve karşılaştırılabilir finansal veri bekleniyor." else "Şirket emsal kıyası VİOP sözleşmesinde uygulanmaz.", 12f)
        if (model.comparisons.isEmpty()) text(sector, "Kıyas için en az ${Engine.MIN_PEERS} geçerli emsal gerekir. Eksik veriyle sektör ortalaması veya iskonto üretilmez.", 12f)
        else model.comparisons.forEach { comparison(sector, it) }
        model.snapshot?.let { s ->
            text(this, "Finansal dönem: ${date.format(Date(s.financialPeriodEndMs))}\nFiyat gözlem zamanı: ${date.format(Date(s.marketObservedAtMs))}\nBildirim: ${date.format(Date(s.disclosurePublishedAtMs))}\nKAP: ${s.disclosureUrl}", 11f)
        }
        text(this, "Finansal oranlar seçili mum döneminden hesaplanmaz. Bu kıyas hedef fiyat veya alış/satış sinyali değildir.", 11f)
        addView(Button(context).apply {
            text = "FİNANSALLARI YENİLE"; isAllCaps = false; setTextColor(green); minimumHeight = dp(44)
            setOnClickListener { load(force = true) }
        }, LayoutParams(-1, dp(48)))
    }
}
