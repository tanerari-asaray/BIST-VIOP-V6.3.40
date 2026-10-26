package tr.borsatakip.v5.data

import android.content.Context
import tr.borsatakip.v5.data.DeepDiagnosticEngine.Status as S
import tr.borsatakip.v5.data.DeepDiagnosticEngine.Priority as P
import tr.borsatakip.v5.data.DeepDiagnosticEngine.Proof as R
import org.json.JSONArray
import org.json.JSONObject
import tr.borsatakip.v5.scan.BistScanMode
import tr.borsatakip.v5.scan.ScanState

/** Purely local snapshot conversion. No hidden probes, no network, no fabricated successes. */
object DeepDiagnosticCollector {
    // Keep evidence bounded at the journal source, not the detailed list of distinct issues.
    fun build(
        provider: ProviderReadinessSnapshot,
        runtime: RuntimeReadinessDiagnostics.Snapshot,
        scan: ScanState?,
        events: List<DiagnosticJournal.Event>,
        capturedAt: Long,
        delayed: FullUniverseScanSnapshotStore.Snapshot? = null
    ): DeepDiagnosticEngine.Result {
        val entries = mutableListOf<DeepDiagnosticEngine.Check>()
        fun add(
            id: String, module: String, code: String, status: DeepDiagnosticEngine.Status,
            priority: DeepDiagnosticEngine.Priority, proof: DeepDiagnosticEngine.Proof,
            at: Long, summary: String, actual: String, expected: String,
            impact: String, possible: String, confirmed: String? = null,
            fix: String, retest: String, group: String,
            financialRisk: String = "Doğrulanmayan verilerden canlı işlem sinyali üretilmemeli.",
            count: Int = 1, first: Long = at, evidence: String = proof.name,
            confidence: String = "DOĞRULANMADI", affected: String = module
        ) {
            entries += DeepDiagnosticEngine.Check(id, module, code, status, priority, proof, at,
                summary, actual, expected, impact, possible, confirmed, fix, retest,
                group, affectedFeatures = affected, financialRisk = financialRisk,
                confidence = confidence, occurrenceCount = count, firstSeenAt = first,
                lastSeenAt = at, evidence = evidence)
        }
        if (provider.failureCode == ProviderFailureCode.BACKEND_URL_MISSING) {
            add("BACKEND-URL-LOCAL", "Bağlantı", "BACKEND_URL_MISSING", S.CRITICAL, P.P0,
                R.LOCAL_SETTING, capturedAt, "HTTPS sunucu adresi eksik", "Adres yapılandırılmamış",
                "Geçerli HTTPS adresi", "Üretim verisi alınamaz", "Yerel ayar boş",
                "Backend adresi eksik", "Bağlantı ayarlarından HTTPS adresini tanımla",
                "Yeni bağlantı testi çalıştır", "AUTH_CONFIG", confidence="KESİN: YEREL AYAR")
        }
        val hasLocalKey = runtime.apiKeyConfigured
        add("BACKEND-KEY-LOCAL", "API/Kimlik Doğrulama", "API_KEY_CONFIGURATION",
            if (hasLocalKey) S.PASS else S.CRITICAL, if (hasLocalKey) P.P3 else P.P0,
            R.LOCAL_SETTING, capturedAt,
            if (hasLocalKey) "Anahtar cihazda tanımlı" else "Uygulama API anahtarı eksik",
            if (hasLocalKey) "Anahtar var; sunucuda kabulü henüz bu kontrolle kanıtlanmadı" else "API anahtarı yok",
            "Anahtar cihaz Keystore ayarlarında bulunmalı ve sunucu kabul testi ayrıca çalışmalı",
            "Üretim veri istekleri kimlik doğrulamasını geçemeyebilir",
            "Yeni sürümün farklı uygulama kimliği eski ayarı taşımamış olabilir",
            if (!hasLocalKey) "Yerel yapılandırmada anahtar alanı boş" else null,
            "Anahtarı uygulamanın güvenli ayarlarında tanımla; değeri rapora ekleme",
            "Yetkili /v1/health çağrısı yeni ölçüm zamanında 401 dönmemeli; veri hazır olma ayrı kontrol edilmeli",
            "AUTH_CONFIG", evidence = "LOCAL_SETTING:apiKeyConfigured=$hasLocalKey",
            confidence = if (hasLocalKey) "YALNIZ YEREL VARLIK" else "KESİN: YEREL AYAR")
        val failure = provider.failureCode.name
        val wasProbed = provider.testedAt > 0L
        val age = if (wasProbed && capturedAt >= provider.testedAt) capturedAt - provider.testedAt else null
        val recent = age != null && age <= 300_000L
        val probeProof = if (wasProbed) R.RECORDED_PROBE else R.NOT_COLLECTED
        val status = when {
            !hasLocalKey -> S.NOT_TESTED
            !wasProbed -> S.NOT_TESTED
            provider.state == ProviderState.PROVIDER_READY && recent && provider.healthOk -> S.PASS
            !recent -> S.UNVERIFIED
            failure == "AUTH_ERROR" -> S.FAIL
            failure == "NONE" -> S.UNVERIFIED
            else -> S.FAIL
        }
        add("BACKEND-AUTH-RESPONSE", "API/Kimlik Doğrulama", "AUTH_RESULT", status, P.P1, probeProof,
            provider.testedAt.takeIf { it > 0 } ?: 0L,
            "Sunucu kimlik doğrulaması", "Durum=${provider.state}; son hata=$failure; test yaşı=${age ?: "ÖLÇÜLEMEDİ"} ms",
            "Yeni bir yetkili API isteğinde 2xx veya veri kaynağına özgü 503; 401 olmamalı",
            "Tarama ve finansal veri uçları reddedilebilir",
            "Anahtar farklı, eksik veya süresi geçmiş olabilir; sunucunun gerçek yanıtı gerekli",
            null,
            "Anahtar eşleşmesini güvenli ayarlarla düzelt; sunucu anahtarını halka açık etme",
            "İstek kimliği, saat, HTTP kodu ve yetkili test sonucu kaydedilmeli",
            "AUTH_CONFIG", evidence = "RECORDED_STATE:$failure;ts=${provider.testedAt}",
            confidence = "BELİRTİ KAYITLI; KÖK NEDEN KANITLANMADI")
        add("BACKEND-HEALTH", "Railway/Backend", "HEALTH_PROBE",
            when {
                !wasProbed -> S.NOT_TESTED
                !recent -> S.UNVERIFIED
                provider.healthOk -> S.PASS
                failure in setOf("SERVER_ERROR", "DATA_SOURCE_NOT_CONNECTED", "HEALTH_ERROR") -> S.FAIL
                else -> S.NOT_TESTED
            }, P.P1, probeProof, provider.testedAt,
            "Backend sağlık kontrolü", "healthOk=${provider.healthOk}; kod=$failure; yaş=${age ?: "YOK"}ms",
            "Ağ erişimi, kimlik doğrulama ve piyasa-verisi hazır olma ayrı raporlanmalı",
            "Backend erişimi veri kaynağının hazır olduğunu kanıtlamaz",
            "503 lisanslı veri eksikliği de olabilir; 401 auth nedeniyle sonraki aşama çağrılmamış olabilir",
            null,
            "Sunucu health/auth/data-ready bölümlerini ayır; uygun veri sağlanana kadar canlı işlem kilitli",
            "Ayrı ulaşılabilirlik, 401, 503 ve hazır olma denemelerini sunucu loguyla eşleştir",
            "PROVIDER_CONNECTIVITY", evidence="RECORDED_HEALTH:healthOk=${provider.healthOk}")
        val quoted = listOf(
            Triple("BIST-UNIVERSE-ENDPOINT", "BIST Sembol", provider.bistSymbolsOk),
            Triple("BIST-QUOTE-ENDPOINT", "BIST Fiyat", provider.bistQuoteOk),
            Triple("BIST-HISTORY-ENDPOINT", "BIST Tarihçe", provider.bistHistoryOk),
            Triple("VIOP-CONTRACTS-ENDPOINT", "VİOP Sözleşme", provider.viopContractsOk),
            Triple("VIOP-QUOTE-ENDPOINT", "VİOP Fiyat", provider.viopQuoteOk),
            Triple("VIOP-HISTORY-ENDPOINT", "VİOP Tarihçe", provider.viopHistoryOk)
        )
        quoted.forEach { (id, module, ok) ->
            // Readiness snapshot contains booleans but no per-step attempted flags.
            // False does NOT mean that an endpoint request was tried and failed.
            add(id, module, "STEP_EXECUTION_UNVERIFIED", S.UNVERIFIED, P.P1, R.NOT_COLLECTED, 0L,
                "$module gerçek endpoint denetimi", "Kayıtlı boolean=$ok; bu adımın çağrılıp çağrılmadığı bilinmiyor",
                "Gerçek requestId, attempted, HTTP durumu ve kaynak damgası bulunan ayrı kontrol",
                "Piyasa kapsama veya işlem uygunluğu kanıtlanamaz",
                "Üst aşama başarısız olmuş ya da bu alt adım hiç çalışmamış olabilir", null,
                "Her uç için attempted/testedAt/status/evidence kaydı ekle",
                "Gerçek servis çağrısı ve lisans/veri tazeliği kanıtıyla yeniden çalıştır",
                "PROVIDER_READINESS", evidence="BOOLEAN_ONLY:not-independent-proof")
        }
        if (scan == null) {
            add("SCAN-LAST-RUN", "BIST Tarama", "LEGACY_SCAN_NOT_AVAILABLE", S.NOT_TESTED, P.P1,
                R.NOT_COLLECTED, 0L, "Eski canlı BIST analiz taraması kaydı yok",
                if (delayed == null) "Yeni evren gözlemi de kayıtlı değil"
                else "Gecikmeli evren gözlemi ayrı kaydedildi: ${delayed.triedBist}/${delayed.discoveredBist}; durum=${delayed.status}",
                "Tam ve zaman damgalı tarama kaydı", "Kapsam ve başarım değerlendirilemez", "Tarama yapılmamış ya da süreç belleği kaybolmuş olabilir", null,
                "Tarama kayıtlarının kalıcı metadatasını sakla", "Bir tarama başlatıp doğrulanmış sonuçlarını incele", "SCAN_COVERAGE")
        } else {
            val run = scan.scanRun
            val complete = run?.status?.name == "COMPLETE"
            add("SCAN-LAST-RUN", "BIST Tarama", "SCAN_COVERAGE",
                if (complete && scan.successful == scan.total && scan.total > 0) S.PASS else S.WARNING,
                P.P1, R.RECORDED_RUN, run?.scanCompletedAt ?: 0L,
                "BIST tarama kapsamı", "Evren=${scan.total}; başarılı=${scan.successful}; durum=${run?.status ?: "YOK"}",
                "Sembol evreni ve eksiklerin tamamı neden koduyla sayılmalı", "Eksik semboller yanlış tam-kapsam algısı yaratır",
                "Veri eksikliği, sembol eşleme veya sağlayıcı sorunu; kök neden sembol bazlı incelenmeli", null,
                "Eksik sembolleri neden koduyla izle ve yeniden tarama yap", "Tam kapsamı kaynak tarihi, payda ve sembol listesiyle doğrula",
                "SCAN_COVERAGE", evidence="RUN_ID_PRESENT=${run?.scanRunId != null};NO_DATA=${scan.noData};ERRORS=${scan.errors}")
            val symbolDiagnostics = scan.missingSymbolDiagnostics.distinctBy { it.symbol }
            symbolDiagnostics.forEach { diag ->
                // User symbols are public tickers, but never put free text from upstream in the report.
                val symbol = diag.symbol.takeIf { it.matches(Regex("[A-Z0-9_.-]{1,20}")) } ?: "INVALID_SYMBOL"
                val code = diag.code.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) } ?: "UNKNOWN"
                val sourceReported = diag.evidence.startsWith("PROVIDER_REPORTED:")
                add("SCAN-SYMBOL-$symbol", "BIST Sembol", code, S.NO_DATA, P.P2,
                    R.RECORDED_RUN, run?.scanCompletedAt ?: 0L,
                    "$symbol için analiz verisi eksik", "Kaydedilen sınıf=$code; kaynak neden doğrulama=${if (sourceReported) "sağlayıcı beyanı" else "KANITLANMADI"}",
                    "Geçerli OHLCV, para birimi ve tarih damgası", "Bu hisse kapsamdan eksik kalır",
                    "Sağlayıcı veri yok, boş bar, hata veya eşleştirme problemi olabilir", null,
                    "Sembolün kaynağını ve zaman damgasını kontrol et; uydurma sonuç üretme",
                    "Aynı sembol için kaynaklı/uyumlu OHLCV analizi yeniden tamamlanmalı",
                    "SCAN_DATA_GAPS", evidence="RUN:${run?.scanRunId ?: "UNKNOWN"};SYMBOL:$symbol;CLASS:$code")
            }
            if (scan.scanMode == BistScanMode.DELAYED_ANALYSIS) add("TRADE-GATE-DELAYED", "Risk Kapısı", "DELAYED_TRADE_BLOCK",
                S.SAFETY_BLOCK, P.P0, R.RECORDED_RUN, run?.scanCompletedAt ?: 0L,
                "Gecikmeli veriyle canlı işlem engellendi", "Deneysel/gecikmeli kaynak işlem sinyali olarak kullanılamaz",
                "Lisanslı, doğrulanmış ve güncel veri olmadan canlı sinyal engellenmeli",
                "Beklenen koruma; arıza değildir", "Tasarım gereği güvenlik kapısı", "GECİKMELİ VERİ KORUMASI",
                "Koruma etkin kalmalı", "Gecikmeli girdilerden canlı sinyal oluşmadığını test et", "TRADE_SAFETY",
                financialRisk="Risk azaltıcı koruma etkin", confidence="KAYITLI GÜVENLİK KARARI")
        }
        if (delayed != null) {
            add("DELAYED-UNIVERSE-OBSERVATION", "Gecikmeli BIST Gözlem",
                "LOCAL_OBSERVATION_NOT_TRADE_VERIFIED", S.WARNING, P.P2, R.RECORDED_RUN,
                delayed.updatedAt, "Gecikmeli evren taraması ayrı kayıtlı",
                "Mod=${delayed.mode}; durum=${delayed.status}; BIST denenen=${delayed.triedBist}/${delayed.discoveredBist}; veri alınan=${delayed.successfulBist}; VİOP denenen=${delayed.triedViop}/${delayed.discoveredViop}",
                "Kaynak güncelliği doğrulanmış sembol listesi ayrı, canlı işlem taraması ayrı tutulmalıdır",
                "Gecikmeli gözlem canlı emir veya tam BIST/VİOP kapsamı kanıtı değildir",
                "Yerel evren listesi ve fiyat hakları ayrı doğrulanmalı", null,
                "Sembol bazlı yeniden doğrulama ve gerçek veri kaynağı bütünlük testi",
                "Yeni evren gözlemi ve gerçek zamanlı analiz için ayrı kabul testleri",
                "DELAYED_OBSERVATION", evidence="LOCAL_RUN:${delayed.runId};MODE:${delayed.mode};STATUS:${delayed.status}")
        }
        if (!runtime.notificationPermission) add("NOTIFICATION-PERMISSION", "Bildirim", "POST_NOTIFICATIONS_DENIED", S.WARNING,
            P.P2, R.DEVICE_SETTING, capturedAt, "Android bildirim izni kapalı", "permission=false", "Bildirim izni açık",
            "Uyarı ekranda görüntülenmeyebilir", "İzin kullanıcı veya sistem tarafından reddedilmiş olabilir", "İzin kapalı (yerel Android kontrolü)",
            "Android bildirim iznini aç; kullanıcı tercihini ayrıca kontrol et", "Gerçek cihaz bildirim teslimi ayrıca test edilmeli", "NOTIFICATION_DELIVERY", confidence="KESİN: CİHAZ İZNİ")
        add("NOTIFICATION-END-TO-END", "Bildirim", "DELIVERY_NOT_VERIFIED", S.NOT_TESTED, P.P1,
            R.NOT_COLLECTED, 0L, "Uçtan uca bildirim teslimi", "Oluşturma, teslim ve ekranda görünme kanıtı yok",
            "Gerçek cihazda oluşum/kanal/görünürlük/süre kaydı", "Önemli risk uyarıları kaçırılabilir",
            "İzin veya pil kısıtları etkileyebilir; test edilmedi", null, "Cihaz izinleri ve arka plan senaryolarını çalıştır",
            "Üretim koşulunda bildirim oluşturma ve gerçek görünürlük testi", "NOTIFICATION_DELIVERY")
        if (runtime.autoScanEnabled) {
            add("AUTOMATION-WATCHDOG", "Otomatik Tarama", "HEARTBEAT_EVIDENCE",
                if (runtime.lastHeartbeatAt <= 0L) S.UNMEASURABLE else S.UNVERIFIED,
                P.P1, R.RECORDED_EVENT, runtime.lastHeartbeatAt,
                "Arka plan tarama sürekliliği", "Son heartbeat=${runtime.lastHeartbeatAt}; son durum=${runtime.lastAutoScanStatus}",
                "Çalışırken düzenli heartbeat ve tekrar eden başarılı tarama", "Uyarıların zamanında üretilmesi aksayabilir",
                "Android Doze/izin/zamanlayıcı etkisi olabilir; tek heartbeat ile kesin neden çıkmaz", null,
                "24/48/72 saat cihaz testlerini çalıştır", "En az 24 saat ölçümlü tarama aralığı raporu", "BACKGROUND_EXECUTION")
        } else add("AUTOMATION-DISABLED", "Otomatik Tarama", "DISABLED_BY_USER", S.NOT_APPLICABLE,
            P.P3, R.LOCAL_SETTING, capturedAt, "Otomatik tarama kullanıcı tercihiyle kapalı", "enabled=false", "Kapalı tercih servis çalıştırmaz",
            "Otomatik analiz planlanmaz", "Ayar kapalı; servis arızası kanıtı yok", "KULLANICI TERCİHİ",
            "Otomatik tarama istenirse kullanıcının açması gerekir", "Açıldıktan sonra scheduler gerçek cihazda test edilmeli", "AUTOMATION_CONFIG")
        // The journal has only a fixed-size retained window. Report all distinct error types
        // in that window; never silently drop the 100th distinct issue.
        val errorEvents = events.filter {
            it.code in setOf("SCAN_FAILED", "SCAN_CANCELLED", "SCAN_INTERRUPTED", "SCAN_ALREADY_RUNNING",
                "AUTO_CONFIG_ERROR", "AUTO_FAILED", "PROVIDER_NO_DATA", "ANALYSIS_OR_PROVIDER_FAILURE", "API_KEY_MISSING", "AUTH_ERROR") || it.code.endsWith("_ERROR")
        }.groupBy { it.subsystem + "_" + it.code }
        errorEvents.forEach { (eventKey, rows) ->
            val ev = rows.maxByOrNull { it.at } ?: return@forEach
            if (eventKey == "PROVIDER_API_KEY_MISSING" && !hasLocalKey) return@forEach // same local root already shown
            add("EVENT-$eventKey", ev.subsystem, ev.code,
                if (ev.code == "SCAN_ALREADY_RUNNING") S.WARNING else S.FAIL, P.P2,
                R.RECORDED_EVENT, ev.at, "Tanılama günlüğünde kaydedilmiş hata türü", "Kod=${ev.code}; olay=${rows.size}",
                "Kayıtlı olayın yeniden testte görülmemesi ve başarılı alt test", "${ev.subsystem} işlevi kısmen çalışmamış olabilir",
                "Gerçek kök neden için ayrı HTTP/istisna/cihaz kanıtı gerekir", null,
                "İlgili modülü güvenli biçimde yeniden çalıştır ve kanıtları karşılaştır", "Başarılı aynı kapsamlı yeniden test",
                "EVENT_${ev.subsystem}", count=rows.size, first=rows.minOf { it.at }, evidence="JOURNAL:${ev.subsystem}/${ev.code};events=${rows.size}")
        }
        listOf("CANLI_EMİR_TESTİ", "FILL_SLIPPAGE_TESTİ", "WEBSOCKET_PROBE", "24_48_72_SAAT_STABİLİTE",
            "CİHAZ_ANR_KÖK_NEDENİ", "LİSANS_DOĞRULAMASI").forEach { id ->
            add("UNVERIFIED-$id", "Bağımsız Kabul", id, S.NOT_TESTED, P.P1, R.NOT_COLLECTED,
                0L, "$id kapsamı", "Bağımsız üretim kanıtı yok", "Gerçek üretim/cihaz test kaydı",
                "Canlı işlem güvenilirliği doğrulanamaz", "Test kanıtı toplanmadı", null,
                "Uygun yetki ve test ortamı bulunduğunda test et", "Tarih, senaryo ve kanıtla geçiş", "ACCEPTANCE_GAPS")
        }
        return DeepDiagnosticEngine.evaluate(entries)
    }

    fun serialize(result: DeepDiagnosticEngine.Result): JSONObject = JSONObject().apply {
        val s = result.summary
        put("schemaVersion", "DEEP-DIAGNOSIS/1")
        put("counts", JSONObject().apply {
            put("total", s.total); put("critical", s.critical); put("failures", s.failures)
            put("notApplicable", s.notApplicable); put("warnings", s.warnings); put("passed", s.passed); put("notTested", s.notTested)
            put("unmeasurable", s.unmeasurable); put("unverified", s.unverified)
            put("noData", s.noData); put("safetyBlocks", s.safetyBlocks)
            put("rootGroups", s.rootGroups)
        })
        put("rootCauseClusters", JSONObject().apply {
            result.groups.forEach { (name, codes) -> put(name, JSONArray(codes)) }
        })
        put("issues", JSONArray().apply {
            result.checks.forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("code", c.code); put("module", c.module)
                put("priority", c.priority.name); put("status", c.status.name)
                put("statusLabel", c.status.label); put("proof", c.proof.name)
                put("observedAt", c.observedAt); put("firstSeenAt", c.firstSeenAt)
                put("lastSeenAt", c.lastSeenAt); put("occurrenceCount", c.occurrenceCount)
                put("summary", c.summary); put("expected", c.expected); put("actual", c.actual)
                put("impact", c.impact); put("affectedFeatures", c.affectedFeatures)
                put("financialRisk", c.financialRisk); put("possibleCause", c.possibleCause)
                put("confirmedCause", c.confirmedCause ?: JSONObject.NULL)
                put("confidence", c.confidence); put("evidence", c.evidence)
                put("reproduction", c.reproduction); put("fix", c.fix); put("retest", c.retest)
                put("rootCauseGroup", c.group); put("fixState", c.currentFixState)
            }) }
        })
    }
}

