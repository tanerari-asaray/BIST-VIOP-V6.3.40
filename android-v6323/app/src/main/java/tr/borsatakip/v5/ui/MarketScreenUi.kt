package tr.borsatakip.v5.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import tr.borsatakip.v5.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Styling is deliberately scoped to the two market lists, not the shared app theme. */
internal object MarketScreenUi {
    fun chip(button: Button, selected: Boolean, short: Boolean = false) {
        val context = button.context
        val accent = context.getColor(if (short) R.color.red else R.color.market_mint)
        button.backgroundTintList = null
        val shape = GradientDrawable().apply {
            cornerRadius = 24 * context.resources.displayMetrics.density
            setColor(if (selected) accent else context.getColor(R.color.market_surface))
            setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                if (selected) accent else context.getColor(R.color.market_line))
        }
        button.background = RippleDrawable(ColorStateList.valueOf(0x3356F1C1), shape, null)
        button.setTextColor(if (selected && !short) context.getColor(R.color.market_bg)
            else if (selected) Color.WHITE else context.getColor(R.color.market_muted))
        button.isSelected = selected
    }

    private fun neutralButtons(view: View) {
        if (view is Button) chip(view, false)
        if (view is ViewGroup) for (index in 0 until view.childCount) neutralButtons(view.getChildAt(index))
    }

    fun bottomNav(activity: Activity, activeId: Int) {
        neutralButtons(activity.findViewById(android.R.id.content))
        listOf(R.id.stocksEmptyAction, R.id.viopEmptyAction, R.id.refresh).forEach { id ->
            activity.findViewById<Button>(id)?.setTextColor(activity.getColor(R.color.market_mint))
        }
        val labels = listOf(R.id.navHome to "⌂\nAna Sayfa", R.id.navBist to "▥\nHisseler",
            R.id.navViop to "Ⅱ\nVİOP", R.id.navFav to "☆\nFavoriler", R.id.navSettings to "⚙\nAyarlar")
        activity.findViewById<View>(R.id.bottomNav).setBackgroundColor(activity.getColor(R.color.market_bg))
        labels.forEach { (id, label) ->
            activity.findViewById<TextView>(id).apply {
                text = SpannableString(label).apply {
                    setSpan(RelativeSizeSpan(1.8f), 0, label.indexOf('\n'), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                textSize = 10f
                setTextColor(activity.getColor(if (id == activeId) R.color.market_mint else R.color.market_muted))
                setTypeface(null, if (id == activeId) Typeface.BOLD else Typeface.NORMAL)
                contentDescription = label.substringAfter('\n') + if (id == activeId) ", seçili" else ""
                isSelected = id == activeId
            }
        }
    }

    fun details(view: TextView, title: String) {
        view.setOnClickListener {
            AlertDialog.Builder(view.context).setTitle(title).setMessage(view.text)
                .setPositiveButton("Kapat", null).show()
        }
    }

    fun notice(view: TextView, title: String, message: String) {
        view.text = message
        AlertDialog.Builder(view.context).setTitle(title).setMessage(message)
            .setPositiveButton("Tamam", null).show()
    }

    fun sortMenu(anchor: View, current: MarketListOrder, select: (MarketListOrder) -> Unit) {
        PopupMenu(anchor.context, anchor).apply {
            MarketListOrder.entries.forEach { order ->
                menu.add(0, order.ordinal, order.ordinal, order.label).apply {
                    isCheckable = true
                    isChecked = order == current
                }
            }
            setOnMenuItemClickListener { item -> select(MarketListOrder.entries[item.itemId]); true }
            show()
        }
    }

    fun stamp(label: String, timestamp: Long): String = if (timestamp > 0)
        "$label · ${SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(timestamp))}" else "$label · Zaman yok"
}
