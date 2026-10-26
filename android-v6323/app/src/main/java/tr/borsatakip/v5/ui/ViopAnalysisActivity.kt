package tr.borsatakip.v5.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ViopAnalysisCatalog
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Complete provider response, independent of dashboard filters, scan scores and watchlists. */
class ViopAnalysisActivity : BaseActivity() {
    private var catalogLoader: suspend () -> Result<List<ViopContract>> = { BackendProvider(this).loadViop() }
    private var request: Job? = null
    private var contracts = emptyList<ViopContract>()
    private var query = ""
    private var kind = ViopAnalysisCatalog.Kind.ALL
    private var expiry: String? = null
    private var problem: String? = null
    private var needsSettings = false
    private var returningFromSettings = false
    private var busy = false
    private lateinit var rows: ContractAdapter
    private lateinit var expiryPicker: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viop_analysis)
        setupBottomNav()
        query = savedInstanceState?.getString("query").orEmpty()
        kind = savedInstanceState?.getString("kind")?.let {
            runCatching { ViopAnalysisCatalog.Kind.valueOf(it) }.getOrNull()
        } ?: ViopAnalysisCatalog.Kind.ALL
        expiry = savedInstanceState?.getString("expiry")
        returningFromSettings = savedInstanceState?.getBoolean("returningFromSettings") ?: false
        rows = ContractAdapter { contract ->
            // A catalog entry must never inherit another contract's quote, candles or signal.
            AppSession.selectedViopOpportunity = null
            AppSession.selectedViopContract = contract
            startActivity(Intent(this, ViopDetailActivity::class.java))
        }
        findViewById<RecyclerView>(R.id.analysisList).apply {
            layoutManager = LinearLayoutManager(this@ViopAnalysisActivity)
            adapter = rows
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        findViewById<View>(R.id.analysisBack).setOnClickListener { finish() }
        findViewById<View>(R.id.analysisSettings).setOnClickListener { openSettings() }
        findViewById<View>(R.id.analysisRefresh).setOnClickListener { loadCatalog() }
        findViewById<View>(R.id.analysisClear).setOnClickListener { clearFilters() }
        findViewById<Button>(R.id.analysisEmptyAction).setOnClickListener {
            when { needsSettings -> openSettings(); problem != null -> loadCatalog(); else -> clearFilters() }
        }
        findViewById<EditText>(R.id.analysisSearch).apply {
            setText(query)
            doAfterTextChanged { query = it.toString(); render() }
        }
        listOf(R.id.analysisAll to ViopAnalysisCatalog.Kind.ALL,
            R.id.analysisFutures to ViopAnalysisCatalog.Kind.FUTURE,
            R.id.analysisOptions to ViopAnalysisCatalog.Kind.OPTION).forEach { (id, value) ->
            findViewById<View>(id).setOnClickListener { kind = value; render() }
        }
        expiryPicker = findViewById(R.id.analysisExpiry)
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        if (returningFromSettings) { returningFromSettings = false; loadCatalog() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putString("kind", kind.name)
        outState.putString("expiry", expiry)
        outState.putBoolean("returningFromSettings", returningFromSettings)
        super.onSaveInstanceState(outState)
    }

    private fun openSettings() {
        returningFromSettings = true
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_RETURN_TO_VIOP, true))
    }

    private fun loadCatalog() {
        if (request?.isActive == true) return
        // Clear at request start and on failure. An old universe must not look like a successful refresh.
        contracts = emptyList()
        problem = null
        needsSettings = !tr.borsatakip.v5.data.BackendUrlPolicy.isValid(SettingsStore(this).baseUrl.trim())
        busy = !needsSettings
        findViewById<TextView>(R.id.analysisSource).text = "Sağlayıcı sözleşmeleri · tüm vadeler"
        if (needsSettings) {
            problem = "Gerçek VİOP sözleşme listesi için veri sağlayıcı bağlantısını kurun. BIST dayanakları ve yerel örnekler bu listeye eklenmez."
            updateExpiries(); render(); return
        }
        render()
        request = lifecycleScope.launch {
            try {
                val source = catalogLoader().getOrThrow()
                contracts = ViopAnalysisCatalog.available(source)
                val time = SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR")).apply {
                    timeZone = TimeZone.getTimeZone("Europe/Istanbul")
                }.format(Date())
                findViewById<TextView>(R.id.analysisSource).text =
                    "Son alınış $time · sağlayıcı: ${source.size} kayıt\nBorsa genelinde eksiksiz kapsam doğrulanmadı. Fiyat/sinyal listesi değildir."
                if (contracts.isEmpty()) problem = "Sağlayıcı gösterilebilir sözleşme döndürmedi. Vadesi veya son işlem zamanı geçmiş kayıtlar listelenmez."
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                contracts = emptyList()
                problem = "Sözleşmeler alınamadı. Bağlantıyı kontrol edip yeniden deneyin. Önceki liste temizlendi."
            } finally {
                busy = false
                updateExpiries()
                render()
            }
        }
    }

    private fun clearFilters() {
        query = ""; kind = ViopAnalysisCatalog.Kind.ALL; expiry = null
        findViewById<EditText>(R.id.analysisSearch).setText("")
        expiryPicker.setSelection(0)
        render()
    }

    private fun updateExpiries() {
        // Keep a restored selection while loading; reconcile only after a completed response.
        val values = listOf<String?>(null) + contracts.map { it.expiry }.distinct().sorted()
        if (!busy && expiry !in values) expiry = null
        expiryPicker.onItemSelectedListener = null
        expiryPicker.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
            values.map { it ?: "Tüm vadeler" }) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(getColor(R.color.market_ink)); textSize = 12f
                }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(getColor(R.color.market_ink)); setBackgroundColor(getColor(R.color.market_surface))
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                }
        }
        expiryPicker.setSelection(values.indexOf(expiry).coerceAtLeast(0))
        expiryPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                expiry = values[position]; render()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun render() {
        val visible = ViopAnalysisCatalog.filter(contracts, query, kind, expiry)
        rows.submit(visible)
        findViewById<View>(R.id.analysisProgress).visibility = if (busy) View.VISIBLE else View.GONE
        findViewById<View>(R.id.analysisRefresh).apply { isEnabled = !busy; alpha = if (busy) 0.4f else 1f }
        findViewById<TextView>(R.id.analysisCount).text = if (busy) "Sözleşmeler yükleniyor…" else "${visible.size} / ${contracts.size} sözleşme"
        listOf(R.id.analysisAll to ViopAnalysisCatalog.Kind.ALL, R.id.analysisFutures to ViopAnalysisCatalog.Kind.FUTURE,
            R.id.analysisOptions to ViopAnalysisCatalog.Kind.OPTION).forEach { (id, value) ->
            findViewById<Button>(id).setTextColor(getColor(if (kind == value) R.color.market_mint else R.color.market_muted))
        }
        findViewById<View>(R.id.analysisEmpty).visibility = if (!busy && visible.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.analysisEmptyTitle).text = when {
            needsSettings -> "VİOP bağlantısı gerekli"
            problem != null -> "Sözleşme listesi yok"
            else -> "Eşleşen sözleşme yok"
        }
        findViewById<TextView>(R.id.analysisEmptyMessage).text = problem ?: "Aramayı veya vade filtresini değiştirin."
        findViewById<Button>(R.id.analysisEmptyAction).text = when {
            needsSettings -> "Bağlantı ayarları"
            problem != null -> "Yeniden dene"
            else -> "Filtreleri temizle"
        }
    }

    private class ContractAdapter(private val select: (ViopContract) -> Unit) : RecyclerView.Adapter<ContractAdapter.Holder>() {
        private var items = emptyList<ViopContract>()
        class Holder(view: View) : RecyclerView.ViewHolder(view)
        fun submit(value: List<ViopContract>) { items = value; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_viop_analysis, parent, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val c = items[position]
            holder.itemView.findViewById<TextView>(R.id.analysisSymbol).text = c.symbol
            holder.itemView.findViewById<TextView>(R.id.analysisContractMeta).text = "${c.underlying} · ${c.expiry} · ${c.contractType}"
            holder.itemView.findViewById<TextView>(R.id.analysisContractState).text =
                "${c.providerLabel} · " + when (c.validity) {
                    SignalValidity.VALID -> "Analiz için fiyat ve mumlar yeniden doğrulanır"
                    SignalValidity.WATCH -> "İzleme · canlı veri doğrulanmadı"
                    SignalValidity.INSUFFICIENT -> "Eksik sözleşme verisi · sinyal yok"
                    SignalValidity.REJECTED -> "Doğrulanamadı · sinyal yok"
                }
            holder.itemView.setOnClickListener { select(c) }
        }
    }
}
