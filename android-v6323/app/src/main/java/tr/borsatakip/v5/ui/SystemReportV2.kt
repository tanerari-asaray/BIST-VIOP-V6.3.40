package tr.borsatakip.v5.ui

import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import android.app.NotificationManager
import android.os.Build
import tr.borsatakip.v5.data.DiagnosticOverlapAudit
import tr.borsatakip.v5.scan.BistScanGate
import tr.borsatakip.v5.data.DeepDiagnosticCollector
import tr.borsatakip.v5.data.DeepDiagnosticEngine
import tr.borsatakip.v5.data.DeepDiagnosticHistory
import tr.borsatakip.v5.data.DiagnosticJournal
import tr.borsatakip.v5.data.DiagnosticStallPolicy
import tr.borsatakip.v5.data.PreviousProcessDiagnostics
import tr.borsatakip.v5.BuildConfig
import tr.borsatakip.v5.data.ProviderReadinessSnapshot
import tr.borsatakip.v5.data.ProviderState
import tr.borsatakip.v5.data.RuntimeReadinessDiagnostics
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.FullUniverseScanSnapshotStore
import tr.borsatakip.v5.scan.IndependentScanAudit
import tr.borsatakip.v5.scan.BistScanMode
import tr.borsatakip.v5.scan.ScanState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Truthful, offline diagnostics; never sends data to an external AI or endpoint. */
object SystemReportV2 {
    data class NotificationChannelEvidence(val id: String, val importance: Int)
    data class Evidence(
        val events: List<DiagnosticJournal.Event>,
        val processExits: JSONArray,
        val notificationsSystemEnabled: Boolean?,
        val channels: List<NotificationChannelEvidence>,
        val activeRunId: String? = null,
        val activeOwner: String? = null,
        val activeAgeMs: Long? = null
    )

