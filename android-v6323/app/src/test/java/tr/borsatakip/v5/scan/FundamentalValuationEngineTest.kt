package tr.borsatakip.v5.scan

import org.junit.Test
import org.junit.Assert.*

class FundamentalValuationEngineTest {
 private val now=1790000000000L
 private val e=FundamentalValuationEngine
 private fun row(code:String, cap:Double=1000.0, net:Double=100.0, equity:Double=500.0,
  fcf:Double=90.0, sector:String="industry",type:FundamentalValuationEngine.BusinessType=FundamentalValuationEngine.BusinessType.NON_FINANCIAL)
  =FundamentalValuationEngine.Snapshot(code,sector,type,"TMS29","TRY",now-70L*86400000L,now-60L*86400000L,now-3600000L,
    cap,cap/100,100.0,net,equity,200.0,100.0,fcf,"https://www.kap.org.tr/tr/Bildirim/123",code)
 @Test fun missingStatementsAreNotCheap(){
  assertEquals(FundamentalValuationEngine.Status.NO_FINANCIALS,e.scan(listOf("ASELS"),emptyList(),now).single().status)
 }
 @Test fun fivePeersTwoMultiplesRequired(){
  val stocks=listOf(row("TEST",cap=500.0))+(1..6).map{row("PEER"+it,cap=1200.0)}
  val a=e.scan(stocks.map{it.symbol},stocks,now).first()
  assertEquals(FundamentalValuationEngine.Status.RELATIVE_DISCOUNT,a.status)
  assertTrue(a.comparisons.count{it.discountPct>=20.0}>=2)
 }
 @Test fun cashFlowMustBePositive(){
  val stocks=listOf(row("TEST",cap=500.0,fcf=-10.0))+(1..6).map{row("PEER"+it,cap=1200.0)}
  assertEquals(FundamentalValuationEngine.Status.FINANCIAL_RISK,e.scan(stocks.map{it.symbol},stocks,now).first().status)
 }
 @Test fun unrelatedBankPeersNeverCount(){
  val stocks=listOf(row("TEST",cap=500.0))+(1..5).map{row("BANK"+it,cap=1200.0,type=FundamentalValuationEngine.BusinessType.BANK)}
  assertEquals(FundamentalValuationEngine.Status.INSUFFICIENT_PEERS,e.scan(stocks.map{it.symbol},stocks,now).first().status)
 }
 @Test fun oldPriceAndWrongCurrencyRejected(){
  val old=row("OLD").copy(marketObservedAtMs=now-11L*86400000L)
  val foreign=row("USD").copy(financialCurrency="USD")
  val rs=e.scan(listOf("OLD","USD"),listOf(old,foreign),now)
  assertTrue(rs.all{it.status==FundamentalValuationEngine.Status.INVALID_OR_STALE})
 }
 @Test fun duplicateFinancialRowsAreNeverSelected(){
  val a=row("DUP")
  assertEquals(FundamentalValuationEngine.Status.INVALID_OR_STALE,e.scan(listOf("DUP"),listOf(a,a),now).single().status)
 }
 @Test fun highPriceDoesNotExcludeDiscountedEquity(){
  val stocks=listOf(row("TEST",cap=5000.0,net=1000.0,equity=5000.0))+
   (1..6).map{row("PEER"+it,cap=12000.0,net=1000.0,equity=5000.0)}
  assertEquals(FundamentalValuationEngine.Status.RELATIVE_DISCOUNT,e.scan(stocks.map{it.symbol},stocks,now).first().status)
 }
 @Test fun bankLossIsFinancialRiskEvenWithoutFivePeers(){
  val bank=row("BANK",net=-20.0,type=FundamentalValuationEngine.BusinessType.BANK)
  val result=e.scan(listOf("BANK"),listOf(bank),now).single()
  assertEquals(FundamentalValuationEngine.Status.FINANCIAL_RISK,result.status)
  assertEquals(0,result.evidenceCount)
 }
 @Test fun bankNegativeEquityCannotBeReportedAsCheap(){
  val bank=row("BANK",equity=-50.0,type=FundamentalValuationEngine.BusinessType.BANK)
  assertEquals(FundamentalValuationEngine.Status.FINANCIAL_RISK,
    e.scan(listOf("BANK"),listOf(bank),now).single().status)
 }

}
