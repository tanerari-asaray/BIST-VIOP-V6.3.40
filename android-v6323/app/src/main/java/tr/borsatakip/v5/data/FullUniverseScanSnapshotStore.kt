package tr.borsatakip.v5.data

import android.content.Context

/**
 * Independently persists *metadata* about the new scanner. No quotes, API keys
 * or full universe are written. It must not be mistaken for live-signal audit.
 */
class FullUniverseScanSnapshotStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("full_universe_scan_snapshot_v643", Context.MODE_PRIVATE)

    data class Snapshot(
        val runId: String,
        val mode: String,
        val status: String,
        val discoveredBist: Int,
        val triedBist: Int,
        val successfulBist: Int,
        val noDataBist: Int,
        val errorsBist: Int,
        val discoveredViop: Int,
        val triedViop: Int,
        val successfulViop: Int,
        val noDataViop: Int,
        val errorsViop: Int,
        val failure: String,
        val startedAt: Long,
        val updatedAt: Long
    ) {
        val bistListComplete: Boolean get() = discoveredBist > 0 && triedBist == discoveredBist &&
            successfulBist == discoveredBist && noDataBist == 0 && errorsBist == 0
        fun summary(): String =
            "Run: " + runId + " • Mod: " + mode + " • Durum: " + status +
            "\nBIST " + triedBist + "/" + discoveredBist + " • başarılı " + successfulBist +
            " • veri yok " + noDataBist + " • hata " + errorsBist +
            "\nVİOP " + triedViop + "/" + discoveredViop + " • başarılı " + successfulViop +
            " • veri yok " + noDataViop + " • hata " + errorsViop +
            "\nBaşlangıç: " + startedAt + " • Son güncelleme: " + updatedAt +
            "\nKapsam: BAĞIMSIZ / DOĞRULANMADI" +
            if (failure.isNotBlank()) "\nHata: " + failure else ""
    }

    fun write(
        runId: String, mode: String, status: String,
        counters: FullUniversePolicy.Counters, failure: String,
        startedAt: Long, updatedAt: Long
    ) {
        preferences.edit()
            .putString("run_id", runId)
            .putString("mode", mode)
            .putString("status", status)
            .putInt("bist_discovered", counters.discoveredBist)
            .putInt("bist_tried", counters.triedBist)
            .putInt("bist_success", counters.successfulBist)
            .putInt("bist_no_data", counters.noDataBist)
            .putInt("bist_errors", counters.errorsBist)
            .putInt("viop_discovered", counters.discoveredViop)
            .putInt("viop_tried", counters.triedViop)
            .putInt("viop_success", counters.successfulViop)
            .putInt("viop_no_data", counters.noDataViop)
            .putInt("viop_errors", counters.errorsViop)
            .putString("failure", failure.take(500))
            .putLong("started", startedAt)
            .putLong("updated", updatedAt)
            .apply()
    }

    /**
     * Called once at Application.onCreate before any process-scoped scan can run.
     * A persisted RUNNING checkpoint belongs to a different, now-dead process.
     * Reclassify it as PARTIAL so the diagnostics and scanner screen agree.
     * This does not claim a resumable quote cache or automatically restart work.
     */
    fun recoverInterruptedProcess() {
        val previous = read() ?: return
        if (previous.status != FullUniversePolicy.State.RUNNING.name) return
        val counters = FullUniversePolicy.Counters(
            discoveredBist = previous.discoveredBist,
            triedBist = previous.triedBist,
            successfulBist = previous.successfulBist,
            noDataBist = previous.noDataBist,
            errorsBist = previous.errorsBist,
            discoveredViop = previous.discoveredViop,
            triedViop = previous.triedViop,
            successfulViop = previous.successfulViop,
            noDataViop = previous.noDataViop,
            errorsViop = previous.errorsViop
        )
        write(
            previous.runId, previous.mode, FullUniversePolicy.State.PARTIAL.name,
            counters,
            (previous.failure + " | ÖNCEKİ UYGULAMA SÜRECİ KESİLDİ; kayıtlı ilerleme: " +
                "${previous.triedBist}/${previous.discoveredBist}").trim().trimStart('|', ' '),
            previous.startedAt, System.currentTimeMillis()
        )
    }

    fun read(): Snapshot? {
        if (!preferences.contains("run_id")) return null
        return Snapshot(
            runId = preferences.getString("run_id", "").orEmpty(),
            mode = preferences.getString("mode", "").orEmpty(),
            status = preferences.getString("status", "").orEmpty(),
            discoveredBist = preferences.getInt("bist_discovered", 0),
            triedBist = preferences.getInt("bist_tried", 0),
            successfulBist = preferences.getInt("bist_success", 0),
            noDataBist = preferences.getInt("bist_no_data", 0),
            errorsBist = preferences.getInt("bist_errors", 0),
            discoveredViop = preferences.getInt("viop_discovered", 0),
            triedViop = preferences.getInt("viop_tried", 0),
            successfulViop = preferences.getInt("viop_success", 0),
            noDataViop = preferences.getInt("viop_no_data", 0),
            errorsViop = preferences.getInt("viop_errors", 0),
            failure = preferences.getString("failure", "").orEmpty(),
            startedAt = preferences.getLong("started", 0L),
            updatedAt = preferences.getLong("updated", 0L)
        )
    }
}
