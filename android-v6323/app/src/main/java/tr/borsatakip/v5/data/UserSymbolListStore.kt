package tr.borsatakip.v5.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Opt-in user's own tickers: not a market-data feed. */
class UserSymbolListStore(private val context:Context) {
 private val file get() = File(context.filesDir,"bist-symbol-list-user-v1.txt")
 suspend fun loadSymbols():List<String> = withContext(Dispatchers.IO) {
  if(!file.isFile) emptyList() else SymbolUniverseTools.parseUserList(file.readText(Charsets.UTF_8))
 }
 suspend fun importSymbols(uri:Uri):Int=withContext(Dispatchers.IO) {
  val raw=context.contentResolver.openInputStream(uri)?.use { source ->
   val out=ByteArrayOutputStream();val buf=ByteArray(8192)
   while(true){val n=source.read(buf);if(n<0)break
    require(out.size()+n<=1_048_576){"Sembol listesi 1 MB üstünde"};out.write(buf,0,n)
   };out.toByteArray().toString(Charsets.UTF_8)
  } ?: error("Sembol listesi açılamadı")
  val parsed=SymbolUniverseTools.parseUserList(raw)
  val tmp=File(context.filesDir,file.name+".tmp")
  tmp.writeText(parsed.joinToString("\n"),Charsets.UTF_8)
  require(tmp.renameTo(file)){"Sembol listesi kaydedilemedi"}
  parsed.size
 }
}
