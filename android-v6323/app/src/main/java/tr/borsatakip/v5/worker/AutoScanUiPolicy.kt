package tr.borsatakip.v5.worker

/** Veriye dayalı ototaramayı görünür ve doğrulanabilir tek durumla sunar. */
object AutoScanUiPolicy {
    fun isCompleted(lastStatus: String): Boolean = lastStatus == AutomaticScanRunner.Outcome.COMPLETED.name

    enum class State { OFF, STARTING, RUNNING, WAITING, FAILED, INTERRUPTED }
    data class Snapshot(val state: State, val label: String, val detail: String, val active: Boolean)

    fun evaluate(enabled: Boolean, lastStatus: String, lastMessage: String,
                 heartbeatAt: Long, startedAt: Long, cadenceMinutes: Int,
                 nowMs: Long = System.currentTimeMillis()): Snapshot {
        if (!enabled) {
            if (lastStatus == "CONFIG_ERROR") return Snapshot(State.FAILED, "BAŞLATILAMADI",
                lastMessage.ifBlank { "Zamanlayıcı başlatılamadı; veri sağlayıcısını kontrol edin." }, false)
            return Snapshot(State.OFF, "KAPALI", "Otomatik tarama kapalı • Manuel BAŞLAT kullanılabilir.", false)
        }
        val threshold = (cadenceMinutes.coerceAtLeast(1) * 2L * 60_000L + 120_000L)
        val evidence = maxOf(heartbeatAt, startedAt)
        if (evidence <= 0L) return Snapshot(State.STARTING, "BAŞLAMA DOĞRULANIYOR",
            "İlk gerçek tarama henüz doğrulanmadı.", false)
        if (nowMs < evidence || nowMs - evidence > threshold) return Snapshot(
            State.INTERRUPTED, "TARAMA KESİLMİŞ OLABİLİR",
            "Son zamanlayıcı/işlem kaydı eskidi. Pil, bildirim ve internet izinlerini kontrol edin.", false)
        val state = lastStatus.uppercase(java.util.Locale.ROOT)
        if (state in setOf("CONFIG_ERROR","NO_DATA","RETRYABLE_ERROR","ERROR","FAILED"))
            return Snapshot(State.FAILED, "SON TARAMA BAŞARISIZ",
                lastMessage.ifBlank { "Veri sağlayıcı hazır değil veya işlem başarısız." }, false)
        if (state in setOf("STARTING","SERVICE_STARTING","SCHEDULED", "PENDING"))
            return Snapshot(State.STARTING, "İLK TARAMA BEKLENİYOR",
                "Zamanlayıcı talebi kabul edildi; tarama tamamlandığı doğrulanmadı.", false)
        if (state in setOf("LOADING_SYMBOLS","LOADING_DATA","SCANNING","CALCULATING","RANKING"))
            return Snapshot(State.RUNNING, "TARAMA ÇALIŞIYOR", lastMessage, true)
        if (state in setOf("COMPLETED","PARTIAL"))
            return Snapshot(State.WAITING, "TARAMA GERÇEKLEŞTİ • SIRADAKİ BEKLENİYOR",
                lastMessage, true)
        return Snapshot(State.STARTING, "TARAMA BAŞLAMADI / DURUM BELİRSİZ",
            lastMessage.ifBlank { "İlk tarama kaydı bekleniyor." }, false)
    }
}
