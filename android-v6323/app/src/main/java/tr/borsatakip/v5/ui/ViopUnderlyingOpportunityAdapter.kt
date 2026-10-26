package tr.borsatakip.v5.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ViopUnderlyingScanner
import tr.borsatakip.v5.analysis.OpportunityTrendPresentation
import tr.borsatakip.v5.model.DataMode
import java.util.Locale

class ViopUnderlyingOpportunityAdapter(
    private val items: List<ViopUnderlyingScanner.Candidate>,
    private val onOpen: (ViopUnderlyingScanner.Candidate) -> Unit
) : RecyclerView.Adapter<ViopUnderlyingOpportunityAdapter.H>() {
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
        val o = x.underlying
        val trend = OpportunityTrendPresentation.from(o).trend
        val trendColor = TrendRowStyle.apply(holder.itemView, trend, compact = true)

        holder.symbol.text = x.contract.underlying.uppercase(Locale.ROOT)
        holder.symbol.setTextColor(if (trend.strengthLevel > 0) trendColor else holder.itemView.context.getColor(R.color.market_ink))
        holder.subtitle.text = o.companyName?.trim()?.takeIf { it.isNotBlank() } ?: context.getString(R.string.underlying_row_company_missing)
        holder.subtitle.setTextColor(holder.itemView.context.getColor(R.color.market_muted))
        holder.klass.text = if (o.rankingScore > 0) context.getString(R.string.underlying_row_score, o.rankingScore.coerceIn(0, 100))
            else context.getString(R.string.underlying_row_score_missing)
        holder.klass.setTextColor(Color.parseColor("#FFC928"))

        holder.price.setText(R.string.underlying_row_viop_missing)
        holder.change.text = context.getString(R.string.underlying_row_change,
            o.dailyChangePct?.takeIf(Double::isFinite)?.let { "%+.2f%%".format(it) } ?: missing)
        holder.change.setTextColor(Color.parseColor("#AFC2D4"))
        holder.chart.setTrendStyle(trend)
        holder.chart.setCandles(o.candles)

        holder.direction.text = context.getString(R.string.underlying_row_direction, trend.arrow, trend.label)
        holder.direction.setTextColor(trendColor)

        val modeLabel = when (o.dataMode) {
            DataMode.DELAYED, DataMode.EOD -> context.getString(R.string.underlying_row_delayed)
            else -> OpportunityTrendPresentation.from(o).dataLabel
        }
        holder.scores.text = MarketScreenUi.stamp(context.getString(R.string.underlying_row_origin, modeLabel), o.exchangeTimestamp)
        holder.scores.setTextColor(holder.itemView.context.getColor(R.color.market_amber))
        holder.levels.setText(R.string.underlying_row_contract_missing)
        holder.reason.text = context.getString(R.string.underlying_row_reason, o.signalValidityReason)
        holder.detail.setText(R.string.market_row_more)
        holder.detail.contentDescription = context.getString(R.string.underlying_row_detail, holder.symbol.text)
        holder.itemView.contentDescription = context.getString(R.string.underlying_row_description,
            holder.symbol.text, holder.subtitle.text, holder.direction.text, holder.scores.text, holder.change.text, holder.reason.text)
        holder.chart.setOnClickListener { onOpen(x) }
        holder.detail.setOnClickListener { onOpen(x) }
        holder.itemView.setOnClickListener { onOpen(x) }
    }

}
