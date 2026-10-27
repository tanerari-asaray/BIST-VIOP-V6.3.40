package tr.borsatakip.v5.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import tr.borsatakip.v5.model.Candle
import java.io.File

/** App-private atomic checkpoint; no credentials. Run once on IO, never on UI. */
class ObservationCheckpoint(context: Context) {
    private val file = AtomicFile(File(context.filesDir,"universe-observation-v648.json"))
    data class Saved(val local: Boolean, val universe: List<String>, val rows: List<FullUniversePolicy.Entry>, val savedAt: Long, val sourceIdentity: String = "", val runId: String = "")
    fun write(local: Boolean, universe: List<String>, rows: List<FullUniversePolicy.Entry>, sourceIdentity: String = "", runId: String = "") {
        val root=JSONObject().put("schemaVersion", 4).put("sourceIdentity", sourceIdentity).put("runId",runId)
            .put("local",local).put("savedAt",System.currentTimeMillis())
            .put("universe",JSONArray(universe)).put("rows",JSONArray().apply {
                rows.forEach { row -> put(JSONObject().apply {
                    put("symbol",row.symbol);put("market",row.market.name);put("subtitle",row.subtitle)
                    put("price",row.price);put("currency",row.currency);put("trend",row.trend.name)
                    put("quality",row.quality.name);put("source",row.source);put("dataTime",row.dataTimeMs)
                    put("received",row.receivedMs);put("detail",row.detail);put("reason",row.reasonCode)
                    put("change",row.changePct);put("financial",row.financialNote);put("discount",row.relativeDiscountPct)
                    put("financialStatus",row.financialStatus)
                    put("duration",row.durationMs);put("attempts",row.attempts);put("attemptErrors",JSONArray(row.attemptErrors))
                    put("candles",JSONArray().apply { row.candles.takeLast(400).forEach { c ->
                        put(JSONArray(listOf(c.timestamp,c.open,c.high,c.low,c.close,c.volume)))
                    } })
                }) }
            })
        require(universe.size <= 10000 && rows.size <= 10000) { "Checkpoint row limit exceeded" }
        require(runId.length <= 128 && sourceIdentity.length <= 512) { "Invalid checkpoint provenance" }
        val stream=file.startWrite()
        try { stream.write(root.toString().toByteArray(Charsets.UTF_8));file.finishWrite(stream) }
        catch (e:Exception) { file.failWrite(stream);throw e }
    }
    fun read(): Saved? = runCatching {
        if (!file.baseFile.exists() || file.baseFile.length()>24*1024*1024) return null
        val root=JSONObject(file.openRead().bufferedReader().use { it.readText() })
        if(root.optInt("schemaVersion",0) !in 3..4) return null
        val array=root.getJSONArray("rows")
        val universeArray=root.getJSONArray("universe")
        if (array.length()>10000 || universeArray.length()>10000) return null
        if (root.optString("runId").length>128 || root.optString("sourceIdentity").length>512) return null
        if (root.optInt("schemaVersion") == 4 && root.optString("runId").isBlank()) return null
        val rows=(0 until array.length()).map { i ->
            val r=array.getJSONObject(i)
            fun num(key:String)=if(r.isNull(key)) null else r.optDouble(key).takeIf(Double::isFinite)
            val a=r.getJSONArray("candles")
            if(a.length()>400) return null
            val bars=(0 until a.length()).map { k -> val b=a.getJSONArray(k)
                Candle(b.getLong(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),b.getDouble(4),b.getDouble(5)) }
            FullUniversePolicy.Entry(FullUniversePolicy.Market.valueOf(r.getString("market")),r.getString("symbol"),
                r.getString("subtitle"),num("price"),r.optString("currency").takeIf { it.isNotBlank() },
                FullUniversePolicy.Trend.valueOf(r.getString("trend")),FullUniversePolicy.Quality.valueOf(r.getString("quality")),
                r.getString("source"),r.getLong("dataTime"),r.getLong("received"),r.getString("detail"),
                bars.map { it.close },r.optString("reason").takeIf { it.isNotBlank() },bars,num("change"),
                r.optString("financial"),num("discount"),r.optLong("duration"),
                r.optInt("attempts",1).coerceAtLeast(1),r.optJSONArray("attemptErrors")?.let { errors ->
                    (0 until errors.length()).map { errors.getString(it) }
                }.orEmpty(),r.optString("financialStatus","NOT_EVALUATED"))
        }
        val u=universeArray
        Saved(root.getBoolean("local"),(0 until u.length()).map { u.getString(it) },rows,root.getLong("savedAt"),root.optString("sourceIdentity"),root.optString("runId"))
    }.getOrNull()
}
