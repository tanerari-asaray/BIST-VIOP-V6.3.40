package tr.borsatakip.v5.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.analysis.ViopV6Policy
import tr.borsatakip.v5.analysis.TrendUiPolicy
import tr.borsatakip.v5.data.ViopQuoteDisplayPolicy
import java.util.Locale

class ViopOpportunityAdapter(
    private val items: List<ViopOpportunity>,
    private val assessedAtMs: Long = System.currentTimeMillis(),
    private val click: ((ViopOpportunity) -> Unit)? = null
) : RecyclerView.Adapter<ViopOpportunityAdapter.H>() {
    class H(v: View) : RecyclerView.ViewHolder(v) {
        val symbol: TextView = v.findViewById(R.id.viopSymbol)
        val klass: TextView = v.findViewById(R.id.viopClass)
        val subtitle: TextView = v.findViewById(R.id.viopSubtitle)
        val direction: TextView = v.findViewById(R.id.viopDirection)
        val scores: TextView = v.findViewById(R.id.viopScores)
        val levels: TextView = v.findViewById(R.id.viopLevels)
        val reason: TextView = v.findViewById(R.id.viopReason)
        val detail: TextView = v.findViewById(R.id.viopDetailButton)
        val chart: ViopSparklineView = v.findViewById(R.id.viopMiniChart)
        val price: TextView = v.findViewById(R.id.viopPrice)
        val change: TextView = v.findViewById(R.id.viopChange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        H(LayoutInflater.from(parent.context).inflate(R.layout.item_viop_opportunity, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: H, position: Int) {
        val x = items[position]
        val context = holder.itemView.context
        val missing = context.getString(R.string.market_row_missing)
        val q = x.quote
        val quoteDisplay = ViopQuoteDisplayPolicy.from(q, assessedAtMs)
        val c = x.contract
        val isLong = x.direction.equals("LONG", true)
        val isShort = x.direction.equals("SHORT", true)
        // Quote price may differ from the closed-bar decision snapshot; the trend
        // and its color must use the decision-timeframe technical evidence.
        val trend = TrendUiPolicy.resolve(x.decisionPrice, x.technical, x.decisionState)
        val trendColor = TrendRowStyle.apply(holder.itemView, trend, compact = true)
        val assessment = ViopV6Policy.assess(x, assessedAtMs)
        val verified = assessment.eligible && x.validity == SignalValidity.VALID &&
            x.decisionState.name == "VERIFIED_OPPORTUNITY"

        holder.symbol.text = c.symbol.uppercase(Locale.ROOT)
        holder.symbol.setTextColor(if (trend.strengthLevel > 0) trendColor else holder.itemView.context.getColor(R.color.market_ink))
        holder.subtitle.text = context.getString(R.string.viop_row_subtitle, c.underlying.uppercase(Locale.ROOT), c.expiry)
        holder.subtitle.setTextColor(holder.itemView.context.getColor(R.color.market_muted))
        holder.klass.text = if (verified) context.getString(R.string.viop_row_quality,
            tr.borsatakip.v5.BuildConfig.VERSION_NAME, assessment.quality)
            else context.getString(R.string.viop_row_watch_reason, assessment.reason)
        holder.klass.setTextColor(
            when {
                verified && x.finalScore >= 70 -> Color.parseColor("#00F080")
                verified && x.finalScore >= 55 -> Color.parseColor("#FFC928")
                else -> Color.parseColor("#AFC2D4")
            }
        )

        holder.price.text = q.price.takeIf { it.isFinite() && it > 0.0 }?.let(::formatPrice) ?: missing
        holder.change.text = quoteDisplay.changePct?.let { pct -> "%+.2f%%".format(pct) } ?: missing
        holder.change.setTextColor(
            when {
                quoteDisplay.changeDirection > 0 -> Color.parseColor("#00F080")
                quoteDisplay.changeDirection < 0 -> Color.parseColor("#FF5A66")
                else -> Color.parseColor("#AFC2D4")
            }
        )
        holder.chart.setTrendStyle(trend)
        holder.chart.setCandles(x.candles)

        val signalLabel = context.getString(when {
            verified && isLong -> R.string.viop_row_signal_long
            verified && isShort -> R.string.viop_row_signal_short
            else -> R.string.viop_row_signal_watch
        })
        holder.direction.text = context.getString(R.string.viop_row_direction, trend.arrow, trend.label, signalLabel)
        holder.direction.setTextColor(trendColor)

        val origin = context.getString(when {
            quoteDisplay.isLive -> R.string.viop_row_live
            q.realtime -> R.string.viop_row_stale
            else -> R.string.viop_row_delayed
        })
        holder.scores.text = MarketScreenUi.stamp(origin, q.exchangeTimestamp)
        holder.levels.text = context.getString(R.string.viop_row_levels,
            q.volume?.takeIf { it.isFinite() && it >= 0.0 }?.let(::formatCompact) ?: missing,
            q.openInterest?.takeIf { it >= 0L }?.toString() ?: missing)
        holder.reason.text = context.getString(R.string.viop_row_manual_reason, assessment.reason)
        holder.detail.setText(R.string.market_row_more)
        holder.detail.contentDescription = context.getString(R.string.viop_row_detail, c.symbol)
        holder.itemView.contentDescription = context.getString(R.string.viop_row_description,
            holder.symbol.text, holder.subtitle.text, holder.price.text, holder.change.text,
            holder.direction.text, holder.scores.text, holder.levels.text, holder.klass.text)

        val tap = View.OnClickListener { click?.invoke(x) }
        holder.itemView.setOnClickListener(tap)
        holder.chart.setOnClickListener(tap)
        holder.detail.setOnClickListener(tap)
    }

    private fun formatPrice(v: Double) = if (v >= 1000.0) "%,.2f".format(v) else "%.2f".format(v)
    private fun formatCompact(v: Double) = when {
        v >= 1_000_000 -> "%.1f M".format(v / 1_000_000.0)
        v >= 1_000 -> "%.1f K".format(v / 1_000.0)
        else -> "%.0f".format(v)
    }
}
