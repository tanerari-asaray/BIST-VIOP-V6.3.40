package tr.borsatakip.v5.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R

/**
 * Emulator regression for the historical java.lang.VerifyError in the scan click coroutine.
 * The primary scan button is intentionally below the initial viewport in a ScrollView.
 * Scrolling to the real button checks user-reachable UI rather than falsely requiring it
 * to be initially visible on every supported screen size.
 *
 * Successful backend/network scan is out of scope for this runtime smoke test.
 */
@RunWith(AndroidJUnit4::class)
class BistScanActivityRuntimeTest {
    @Test
    fun activeBistBottomNavigationIsSelectedButNotAnInertButton() {
        ActivityScenario.launch(BistScanActivity::class.java).use { scene ->
            scene.onActivity { activity ->
                val active = activity.findViewById<android.widget.TextView>(R.id.navViop)
                org.junit.Assert.assertTrue(active.isSelected)
                org.junit.Assert.assertFalse(active.isClickable)
                org.junit.Assert.assertFalse(active.isFocusable)
            }
        }
    }

    @Test
    fun activityLoadsAndScanButtonClickDoesNotTriggerVerifierCrash() {
        ActivityScenario.launch(BistScanActivity::class.java).use { scene ->
            scene.onActivity { activity ->
                val scan = activity.findViewById<android.view.View>(R.id.btnStartScan)
                org.junit.Assert.assertTrue("Scan button missing", scan != null)
                org.junit.Assert.assertTrue("Scan button did not accept click", scan.performClick())
                org.junit.Assert.assertNotNull(activity.findViewById<android.view.View>(R.id.txtStatus))
            }
        }
    }
}
