package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class SymbolUniverseToolsTest {
 @Test fun importedCsvWithoutInternet(){
  assertEquals(listOf("AKBNK","THYAO"),SymbolUniverseTools.parseUserList("Sembol,Fiyat\nTHYAO,335\nakbnk;bank\nTHYAO,dup"))
 }
 @Test fun yahooSuffixNormalizedWithoutQuoteClaims(){
  assertEquals(listOf("BIMAS","THYAO"),SymbolUniverseTools.normalize(listOf("THYAO.IS"," BIMAS ","sembol","%%%")))
 }
 @Test fun searchIsLocalCaseInsensitiveAndLimited(){
  val items=listOf("AKBNK","AKGRT","THYAO")
  assertEquals(2,SymbolUniverseTools.count(items,"ak"))
  assertEquals(listOf("AKBNK"),SymbolUniverseTools.search(items,"ak",1))
 }
 @Test fun rejectsEmptyOrOverlongList(){
  assertThrows(IllegalArgumentException::class.java){SymbolUniverseTools.parseUserList("invalid-symbol")}
  assertThrows(IllegalArgumentException::class.java){SymbolUniverseTools.parseUserList("X".repeat(1_048_577))}
 }
}
