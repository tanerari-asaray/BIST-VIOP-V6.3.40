package tr.borsatakip.v5

import android.app.Application
import android.util.Log
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.DiagnosticJournal
import tr.borsatakip.v5.data.FullUniverseScanSnapshotStore
import tr.borsatakip.v5.worker.WorkScheduler

class BorsaTakipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        // Previous process checkpoints cannot remain RUNNING after a cold start.
        FullUniverseScanSnapshotStore(this).recoverInterruptedProcess()
        val s = SettingsStore(this)
        WorkScheduler.reconcile(this, s.notifications, s.refreshMinutes)
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { DiagnosticJournal(this).record("APPLICATION", "CRASH", "UNCAUGHT_EXCEPTION") }
            if (BuildConfig.DEBUG) Log.e("BIST_CRASH", "[BIST_CRASH] exception=${throwable::class.java.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
