package tr.borsatakip.v5.ui

import tr.borsatakip.v5.scan.FundamentalValuationEngine as Engine
import kotlin.math.abs

/** Presentation only: the existing valuation engine owns all calculations and peer rules. */
object ChartFinancialCards {
    data class Metric(val key: String, val title: String, val formula: String,
                      val meaning: String, val value: Double?, val comment: String)
    data class Page(val symbol: String, val applicable: Boolean, val snapshot: Engine.Snapshot?,
                    val metrics: List<Metric>, val comparisons: List<Engine.Comparison>,
                    val conclusion: String, val reason: String)

    fun build(symbol: String, market: String, assessment: Engine.Assessment?, now: Long): Page {
        val applicable = market == "BIST"
        val matching = assessment?.takeIf { it.symbol == symbol && it.snapshot?.symbol == symbol }
        val invalid = matching?.snapshot?.let { Engine.validate(it, now) }
        val snapshot = if (applicable && matching != null && invalid == null &&
            matching.status != Engine.Status.INVALID_OR_STALE && matching.status != Engine.Status.NO_FINANCIALS)
            matching.snapshot else null
        val values = snapshot?.let(Engine::multiples)
        val reason = when {
            !applicable -> "F/K, PD/DD ve şirket borçluluk oranları VİOP sözleşmesinde uygulanmaz."
            assessment != null && matching == null -> "Bu sembole ait geçerli finansal kayıt bulunamadı."
            invalid != null -> "Finansal kayıt kullanılamıyor: $invalid."
            snapshot == null -> matching?.reason ?: "Finansal veri bekleniyor. Fiyat ve teknik göstergeler bilanço yerine kullanılamaz."
            else -> matching?.reason.orEmpty()
        }
        val unavailable = if (applicable) "Hesaplanamadı; geçerli finansal tablo gerekli." else "VİOP sözleşmesinde uygulanmaz."
        val metrics = listOf(
            Metric("F/K", "F/K · KÂRA GÖRE FİYATLAMA", "Hisse fiyatı / hisse başına TTM net kâr",
                "Şirketin son 12 aylık 1 TL net kârı için ödenen fiyatı gösterir.", values?.pe,
                when {
                    snapshot == null -> unavailable
                    (snapshot.netIncomeTtmTry ?: 0.0) <= 0 -> "Net kâr sıfır veya negatif; F/K hesaplanmaz."
                    else -> "Aynı sektördeki şirketlerle, kâr büyümesi ve tek seferlik gelirlerle birlikte değerlendirilir. Düşük F/K tek başına ucuzluk kanıtı değildir."
                }),
            Metric("PD/DD", "PD/DD · ÖZKAYNAĞA GÖRE FİYATLAMA", "Piyasa değeri / özkaynak",
                "Şirketin defter değerinin kaç katından fiyatlandığını gösterir.", values?.pb,
                when {
                    snapshot == null -> unavailable
                    (snapshot.equityTry ?: 0.0) <= 0 -> "Özkaynak sıfır veya negatif; PD/DD hesaplanmaz."
                    else -> "1'in altı tek başına ucuzluk değildir. Özkaynak kârlılığı, varlık kalitesi ve sektör yapısı birlikte incelenir."
                }),
            Metric("Net Borç/FAVÖK", "NET BORÇ / FAVÖK · BORÇLULUK", "Net borç / son 12 aylık FAVÖK",
                "Borç yükünü faaliyet kazancına göre karşılaştırır; kesin borç ödeme süresi değildir.", values?.netDebtToEbitda,
                when {
                    snapshot == null -> unavailable
                    snapshot.businessType != Engine.BusinessType.NON_FINANCIAL -> "Banka ve finans şirketlerinde bu oran uygulanmaz."
                    (snapshot.ebitdaTtmTry ?: 0.0) <= 0 -> "FAVÖK sıfır veya negatif; oran hesaplanmaz."
                    (snapshot.netDebtTry ?: 0.0) < 0 -> "Net nakit pozisyonu. Borç eğilimi ve sektör normu için ek dönem verisi gerekir."
                    else -> "Düşük oran daha sınırlı borç yüküne işaret edebilir. Sektör normu, nakit akışı ve borcun dönemsel gelişimi ayrıca değerlendirilir."
                })
        )
        val comparisons = if (snapshot == null) emptyList() else matching?.comparisons.orEmpty().filter { c ->
            val own = when (c.metric) { "F/K" -> values?.pe; "PD/DD" -> values?.pb; "FD/FAVÖK" -> values?.evEbitda; else -> null }
            own != null && c.own.isFinite() && c.median.isFinite() && c.own > 0 && c.median > 0 &&
                abs(c.own - own) <= abs(own) * 1e-8 && c.peerCount >= Engine.MIN_PEERS &&
                c.peerSymbols.distinct().size == c.peerCount && symbol !in c.peerSymbols
        }
        val conclusion = when {
            !applicable -> "ŞİRKET ORANLARI UYGULANMAZ"
            snapshot == null -> "DEĞERLEME HESAPLANAMADI"
            matching?.status == Engine.Status.FINANCIAL_RISK -> "FİNANSAL RİSK"
            matching?.status == Engine.Status.RELATIVE_DISCOUNT && comparisons.count { it.discountPct >= 20 } >= 2 -> "VERİYE GÖRE SEKTÖREL İSKONTO"
            comparisons.size < 2 -> "EMSAL VERİSİ YETERSİZ"
            else -> "UCUZLUK DOĞRULANMADI"
        }
        return Page(symbol, applicable, snapshot, metrics, comparisons, conclusion, reason)
    }
}
