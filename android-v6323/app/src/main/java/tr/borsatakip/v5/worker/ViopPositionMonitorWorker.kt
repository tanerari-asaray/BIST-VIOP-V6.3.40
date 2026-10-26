package tr.borsatakip.v5.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import tr.borsatakip.v5.data.AlertEventStore
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.ProviderReadinessService
import tr.borsatakip.v5.data.ProviderState
import tr.borsatakip.v5.data.ViopManualPositionStore
import tr.borsatakip.v5.model.ViopQuote

/**
 * D02: independent monitoring of the user's recorded OPEN VIOP positions.
 * No opportunity scan, no BIST scan, no MTF/history dependency.
 * WorkManager is best-effort (>=15 min periodic); this is NOT a real-time protection system.
 */
class ViopPositionMonitorWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val prefs = applicationContext.getSharedPreferences("viop_position_monitor_v2", Context.MODE_PRIVATE)
        val journal = ViopManualPositionStore(applicationContext)
        val open = try { journal.openPositions() } catch (ce: CancellationException) { throw ce }
            catch (_: Exception) {
                prefs.edit().putString("state","LEDGER_CORRUPT").putLong("lastCheckAt",now).commit()
                return Result.failure()
            }
        if (open.isEmpty()) {
            prefs.edit().putString("state","NO_OPEN_POSITIONS").putLong("lastCheckAt",now).commit()
            return Result.success()
        }
        val available = try {
            ProviderReadinessService(applicationContext).localConfigState().state == ProviderState.PROVIDER_READY
        } catch (ce: CancellationException) { throw ce }
          catch (_: Exception) { false }
        val quotes = mutableMapOf<String, ViopQuote>()
        if (available) {
            val provider = BackendProvider(applicationContext)
            for (symbol in open.map { it.symbol }.distinct()) {
                try {
                    val quote = provider.loadViopQuote(symbol).getOrNull()
                    if (quote != null) quotes[symbol] = quote
                } catch (ce: CancellationException) { throw ce }
                  catch (_: Exception) { /* no verified price, record DATA_STALE */ }
            }
        }
        try {
            AlertEventStore(applicationContext).evaluateManualViop(open, quotes, now)
        } catch (ce: CancellationException) { throw ce }
          catch (_: Exception) {
            prefs.edit().putString("state","EVENT_STORE_ERROR").putLong("lastCheckAt",now).commit()
            return Result.retry()
        }
        val healthy = open.all { pos ->
            val q = quotes[pos.symbol]
            q != null && q.realtime && q.delaySeconds == 0 &&
                q.exchangeTimestamp > 0 && now - q.exchangeTimestamp in 0L..60000L
        }
        val status = when {
            !available -> "PROVIDER_UNAVAILABLE"
            healthy -> "MONITORED_LAST_CHECK"
            else -> "UNVERIFIED_OR_STALE_PRICE"
        }
        prefs.edit().putString("state",status).putLong("lastCheckAt",now)
            .putLong("lastVerifiedAt", if (healthy) now else prefs.getLong("lastVerifiedAt",0L))
            .putInt("openCount",open.size).commit()
        // Persisted events are retried after POST_NOTIFICATIONS is granted, even if no new
        // event was emitted on this run due to the event de-duplication window.
        val history = AlertEventStore(applicationContext).recentEvents(200)
        val openIds = open.map { it.id }.toSet()
        val pending = tr.borsatakip.v5.data.ViopAlertDeliveryStore(applicationContext)
            .pending(history.filter { it.identity in openIds })
        if (pending.isNotEmpty()) notifyRecordedAlerts(pending)
        return Result.success()
    }

    private fun notifyRecordedAlerts(events: List<AlertEventStore.Event>) {
        val delivery = tr.borsatakip.v5.data.ViopAlertDeliveryStore(applicationContext)
        val prefs = applicationContext.getSharedPreferences("viop_position_monitor_v2", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            events.forEach { delivery.mark(it, "PERMISSION_DENIED") }
            prefs.edit().putString("lastDeliveryState","PERMISSION_DENIED").commit()
            return
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "viop_real_position_monitor"
        nm.createNotificationChannel(NotificationChannel(channel,"VİOP Defter Pozisyonları",NotificationManager.IMPORTANCE_HIGH))
        for (event in events) {
            val id = ("viop:${event.identity}:${event.type}:${event.createdAt}").hashCode().and(Int.MAX_VALUE)
            val intent = Intent(applicationContext,tr.borsatakip.v5.ui.ViopActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("manualPositionId", event.identity)
                .putExtra("manualAlertType", event.type.name)
            val pending = PendingIntent.getActivity(applicationContext,id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                nm.notify(id, NotificationCompat.Builder(applicationContext,channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("VİOP • ${event.symbol} • ${event.type.name}")
                    .setContentText(event.message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                    .setContentIntent(pending).setAutoCancel(true).build())
                delivery.mark(event, "SUBMITTED_TO_ANDROID")
            } catch (_: Exception) { delivery.mark(event,"SEND_FAILED") }
        }
        prefs.edit().putString("lastDeliveryState","OS_SUBMISSION_RECORDED_NOT_CONFIRMED_DELIVERY").commit()
    }

    companion object {
        const val UNIQUE_PERIODIC = "viop_position_monitor_periodic_v2"
        private const val UNIQUE_NOW = "viop_position_monitor_immediate_v2"
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ViopPositionMonitorWorker>().build())
        }
    }
}
