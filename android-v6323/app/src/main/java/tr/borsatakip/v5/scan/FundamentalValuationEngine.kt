package tr.borsatakip.v5.scan

import kotlin.math.abs

object FundamentalValuationEngine {
 const val MIN_PEERS = 5
 enum class BusinessType { NON_FINANCIAL, BANK, FINANCIAL }
 enum class Status { RELATIVE_DISCOUNT, NOT_DISCOUNT, FINANCIAL_RISK, INSUFFICIENT_PEERS, INVALID_OR_STALE, NO_FINANCIALS }
 data class Snapshot(val symbol:String,val sector:String,val businessType:BusinessType,val accountingBasis:String,
  val financialCurrency:String,val financialPeriodEndMs:Long,val disclosurePublishedAtMs:Long,
  val marketObservedAtMs:Long,val marketCapTry:Double,val priceTry:Double,val sharesOutstanding:Double,
  val netIncomeTtmTry:Double?,val equityTry:Double?,val ebitdaTtmTry:Double?,
  val netDebtTry:Double?,val freeCashFlowTtmTry:Double?,val disclosureUrl:String,val companyName:String?=null,
  val priorNetIncomeTtmTry:Double?=null,val priorFinancialPeriodEndMs:Long?=null,
  val priorDisclosurePublishedAtMs:Long?=null,val priorAccountingBasis:String?=null)
 data class Multiples(val pe:Double?,val pb:Double?,val evEbitda:Double?,val roe:Double?,
   val fcfYield:Double?,val netDebtToEbitda:Double?)
 data class Comparison(val metric:String,val own:Double,val median:Double,val discountPct:Double,val peerCount:Int,val peerSymbols:List<String> = emptyList())
 data class Assessment(val symbol:String,val status:Status,val reason:String,val snapshot:Snapshot?,
  val multiples:Multiples?=null,val comparisons:List<Comparison> = emptyList(),val evidenceCount:Int=0,val peerCount:Int=0)
 fun profitGrowthPct(s:Snapshot):Double? {
  val prior=s.priorNetIncomeTtmTry ?: return null
  val current=s.netIncomeTtmTry ?: return null
  val period=s.priorFinancialPeriodEndMs ?: return null
  val published=s.priorDisclosurePublishedAtMs ?: return null
  val delta=(s.financialPeriodEndMs-period)/86400000L
  if(!prior.isFinite() || !current.isFinite() || prior<=0 || current<0 || delta !in 350L..380L ||
     s.priorAccountingBasis!=s.accountingBasis || published<period || published>s.disclosurePublishedAtMs) return null
  return ((current/prior-1)*100).takeIf { it.isFinite() }
 }
 private fun ratio(a:Double?,b:Double?):Double? = if(a!=null&&b!=null&&a.isFinite()&&b.isFinite()&&a>0&&b>0)
  (a/b).takeIf{it.isFinite()&&it>0} else null
 fun multiples(s:Snapshot):Multiples {
  val non=s.businessType==BusinessType.NON_FINANCIAL
  return Multiples(ratio(s.marketCapTry,s.netIncomeTtmTry),ratio(s.marketCapTry,s.equityTry),
    if(non) ratio(s.netDebtTry?.let{s.marketCapTry+it},s.ebitdaTtmTry) else null,
    if((s.equityTry?:0.0)>0&&s.netIncomeTtmTry!=null) (s.netIncomeTtmTry/s.equityTry!!).takeIf{it.isFinite()} else null,
    if(non&&s.freeCashFlowTtmTry!=null) (s.freeCashFlowTtmTry/s.marketCapTry).takeIf{it.isFinite()} else null,
    if(non&&s.netDebtTry!=null&&(s.ebitdaTtmTry?:0.0)>0)
      (s.netDebtTry/s.ebitdaTtmTry!!).takeIf{it.isFinite()} else null)
 }
 fun validate(s:Snapshot,now:Long):String? {
  if(!s.symbol.matches(Regex("[A-Z0-9]{3,10}"))||s.sector.isBlank()||s.sector.length>90||
    s.accountingBasis !in setOf("TMS29","IFRS","LOCAL_GAAP")||s.financialCurrency!="TRY")
    return "Sektör, muhasebe veya para birimi geçersiz"
  // Require a publication-specific reference. The KAP homepage is not evidence of a filing.
  val kapPath = runCatching { java.net.URI(s.disclosureUrl) }.getOrNull()
  if (kapPath?.scheme != "https" || kapPath.host !in setOf("www.kap.org.tr", "kap.org.tr") ||
      kapPath.path.isNullOrBlank() || kapPath.path == "/")
      return "KAP bildirim bağlantısı eksik"
  if(s.financialPeriodEndMs !in 1000000000000L..now||
     s.disclosurePublishedAtMs !in s.financialPeriodEndMs..(now+120000L)||
     now-s.disclosurePublishedAtMs>210L*86400000L ||
     now-s.financialPeriodEndMs>450L*86400000L) return "Finansal rapor tarihi eski/geçersiz"
  if(s.marketObservedAtMs !in 1000000000000L..(now+120000L)||
     now-s.marketObservedAtMs>3L*86400000L) return "Piyasa değeri eski/geçersiz"
  if(!s.priceTry.isFinite()||!s.marketCapTry.isFinite()||!s.sharesOutstanding.isFinite()||
     s.priceTry<=0||s.marketCapTry<=0||s.sharesOutstanding<=0||
     abs(s.priceTry*s.sharesOutstanding/s.marketCapTry-1.0)>0.05)
     return "Fiyat, piyasa değeri ve pay sayısı uyuşmuyor"
  if(listOf(s.netIncomeTtmTry,s.equityTry,s.ebitdaTtmTry,s.netDebtTry,s.freeCashFlowTtmTry)
       .any{it!=null&&!it.isFinite()}) return "Finansal sayı geçersiz"
  if(s.netIncomeTtmTry==null||s.equityTry==null) return "Net kâr/özkaynak eksik"
  if(s.businessType==BusinessType.NON_FINANCIAL&&
    (s.ebitdaTtmTry==null||s.netDebtTry==null||s.freeCashFlowTtmTry==null))
     return "FAVÖK/net borç/serbest nakit akımı eksik"
  return null
 }
 private fun metric(m:Multiples,n:String):Double? = when(n) {
  "F/K"->m.pe;"PD/DD"->m.pb;"FD/FAVÖK"->m.evEbitda;else->null
 }
 fun scan(universe:List<String>,raw:List<Snapshot>,now:Long):List<Assessment> {
  val symbols=universe.distinct();val grouped=raw.filter{it.symbol in symbols.toSet()}.groupBy{it.symbol}
  val valid=grouped.mapNotNull{(key,rows)->rows.singleOrNull()?.takeIf{validate(it,now)==null}?.let{key to it}}.toMap()
  val ratios=valid.mapValues{multiples(it.value)}
  val groups=valid.values.groupBy{Triple(it.sector.uppercase(),it.businessType,it.accountingBasis)}
  return symbols.map{symbol->
   val entries=grouped[symbol].orEmpty()
   if(entries.isEmpty()) return@map Assessment(symbol,Status.NO_FINANCIALS,
      "Doğrulanabilir finansal tablo yok; nominal fiyat ucuzluk kanıtı değildir",null)
   val s=valid[symbol] ?: return@map Assessment(symbol,Status.INVALID_OR_STALE,
     if(entries.size!=1) "Çakışan finansal kayıtlar" else validate(entries.first(),now)?:"Geçersiz kayıt",entries.first())
   val m=ratios.getValue(symbol)
   val peers=groups[Triple(s.sector.uppercase(),s.businessType,s.accountingBasis)].orEmpty().filter{it.symbol!=symbol && it.financialPeriodEndMs==s.financialPeriodEndMs &&
     abs(it.marketObservedAtMs-s.marketObservedAtMs)<=86400000L}
   val names=if(s.businessType==BusinessType.NON_FINANCIAL) listOf("F/K","PD/DD","FD/FAVÖK") else listOf("F/K","PD/DD")
   val c=names.mapNotNull{name->
    val own=metric(m,name)?:return@mapNotNull null
    val matched=peers.mapNotNull { p -> metric(ratios.getValue(p.symbol),name)?.let { p.symbol to it } }
    val values=matched.map { it.second }.sorted()
    if(values.size<MIN_PEERS)return@mapNotNull null
    val median=(values[(values.size-1)/2]+values[values.size/2])/2.0
    if(median>0&&median.isFinite()) Comparison(name,own,median,(1-own/median)*100,values.size,matched.map { it.first }.sorted()) else null
   }
   val risk=when{
    (s.netIncomeTtmTry?:0.0)<=0->"TTM net kâr negatif/sıfır"
    (s.equityTry?:0.0)<=0->"Özkaynak negatif/sıfır"
    m.roe==null||m.roe<0.08->"ROE %8 altında"
    s.businessType==BusinessType.NON_FINANCIAL&&(m.fcfYield?:-1.0)<=0->"Serbest nakit akımı pozitif değil"
    s.businessType==BusinessType.NON_FINANCIAL&&(m.netDebtToEbitda?:Double.POSITIVE_INFINITY)>3->"Net borç/FAVÖK 3 üzeri"
    else->null
   }
   // Accounting losses/negative equity are risks even when multiples cannot be computed.
   if(risk!=null) return@map Assessment(symbol,Status.FINANCIAL_RISK,
    "Finansal risk: "+risk+"; emsal iskontosu iddia edilmez",s,m,c,0,peers.size)
   if(peers.size<MIN_PEERS||c.size<2) return@map Assessment(symbol,Status.INSUFFICIENT_PEERS,
    "Aynı sektör, işletme türü ve muhasebe bazında en az 5 emsal ve 2 çarpan gerekli",s,m,c,0,peers.size)
   val count=c.count{it.discountPct>=20}
   val notes=c.joinToString("; "){it.metric+": %"+it.discountPct.toInt()+" (n="+it.peerCount+")"}
   when{
    count>=2->Assessment(symbol,Status.RELATIVE_DISCOUNT,
      "En az 2 sektörel çarpanda ≥%20 göreli iskonto; kârlılık/borç/nakit akımı kapısı geçti. "+notes+
      ". İçsel değer veya işlem sinyali değildir.",s,m,c,count,peers.size)
    else->Assessment(symbol,Status.NOT_DISCOUNT,"Sektörel iskonto kanıtı yetersiz. "+notes,s,m,c,count,peers.size)
   }
  }
 }
}
