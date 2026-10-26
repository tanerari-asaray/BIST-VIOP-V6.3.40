package tr.borsatakip.v5.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.launch
import tr.borsatakip.v5.data.RealtimeScannerClient
import tr.borsatakip.v5.data.RealtimeScannerSocket
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.ScanMode
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.ui.MainActivity
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-visible foreground monitor for realtime BIST opportunities.
 *
 * It consumes only /v1/scanner/live. TradingView alarms are not market data and do not trigger
 * these notifications. A periodic WorkManager job remains as a backup when the live service is
 * unavailable or stopped by the OS.
 */
class RealtimeAlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var scannerSocket: RealtimeScannerSocket
    private var reconnectJob: Job? = null
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        // A service started with startForegroundService() must promote itself immediately.
        // Keep all network/socket initialization strictly after this point.
        createChannels()
        startRequested.set(true)
        runningInProcess.set(true)
        promoteToForeground()
        scannerSocket = RealtimeScannerSocket(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsStore(this)
        if (!settings.notifications || !tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl) || settings.apiKey.isBlank()) {
            startRequested.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        stopping = false
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        stopping = true
        startRequested.set(false)
        runningInProcess.set(false)
        reconnectJob?.cancel()
        if (::scannerSocket.isInitialized) scannerSocket.close()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Defensive cleanup for any platform-enforced foreground-service timeout.
        stopping = true
        startRequested.set(false)
        runningInProcess.set(false)
        if (::scannerSocket.isInitialized) scannerSocket.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun connect() {
        if (stopping) return
        val settings = SettingsStore(this)
        scannerSocket.connect(
            minScore = ALERT_MIN_SCORE,
            limit = 20,
            actionableOnly = true,
            minIntervalMs = 1000,
            analysisTimeframeMinutes = settings.analysisTimeframeMinutes,
            scanCadenceMinutes = settings.scanCadenceMinutes,
            scanMode = ScanMode.LIVE,
            listener = object : RealtimeScannerSocket.Listener {
                override fun onSubscribed() {
                    updateForeground("Canlı BIST scanner bağlı • fırsat bekleniyor")
                }

                override fun onSnapshot(snapshot: RealtimeScannerClient.Snapshot) {
                    val total = snapshot.expectedSymbols.takeIf { it > 0 } ?: snapshot.trackedSymbols
                    val coverage = snapshot.readyCoveragePct?.let { " • hazır %${"%.0f".format(it)}" } ?: ""
                    updateForeground("${snapshot.provider} • ${snapshot.readySymbols}/$total hazır$coverage")
                    snapshot.opportunities
                        .filter(::isActionableRealtime)
                        .take(5)
                        .forEach(::maybeNotify)
                }

                override fun onDisconnected(reason: String) {
                    updateForeground("Canlı scanner bağlantısı kapandı • yeniden bağlanıyor")
                    scheduleReconnect()
                }

                override fun onError(message: String) {
                    updateForeground("Canlı scanner kesildi • yeniden bağlanıyor")
                    scheduleReconnect()
                }
            }
        )
    }

    private fun isActionableRealtime(x: Opportunity): Boolean =
        x.isRealtime &&
            x.dataMode == DataMode.REALTIME &&
            x.signalValidity == SignalValidity.VALID &&
            x.direction in setOf("LONG", "SHORT") &&
            x.finalSignalScore >= ALERT_MIN_SCORE &&
            (x.dataAgeMs == null || x.dataAgeMs in 0..60_000L)

    private fun scheduleReconnect() {
        if (stopping || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(5_000L)
            if (!stopping && SettingsStore(this@RealtimeAlertService).notifications) connect()
        }
    }

    private fun maybeNotify(x: Opportunity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("realtime_alert_cooldown", Context.MODE_PRIVATE)
        val key = "${x.symbol}:${x.direction}"
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < ALERT_COOLDOWN_MS) return
        prefs.edit().putLong(key, now).apply()

        val isShort = x.direction == "SHORT"
        val action = if (isShort) "SAT ADAYI" else "AL ADAYI"
        val arrow = if (isShort) "▼" else "▲"
        val title = "${x.symbol} • $arrow $action • ${x.finalSignalScore}/100"
        val change = x.dailyChangePct?.takeIf { it.isFinite() }?.let { "%+.2f%%".format(it) } ?: "—"
        val body = "Fiyat %.2f TL • Günlük %s • Risk %d/100 • Canlı veri doğrulandı".format(
            x.price, change, x.riskScore
        )
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_SYMBOL, x.symbol)
        }
        val pending = PendingIntent.getActivity(
            this,
            abs((x.symbol + x.direction).hashCode()),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(ALERT_ID_BASE + abs(x.symbol.hashCode() % 20_000), notification)
    }


    private fun promoteToForeground() {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            monitorNotification("Canlı BIST fırsatları izleniyor • bağlantı kuruluyor"),
            serviceType
        )
    }

    private fun updateForeground(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(FOREGROUND_ID, monitorNotification(text))
    }

    private fun monitorNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            7001,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, MONITOR_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("BIST / VİOP V${tr.borsatakip.v5.BuildConfig.VERSION_NAME} • Canlı BIST İzleme")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, "Canlı BIST İzleme", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Anlık Fırsat Uyarıları", NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val EXTRA_ALERT_SYMBOL = "realtime_alert_symbol"
        private const val MONITOR_CHANNEL = "realtime_bist_monitor"
        private const val ALERT_CHANNEL = "realtime_bist_alerts"
        private const val FOREGROUND_ID = 7400
        private const val ALERT_ID_BASE = 7500
        private const val ALERT_MIN_SCORE = 80
        private const val ALERT_COOLDOWN_MS = 15 * 60 * 1000L

        private val startRequested = AtomicBoolean(false)
        private val runningInProcess = AtomicBoolean(false)

        fun isRunningInProcess(): Boolean = runningInProcess.get()

        fun start(context: Context): Boolean {
            val appContext = context.applicationContext
            val settings = SettingsStore(appContext)
            if (!settings.notifications || !tr.borsatakip.v5.data.BackendUrlPolicy.isValid(settings.baseUrl) || settings.apiKey.isBlank()) {
                return false
            }
            if (!startRequested.compareAndSet(false, true)) return true

            return runCatching {
                val intent = Intent(appContext, RealtimeAlertService::class.java)
                ContextCompat.startForegroundService(appContext, intent)
                true
            }.getOrElse {
                startRequested.set(false)
                false
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val stopped = appContext.stopService(Intent(appContext, RealtimeAlertService::class.java))
            if (!stopped) startRequested.set(false)
        }
    }
}
