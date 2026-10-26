package tr.borsatakip.v5.data

import java.util.Locale

/** No remote lookups and no fabricated financial evidence. */
object SymbolUniverseTools {
 private val ticker=Regex("[A-Z0-9]{3,10}")
 fun normalize(input:Iterable<String>):List<String> = input.asSequence()
  .map { it.trim().uppercase(Locale.ROOT).removeSuffix(".IS") }
  .filter { it.matches(ticker) && it !in setOf("SYMBOL", "SEMBOL", "TICKER") }
  .distinct().sorted().take(2000).toList()
 fun parseUserList(raw:String):List<String> {
  require(raw.length <= 1_048_576) { "Sembol dosyası 1 MB sınırını aşıyor" }
  val firstColumn=raw.lineSequence()
   .map { it.trim().substringBefore(';').substringBefore(',').substringBefore('\t').trim().trim('"') }
   .filter { it.isNotBlank() }
   .take(2001).toList()
  require(firstColumn.size<=2000) { "En çok 2000 satırlık sembol listesi desteklenir" }
  val symbols=normalize(firstColumn)
  require(symbols.isNotEmpty()) { "Geçerli sembol kodu bulunamadı" }
  return symbols
 }
 fun count(symbols:List<String>, query:String):Int {
  val needle=query.trim().uppercase(Locale.ROOT)
  return if(needle.isEmpty())symbols.size else symbols.count {it.contains(needle)}
 }
 fun search(symbols:List<String>,query:String,limit:Int=120):List<String> {
  val needle=query.trim().uppercase(Locale.ROOT)
  return symbols.asSequence().filter{needle.isEmpty()||it.contains(needle)}.take(limit.coerceIn(1,2000)).toList()
 }
}
