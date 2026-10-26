package tr.borsatakip.v5.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tr.borsatakip.v5.data.ScanTimeframe
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.ui.BistScanActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android WorkManager'ın 15 dk altı periyodik sınırını aşmaya çalışmaz.
 * Kullanıcı açıkça otomatik taramayı etkinleştirdiğinde, 1/3/5/10 DK taramaları kullanıcıya
 * sürekli bildirim gösteren specialUse foreground service içinde, üst üste binmeden çalıştırılır.
 */
class AutoScanForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startRequested.set(true)
        SettingsStore(this).apply {
            autoScanLastStatus = "SERVICE_STARTING"
            autoScanLastMessage = "Ön plan taraması başlıyor; işlem kanıtı bekleniyor."
            autoScanLastHeartbeatAt = System.currentTimeMillis()
        }
        promoteToForeground("Otomatik BIST taraması hazırlanıyor")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SettingsStore(this).apply {
                autoScanEnabled = false
                autoScanLastStatus = "STOPPED"
                autoScanLastMessage = "Kullanıcı tarafından durduruldu."
            }
            stopSelfSafely()
            return START_NOT_STICKY
        }
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val settings = SettingsStore(this)
            if (!settings.autoScanEnabled) break
            val timeframe = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
            val cadence = settings.scanCadenceMinutes
            if (cadence >= AutoScanScheduler.WORK_MANAGER_MINUTES) break
            val sourceMode = if (tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl) && settings.apiKey.isNotBlank()) {
                "Production Backend"
            } else {
                "Yahoo YEDEK/GECİKMELİ"
            }
            updateForeground("${timeframe.label} • $sourceMode • otomatik tarama çalışıyor")
            val result = AutomaticScanRunner.runOnce(this)
            updateForeground("${timeframe.label} • ${result.message.take(80)}")
            if (!SettingsStore(this).autoScanEnabled) break
            delay(cadence * 60_000L)
        }
        stopSelfSafely()
    }

    override fun onDestroy() {
        startRequested.set(false)
        val local = SettingsStore(this)
        if (local.autoScanEnabled && local.autoScanLastStatus in setOf("SERVICE_STARTING", "STARTING", "SCANNING")) {
            local.autoScanLastStatus = "INTERRUPTED"
            local.autoScanLastMessage = "Ön plan servisi sonlandı; tarama kesilmiş olabilir."
        }
        loopJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopSelfSafely() {
        startRequested.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForeground(text: String) {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(text), serviceType)
    }

    private fun updateForeground(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            7810,
            Intent(this, BistScanActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            7811,
            Intent(this, AutoScanForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("BIST / VİOP V${tr.borsatakip.v5.BuildConfig.VERSION_NAME} • Otomatik BIST Tarama")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stop)
            .build()
    }

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Otomatik BIST Tarama", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "automatic_bist_scan"
        private const val NOTIFICATION_ID = 7800
        private const val ACTION_STOP = "tr.borsatakip.v5.AUTO_SCAN_STOP"
        private val startRequested = AtomicBoolean(false)

        fun start(context: Context): Boolean {
            val app = context.applicationContext
            val settings = SettingsStore(app)
            if (!settings.autoScanEnabled) return false
            if (!startRequested.compareAndSet(false, true)) return true
            return runCatching {
                val intent = Intent(app, AutoScanForegroundService::class.java)
                ContextCompat.startForegroundService(app, intent)
                true
            }.getOrElse {
                startRequested.set(false)
                false
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            val stopped = app.stopService(Intent(app, AutoScanForegroundService::class.java))
            if (!stopped) startRequested.set(false)
        }
    }
}
