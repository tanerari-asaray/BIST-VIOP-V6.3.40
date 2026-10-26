package tr.borsatakip.v5.worker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import tr.borsatakip.v5.ui.FullUniverseScanActivity
import tr.borsatakip.v5.ui.FullUniverseScanRuntime

/** User-started finite data synchronization; persistent results are independently checkpointed. */
class UniverseObservationService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var collector: Job?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP") { FullUniverseScanRuntime.cancel();stopSelf();return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("universe_progress","Tarama ilerlemesi",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,648,Intent(this,FullUniverseScanActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,649,Intent(this,javaClass).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        fun notification(message:String)=NotificationCompat.Builder(this,"universe_progress")
            .setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("BIST / VİOP • Gözlem")
            .setContentText(message).setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"Durdur",stop).build()
        startForeground(648,notification("Tarama hazırlanıyor"))
        if(!FullUniverseScanRuntime.isRunning)FullUniverseScanRuntime.start(this,intent?.getBooleanExtra("local",true)?:true,intent?.getBooleanExtra("resume",false)?:false,intent?.getBooleanExtra("retryFailuresOnly",false)?:false)
        if(!FullUniverseScanRuntime.isRunning){stopSelf();return START_NOT_STICKY}
        collector?.cancel()
        collector=scope.launch {
            FullUniverseScanRuntime.snapshots.collect { s ->
                if(s.runId.isNotBlank()) {
                    manager.notify(648,notification("${s.counters.triedBist}/${s.counters.discoveredBist} • ${s.state}"))
                    if(s.state!=tr.borsatakip.v5.data.FullUniversePolicy.State.RUNNING)stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) { FullUniverseScanRuntime.cancel();stopSelf() }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    companion object {
        fun start(context:Context,local:Boolean,resume:Boolean=false,retryFailuresOnly:Boolean=false) {
            ContextCompat.startForegroundService(context,Intent(context,UniverseObservationService::class.java).putExtra("local",local).putExtra("resume",resume).putExtra("retryFailuresOnly",retryFailuresOnly))
        }
    }
}
