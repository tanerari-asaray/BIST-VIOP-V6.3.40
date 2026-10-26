package tr.borsatakip.v5.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.TrendUiStyle
import tr.borsatakip.v5.model.Candle
import kotlin.math.max

class ViopSparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var values: List<Double> = emptyList()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = context.getColor(R.color.green)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x2200F080
    }
    private val linePath = Path()
    private val fillPath = Path()

    fun setCandles(candles: List<Candle>) {
        values = candles.takeLast(42).mapNotNull { it.close.takeIf(Double::isFinite) }
        invalidate()
    }

    fun setDirection(direction: String) {
        val color = when {
            direction.equals("SHORT", ignoreCase = true) -> context.getColor(R.color.red)
            direction.equals("LONG", ignoreCase = true) -> context.getColor(R.color.green)
            else -> context.getColor(R.color.text_secondary)
        }
        setTrendColor(color)
    }

    /** Colors the sparkline from the verified technical trend, independently of LONG/SHORT. */
    fun setTrendColor(color: Int) {
        linePaint.color = color
        linePaint.strokeWidth = 3f
        fillPaint.color = Color.argb(34, Color.red(color), Color.green(color), Color.blue(color))
        invalidate()
    }

    fun setTrendStyle(trend: TrendUiStyle) {
        linePaint.color = trend.color
        linePaint.strokeWidth = trend.sparklineWidthDp * resources.displayMetrics.density
        fillPaint.color = (trend.sparklineFillAlpha shl 24) or (trend.color and 0x00FFFFFF)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return
        val inset = linePaint.strokeWidth / 2f
        val left = paddingLeft + inset
        val right = width - paddingRight - inset
        val top = paddingTop + inset
        val bottom = height - paddingBottom - inset
        if (right <= left || bottom <= top) return
        var minV = values.minOrNull() ?: return
        var maxV = values.maxOrNull() ?: return
        if (maxV <= minV) {
            minV -= 1.0
            maxV += 1.0
        }
        val span = max(1e-9, maxV - minV)
        val dx = (right - left) / (values.size - 1)
        fun y(v: Double): Float = bottom - (((v - minV) / span) * (bottom - top)).toFloat()
        val path = linePath.apply { rewind() }
        values.forEachIndexed { i, v ->
            val x = left + dx * i
            val yy = y(v)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        val fill = fillPath.apply { set(path) }
        fill.lineTo(right, bottom)
        fill.lineTo(left, bottom)
        fill.close()
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
