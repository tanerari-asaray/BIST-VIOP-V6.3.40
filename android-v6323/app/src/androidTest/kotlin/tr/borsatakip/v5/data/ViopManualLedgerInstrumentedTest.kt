package tr.borsatakip.v5.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Device/emulator acceptance for D01, D05, D06: not executed by JVM unit test task. */
@RunWith(AndroidJUnit4::class)
class ViopManualLedgerInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("v6_manual_positions_v1", Context.MODE_PRIVATE)
    @Before fun clearBefore() { assertTrue(prefs.edit().clear().commit()) }
    @After fun clearAfter() { prefs.edit().clear().commit() }

    private fun record(ref: String, quantity: Int = 1) = ViopManualPositionStore(context).record(
        symbol = "F_TEST", side = "LONG", quantity = quantity,
        entry = 100.0, stop = 99.0, target = 105.0, multiplier = 100.0,
        costPerContract = 1.0, marginPerContract = 1000.0, brokerExecutionId = ref
    )

    @Test fun actualExecutionOverLimitStillRecordedAndBlocksNextPlan() {
        val actual = record("BROKER-A", 3) // planned risk 303 TL > 250 TL cap
        assertTrue(actual.accepted)
        assertEquals("RISK_BREACH_RECORDED", actual.reason)
        assertEquals(303.0, ViopManualPositionStore(context).state().openRiskTry, 0.001)
        val duplicate = record("BROKER-A", 3)
        assertFalse(duplicate.accepted)
        assertEquals("DUPLICATE_BROKER_EXECUTION", duplicate.reason)
    }

    @Test fun corruptNonObjectRowFailsClosedWithoutOverwriting() {
        val raw = """[null,7,"x"]"""
        assertTrue(prefs.edit().putString("positions", raw).commit())
        try { ViopManualPositionStore(context).state(); fail("Must not report zero open risk") }
        catch (expected: IllegalStateException) { assertEquals("POSITION_STORAGE_CORRUPT", expected.message) }
        assertFalse(record("BROKER-B").accepted)
        assertEquals(raw, prefs.getString("positions", null))
    }

    @Test fun twoRepositoryInstancesDoNotOverwriteEachOther() {
        val latch = CountDownLatch(1)
        val done = CountDownLatch(2)
        val errors = mutableListOf<Throwable>()
        for (n in 0..1) {
            thread {
                try {
                    latch.await(4, TimeUnit.SECONDS)
                    for (i in 0 until 10) {
                        check(record("BROKER-$n-$i").accepted)
                    }
                } catch (t: Throwable) { synchronized(errors) { errors.add(t) } }
                finally { done.countDown() }
            }
        }
        latch.countDown()
        assertTrue(done.await(15, TimeUnit.SECONDS))
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(20, ViopManualPositionStore(context).positions().size)
    }
}
