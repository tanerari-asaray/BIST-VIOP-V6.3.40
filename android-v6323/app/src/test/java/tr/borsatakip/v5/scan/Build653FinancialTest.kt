package tr.borsatakip.v5.scan
import org.junit.Assert.*
import org.junit.Test
class Build653FinancialTest {
 private val now=1790000000000L
 private fun row(code:String)=FundamentalValuationEngine.Snapshot(code,"industry",FundamentalValuationEngine.BusinessType.NON_FINANCIAL,
 "TMS29","TRY",now-70L*86400000L,now-60L*86400000L,now-3600000L,
 1000.0,10.0,100.0,120.0,500.0,200.0,100.0,90.0,"https://www.kap.org.tr/tr/Bildirim/123",code,
 100.0,now-435L*86400000L,now-420L*86400000L,"TMS29")
 @Test fun growthUsesComparableTtm(){assertEquals(20.0,FundamentalValuationEngine.profitGrowthPct(row("TEST"))!!,0.001)}
 @Test fun lossBaseHasNoGrowthPercent(){assertNull(FundamentalValuationEngine.profitGrowthPct(row("TEST").copy(priorNetIncomeTtmTry=-10.0)))}
 @Test fun missingAndMismatchedPeriodsHaveNoGrowth(){
  assertNull(FundamentalValuationEngine.profitGrowthPct(row("TEST").copy(priorFinancialPeriodEndMs=null)))
  assertNull(FundamentalValuationEngine.profitGrowthPct(row("TEST").copy(priorAccountingBasis="IFRS")))
  assertNull(FundamentalValuationEngine.profitGrowthPct(row("TEST").copy(priorFinancialPeriodEndMs=now-100L*86400000L)))
 }
 @Test fun peersExposeActualContributorsAndExcludeOtherPeriod(){
  val rows=listOf(row("TEST"))+(1..5).map{row("PEER$it")}+row("OLD").copy(financialPeriodEndMs=now-160L*86400000L)
  val a=FundamentalValuationEngine.scan(rows.map{it.symbol},rows,now).first()
  assertTrue(a.comparisons.isNotEmpty())
  a.comparisons.forEach{assertEquals(5,it.peerCount);assertEquals((1..5).map { n->"PEER$n" },it.peerSymbols);assertFalse(it.peerSymbols.contains("OLD"))}
 }
}
