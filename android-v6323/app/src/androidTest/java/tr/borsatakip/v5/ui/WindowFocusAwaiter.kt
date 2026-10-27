package tr.borsatakip.v5.ui

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File

/** Wait outside Espresso: an Activity can be RESUMED before its window receives input. */
internal object WindowFocusAwaiter {
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    fun await(type: Class<out Activity>) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var stable = 0
        var observed = "No resumed activity"
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            inst.runOnMainSync {
                val activities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                observed = activities.joinToString { a ->
                    "${a.javaClass.simpleName}: focus=${a.hasWindowFocus()}, shown=${a.window.decorView.isShown}, layout=${a.window.decorView.isLayoutRequested}"
                }
                ready = activities.any { a -> type.isInstance(a) && a.window.decorView.let {
                    it.isShown && it.hasWindowFocus() && !it.isLayoutRequested && it.width > 0 && it.height > 0
                } }
            }
            stable = if (ready) stable + 1 else 0
            if (stable >= 2) return
            SystemClock.sleep(50)
        }
        capture("focus-timeout-${type.simpleName}")
        throw AssertionError("Window never became ready for touch: ${type.simpleName}; $observed")
    }

    fun capture(name: String) {
        val dir = File(inst.targetContext.filesDir, "build670-screens").apply { mkdirs() }
        // Diagnostics must never replace the original assertion failure.
        runCatching {
            inst.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(dir, "$name.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("dumpsys window")).use {
                File(dir, "$name-window.txt").writeBytes(it.readBytes())
            }
        }
    }
}