/** Previous report summary only, not prices, tokens, URLs or personal data. */
object DeepDiagnosticHistory {
    private const val NAME = "deep_diagnostic_history_v1"
    fun previous(context: Context): Pair<Long, Map<String, Long>> = runCatching {
        val json = JSONObject(context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("last", "{}") ?: "{}")
        val entries = json.optJSONObject("failures") ?: JSONObject()
        val map = mutableMapOf<String, Long>()
        val names = entries.keys()
        while (names.hasNext()) { val key = names.next(); map[key] = entries.optLong(key) }
        json.optLong("at") to map
    }.getOrDefault(0L to emptyMap())
    fun save(context: Context, result: DeepDiagnosticEngine.Result, capturedAt: Long) {
        val issues = JSONObject()
        val (priorAt, old) = previous(context)
        val diff = DeepDiagnosticEngine.compare(old, result.checks, priorAt)
        // Unverified disappearance must survive across report generations.
        diff.notReverified.forEach { id -> issues.put(id, old[id] ?: 0L) }
        result.checks.filter { it.status in setOf(DeepDiagnosticEngine.Status.CRITICAL,
            DeepDiagnosticEngine.Status.FAIL, DeepDiagnosticEngine.Status.WARNING, DeepDiagnosticEngine.Status.NO_DATA) }
            .forEach { issues.put(it.id, it.lastSeenAt) }
        // Synchronous commit prevents the next exported report from racing this persisted snapshot.
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("last", JSONObject().put("at", capturedAt).put("failures", issues).toString()).commit()
    }
}
