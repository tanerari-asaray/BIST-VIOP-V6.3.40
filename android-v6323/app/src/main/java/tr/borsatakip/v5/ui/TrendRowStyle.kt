package tr.borsatakip.v5.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.TrendUiStyle

/** One bounded, static highlight for stock, scan, contract and underlying rows. */
internal object TrendRowStyle {
    fun apply(view: View, trend: TrendUiStyle, compact: Boolean = false): Int {
        val density = view.resources.displayMetrics.density
        val base = if (compact) view.context.getColor(R.color.market_bg) else Color.rgb(5, 28, 45)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (if (compact) 8f else 14f) * density
            setColor(ColorUtils.blendARGB(base, trend.color, trend.rowFillAlpha / 255f))
            setStroke((trend.borderWidthDp * density).toInt().coerceAtLeast(1),
                ColorUtils.setAlphaComponent(trend.color, trend.borderAlpha))
        }
        // Always overwrite recycled backgrounds, including neutral / insufficient states.
        // No animation, blur, elevation or changes to signal eligibility.
        return trend.color
    }
}
