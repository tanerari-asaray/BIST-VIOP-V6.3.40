package tr.borsatakip.v5.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import tr.borsatakip.v5.data.ScanTimeframe
import tr.borsatakip.v5.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Otomatik tarama zamanlayıcısı.
 * WorkManager periyodik işlerde minimum 15 dakika istediği için 1/3/5/10 DK kullanıcı-açık
 * foreground servis ile; 15 DK ve üzeri WorkManager ile yürütülür.
 */
object AutoScanScheduler {
    private const val UNIQUE_WORK = "automatic_bist_scan"
    private const val IMMEDIATE_WORK = "automatic_bist_scan_now"
    const val WORK_MANAGER_MINUTES = 15

    data class ScheduleResult(val accepted: Boolean, val message: String)

    fun reconcile(context: Context, allowForegroundStart: Boolean): ScheduleResult {
        val app = context.applicationContext
        val settings = SettingsStore(app)
        val wm = WorkManager.getInstance(app)
        if (!settings.autoScanEnabled) {
            wm.cancelUniqueWork(UNIQUE_WORK)
            wm.cancelUniqueWork(IMMEDIATE_WORK)
            AutoScanForegroundService.stop(app)
            return ScheduleResult(false, "Otomatik tarama KAPALI")
        }

        val timeframe = ScanTimeframe.fromStored(settings.analysisTimeframeMinutes)
        val cadence = settings.scanCadenceMinutes

        if (cadence < WORK_MANAGER_MINUTES) {
            wm.cancelUniqueWork(UNIQUE_WORK)
            wm.cancelUniqueWork(IMMEDIATE_WORK)
            if (!allowForegroundStart) return ScheduleResult(false, "${cadence} DK foreground hizmeti arka plandan başlatılamadı; uygulamayı açın.")
            val started = AutoScanForegroundService.start(app)
            if (!started) return ScheduleResult(false, "Ön plan tarama servisi başlatılamadı. Android izinlerini kontrol edin.")
            return ScheduleResult(true, "Ön plan servis talebi kabul edildi; ilk tarama bekleniyor.")
        }

        AutoScanForegroundService.stop(app)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val immediate = OneTimeWorkRequestBuilder<AutomaticScanWorker>()
            .setConstraints(constraints)
            .build()
        wm.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, immediate)

        val request = PeriodicWorkRequestBuilder<AutomaticScanWorker>(cadence.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        return ScheduleResult(true, "WorkManager zamanlandı; ilk gerçek çalışma bekleniyor.")
    }
}
