package tr.borsatakip.v5.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.BackendHttpDiagnostic
import tr.borsatakip.v5.data.StockQuoteRefreshDiagnostics
import tr.borsatakip.v5.data.StockQuoteRefreshResult

internal object StocksScreenPresentation {
    fun quoteFailureMessage(context: Context, result: StockQuoteRefreshResult): String {
        val groups = StockQuoteRefreshDiagnostics.groups(result)
        val messages = groups.take(4).map { group ->
            val reason = group.httpEvidence?.let(BackendHttpDiagnostic::describe) ?: context.getString(when (group.code) {
                "BACKEND_URL_MISSING" -> R.string.stock_quote_config_url_missing
                "INVALID_HTTPS" -> R.string.stock_quote_config_url_invalid
                "API_KEY_MISSING" -> R.string.stock_quote_config_key_missing
                "AUTH_ERROR" -> R.string.stock_quote_auth_error
                "RATE_LIMIT" -> R.string.stock_quote_rate_limit
                "TIMEOUT" -> R.string.stock_quote_timeout
                "SCHEMA_ERROR" -> R.string.stock_quote_schema_error
                "NO_QUOTE_DATA" -> R.string.stock_quote_no_data
                else -> R.string.stock_quote_network_error
            })
            context.resources.getQuantityString(R.plurals.stock_quote_failure_group, group.symbols.size, group.symbols.size, reason,
                group.code, group.symbols.take(3).joinToString(", ") + if (group.symbols.size > 3) "…" else "")
        }.toMutableList()
        if (groups.size > 4) messages += context.getString(R.string.stock_quote_more_groups, groups.size - 4)
        messages += context.getString(R.string.stock_quote_failure_footer)
        return messages.joinToString("\n\n")
    }

    fun age(timestamp: Long, now: Long = System.currentTimeMillis()): String = when {
        timestamp <= 0 || timestamp > now -> "zaman doğrulanmadı"
        now - timestamp < 60_000 -> "1 dk'dan az"
        now - timestamp < 3_600_000 -> "${(now - timestamp) / 60_000} dk"
        else -> "${(now - timestamp) / 3_600_000} sa ${((now - timestamp) / 60_000) % 60} dk"
    }

    fun dialog(context: Context, title: String, message: String) {
        val heading = TextView(context).apply {
            text = title; textSize = 18f; setTextColor(context.getColor(R.color.market_ink))
            val p = (24 * resources.displayMetrics.density).toInt(); setPadding(p, p, p, p / 2)
        }
        val dialog = AlertDialog.Builder(context).setCustomTitle(heading).setMessage(message).setPositiveButton("Tamam", null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(context.getColor(R.color.market_surface)))
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(context.getColor(R.color.market_ink))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(context.getColor(R.color.market_mint))
        }
        dialog.show()
    }
}
