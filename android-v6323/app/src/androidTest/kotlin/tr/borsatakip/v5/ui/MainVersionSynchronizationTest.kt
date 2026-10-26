package tr.borsatakip.v5.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.BuildConfig
import tr.borsatakip.v5.R

@RunWith(AndroidJUnit4::class)
class MainVersionSynchronizationTest {
    @Test
    fun mainScreenVersionMatchesBuildConfigVersionName() {
        ActivityScenario.launch(MainActivity::class.java).use {
            it.onActivity { activity ->
                val badge = activity.findViewById<android.widget.TextView>(R.id.txtVersionBadge)
                org.junit.Assert.assertEquals("V${BuildConfig.VERSION_NAME}", badge.text.toString())
            }
        }
    }
}
