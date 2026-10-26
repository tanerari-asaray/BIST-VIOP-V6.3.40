package tr.borsatakip.v5.ui

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.analysis.OpportunityTrendPresentation
import java.util.Locale

class StocksCompactAdapter(
    private var items: List<Opportunity>,
    private var favoriteSymbols: Set<String>,
    private val click: (Opportunity) -> Unit,
    private val toggleFavorite: (Opportunity) -> Unit
) : RecyclerView.Adapter<StocksCompactAdapter.Holder>() {
    private val expandedCompanySymbols = mutableSetOf<String>()
    fun submit(next: List<Opportunity>, favorites: Set<String>) {
        val old = items; val oldFavorites = favoriteSymbols
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(a: Int, b: Int) = old[a].symbol == next[b].symbol
            override fun areContentsTheSame(a: Int, b: Int) = old[a] == next[b] &&
                (old[a].symbol in oldFavorites) == (next[b].symbol in favorites)
        })
        items = next; favoriteSymbols = favorites; diff.dispatchUpdatesTo(this)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val favorite: TextView = view.findViewById(R.id.stockFavorite)
        val symbol: TextView = view.findViewById(R.id.stockSymbol)
        val company: TextView = view.findViewById(R.id.stockCompany)
        val direction: TextView = view.findViewById(R.id.stockDirection)
        val chart: StockSparklineView = view.findViewById(R.id.stockMiniChart)
        val price: TextView = view.findViewById(R.id.stockPrice)
        val change: TextView = view.findViewById(R.id.stockChange)
        val volume: TextView = view.findViewById(R.id.stockVolume)
        val more: TextView = view.findViewById(R.id.stockMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_stock_compact, parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val missing = context.getString(R.string.market_row_missing)
        val symbol = FavoriteRepository.normalizeSymbol(item.symbol)
        val isFavorite = favoriteSymbols.contains(symbol)
        val visual = OpportunityTrendPresentation.from(item)
        val changePct = tr.borsatakip.v5.data.StockQuotePolicy.change(item)?.takeIf(Double::isFinite)
        val trendColor = TrendRowStyle.apply(holder.itemView, visual.trend, compact = true)

        holder.favorite.setText(if (isFavorite) R.string.market_row_favorite_on else R.string.market_row_favorite_off)
        holder.favorite.setTextColor(if (isFavorite) holder.itemView.context.getColor(R.color.yellow) else holder.itemView.context.getColor(R.color.text_secondary))
        val favoriteAction = context.getString(if (isFavorite) R.string.market_row_favorite_remove else R.string.market_row_favorite_add)
        holder.favorite.setOnClickListener { toggleFavorite(item) }

        holder.symbol.text = symbol
        holder.symbol.setTextColor(if (visual.trend.strengthLevel > 0) trendColor else holder.itemView.context.getColor(R.color.market_ink))
        val verifiedName = item.companyName?.trim()?.takeIf { it.isNotBlank() }
        holder.company.text = verifiedName ?: context.getString(R.string.stock_row_company_missing)
        // No guessed company names; expansion only reveals the complete provider-supplied name.
        val initiallyExpanded = symbol in expandedCompanySymbols
        holder.company.maxLines = if (initiallyExpanded) Int.MAX_VALUE else 1
        holder.company.ellipsize = if (initiallyExpanded) null else TextUtils.TruncateAt.END
        holder.company.contentDescription = verifiedName?.let { context.getString(R.string.stock_row_company_description, it) }
            ?: context.getString(R.string.stock_row_company_unverified)
        holder.company.isClickable = verifiedName != null
        holder.company.isFocusable = verifiedName != null
        holder.company.setOnClickListener(if (verifiedName == null) null else View.OnClickListener {
            val expanded = if (symbol in expandedCompanySymbols) {
                expandedCompanySymbols.remove(symbol)
                false
            } else {
                expandedCompanySymbols.add(symbol)
                true
            }
            holder.company.maxLines = if (expanded) Int.MAX_VALUE else 1
            holder.company.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        })
        val dataLabel = if (visual.verifiedSignal) context.getString(R.string.stock_row_verified_label, visual.dataLabel, visual.signalLabel) else visual.dataLabel
        val timestamp = tr.borsatakip.v5.data.StockQuotePolicy.timestamp(item)
        holder.direction.text = context.getString(R.string.stock_row_direction, visual.trend.arrow, visual.trend.label,
            MarketScreenUi.stamp(dataLabel, timestamp), StocksScreenPresentation.age(timestamp))
        holder.direction.setTextColor(trendColor)
        holder.direction.contentDescription = context.getString(R.string.stock_row_direction_description,
            visual.dataLabel, visual.trend.label, visual.signalLabel)

        holder.chart.setCandles(item.candles, changePct)
        holder.chart.setTrendStyle(visual.trend)
        val closes = item.candles.takeLast(36).map { it.close }.filter { it.isFinite() }
        holder.itemView.findViewById<TextView>(R.id.stockMiniChartState).apply {
            setText(if (closes.size < 2) R.string.stock_row_no_candles else R.string.stock_row_flat_series)
            visibility = if (closes.size < 2 || closes.distinct().size == 1) View.VISIBLE else View.GONE
        }
        holder.price.text = tr.borsatakip.v5.data.StockQuotePolicy.price(item).takeIf { it.isFinite() && it > 0.0 }?.let(::formatPrice) ?: missing
        holder.change.text = changePct?.let { "%+.2f%%".format(Locale.getDefault(), it) } ?: missing
        holder.change.setTextColor(
            when {
                changePct == null -> holder.itemView.context.getColor(R.color.text_secondary)
                changePct > 0.0 -> holder.itemView.context.getColor(R.color.green)
                changePct < 0.0 -> holder.itemView.context.getColor(R.color.red)
                else -> holder.itemView.context.getColor(R.color.text_secondary)
            }
        )
        holder.volume.text = context.getString(R.string.stock_row_volume,
            item.technical.volumeRatio?.takeIf(Double::isFinite)?.let { "%.1fx".format(Locale.getDefault(), it) } ?: missing)
        holder.more.setText(R.string.market_row_more)
        holder.more.contentDescription = context.getString(R.string.stock_row_detail)

        holder.itemView.contentDescription = context.getString(R.string.stock_row_description, symbol,
            holder.company.text, holder.price.text, holder.change.text, holder.direction.text, holder.volume.text, visual.signalLabel)
        holder.favorite.contentDescription = context.getString(R.string.market_row_favorite_action, symbol, favoriteAction)
        val tap = View.OnClickListener { click(item) }
        holder.itemView.setOnClickListener(tap)
        holder.more.setOnClickListener(tap)
    }

    private fun formatPrice(value: Double): String = if (value >= 1000.0) {
        "%,.2f".format(Locale.getDefault(), value)
    } else {
        "%.2f".format(Locale.getDefault(), value)
    }
}
