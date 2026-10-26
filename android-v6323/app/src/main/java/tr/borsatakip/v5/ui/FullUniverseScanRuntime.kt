package tr.borsatakip.v5.ui

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tr.borsatakip.v5.data.*
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.Stock
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.scan.BistScanGate
import tr.borsatakip.v5.scan.launchWithBistLease
import tr.borsatakip.v5.scan.FundamentalValuationEngine
import java.util.Locale
import java.util.UUID

/** Bounded, cancellable observation with an app-private checkpoint and shared scan lease. */
object FullUniverseScanRuntime {
    data class Progress(val runId:String="",val localMode:Boolean=true,val startedAt:Long=0L,
        val state:FullUniversePolicy.State=FullUniversePolicy.State.IDLE,
        val counters:FullUniversePolicy.Counters=FullUniversePolicy.Counters(),
        val entries:List<FullUniversePolicy.Entry> = emptyList(),val failure:String="",val currentSymbol:String="")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow(Progress())
    val snapshots:StateFlow<Progress> get()=mutable
    @Volatile private var job:Job?=null
    val isRunning:Boolean get()=job?.isCompleted==false
    @Volatile private var stopRequested=false
    @Synchronized fun cancel() { stopRequested=true;job?.cancel() }

    @Synchronized fun start(inputContext:Context,localMode:Boolean,resume:Boolean=false,retryFailuresOnly:Boolean=false) {
        if(isRunning)return
        stopRequested=false
        val context=inputContext.applicationContext
        val runId=UUID.randomUUID().toString()
        val lease=BistScanGate.tryAcquire(runId,"UNIVERSE_OBSERVATION")
        if(lease==null) {
            DiagnosticJournal(context).record("SCAN","CONCURRENCY","SCAN_ALREADY_RUNNING",runId)
            mutable.value=Progress(runId,localMode,state=FullUniversePolicy.State.FAILED,failure="Başka bir tarama çalışıyor. Tamamlanmasını bekleyin.")
            return
        }
        mutable.value=Progress(runId,localMode,System.currentTimeMillis(),FullUniversePolicy.State.RUNNING)
        job=scope.launchWithBistLease(lease) {
            try {
            val settings=SettingsStore(context)
            val connection=settings.connectionSnapshot()
            val sourceIdentity=ObservationSourcePolicy.identity(localMode,connection)
            val started=System.currentTimeMillis()
            val checkpoint=ObservationCheckpoint(context)
            val journal=DiagnosticJournal(context)
            val store=FullUniverseScanSnapshotStore(context)
            var counts=FullUniversePolicy.Counters()
            var state=FullUniversePolicy.State.RUNNING
            var note=if(localMode) "KISMİ KAPSAM: VİOP taranmadı (gecikmeli VİOP veri sağlayıcısı yapılandırılmamış). Yalnız BIST sembolleri gözlemlenir; TÜMÜ tam BIST+VİOP anlamına gelmez." else ""
            var universe=emptyList<String>()
            val entries=mutableListOf<FullUniversePolicy.Entry>()
            var symbolNow=""
            fun publish(save:Boolean=false) {
                if(!localMode && !settings.isConnectionCurrent(connection))
                    throw CancellationException("Bağlantı ayarları değişti; tarama yeni ayarlarla başlatılmalı.")
                mutable.value=Progress(runId,localMode,started,state,counts,entries.toList(),note,symbolNow)
                store.write(runId,if(localMode)"LOCAL_DELAYED" else "BACKEND_AUTHORIZED",state.name,counts,note,started,System.currentTimeMillis())
                if(save)checkpoint.write(localMode,universe,entries,sourceIdentity)
            }
            try {
                journal.record("SCAN","START","SCAN_STARTED_UNIVERSE",runId)
                publish()
                val saved=if(resume || retryFailuresOnly)checkpoint.read()?.takeIf { ObservationSourcePolicy.canResume(localMode,it.local,it.sourceIdentity,sourceIdentity,it.savedAt,System.currentTimeMillis()) } else null
                if(retryFailuresOnly && saved==null) error("RETRY_CHECKPOINT_NOT_AVAILABLE")
                val provider:MarketDataProvider=if(localMode)YahooFallbackProvider(context) else ProviderRouter(context,experimentalFallbackOverride=false,connectionOverride=connection)
                // VIOP discovery is independent: a BIST outage must not prevent it.
                if (!localMode) {
                    val backend=BackendProvider(context,connection)
                    try {
                        val contracts=backend.loadViop().getOrThrow().distinctBy { it.symbol }
                        counts=counts.copy(discoveredViop=contracts.size,viopDiscoveryOk=true)
                        if (contracts.isEmpty()) note+=" VİOP: sağlayıcı sözleşme listesi boş."
                        for (contract in contracts) {
                            ensureActive()
                            var row=contractEntry(contract)
                            try {
                                val q=backend.loadViopQuote(contract.symbol, observationOnly=true).getOrThrow()
                                val barsResult=backend.loadViopHistoryFlexible(contract.symbol,"1y","1d",0)
                                val bars=barsResult.getOrNull().orEmpty()
                                val validPrice=q.price.takeIf { contract.validity!=SignalValidity.REJECTED }
                                row=row.copy(price=validPrice,source=q.source,dataTimeMs=q.exchangeTimestamp,
                                    receivedMs=q.receivedAt,changePct=q.dailyChangePct,
                                    quality=if(validPrice==null)FullUniversePolicy.Quality.NO_DATA else if(!q.realtime || (q.delaySeconds?:0)>0)FullUniversePolicy.Quality.DELAYED else FullUniversePolicy.Quality.UNVERIFIED,
                                    reasonCode=if(validPrice==null)"CONTRACT_REJECTED" else null,
                                    candles=bars,closes=bars.map { it.close },trend=FullUniversePolicy.trend(bars.map { it.close }),
                                    detail=row.detail+" • Bildirilen gecikme: ${q.delaySeconds?.let { "$it sn" }?:"bilinmiyor"}"+
                                        (barsResult.exceptionOrNull()?.let { " • Grafik alınamadı: ${reason(it as? Exception ?: Exception(it))}" }?:""))
                            } catch(e:CancellationException) { throw e }
                            catch(e:Exception) { row=row.copy(price=null,quality=FullUniversePolicy.Quality.NO_DATA,reasonCode=reason(e)) }
                            entries+=row
                            counts=counts.copy(triedViop=counts.triedViop+1,
                                successfulViop=counts.successfulViop+if(row.price!=null)1 else 0,
                                noDataViop=counts.noDataViop+if(row.price==null&&row.reasonCode=="EMPTY_DATA")1 else 0,
                                errorsViop=counts.errorsViop+if(row.price==null&&row.reasonCode!="EMPTY_DATA")1 else 0)
                            publish()
                        }
                    } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { note+=" VİOP_KEŞİF_HATASI: ${reason(e)}";journal.record("SCAN","VIOP_DISCOVERY",reason(e),runId) }
                }
                universe=try { saved?.universe ?: if(localMode) DelayedScanScope.select(UserSymbolListStore(context).loadSymbols(),SettingsStore(context).cachedBistSymbols.toList())
                    .ifEmpty { provider.listSymbols() } else FullUniversePolicy.normalizeSymbols(provider.listSymbols()) }
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){note+=" BIST_KEŞİF_HATASI: ${reason(e)}";emptyList()}
                universe=FullUniversePolicy.normalizeSymbols(universe)
                if(universe.isEmpty()) note+=" BIST: sembol listesi alınamadı veya boş."
                entries.addAll(saved?.rows.orEmpty().filter { it.market==FullUniversePolicy.Market.BIST && it.symbol in universe && (!retryFailuresOnly || (it.price!=null && it.reasonCode==null && it.candles.isNotEmpty())) }.distinctBy { it.symbol })
                if(retryFailuresOnly) note+=" Yalnız önceki taramada başarısız/verisiz semboller tekrar denenecek."
                counts=counts.copy(discoveredBist=universe.size,bistDiscoveryOk=!localMode&&universe.isNotEmpty(),
                    triedBist=entries.count { it.market==FullUniversePolicy.Market.BIST },successfulBist=entries.count { it.market==FullUniversePolicy.Market.BIST&&it.price!=null },
                    noDataBist=entries.count { it.market==FullUniversePolicy.Market.BIST&&it.price==null && it.reasonCode=="EMPTY_DATA" },
                    errorsBist=entries.count { it.market==FullUniversePolicy.Market.BIST&&it.price==null && it.reasonCode!="EMPTY_DATA" })
                if(saved!=null)note+=" Kayıtlı sonuçlar tarihleriyle geri yüklendi; kalan semboller devam ediyor."
                publish(true)
                val remaining=ObservationRetryPolicy.remaining(universe, entries, retryFailuresOnly)
                coroutineScope {
                    val workerCount=if(localMode) 2 else 4
                    val symbols=Channel<String>(workerCount * 2)
                    val results=Channel<FullUniversePolicy.Entry>(workerCount * 2)
                    val feeder=launch {
                        try { for(symbol in remaining) symbols.send(symbol) }
                        finally { symbols.close() }
                    }
                    val workers=List(workerCount) {
                        launch {
                            for(symbol in symbols) {
                                val tick=System.nanoTime()
                                val result=ObservationRetryPolicy.execute(
                                    fetch={ if(provider is YahooFallbackProvider) provider.fetchObservation(symbol) else provider.fetchOne(symbol) },
                                    validate={ stock ->
                                        val row=stockEntry(symbol,stock,null,localMode)
                                        when { row.price==null -> "EMPTY_DATA"; row.candles.isEmpty() -> "HISTORY_EMPTY"; else -> null }
                                    },
                                    classify=::reason)
                                results.send(stockEntry(symbol,result.value,result.error,localMode).copy(
                                    durationMs=(System.nanoTime()-tick)/1_000_000,
                                    attempts=result.attempts, attemptErrors=result.errors,
                                    detail=stockEntry(symbol,result.value,result.error,localMode).detail +
                                        " • Deneme: ${result.attempts}" + if(result.errors.isEmpty()) "" else " • Hata geçmişi: ${result.errors.joinToString() }"))
                                if(localMode)delay(500)
                            }
                        }
                    }
                    val closer=launch {
                        try { feeder.join();workers.joinAll() }
                        finally { results.close() }
                    }
                    for(row in results) {
                        symbolNow=row.symbol;entries+=row
                        counts=counts.copy(triedBist=counts.triedBist+1,
                            successfulBist=counts.successfulBist+if(row.price!=null)1 else 0,
                            noDataBist=counts.noDataBist+if(row.price==null&&row.reasonCode=="EMPTY_DATA")1 else 0,
                            errorsBist=counts.errorsBist+if(row.price==null&&row.reasonCode!="EMPTY_DATA")1 else 0)
                        publish(counts.triedBist%4==0)
                        if(counts.triedBist%8==0||row.reasonCode!=null||row.attemptErrors.isNotEmpty())
                            journal.record("SCAN","PROGRESS",row.reasonCode?:"UNIVERSE_PROGRESS",runId,row.durationMs,counts.triedBist)
                    }
                    closer.join()
                }
                // Valuation is optional and independently labelled; no financial values are inferred from price.
                val financials=FundamentalsBatchClient(context,connectionOverride=connection)
                if(financials.hasLocalImport() || (SettingsStore(context).baseUrl.isNotBlank()&&SettingsStore(context).apiKey.isNotBlank())) {
                    try {
                        val batch=if(financials.hasLocalImport())financials.loadLocal()else financials.load()
                        val assessed=FundamentalValuationEngine.scan(batch.items.map { it.symbol },batch.items,System.currentTimeMillis()).associateBy { it.symbol }
                        for(i in entries.indices) { if(entries[i].market!=FullUniversePolicy.Market.BIST)continue;val a=assessed[entries[i].symbol]?:continue
                            entries[i]=entries[i].copy(financialNote=batch.sourceNote+"\n"+a.reason+"\n"+a.comparisons.joinToString("\n") {
                                "${it.metric}: ${"%.2f".format(Locale.ROOT,it.own)} • sektör medyanı ${"%.2f".format(Locale.ROOT,it.median)} • ${it.peerCount} emsal"
                            }, relativeDiscountPct=if(a.status==FundamentalValuationEngine.Status.RELATIVE_DISCOUNT)a.comparisons.map { it.discountPct }.average()else null)
                        }
                    }catch(e:CancellationException){throw e}catch(e:Exception){note+=" Temel veri yüklenemedi: ${reason(e)}"}
                }
                state=when {
                    counts.triedBist != counts.discoveredBist -> FullUniversePolicy.State.PARTIAL
                    counts.failures>0 || entries.any { it.market==FullUniversePolicy.Market.BIST && it.reasonCode!=null } -> FullUniversePolicy.State.PARTIAL
                    localMode -> FullUniversePolicy.State.PARTIAL // BIST-only coverage must not imply a complete BIST+VIOP scan
                    counts.attemptedDiscoveredUniverse -> FullUniversePolicy.State.FINISHED_UNVERIFIED
                    else -> FullUniversePolicy.State.PARTIAL
                }
            } catch(e:CancellationException){state=FullUniversePolicy.State.CANCELLED;note+=if(stopRequested) " KULLANICI_TARAFINDAN_DURDURULDU" else " BEKLENMEYEN_IPTAL"}
            catch(e:Exception){state=FullUniversePolicy.State.FAILED;note+=" ${reason(e)}"}
            finally {
                withContext(NonCancellable+Dispatchers.IO) {
                    if(localMode || settings.isConnectionCurrent(connection)) publish(true)
                    else mutable.value=Progress(runId,localMode,started,FullUniversePolicy.State.CANCELLED,
                        failure="Bağlantı ayarları değişti; kayıtlı eski sonuçlar kullanılmadı.")
                    journal.record("SCAN","END",state.name,runId,lease.elapsedMs,counts.triedBist)
                }
            }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                mutable.value=Progress(runId,localMode,state=FullUniversePolicy.State.FAILED,
                    failure="Tarama başlatılamadı veya sonuçlar kaydedilemedi.")
            }
        }.also { launched ->
            launched.invokeOnCompletion { failure ->
                val last = mutable.value
                if (failure != null && last.runId == runId && last.state == FullUniversePolicy.State.RUNNING)
                    mutable.value = last.copy(
                        state = if (failure is CancellationException) FullUniversePolicy.State.CANCELLED else FullUniversePolicy.State.FAILED,
                        failure = if (failure is CancellationException) "Tarama iptal edildi." else "Tarama başlatılamadı.")
            }
        }
    }
    internal fun stockEntry(symbol:String,stock:Stock?,error:String?,local:Boolean):FullUniversePolicy.Entry {
        val bars=ObservationAnalysis.clean(stock?.candles.orEmpty())
        val price=(stock?.quotePrice?:bars.lastOrNull()?.close)?.takeIf { it.isFinite()&&it>0 }
        val prior=bars.dropLast(1).lastOrNull()?.close
        return FullUniversePolicy.Entry(FullUniversePolicy.Market.BIST,symbol,stock?.companyName?:"BIST payı",price,stock?.currency,
            FullUniversePolicy.trend(bars.map { it.close }),if(price==null)FullUniversePolicy.Quality.NO_DATA else if(local||stock?.isRealtime!=true)FullUniversePolicy.Quality.DELAYED else FullUniversePolicy.Quality.UNVERIFIED,
            stock?.source?:"Kaynak yok",stock?.dataTimestamp?:0L,stock?.receivedAt?:System.currentTimeMillis(),
            "Gözlem verisidir; canlı işlem sinyali değildir. Günlük son mum henüz kapanmamış olabilir.",bars.map { it.close },
            if(price==null)error?:"EMPTY_DATA" else error ?: if(bars.isEmpty())"HISTORY_EMPTY" else null,bars,
            prior?.takeIf { it>0 }?.let { price?.let { p -> (p/it-1)*100 } })
    }
    private fun contractEntry(c:ViopContract):FullUniversePolicy.Entry {
        val p=c.lastPrice?.takeIf { it.isFinite()&&it>0&&c.validity!=SignalValidity.REJECTED }
        return FullUniversePolicy.Entry(FullUniversePolicy.Market.VIOP,c.symbol,"${c.underlying} • ${c.expiry} • ${c.contractType}",p,c.currency,
            FullUniversePolicy.Trend.UNKNOWN,if(p==null)FullUniversePolicy.Quality.NO_DATA else FullUniversePolicy.Quality.UNVERIFIED,
            c.providerLabel,c.dataTimestamp,c.receivedAt,"Çarpan ${c.multiplier?:"—"} • Açık pozisyon ${c.openInterest?:"—"}. Sözleşme fiyatı hisse ucuzluğu değildir.",
            reasonCode=if(p==null)"EMPTY_DATA"else null,changePct=c.dailyChangePct,
            financialNote="VİOP göreli avantajı için teorik fiyat, fonlama, spread ve likidite gerekir; şu an hesaplanmadı.")
    }
    internal fun reason(e:Exception):String {
        if(e is ProviderException)return e.code.name
        val m=e.message.orEmpty().uppercase(Locale.ROOT)
        return when { "429" in m||"RATE_LIMIT" in m->"RATE_LIMIT";"401" in m||"403" in m->"AUTH_ERROR"
            "504" in m||"TIMEOUT" in m||e is java.net.SocketTimeoutException->"NETWORK_TIMEOUT"
            "503" in m->"SERVICE_UNAVAILABLE";"SCHEMA" in m->"SCHEMA_ERROR";"EMPTY" in m->"EMPTY_DATA";e is java.net.UnknownHostException->"DNS_ERROR"
            e is java.io.IOException->"NETWORK_ERROR";else->"PROVIDER_ERROR" }
    }
}

