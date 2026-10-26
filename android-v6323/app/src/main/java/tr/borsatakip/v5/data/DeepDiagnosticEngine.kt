package tr.borsatakip.v5.data

/** Evidence-only fault inventory. No network I/O; every observed failure is retained. */
object DeepDiagnosticEngine {
    enum class Status(val label: String) {
        PASS("SAĞLIKLI"), WARNING("UYARI"), FAIL("HATALI"), CRITICAL("KRİTİK"),
        NOT_TESTED("TEST EDİLMEDİ"), UNMEASURABLE("ÖLÇÜLEMEDİ"),
        UNVERIFIED("DOĞRULANAMADI"), NO_DATA("VERİ YOK"),
        SAFETY_BLOCK("GÜVENLİK ENGELİ AKTİF"), NOT_APPLICABLE("UYGULANAMAZ")
    }
    enum class Priority { P0, P1, P2, P3 }
    enum class Proof { LOCAL_SETTING, RECORDED_PROBE, RECORDED_RUN, RECORDED_EVENT, DEVICE_SETTING, NOT_COLLECTED }
    data class Check(
        val id: String, val module: String, val code: String,
        val status: Status, val priority: Priority, val proof: Proof,
        val observedAt: Long, val summary: String,
        val actual: String, val expected: String,
        val impact: String, val possibleCause: String,
        val confirmedCause: String? = null, val fix: String,
        val retest: String, val group: String,
        val reproduction: String = "Yeniden üretim adımları için ilgili modülün kayıtlı testini çalıştır.",
        val affectedFeatures: String = module,
        val financialRisk: String = "Canlı işlem uygunluğu ayrıca doğrulanmalıdır.",
        val confidence: String = "DOĞRULANMADI",
        val occurrenceCount: Int = 1,
        val firstSeenAt: Long = observedAt,
        val lastSeenAt: Long = observedAt,
        val evidence: String = proof.name,
        val currentFixState: String = "DOĞRULANMADI"
    )
    data class Summary(
        val total: Int, val critical: Int, val failures: Int,
        val warnings: Int, val passed: Int, val notTested: Int,
        val unmeasurable: Int, val unverified: Int, val noData: Int,
        val safetyBlocks: Int, val rootGroups: Int, val notApplicable: Int = 0
    )
    data class Result(val checks: List<Check>, val summary: Summary, val groups: Map<String, List<String>>) {
        fun text(): String = buildString {
            append("\n\n[21] BIST–VİOP | DERİN SİSTEM SAĞLIK VE HATA ANALİZ RAPORU\n")
            append("Rapor durumu: ${if (summary.critical > 0) "KRİTİK" else if (summary.failures > 0) "HATALI" else if (summary.warnings > 0) "UYARI" else "TAM SAĞLIKLI OLDUĞU DOĞRULANMADI"}\n")
            append("Toplam kontrol=${summary.total}; KRİTİK=${summary.critical}; HATALI=${summary.failures}; UYARI=${summary.warnings}; SAĞLIKLI=${summary.passed}; TEST EDİLMEDİ=${summary.notTested}; ÖLÇÜLEMEDİ=${summary.unmeasurable}; DOĞRULANAMADI=${summary.unverified}; VERİ YOK=${summary.noData}; GÜVENLİK ENGELİ=${summary.safetyBlocks}; UYGULANAMAZ=${summary.notApplicable}.\n")
            append("Ayrı sorun kayıtları ile ilişkili kök neden grupları aynı şey değildir. Bağımsız kök neden grubu sayısı=${summary.rootGroups}; grup adı nedeni ispatlamaz.\n")
            append("Kanıt kapsamı: yerel ayarlar, saklanan provider sonucu, son tarama ve sınırlı cihaz olay günlüğü. Yeni ağ/cihaz testi otomatik başlatılmadı.\n")
            append("SONUÇ: ${if (summary.critical > 0 || summary.failures > 0) "BAZI TEMEL BİLEŞENLER ÇALIŞMIYOR; CANLI İŞLEM İÇİN HAZIR DEĞİL." else "TÜM SİSTEMİN ÇALIŞTIĞI KANITLANMADI; DOĞRULANMAYAN KONTROLLER VAR."}\n")
            append("\nKÖK NEDEN İLİŞKİ HARİTASI\n")
            groups.forEach { (root, ids) -> append("$root • ilişkili kayıt=${ids.size} • kimlikler=${ids.joinToString(", ")}\n") }
            append("\nTÜM BULGULAR / KAYIT SINIRI OLMADAN\n")
            checks.forEachIndexed { index, c ->
                append("\n${index + 1}. [${c.priority}] [${c.status.label}] ${c.id} / ${c.code}\n")
                append("Modül=${c.module}; İlk gözlem=${c.firstSeenAt}; Son gözlem=${c.lastSeenAt}; Tekrar=${c.occurrenceCount}; Kanıt türü=${c.proof}\n")
                append("Sorun: ${c.summary}\nBeklenen: ${c.expected}\nGerçekte: ${c.actual}\n")
                append("Etkilenen özellikler: ${c.affectedFeatures}\nSisteme etkisi: ${c.impact}\nFinansal risk: ${c.financialRisk}\n")
                append("Olası neden: ${c.possibleCause}\nDoğrulanmış neden: ${c.confirmedCause ?: "KANITLANMADI"}\nKök neden güveni: ${c.confidence}\n")
                append("Bağlantılı grup: ${c.group}\nKanıt: ${c.evidence}\nYeniden üretim: ${c.reproduction}\n")
                append("Düzeltme: ${c.fix}\nKabul/yeniden test: ${c.retest}\nÇözüm durumu: ${c.currentFixState}; test yapılmadan 'çözüldü' sayılmaz.\n")
            }
        }
    }
    /** Do not cap, truncate, or silently discard distinct checks. */
    fun evaluate(checks: List<Check>): Result {
        val identities = HashSet<String>()
        require(checks.all { it.id.isNotBlank() && identities.add(it.id) }) { "Duplicate/blank diagnostic IDs" }
        val byStatus = checks.groupingBy { it.status }.eachCount()
        fun n(s: Status) = byStatus[s] ?: 0
        val groupMap = checks.filter { it.status in setOf(Status.FAIL, Status.CRITICAL, Status.WARNING, Status.NO_DATA) }
            .groupBy({ it.group }, { it.id })
        return Result(checks, Summary(checks.size, n(Status.CRITICAL), n(Status.FAIL), n(Status.WARNING),
            n(Status.PASS), n(Status.NOT_TESTED), n(Status.UNMEASURABLE), n(Status.UNVERIFIED),
            n(Status.NO_DATA), n(Status.SAFETY_BLOCK), groupMap.size, n(Status.NOT_APPLICABLE)), groupMap)
    }
    data class Delta(val newIssues: List<String>, val continuing: List<String>, val notReverified: List<String>, val resolved: List<String>)
    /** Absence of a finding does NOT close it; only a passing check with the SAME id and newer evidence can. */
    fun compare(previousFailures: Map<String, Long>, current: List<Check>, previousAt: Long): Delta {
        val observed = current.associateBy { it.id }
        val active = current.filter { it.status in setOf(Status.CRITICAL, Status.FAIL, Status.WARNING, Status.NO_DATA) }
        return Delta(
            active.filter { it.id !in previousFailures }.map { it.id },
            active.filter { it.id in previousFailures }.map { it.id },
            previousFailures.keys.filter { id ->
                val c = observed[id]
                c == null || (c.status !in setOf(Status.CRITICAL, Status.FAIL, Status.WARNING, Status.NO_DATA) &&
                    (c.status != Status.PASS || c.observedAt <= previousAt || c.proof == Proof.NOT_COLLECTED))
            },
            previousFailures.keys.filter { id ->
                val c = observed[id]
                c != null && c.status == Status.PASS && c.observedAt > previousAt && c.proof != Proof.NOT_COLLECTED
            }
        )
    }
}
