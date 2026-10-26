package tr.borsatakip.v5.scan
import java.util.Locale
/** Provider outcomes are evidence and form a disjoint coverage partition. */
object ScanCoverageAudit {
 data class Summary(val universeCount:Int,val fetched:Int,val noData:Int,val providerError:Int,val unexplained:Int,val duplicateUniverseSymbols:Int,val unexpectedFetchedSymbols:List<String>) {
  val complete:Boolean get()=unexplained==0 && providerError==0 && noData==0 && unexpectedFetchedSymbols.isEmpty() && duplicateUniverseSymbols==0
  val partitionConsistent:Boolean get()=fetched+noData+providerError+unexplained==universeCount
 }
 fun reconcile(universe:Collection<String>,fetched:Collection<String>,noData:Collection<String>,providerErrors:Collection<String>):Summary {
  fun normalize(v:Collection<String>)=v.map{it.trim().uppercase(Locale.ROOT)}.filter{it.isNotBlank()}
  val raw=normalize(universe);val all=raw.toSet();val got=normalize(fetched).toSet();val missing=all-got
  val err=missing.intersect(normalize(providerErrors).toSet())
  val absent=(missing-err).intersect(normalize(noData).toSet());val unknown=missing-err-absent
  return Summary(all.size,got.intersect(all).size,absent.size,err.size,unknown.size,raw.size-all.size,(got-all).sorted())
 }
}
