package tr.borsatakip.v5.scan

import kotlin.math.max
import kotlin.math.min

/** Explains, but does not replace, the audited FundamentalValuationEngine decision. */
object ValueOpportunityScoreEngine {
 enum class Strategy(val display: String, val valueWeight: Double, val qualityWeight: Double, val safetyWeight: Double) {
  BALANCED("Dengeli", .50, .30, .20),
  QUALITY("Kalite Öncelikli", .25, .50, .25),
  DISCOUNT("İskonto Öncelikli", .65, .20, .15)
 }
 enum class Group { FEATURED, WATCH, VALUE_TRAP, INSUFFICIENT }
 data class Score(
  val value: Int, val quality: Int, val safety: Int, val risk: Int,
  val total: Int, val group: Group, val explanation: String,
  val limitations: String,
 )
 data class Ranked(val assessment: FundamentalValuationEngine.Assessment, val score: Score?)
 private fun clamp(n: Double): Int = max(0, min(100, n.toInt()))

 /** Never assigns a numeric valuation rating where peer comparisons or financials are insufficient. */
 fun score(a: FundamentalValuationEngine.Assessment, strategy: Strategy): Score? {
  val m = a.multiples ?: return null
  val s = a.snapshot ?: return null
  if(a.status !in setOf(FundamentalValuationEngine.Status.RELATIVE_DISCOUNT,
      FundamentalValuationEngine.Status.NOT_DISCOUNT,
      FundamentalValuationEngine.Status.FINANCIAL_RISK)) return null
  if(a.comparisons.size < 2 || a.peerCount < FundamentalValuationEngine.MIN_PEERS) return null
  val discounts = a.comparisons.map { it.discountPct }.filter { it.isFinite() }
  if(discounts.size < 2) return null
  // Discount points map sector-relative valuation into a bounded, transparent indicator.
  val value = clamp(50.0 + discounts.average() * 1.25)
  val roe = m.roe ?: return null
  if(!roe.isFinite()) return null
  val profitQuality = clamp(roe * 320.0)
  val freeCash = if(s.businessType == FundamentalValuationEngine.BusinessType.NON_FINANCIAL)
   clamp(50.0 + (m.fcfYield ?: return null) * 600.0) else 50
  val quality = if(s.businessType == FundamentalValuationEngine.BusinessType.NON_FINANCIAL)
   clamp(profitQuality * .65 + freeCash * .35) else profitQuality
  // Sector-aware: financial businesses are not judged using industrial net debt / EBITDA.
  val safety = if(s.businessType == FundamentalValuationEngine.BusinessType.NON_FINANCIAL) {
   val debt = m.netDebtToEbitda ?: return null
   clamp(100.0 - max(0.0, debt) * 23.0)
  } else clamp(30.0 + roe * 340.0)
  val total = clamp(value * strategy.valueWeight + quality * strategy.qualityWeight + safety * strategy.safetyWeight)
  val group = when(a.status) {
   FundamentalValuationEngine.Status.FINANCIAL_RISK -> Group.VALUE_TRAP
   FundamentalValuationEngine.Status.RELATIVE_DISCOUNT ->
    if (total >= 65 && a.evidenceCount >= 2) Group.FEATURED else Group.WATCH
   else -> Group.WATCH
  }
  val explanation = when(group) {
   Group.FEATURED -> "Sektörel göreli iskonto ve mevcut kârlılık/borç kapıları olumlu; veri bağımsız doğrulanmadı."
   Group.WATCH -> "Kısmi model uygunluğu var; güçlü fırsat veya işlem önerisi olarak sınıflandırılmadı."
   Group.VALUE_TRAP -> "Bilanço veya kârlılık riski mevcut; düşük çarpanlar güvenli değer anlamına gelmiyor."
   Group.INSUFFICIENT -> "Veri yetersiz."
  }
  return Score(value, quality, safety, 100-safety, total, group, explanation,
   "Model ağırlıkları deneyseldir; alım zamanlaması, canlı fiyat ve KAP aslı bağımsız doğrulanmadı.")
 }
 fun rank(rows: List<FundamentalValuationEngine.Assessment>, strategy: Strategy): List<Ranked> =
  rows.map { Ranked(it, score(it, strategy)) }
   .sortedWith(compareBy<Ranked> {
    when(it.score?.group) { Group.FEATURED -> 0; Group.WATCH -> 1; Group.VALUE_TRAP -> 2; else -> 3 }
   }.thenByDescending { it.score?.total ?: -1 }.thenBy { it.assessment.symbol })
}