    fun captureEvidence(context: Context): Evidence {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val activeScan = BistScanGate.snapshot()
        return Evidence(
            runCatching { DiagnosticJournal(context).recent() }.getOrDefault(emptyList()),
            PreviousProcessDiagnostics.read(context),
            if (Build.VERSION.SDK_INT >= 26) runCatching { manager?.areNotificationsEnabled() }.getOrNull() else null,
            if (Build.VERSION.SDK_INT >= 26) runCatching {
                manager?.notificationChannels?.map { NotificationChannelEvidence(it.id.take(90).replace(Regex("[^A-Za-z0-9_.-]"), "_"), it.importance) } ?: emptyList()
            }.getOrDefault(emptyList()) else emptyList(),
            activeScan?.runId,
            activeScan?.owner,
            activeScan?.elapsedMs
        )
    }
    private fun time(t: Long): String = if (t <= 0L) "YOK / ÖLÇÜLMEDİ" else
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(t))

    private fun observations(
        provider: ProviderReadinessSnapshot,
        runtime: RuntimeReadinessDiagnostics.Snapshot,
        scan: ScanState?,
        settings: SettingsStore,
        capturedAt: Long
    ): List<Pair<String, String>> = buildList {
        if (provider.state != ProviderState.PROVIDER_READY) add(
            "KRİTİK • PRODUCTION_PROVIDER_NOT_READY" to
                "Üretim sağlayıcısı doğrulanmadı: ${provider.failureCode}. HTTPS backend ve erişim yetkilerini yapılandırıp canlı health/auth/sembol/OHLCV testini çalıştırın. Yedek Yahoo etiketi, production health testinin yerine geçmez."
        )
        if (scan?.scanMode == BistScanMode.DELAYED_ANALYSIS) add(
            "KRİTİK • DELAYED_ANALYSIS_ONLY" to
                "Gecikmeli/deneysel analiz işlem sinyali olarak doğrulanmış değildir; canlı LONG/SHORT veya otomatik emir kararı için kullanmayın."
        )
        if (scan != null && scan.integrityRejected > 0) add(
            (if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS) "BİLGİ • DELAYED_TRADE_GATE_ACTIVE" else "YÜKSEK • INTEGRITY_NOT_VERIFIED") to
                if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS)
                    "${scan.integrityRejected} gecikmeli teknik gözlem, gerçek zamanlı işlem bütünlüğüne uygun değil (beklenen koruma). Bunlar sistem/veri hatası ya da canlı sinyal değildir."
                else "${scan.integrityRejected} canlı analiz kaydı production bütünlük koşullarını geçemedi. Nedenler ayrı incelenmeli."
        )
        if (scan != null && scan.scanRun?.status?.name != "COMPLETE") add(
            "YÜKSEK • SCAN_NOT_COMPLETE" to
                "Tarama sonucu ${scan.scanRun?.status ?: "YOK"}; eksik veya işlenemeyen kapsam var. Sembol bazlı hata/boş veri kayıtları ve kaynak kotası incelenmeli."
        )
        if (runtime.autoScanEnabled && !tr.borsatakip.v5.worker.AutoScanUiPolicy.isCompleted(runtime.lastAutoScanStatus)) add(
            "ORTA • AUTO_SCAN_NOT_VERIFIED" to
                "Otomatik tarama açık; son durum ${runtime.lastAutoScanStatus}. Sonuç üreten tur (COMPLETE veya PARTIAL)=${runtime.successfulRuns}, başarısız tur=${runtime.failedRuns}. Bu sayaç yalnız COMPLETE tarama veya doğrulanmış canlı sinyal sayısı değildir."
        )
        val stall = DiagnosticStallPolicy.evaluate(runtime.autoScanEnabled, runtime.autoScanCadenceMinutes,
            runtime.lastHeartbeatAt, runtime.monitoringStartedAt, capturedAt)
        val elapsedFromHeartbeat = ageMs(runtime.lastHeartbeatAt, capturedAt)
        if (stall == DiagnosticStallPolicy.Status.HEARTBEAT_OVERDUE) add(
            "YÜKSEK • AUTO_SCAN_HEARTBEAT_OVERDUE" to
                "Otomatik tarama açıkken heartbeat yaşı ${elapsedFromHeartbeat} ms; beklenen aralığın üç katını geçti. İş teslimi, Android pil ve servis kısıtları incelenmeli; sebep henüz doğrulanmadı."
        )
        if (stall == DiagnosticStallPolicy.Status.FIRST_HEARTBEAT_OVERDUE) add(
            "YÜKSEK • AUTO_SCAN_NEVER_OBSERVED" to
                "Otomatik tarama tercihi açık ancak izleme başlangıcından beri heartbeat yok. İzin/zamanlayıcı/servis başlatma kayıtları incelenmeli."
        )
        if (!runtime.notificationPermission || !settings.notifications) add(
            "ORTA • ALERT_DELIVERY_DISABLED" to
                "Bildirim tercihi veya Android POST_NOTIFICATIONS izni kapalı. Uygulama bildirim göndereceğini garanti edemez."
        )
        if (!runtime.batteryOptimizationExempt) add(
            "BİLGİ • BACKGROUND_EXECUTION_RISK" to
                "Pil optimizasyonu arka plan görevlerini kısıtlayabilir. Android 16 üzerinde 5 dakikalık servis sürekliliği cihazda ayrıca ölçülmelidir."
        )
    }

    private fun coveragePercent(count: Int, universe: Int): Double? =
        if (universe <= 0) null else ((count.coerceAtLeast(0).toDouble() / universe.toDouble()) * 10000).toInt() / 100.0

    private fun elapsedMs(start: Long, end: Long): Long? =
        if (start <= 0L || end <= 0L || end < start) null else end - start

    private fun ageMs(timestamp: Long, now: Long = System.currentTimeMillis()): Long? =
        if (timestamp <= 0L || timestamp > now) null else now - timestamp

    fun appendix(
        provider: ProviderReadinessSnapshot,
        runtime: RuntimeReadinessDiagnostics.Snapshot,
        scan: ScanState?,
        settings: SettingsStore,
        capturedAt: Long = System.currentTimeMillis(),
        reportId: String = "UNLINKED",
        context: Context? = null,
        evidence: Evidence? = context?.let(::captureEvidence)
    ): String = buildString {
        val findings = observations(provider, runtime, scan, settings, capturedAt)
        append("\n\n[8] TEKNİK DURUM ÖZETİ / İŞLEM GÜVENLİĞİ\n")
        append("Rapor formatı: V5.4.1-SYSTEM-DIAGNOSTICS/4 • Uygulama V${BuildConfig.VERSION_NAME} • Üretim anı: ${time(capturedAt)}\n")
        append("Saat dilimi: ${TimeZone.getDefault().id} • Veri kaynağı: cihaz belleği/yerel ayarlar/son tarama\n")
        append("Üretim sağlayıcısı: ${provider.state}; hata=${provider.failureCode}; son canlı test=${time(provider.testedAt)}\n")
        append("Yedek sağlayıcı etiketi: ${settings.lastProviderLabel.ifBlank { "BİLİNMİYOR" }}\n")
        append("Gerçek zamanlı işlem için hazır: ${if (provider.state == ProviderState.PROVIDER_READY && scan?.scanMode == BistScanMode.REALTIME_ONLY && scan?.scanRun?.status?.name == "COMPLETE" && scan?.integrityRejected == 0) "KAYNAK ÖN KOŞULLARI SAĞLANMIŞ; EMİR/SLIPPAGE YİNE TEST EDİLMEDİ" else "HAYIR / DOĞRULANMADI"}\n")
        append("Rapor, uygulamanın kâr edeceğini veya kesintisiz tarama yapacağını doğrulamaz.\n")

        append("\n[9] BIST SAYAÇLARININ ANLAMI VE TUTARLILIK DENETİMİ\n")
        if (scan == null) {
            append("Eski tarama motoru kaydı yok. Yeni evren taramasının bağımsız sayaçları rapor [2A] bölümündedir.\n")
        } else {
            append("Run ID: ${scan.scanRun?.scanRunId ?: "YOK"} • Başlangıç: ${time(scan.scanRun?.scanStartedAt ?: 0L)} • Bitiş: ${time(scan.scanRun?.scanCompletedAt ?: 0L)}\n")
            append("Evren/total=${scan.total}; ilerleme işlenen/processed=${scan.processed}. Bu sayaç, tek tek OHLCV geçerliliği doğrulandı demek değildir.\n")
            append("Başarılı analiz=${scan.successful}; sonuç listesi=${scan.results.size}; ${if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS) "gecikmeli modda işlem kapısından engellenen" else "production bütünlük reddi"}=${scan.integrityRejected}. Bu kümeler örtüşebilir: toplanmaz.\n")
            append("Provider veri yok=${scan.noData}; provider hata=${scan.errors}; yapısal atlanan=${scan.skipped}. Kaynakta farklı aşamalarda tutuldukları için toplamları evrene eşit olmak zorunda değildir.\n")
            append("Veri alınıp analiz edilen sembol listesi=${scan.scannedSymbols.size}; analizde başarısız sembol listesi=${scan.failedSymbols.size}.\n")
            val unclassified = scan.unclassifiedMissingSymbols
            if (scan.universeSymbols.isNotEmpty()) {
                append("Sembol bazlı denetim: evren=${scan.universeSymbols.size}; veri gelen=${scan.scannedSymbols.size}; eksik veya veri üretmeyen=${(scan.universeSymbols.size - scan.scannedSymbols.size).coerceAtLeast(0)}.\n")
                append("Sağlayıcı EMPTY_DATA/STALE_DATA sembolleri=${scan.providerNoDataSymbols.size}; diğer provider hata sembolleri=${scan.providerErrorSymbols.size}; nedeni sınıflandırılamayan eksik semboller=${unclassified.size}.\n")
                append("Sembol bazlı tanı kayıtları=${scan.missingSymbolDiagnostics.size}; UNKNOWN kodu upstream kök nedenin henüz bilinmediğini belirtir.\n")
                val grouped = scan.missingSymbolDiagnostics.groupingBy { it.code }.eachCount().toSortedMap()
                append("Eksik sembol tanı kodları: ${grouped.entries.joinToString(", ") { "${it.key}=${it.value}" }}. Sağlayıcı raporlamadığı nedeni tahmin etmiyoruz.\n")
                val recordedReasons = scan.missingSymbolDiagnostics.filter { it.evidence.startsWith("PROVIDER_REPORTED:") }
                    .groupingBy { it.evidence.substringAfter(':') }.eachCount().toSortedMap()
                if (recordedReasons.isNotEmpty()) append("Kaydedilen sağlayıcı nedenleri: ${recordedReasons.entries.joinToString(", ") { "${it.key}=${it.value}" }}.\n")
                if (unclassified.isNotEmpty()) {
                    append("Ayrıntı nedeni kaydedilemeyen semboller (ilk 30): ${unclassified.take(30).joinToString(", ")}${if (unclassified.size > 30) " ... +${unclassified.size - 30}" else ""}. Bu semboller için kök neden kanıtlanmadı.\n")
                }
            }
            append("Geçmişe yazılan=${scan.historyPersisted}: gecikmeli analiz modunda merkezi doğrulanmış sinyal geçmişine yazılmaması tasarımsal güvenlik kuralıdır.\n")
            append("Tarama doğrulaması=${scan.scanRun?.status ?: "BİLİNMİYOR"} • Veri modu=${scan.scanMode}. 'COMPLETED' ekran durumu, 'COMPLETE' kapsam doğrulaması değildir.\n")
            append("Canlı işlem sinyal durumu=${if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS) "ENGELLİ • TEKNİK İZLEME" else "BAĞIMSIZ SAĞLAYICI/YAŞ/BÜTÜNLÜK TESTİ GEREKLİ"}.\n")
            append("Tarama hatası=${scan.errorMessage ?: "YOK"}; history hatası=${scan.historyError ?: "YOK"}.\n")
            val duration = elapsedMs(scan.scanRun?.scanStartedAt ?: 0L, scan.scanRun?.scanCompletedAt ?: 0L)
            val lastAge = ageMs(scan.scanRun?.scanCompletedAt ?: 0L, capturedAt)
            val coverage = coveragePercent(scan.scannedSymbols.size, scan.total)
            val analyses = coveragePercent(scan.successful, scan.total)
            val countValid = scan.total >= 0 && scan.processed in 0..scan.total &&
                scan.successful >= 0 && scan.successful <= scan.total &&
                scan.results.size <= scan.total && scan.integrityRejected in 0..scan.total
            append("Tarama geçen süre=${duration?.let { "${it} ms" } ?: "ÖLÇÜLEMEDİ"}; rapor anında sonuç yaşı=${lastAge?.let { "${it} ms" } ?: "ÖLÇÜLEMEDİ"}.\n")
            append("Verisi alınan sembol kapsamı=${coverage?.let { "${it}%" } ?: "ÖLÇÜLEMEDİ"}; analiz sonucu kapsamı=${analyses?.let { "${it}%" } ?: "ÖLÇÜLEMEDİ"}. Bu oranlar veri güncelliği veya onaylı sinyal oranı değildir.\n")
            append("Temel sayaç aralık kontrolü=${if (countValid) "GEÇTİ" else "UYUŞMAZLIK — İNCELENMELİ"}; farklı aşamaların sayaçları toplamsal değildir.\n")
            if (scan.universeSymbols.isNotEmpty()) {
                val unknown = scan.unclassifiedMissingSymbols.size
                append("Eksik sembol kök nedeni kanıtı=${if (unknown == 0) "LİSTEDE SINIFLANDIRILMAMIŞ YOK; SAĞLAYICI TEŞHİSİNİ DOĞRULAYIN" else "$unknown SEMBOL SINIFLANDIRILAMADI"}.\n")
            }
        }

        append("\n[10] SAĞLAYICI DOĞRULAMA ZİNCİRİ\n")
        append("Backend origin=${runtime.backendOrigin}; API anahtarı mevcut=${runtime.apiKeyConfigured} (değer gizlidir).\n")
        append("HTTPS health=${provider.healthOk}; auth=${provider.authOk}; BIST sembol=${provider.bistSymbolsOk} (${provider.bistSymbolCount}); BIST quote=${provider.bistQuoteOk}; BIST geçmiş=${provider.bistHistoryOk}.\n")
        append("VİOP sözleşme=${provider.viopContractsOk} (${provider.viopContractCount}); quote=${provider.viopQuoteOk}; geçmiş=${provider.viopHistoryOk}.\n")
        append("Test kapsamı=VİOP HEALTH/CONTRACT/QUOTE/HISTORY; BIST sembol/quote/geçmiş kontrolü bu readiness testinde ÇAĞRILMADI. Sağlık kontrolü başarısızsa tüm sonraki adımlar TEST EDİLMEDİ; false değeri tek başına isteğin başarısızlığını kanıtlamaz.\n")
        append("Yahoo/deneysel yedek sağlayıcı ile veri çekilmesi, lisanslı/canlı piyasa akışı veya VİOP emir erişimi kanıtı değildir.\n")

        append("\n[11] OTOMASYON, BİLDİRİM VE CİHAZ GÜVENİLİRLİĞİ\n")
        append("Otomatik tarama=${runtime.autoScanEnabled}; cadence=${runtime.autoScanCadenceMinutes} dk; scheduler=${runtime.schedulerMode}.\n")
        append("Heartbeat=${time(runtime.lastHeartbeatAt)}; süre=${runtime.lastDurationMs} ms; son durum=${runtime.lastAutoScanStatus}.\n")
        val heartbeatAge = ageMs(runtime.lastHeartbeatAt, capturedAt)
        val cadenceMs = runtime.autoScanCadenceMinutes.coerceAtLeast(1).toLong() * 60_000L
        append("Heartbeat yaşı=${heartbeatAge?.let { "${it} ms" } ?: "ÖLÇÜLEMEDİ"}; yapılandırılmış tarama aralığı=${cadenceMs} ms.\n")
        append("Heartbeat gecikme uyarısı=${if (runtime.autoScanEnabled && heartbeatAge != null && heartbeatAge > cadenceMs * 3) "EVET — OLASI ARKA PLAN KESİNTİSİ" else "BU ÖLÇÜMDE KANITLANMADI / VERİ YOK"}; telefonun arka plan teslimi için tek bir heartbeat yeterli değildir.\n")
        append("İzleme başlangıcı=${time(runtime.monitoringStartedAt)}; son heartbeat=${time(runtime.lastHeartbeatAt)}.\n")
        append("Otomasyon teslim gözlemi=${DiagnosticStallPolicy.evaluate(runtime.autoScanEnabled, runtime.autoScanCadenceMinutes, runtime.lastHeartbeatAt, runtime.monitoringStartedAt, capturedAt)}; bu kesin çalışma/arıza hükmü değildir.\n")
        append("Sonuç üreten tur (COMPLETE + PARTIAL)/başarısız/ardışık hata=${runtime.successfulRuns}/${runtime.failedRuns}/${runtime.consecutiveFailures}; çakıştığı için atlanan=${runtime.skippedBusyRuns}; boot sonrası planlama=${time(runtime.bootRescheduleAt)}. PARTIAL tam başarılı tur değildir; çakışma atlaması hata değildir.\n")
        append("Bildirim tercihi=${settings.notifications}; Android izni=${runtime.notificationPermission}; pil muafiyeti=${runtime.batteryOptimizationExempt}.\n")
        append("Gerçek fiziksel cihazda 24/48/72 saatlik süreklilik, bildirim teslimi ve ağ kesintisi sonrası toparlanma ölçülmediyse DOĞRULANMADI.\n")

        append("\n[12] KÖK NEDEN ADAYLARI VE ÖNERİLEN DÜZELTMELER\n")
        if (findings.isEmpty()) append("Yerel anlık denetimde belirgin bir uyarı türetilemedi; kapsamlı canlı test yapılmış olduğu anlamına gelmez.\n")
        findings.forEachIndexed { index, (code, action) -> append("${index + 1}. $code\n   $action\n") }
        if (evidence?.events?.let(DiagnosticOverlapAudit::overlaps)?.isNotEmpty() == true) {
            append("EK UYARI • CONCURRENT_SCAN_CONFIRMED — Aynı anda çalışan taramalar günlüğün başlangıç/bitiş kanıtlarıyla saptandı. V6.2.8 ortak tarama kilidi yeni çakışmayı engeller.\n")
        }
        append("Kodlar tanısal bulgudur, sunucu logu veya ağ isteği sonucu değildir.\n")

        append("\n[13] DOĞRULAMA MATRİSİ / EKSİK KANITLAR\n")
        append("Yerel cihaz bilgisi=ÖLÇÜLDÜ; ayarlar=OKUNDU; son tarama=${if (scan != null) "BELLEKTE VAR" else "YOK"}.\n")
        append("Canlı backend preflight=${if (provider.testedAt > 0L) "KAYITLI TEST ${time(provider.testedAt)}" else "TEST EDİLMEDİ"}.\n")
        append("Bağımsız WebSocket probe=YOK; gerçek emir iletimi=TEST EDİLMEDİ; fill/slippage=TEST EDİLMEDİ.\n")
        append("Fiziksel telefon uçtan uca bildirim testi=TEST EDİLMEDİ; 24/48/72 saat stabilite=TEST EDİLMEDİ.\n")
        append("Birim/emülatör testleri bu cihazda çalıştırılmadı; CI sonuçları cihaz canlı-veri testi yerine geçmez.\n")

        append("\n[14] DIŞ AI / DENETİM İÇİN KAPSAM VE MAHREMİYET\n")
        append("JSON dışa aktarma: alanları ve son tarama sonuçlarının özet metadatasını yapılandırılmış biçimde verir.\n")
        append("Sembol/sonuç verisi yalnız bu uygulama sürecinin belleğinde bulunabilir; kalıcı tüm geçmişin veya kaynak kodun eksiksiz dökümü değildir.\n")
        append("API anahtarı, authorization header, token, parola ve özel backend URL yolu dışa aktarılmaz.\n")
        append("Dışarıya aktarım/kopyalama yalnız kullanıcının açık seçimiyle gerçekleşir; rapor kendiliğinden bir AI servisine gönderilmez.\n")
        append("\n[16] BAĞIMSIZ GÜVENLİK VE KABUL TESTİ KANIT DURUMU\n")
        append("Statik kaynak güvenliği, bağımlılık CVE, manifest maruziyeti, ağ yapılandırması ve penetrasyon testi: BU CİHAZ RAPORUNDA ÇALIŞTIRILMADI.\n")
        append("WebSocket, emir iletimi, fill/slippage, bildirim teslimi, boot sonrası toparlanma ve 24/48/72 saat stabilite: DOĞRULANMADI.\n")
        append("Makine tarafından okunabilir audit nesnesi JSON raporuna eklendi. Doğrulanmayan denetimler başarı olarak gösterilmez.\n")
        append("\n[17] BAĞIMSIZ TARAMA DENETİM KAPISI / BGDK\n")
        val delayedObservation = context?.let { FullUniverseScanSnapshotStore(it).read() }
        val independent = IndependentScanAudit.evaluate(provider, scan, capturedAt, delayedObservation)
        append("Tanısal canlı sinyal uygunluğu=${if (independent.liveSignalEligible) "YEREL KAPILAR GEÇTİ — GERÇEK EMİR İZNİ DEĞİL" else "HAYIR / ENGELLENDİ"}.\n")
        append("Kapsam=${independent.coveragePercent ?: "ÖLÇÜLEMEDİ"}%; tarama yaşı=${independent.scanAgeMs ?: "ÖLÇÜLEMEDİ"} ms; eksik sembol=${independent.missingSymbols ?: "ÖLÇÜLEMEDİ"}.\n")
        independent.findings.forEach { finding ->
            append("${finding.severity} • ${finding.code} — ${finding.evidence}\n")
        }
        append("BGDK bağımsız canlı ağ testi, işlem yetkisi veya performans garantisi değildir.\n")

        append("\n[18] KALICI TANILAMA GÜNLÜĞÜ / AŞAMA SÜRELERİ\n")
        append("Rapor ID=$reportId • tek ölçüm zamanı=${time(capturedAt)} • tüm kayıtlar yerel ve sınırlıdır.\n")
        if (evidence == null) append("Kalıcı olay günlüğü bu çağrıda okunamadı.\n") else {
            val events = evidence.events
            append("Kalıcı olay sayısı=${events.size} (azami 150); önceki süreçlerden korunabilir, eski kayıtlar döngüsel silinir.\n")
            events.takeLast(35).forEach { ev ->
                append("${time(ev.at)} • ${ev.subsystem}/${ev.phase} • ${ev.code} • run=${ev.runId.ifBlank { "YOK" }}")
                append(" • süre=${ev.durationMs?.let { "$it ms" } ?: "ÖLÇÜLMEDİ"}")
                if (ev.count != null) append(" • adet=${ev.count}")
                append("\n")
            }
            val overlaps = DiagnosticOverlapAudit.overlaps(events)
            append("Kanıtlı eşzamanlı tarama çakışması=${overlaps.size}; yalnız başlangıcı ve bitişi kaydedilen turlar karşılaştırıldı.\n")
            overlaps.takeLast(5).forEach { overlap ->
                append("ÇAKIŞMA • run=${overlap.firstRunId} / ${overlap.secondRunId} • örtüşme=${overlap.overlapMs} ms.\n")
            }
            val busyRejects = events.count { it.code == "SCAN_ALREADY_RUNNING" }
            append("Kaydedilmiş çakışma engeli=$busyRejects (son 150 olay içinde); bu sayaç geçmişin tümünü kapsamayabilir.\n")
            append("Tarama kilidi (rapor ölçümü)=${if (evidence.activeRunId == null) "BOŞ" else "MEŞGUL • owner=${evidence.activeOwner} • run=${evidence.activeRunId} • süre=${evidence.activeAgeMs} ms"}.\n")
            val pendingRuns = events.filter { it.code.startsWith("SCAN_STARTED_") }
                .filter { start -> events.none { it.runId == start.runId && it.phase == "END" && it.at >= start.at } }
            if (pendingRuns.isNotEmpty()) append("DİKKAT: ${pendingRuns.size} kapanış kaydı olmayan tarama var; süreç ölümü/iptal/eksik kayıt olasıdır, sebep kanıtlanmadı.\n")
            val slowest = events.filter { it.code == "PHASE_FINISHED" && it.durationMs != null }
                .sortedByDescending { it.durationMs }.take(5)
            if (slowest.isNotEmpty()) append("En uzun aşamalar: ${slowest.joinToString("; ") { "${it.phase}=${it.durationMs}ms" }}. Süreler monotonic saatle ölçülmüştür.\n")
            if (!runtime.autoScanEnabled) append("Otomasyon KAPALI: servis çalışmaması kullanıcı ayarıdır, tıkanma değildir.\n")
        }
        append("\n[19] ANDROID ÖNCEKİ SÜREÇ ÇIKIŞLARI / BİLDİRİM KANALLARI\n")
        if (evidence == null) append("Cihaz tanısı bu çağrıda okunamadı.\n") else {
            val exits = evidence.processExits
            append("Android çıkış kayıt sayısı=${exits.length()}; boş liste önceki hataları reddetmez.\n")
            for (i in 0 until exits.length()) {
                val event = exits.getJSONObject(i)
                append("${time(event.optLong("atEpochMs"))} • Android exit reason=${event.optInt("reasonCode")} • importance=${event.optInt("importance")}\n")
            }
            if (Build.VERSION.SDK_INT >= 26) {
                append("Bildirimlerin sistem düzeyinde açık olması=${evidence.notificationsSystemEnabled ?: "ÖLÇÜLEMEDİ"}\n")
                evidence.channels.forEach { channel ->
                    append("Kanal=${channel.id} • önem=${channel.importance} • engelli=${channel.importance == NotificationManager.IMPORTANCE_NONE}\n")
                }
            }
        }
        append("\n[20] TANI KANIT SINIRLARI\n")
        append("Kayıtlı gecikme/hata kanıtı gerçek emir izni, canlı veri lisansı, 24-72 saat başarı veya ANR sebebi kanıtı değildir.\n")
        append("Olay günlüğü ham hata, URL, API anahtarı ve özel piyasa fiyatı içermez. Kanıt olmayan alan: TEST EDİLMEDİ.\n")
        val deep = DeepDiagnosticCollector.build(provider, runtime, scan, evidence?.events ?: emptyList(), capturedAt, delayedObservation)
        append(deep.text())
        if (context != null) {
            val (previousAt, previousIssues) = DeepDiagnosticHistory.previous(context)
            append("\n[22] ÖNCEKİ RAPORLA KANITLI KARŞILAŞTIRMA\n")
            if (previousAt <= 0L) append("Önceki saklanmış karşılaştırılabilir rapor yok.\n") else {
                val diff = DeepDiagnosticEngine.compare(previousIssues, deep.checks, previousAt)
                append("Yeni=${diff.newIssues.size}; Devam eden=${diff.continuing.size}; yeniden test edilemeyen=${diff.notReverified.size}; başarıyla tekrar test edilip kapanan=${diff.resolved.size}.\n")
                append("YENİ: ${diff.newIssues.joinToString(", ").ifBlank { "YOK" }}\n")
                append("DEVAM: ${diff.continuing.joinToString(", ").ifBlank { "YOK" }}\n")
                append("HENÜZ ÇÖZÜLDÜĞÜ KANITLANMAYAN: ${diff.notReverified.joinToString(", ").ifBlank { "YOK" }}\n")
                append("KANITLI KAPANAN: ${diff.resolved.joinToString(", ").ifBlank { "YOK" }}\n")
            }
        }

    }

    fun json(
        provider: ProviderReadinessSnapshot,
        runtime: RuntimeReadinessDiagnostics.Snapshot,
        scan: ScanState?,
        settings: SettingsStore,
        renderedText: String,
        capturedAt: Long = System.currentTimeMillis(),
        reportId: String = "UNLINKED",
        context: Context? = null,
        evidence: Evidence? = context?.let(::captureEvidence)
    ): String {
        val root = JSONObject()
        fun privacySafe(value: String?): String = redact(value ?: "", settings.apiKey)
        root.put("schemaVersion", "V5.4.1-SYSTEM-DIAGNOSTICS/4")
        root.put("generatedAtEpochMs", capturedAt)
        root.put("reportId", reportId)
        root.put("timezone", TimeZone.getDefault().id)
        root.put("applicationId", BuildConfig.APPLICATION_ID)
        root.put("versionName", BuildConfig.VERSION_NAME)
        root.put("versionCode", BuildConfig.VERSION_CODE)
        root.put("device", privacySafe(runtime.device))
        root.put("androidVersion", runtime.androidVersion)
        root.put("productionReady", false)
        root.put("providerReady", provider.state == ProviderState.PROVIDER_READY)
        root.put("providerReadinessScope", "VIOP_QUOTE_ONLY")
        root.put("productionProviderStateOnly", true)
        root.put("productionProviderLastProbeAgeMs", ageMs(provider.testedAt, capturedAt) ?: JSONObject.NULL)
        root.put("liveTradingReady", false)
        root.put("liveTradingReadinessNote", "Provider READY is not proof of broker order access, data timeliness, slippage or real-device stability")
        root.put("tradeExecutionVerified", false)
        root.put("financialPerformanceVerified", false)
        root.put("backend", JSONObject().apply {
            put("origin", runtime.backendOrigin)
            put("apiKeyConfigured", runtime.apiKeyConfigured)
            put("state", provider.state.name)
            put("failureCode", provider.failureCode.name)
            put("message", privacySafe(provider.message))
            put("httpStatus", provider.connection.httpStatus ?: JSONObject.NULL)
            put("endpoint", provider.connection.endpoint)
            put("providerCode", provider.connection.providerCode)
            put("failureStage", provider.connection.failureStage)
            put("upstreamHttpStatus", provider.connection.upstreamHttpStatus ?: JSONObject.NULL)
            put("gatewayAuthenticated", provider.connection.gatewayAuthenticated ?: JSONObject.NULL)
            put("lastTestEpochMs", provider.testedAt)
            put("probeScope", "VIOP_QUOTE_ONLY")
            put("bistStepsAttempted", false)
            put("downstreamAttempted", provider.testedAt > 0L && provider.healthOk)
            put("health", provider.healthOk)
            put("auth", provider.authOk)
            put("bistSymbols", provider.bistSymbolsOk)
            put("bistSymbolCount", provider.bistSymbolCount)
            put("bistQuote", provider.bistQuoteOk)
            put("bistHistory", provider.bistHistoryOk)
            put("viopContracts", provider.viopContractsOk)
            put("viopContractCount", provider.viopContractCount)
            put("viopQuote", provider.viopQuoteOk)
            put("viopHistory", provider.viopHistoryOk)
            put("fallbackLabel", privacySafe(settings.lastProviderLabel))
        })
        context?.let {
            val probes = tr.borsatakip.v5.data.ConnectionProbeStore(it)
            root.put("markets", JSONObject().put("BIST", probes.read("BIST")).put("VIOP", probes.read("VIOP")))
        }
        val observation = context?.let { FullUniverseScanSnapshotStore(it).read() }
        root.put("universeObservation", if (observation == null) JSONObject().put("present", false)
            else JSONObject().apply {
                put("present", true)
                put("scope", "LOCAL_OR_BACKEND_UNIVERSE_OBSERVATION_NOT_LIVE_SIGNAL")
                put("mode", observation.mode)
                put("runId", privacySafe(observation.runId))
                put("status", observation.status)
                put("bistDiscovered", observation.discoveredBist)
                put("bistAttempted", observation.triedBist)
                put("bistSuccessful", observation.successfulBist)
                put("bistNoData", observation.noDataBist)
                put("bistErrors", observation.errorsBist)
                put("viopDiscovered", observation.discoveredViop)
                put("viopAttempted", observation.triedViop)
                put("viopSuccessful", observation.successfulViop)
                put("startedAt", observation.startedAt)
                put("updatedAt", observation.updatedAt)
                put("subsetAttemptedPercent", coveragePercent(observation.triedBist, observation.discoveredBist) ?: JSONObject.NULL)
                put("fullExchangeCoverageVerified", false)
                put("liveTradingEligible", false)
            })
        root.put("scan", if (scan == null) JSONObject().put("present", false).put("scope", "LEGACY_ANALYSIS_ONLY") else JSONObject().apply {
            put("present", true)
            put("runId", scan.scanRun?.scanRunId?.let(::privacySafe) ?: JSONObject.NULL)
            put("status", scan.status.name)
            put("runStatus", scan.scanRun?.status?.name ?: "UNKNOWN")
            put("providerId", privacySafe(scan.scanRun?.providerId ?: "UNKNOWN"))
            put("mode", scan.scanMode.name)
            put("startedAt", scan.scanRun?.scanStartedAt ?: 0L)
            put("completedAt", scan.scanRun?.scanCompletedAt ?: 0L)
            put("universeTotal", scan.total)
            put("processedProgress", scan.processed)
            put("scanDurationMs", elapsedMs(scan.scanRun?.scanStartedAt ?: 0L, scan.scanRun?.scanCompletedAt ?: 0L) ?: JSONObject.NULL)
            put("ageSinceScanEndMs", ageMs(scan.scanRun?.scanCompletedAt ?: 0L, capturedAt) ?: JSONObject.NULL)
            put("fetchedSymbolCoveragePct", coveragePercent(scan.scannedSymbols.size, scan.total) ?: JSONObject.NULL)
            put("analysisResultCoveragePct", coveragePercent(scan.successful, scan.total) ?: JSONObject.NULL)
            put("countersPassBasicRangeCheck", scan.total >= 0 && scan.processed in 0..scan.total &&
                scan.successful >= 0 && scan.successful <= scan.total &&
                scan.results.size <= scan.total && scan.integrityRejected in 0..scan.total)
            put("countersAreNonAdditive", true)
            put("analyticalResults", scan.successful)
            put("resultCount", scan.results.size)
            put("providerNoData", scan.noData)
            put("errors", scan.errors)
            put("structurallySkipped", scan.skipped)
            put("productionIntegrityRejected", scan.integrityRejected)
            put("integrityRejectionContext", if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS) "EXPECTED_DELAYED_FAIL_CLOSED" else "REQUIRES_PER_RESULT_INVESTIGATION")
            put("missingDiagnosticCodeCounts", JSONObject().apply {
                scan.missingSymbolDiagnostics.groupingBy { it.code }.eachCount().forEach { (code, count) -> put(code, count) }
            })
            put("verifiedTradeSignals", if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS) 0 else scan.results.count { it.signalValidity.name == "VALID" })
            put("historyInserted", scan.historyPersisted)
            put("errorMessage", scan.errorMessage?.let(::privacySafe) ?: JSONObject.NULL)
            put("historyError", scan.historyError?.let(::privacySafe) ?: JSONObject.NULL)
            put("scannedSymbols", JSONArray(scan.scannedSymbols.map(::privacySafe)))
            put("universeSymbols", JSONArray(scan.universeSymbols.map(::privacySafe)))
            put("providerNoDataSymbols", JSONArray(scan.providerNoDataSymbols.map(::privacySafe)))
            put("providerErrorSymbols", JSONArray(scan.providerErrorSymbols.map(::privacySafe)))
            put("unclassifiedMissingSymbols", JSONArray(scan.unclassifiedMissingSymbols.map(::privacySafe)))
            put("missingSymbolDiagnostics", JSONArray().apply {
                scan.missingSymbolDiagnostics.forEach { diagnostic ->
                    put(JSONObject().apply {
                        put("symbol", privacySafe(diagnostic.symbol))
                        put("code", diagnostic.code)
                        put("evidence", diagnostic.evidence)
                    })
                }
            })
            put("missingSymbolDiagnosticsCount", scan.missingSymbolDiagnostics.size)
            put("missingSymbolReasonsComplete", scan.universeSymbols.isNotEmpty() && scan.unclassifiedMissingSymbols.isEmpty())
            put("unclassifiedMissingSymbolCount", scan.unclassifiedMissingSymbols.size)
            put("missingRootCausesIndependentlyVerified", false)
            put("failedSymbols", JSONArray(scan.failedSymbols.map(::privacySafe)))
            put("opportunities", JSONArray().apply {
                scan.results.forEach { entry ->
                    put(JSONObject().apply {
                        put("symbol", privacySafe(entry.symbol))
                        put("source", privacySafe(entry.source))
                        put("direction", entry.direction)
                        put("dataMode", entry.dataMode.name)
                        put("signalValidity", entry.signalValidity.name)
                        put("decisionState", entry.decisionState.name)
                        put("score", entry.score)
                        put("rankingScore", entry.rankingScore)
                        put("dataConfidenceScore", entry.dataConfidenceScore)
                        put("exchangeTimestamp", entry.exchangeTimestamp)
                        put("receivedAt", entry.receivedAt)
                    })
                }
            })
        })
        root.put("scheduler", JSONObject().apply {
            put("autoScanEnabled", runtime.autoScanEnabled)
            put("cadenceMinutes", runtime.autoScanCadenceMinutes)
            put("mode", runtime.schedulerMode)
            put("lastStatus", runtime.lastAutoScanStatus)
            put("lastHeartbeatAt", runtime.lastHeartbeatAt)
            put("heartbeatAgeMs", ageMs(runtime.lastHeartbeatAt, capturedAt) ?: JSONObject.NULL)
            put("heartbeatOverThreeCadences", runtime.autoScanEnabled && ageMs(runtime.lastHeartbeatAt, capturedAt) != null &&
                ageMs(runtime.lastHeartbeatAt, capturedAt)!! > runtime.autoScanCadenceMinutes.coerceAtLeast(1).toLong() * 180000L)
            put("lastDurationMs", if (runtime.lastDurationMs > 0L && runtime.lastAutoScanStatus != "STOPPED") runtime.lastDurationMs else JSONObject.NULL)
            put("lastDurationEvidence", if (runtime.lastDurationMs > 0L && runtime.lastAutoScanStatus != "STOPPED") "RECORDED" else "NOT_MEASURED")
            put("successfulRuns", runtime.successfulRuns)
            put("successfulRunsDefinition", "COUNT_OF_COMPLETE_AND_PARTIAL_RUNS_NOT_FULL_VERIFIED_RUNS")
            put("strictCompleteRunsCount", JSONObject.NULL)
            put("failedRuns", runtime.failedRuns)
            put("skippedBusyRuns", runtime.skippedBusyRuns)
            put("skippedBusyRunsDefinition", "REJECTED_CONCURRENT_REQUESTS_NOT_FAILURES")
            put("consecutiveFailures", runtime.consecutiveFailures)
            put("bootRescheduleAt", runtime.bootRescheduleAt)
            put("monitoringStartedAt", runtime.monitoringStartedAt)
            put("deliveryWatchdog", DiagnosticStallPolicy.evaluate(runtime.autoScanEnabled,
                runtime.autoScanCadenceMinutes, runtime.lastHeartbeatAt, runtime.monitoringStartedAt, capturedAt).name)
        })
        root.put("notifications", JSONObject().apply {
            put("preferenceEnabled", settings.notifications)
            put("permissionGranted", runtime.notificationPermission)
            put("batteryOptimizationExempt", runtime.batteryOptimizationExempt)
            put("tradingViewHandlerAvailable", runtime.tradingViewHandlerAvailable)
        })
        root.put("findings", JSONArray().apply {
            observations(provider, runtime, scan, settings, capturedAt).forEach { (code, action) ->
                put(JSONObject().put("code", code).put("explanation", action))
            }
        })
        root.put("audit", JSONObject().apply {
            put("measurementSource", "CURRENT_DEVICE_PROCESS_AND_SETTINGS")
            put("providerVerification", if (provider.testedAt > 0L) "RECORDED_PROVIDER_PROBE" else "NOT_TESTED")
            put("liveTradingReady", false)
            put("independentWebsocketProbe", "NOT_TESTED")
            put("tradeExecution", "NOT_TESTED")
            put("fillAndSlippage", "NOT_TESTED")
            put("stability24h", "NOT_TESTED")
            put("stability48h", "NOT_TESTED")
            put("stability72h", "NOT_TESTED")
            put("security", JSONObject().apply {
                put("staticCodeReview", "NOT_RUN_ON_DEVICE")
                put("dependencyVulnerabilityScan", "NOT_RUN_ON_DEVICE")
                put("manifestExposureAudit", "NOT_RUN_ON_DEVICE")
                put("networkConfigurationAudit", "NOT_RUN_ON_DEVICE")
                put("runtimePenetrationTest", "NOT_TESTED")
                put("credentialFiltering", "BEST_EFFORT_NOT_A_SECURITY_CERTIFICATION")
            })
            put("coverage", JSONObject().apply {
                put("scanAvailable", scan != null)
                put("scanScope", "LEGACY_ANALYSIS_ONLY")
                put("delayedUniverseObservationAvailable", observation != null)
                put("delayedUniverseAttempted", observation?.triedBist ?: 0)
                put("delayedUniverseDiscovered", observation?.discoveredBist ?: 0)
                put("delayedObservationIsLiveSignal", false)
                put("unclassifiedMissingSymbols", scan?.unclassifiedMissingSymbols?.size ?: JSONObject.NULL)
                put("missingReasonEvidenceVerified", false)
                put("unitTestsOnThisDevice", "NOT_RUN")
                put("emulatorTestsOnThisDevice", "NOT_RUN")
                put("notificationDeliveryEndToEnd", "NOT_TESTED")
                put("bootRescheduleEndToEnd", "NOT_TESTED")
            })
        })
        val delayedObservation = context?.let { FullUniverseScanSnapshotStore(it).read() }
        val independent = IndependentScanAudit.evaluate(provider, scan, capturedAt, delayedObservation)
        root.put("independentScanAudit", JSONObject().apply {
            put("schema", "BGDK/1")
            put("observedGatesPass", independent.liveSignalEligible)
            put("liveOrderAuthorization", false)
            put("coveragePercent", independent.coveragePercent ?: JSONObject.NULL)
            put("scanAgeMs", independent.scanAgeMs ?: JSONObject.NULL)
            put("missingSymbols", independent.missingSymbols ?: JSONObject.NULL)
            put("findings", JSONArray().apply {
                independent.findings.forEach { finding ->
                    put(JSONObject().put("code", finding.code).put("severity", finding.severity).put("evidence", finding.evidence))
                }
            })
        })
        root.put("limits", "No independent websocket probe, live order, slippage, profitability or 24/48/72h stability verification")
        root.put("secretValuesIncluded", false)
        root.put("secretFiltering", "KNOWN_CONFIGURED_API_KEY_AND_COMMON_AUTH_TOKEN_PATTERNS")
        root.put("secretFilteringLimit", "Inspect exported data before sharing externally; unknown secret formats may not be detected")
        root.put("diagnosticEvidence", JSONObject().apply {
            put("source", "DEVICE_PERSISTENT_LOCAL_RECORDS")
            put("recordedAtEpochMs", capturedAt)
            val events = evidence?.events ?: emptyList()
            put("maxRetainedEvents", 150)
            put("eventCount", events.size)
            put("events", JSONArray().apply { events.forEach { put(it.json()) } })
            val pending = events.filter { it.code.startsWith("SCAN_STARTED_") }
                .count { start -> events.none { it.runId == start.runId && it.phase == "END" && it.at >= start.at } }
            put("unclosedRunRecords", pending)
            val overlaps = DiagnosticOverlapAudit.overlaps(events)
            put("overlapEvidence", JSONArray().apply {
                overlaps.forEach { overlap ->
                    put(JSONObject().put("firstRunId", overlap.firstRunId)
                        .put("secondRunId", overlap.secondRunId).put("overlapMs", overlap.overlapMs))
                }
            })
            put("overlapEvidenceScope", "BOUNDED_COMPLETED_RUN_INTERVALS_ONLY")
            put("busyRejectionsInJournal", events.count { it.code == "SCAN_ALREADY_RUNNING" })
            put("activeScan", if (evidence?.activeRunId == null) JSONObject().put("busy", false)
                else JSONObject().put("busy", true).put("runId", evidence.activeRunId)
                    .put("owner", evidence.activeOwner).put("ageMs", evidence.activeAgeMs ?: JSONObject.NULL))
            put("unclosedRunInference", "INDETERMINATE_PROCESS_EXIT_OR_INCOMPLETE_LOG_NOT_PROVEN_HANG")
            put("priorProcessExits", evidence?.processExits ?: JSONArray())
            put("notificationsSystemEnabled", evidence?.notificationsSystemEnabled ?: JSONObject.NULL)
            put("notificationChannels", JSONArray().apply {
                evidence?.channels?.forEach { channel ->
                    put(JSONObject().put("id", channel.id).put("importance", channel.importance)
                        .put("blocked", channel.importance == NotificationManager.IMPORTANCE_NONE))
                }
            })
            put("evidenceQuality", "RECORDED_NOT_INDEPENDENTLY_VERIFIED")
        })
        val deep = DeepDiagnosticCollector.build(provider, runtime, scan, evidence?.events ?: emptyList(), capturedAt, delayedObservation)
        root.put("deepDiagnostics", DeepDiagnosticCollector.serialize(deep))
        if (context != null) {
            val (previousAt, previousIssues) = DeepDiagnosticHistory.previous(context)
            root.put("deepDiagnosticsComparison", JSONObject().apply {
                put("hasPriorReport", previousAt > 0L)
                if (previousAt > 0L) {
                    val comparison = DeepDiagnosticEngine.compare(previousIssues, deep.checks, previousAt)
                    put("priorAtEpochMs", previousAt)
                    put("newIssueIds", JSONArray(comparison.newIssues))
                    put("continuingIssueIds", JSONArray(comparison.continuing))
                    put("notReverifiedIssueIds", JSONArray(comparison.notReverified))
                    put("verifiedResolvedIssueIds", JSONArray(comparison.resolved))
                }
            })
        }
        root.put("humanReadableReport", privacySafe(renderedText))
        // Redact individual string fields BEFORE JSON serialization; redacting the serialized
        // JSON string can accidentally corrupt escaping and create invalid output.
        val serialized = root.toString(2)
        if (context != null) runCatching { DeepDiagnosticHistory.save(context, deep, capturedAt) }
        return serialized
    }

    fun redact(report: String, secret: String): String = ReportRedactor.redact(report, secret)
}
