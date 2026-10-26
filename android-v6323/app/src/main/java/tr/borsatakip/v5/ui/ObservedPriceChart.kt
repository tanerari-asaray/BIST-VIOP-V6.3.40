package tr.borsatakip.v5.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import tr.borsatakip.v5.data.FullUniversePolicy
import tr.borsatakip.v5.data.ObservationAnalysis
import tr.borsatakip.v5.model.Candle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ObservedPriceChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var compact = false
    private var bars = emptyList<Candle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pricePath = Path()
    private val averagePath = Path()
    private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR"))
    private val dateValue = Date(0)
    private var averages = DoubleArray(0)
    private var low = 0.0
    private var high = 0.0
    private var maxVolume = 1.0
    private var lineColor = Color.rgb(132, 150, 168)
    private var highLabel = ""
    private var lowLabel = ""
    private var startLabel = ""
    private var endLabel = ""

    constructor(context: Context, compact: Boolean) : this(context) {
        this.compact = compact
    }

    fun setBars(raw: List<Candle>) {
        bars = ObservationAnalysis.clean(raw)
        contentDescription = "Gerçek fiyat geçmişi, ${bars.size} mum"
        // Data-derived values are rebuilt on updates, rather than each draw frame.
        low = bars.minOfOrNull { it.low } ?: 0.0
        high = bars.maxOfOrNull { it.high } ?: 0.0
        maxVolume = bars.maxOfOrNull { it.volume }?.takeIf { it > 0 } ?: 1.0
        highLabel = String.format(Locale.ROOT, "%.2f", high)
        lowLabel = String.format(Locale.ROOT, "%.2f", low)
        lineColor = when (FullUniversePolicy.trend(bars.map { it.close })) {
            FullUniversePolicy.Trend.UP -> Color.rgb(0, 232, 135)
            FullUniversePolicy.Trend.DOWN -> Color.rgb(255, 86, 102)
            FullUniversePolicy.Trend.FLAT -> Color.rgb(18, 215, 215)
            else -> Color.rgb(132, 150, 168)
        }
        averages = if (compact) DoubleArray(0) else DoubleArray(bars.size) { i ->
            if (i < 19) Double.NaN else bars.subList(i - 19, i + 1).map { it.close }.average()
        }
        dateFormat.timeZone = java.util.TimeZone.getDefault()
        fun label(timestamp: Long?): String {
            if (timestamp == null) return ""
            dateValue.time = timestamp
            return dateFormat.format(dateValue)
        }
        startLabel = label(bars.firstOrNull()?.timestamp)
        endLabel = label(bars.lastOrNull()?.timestamp)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.size < 2) return
        val density = resources.displayMetrics.density
        val left = if (compact) 4f * density else 40f * density
        val right = width - 8f * density
        val top = 10f * density
        val bottom = if (compact) height - 4f * density else height * 0.68f
        if (right <= left || bottom <= top) return
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val start = bars.first().timestamp
        val duration = (bars.last().timestamp - start).coerceAtLeast(1)
        fun x(bar: Candle) = left + (right - left) * (bar.timestamp - start).toFloat() / duration
        fun y(value: Double) = bottom - (bottom - top) * ((value - low) / span).toFloat()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = lineColor
        pricePath.rewind()
        bars.forEachIndexed { i, bar ->
            if (i == 0) pricePath.moveTo(x(bar), y(bar.close)) else pricePath.lineTo(x(bar), y(bar.close))
        }
        canvas.drawPath(pricePath, paint)
        if (!compact && bars.size >= 20) {
            averagePath.rewind()
            for (i in 19 until bars.size) {
                if (i == 19) averagePath.moveTo(x(bars[i]), y(averages[i]))
                else averagePath.lineTo(x(bars[i]), y(averages[i]))
            }
            paint.color = Color.rgb(255, 210, 28)
            paint.strokeWidth = density
            canvas.drawPath(averagePath, paint)
        }
        if (!compact) {
            paint.style = Paint.Style.FILL
            paint.textSize = 10f * density * resources.configuration.fontScale
            paint.color = Color.LTGRAY
            canvas.drawText(highLabel, 0f, top + 10f * density, paint)
            canvas.drawText(lowLabel, 0f, bottom, paint)
            canvas.drawText(startLabel, left, height - 4f * density, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(endLabel, right, height - 4f * density, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.argb(160, 18, 215, 215)
            val base = height - 23f * density
            val volumeHeight = (height * 0.22f).coerceAtLeast(1f)
            val volumeWidth = maxOf(1f, (right - left) / bars.size * 0.6f)
            bars.forEach { bar ->
                val bx = x(bar)
                canvas.drawRect(bx, base - volumeHeight * (bar.volume / maxVolume).toFloat(), bx + volumeWidth, base, paint)
            }
            canvas.drawText("Hacim • Sarı: SMA20", left, bottom + 12f * density, paint)
        }
    }
}
