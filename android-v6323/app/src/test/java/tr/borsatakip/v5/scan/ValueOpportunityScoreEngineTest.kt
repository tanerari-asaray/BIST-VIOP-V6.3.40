package tr.borsatakip.v5.scan

import org.junit.Assert.*
import org.junit.Test

class ValueOpportunityScoreEngineTest {
 private val m=FundamentalValuationEngine.Multiples(8.0, .9, 5.0, .18, .08, 1.4)
 private val s=FundamentalValuationEngine.Snapshot("TEST", "DEMIR", FundamentalValuationEngine.BusinessType.NON_FINANCIAL, "TMS29", "TRY", 1760000000000L,
  1760500000000L, 1760600000000L, 1000000000.0, 10.0, 100000000.0, 125000000.0,
  1100000000.0, 200000000.0, 280000000.0, 80000000.0, "https://www.kap.org.tr/tr/")
 private val c=listOf(FundamentalValuationEngine.Comparison("F/K",8.0,11.0,27.0,5),FundamentalValuationEngine.Comparison("PD/DD",.9,1.3,30.0,5))
 private fun a(st:FundamentalValuationEngine.Status) = FundamentalValuationEngine.Assessment("TEST",st,"örnek",s,m,c,2,5)
 @Test fun missingEvidenceNeverGetsScore() {
  assertNull(ValueOpportunityScoreEngine.score(FundamentalValuationEngine.Assessment("TEST",FundamentalValuationEngine.Status.NO_FINANCIALS,"yok",null),ValueOpportunityScoreEngine.Strategy.BALANCED))
  assertNull(ValueOpportunityScoreEngine.score(FundamentalValuationEngine.Assessment("TEST",FundamentalValuationEngine.Status.INSUFFICIENT_PEERS,"az",s,m,c,0,3),ValueOpportunityScoreEngine.Strategy.BALANCED))
 }
 @Test fun usesStrategyWeightsWithoutChangingAuditStatus() {
  val balanced=ValueOpportunityScoreEngine.score(a(FundamentalValuationEngine.Status.RELATIVE_DISCOUNT),ValueOpportunityScoreEngine.Strategy.BALANCED)!!
  val discount=ValueOpportunityScoreEngine.score(a(FundamentalValuationEngine.Status.RELATIVE_DISCOUNT),ValueOpportunityScoreEngine.Strategy.DISCOUNT)!!
  assertTrue(balanced.total in 0..100)
  assertTrue(discount.total in 0..100)
  assertEquals(balanced.value,discount.value)
  assertNotEquals(balanced.total,discount.total)
 }
 @Test fun financialRiskCannotBecomeFeaturedOnGoodMultiples() {
  val r=ValueOpportunityScoreEngine.score(a(FundamentalValuationEngine.Status.FINANCIAL_RISK),ValueOpportunityScoreEngine.Strategy.DISCOUNT)
  assertEquals(ValueOpportunityScoreEngine.Group.VALUE_TRAP,r?.group)
 }
 @Test fun noTimingScoreFabrication() {
  val r=ValueOpportunityScoreEngine.score(a(FundamentalValuationEngine.Status.RELATIVE_DISCOUNT),ValueOpportunityScoreEngine.Strategy.BALANCED)!!
  assertTrue(r.limitations.contains("zamanlaması"))
 }
}
