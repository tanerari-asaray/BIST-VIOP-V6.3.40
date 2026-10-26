package tr.borsatakip.v5.ui

import android.content.res.ColorStateList
import android.widget.Button
import tr.borsatakip.v5.R
import androidx.core.view.ViewCompat

/** The button follows the chart, including restored state and fullscreen hand-off. */
object SupportResistanceControl {
    fun bind(button: Button, chart: PriceChartView) {
        fun refresh(enabled: Boolean) {
            button.text = "S/D"
            button.contentDescription = "S/D"
            button.isSelected = enabled
            button.isActivated = enabled
            button.alpha = 1f
            button.backgroundTintList = ColorStateList.valueOf(button.context.getColor(if (enabled) R.color.green else R.color.chip_bg))
            button.setTextColor(button.context.getColor(if (enabled) R.color.chip_bg else R.color.white))
            ViewCompat.setStateDescription(button, if (enabled) "Destek ve direnç açık" else "Destek ve direnç kapalı")
        }
        chart.onSupportResistanceChanged = ::refresh
        button.setOnClickListener { chart.showSupportResistance = !chart.showSupportResistance }
        refresh(chart.showSupportResistance)
    }
}
