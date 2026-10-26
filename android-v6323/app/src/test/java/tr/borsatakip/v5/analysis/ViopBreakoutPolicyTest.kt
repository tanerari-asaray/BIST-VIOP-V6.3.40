package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class ViopBreakoutPolicyTest {
    @Test fun upwardBreakoutWithVolumeIsConfirmed() {
        val base=List(20){i->Candle((i+1).toLong(),100.0,101.0,99.0,100.0,1000.0)}
        val last=Candle(21,101.0,105.0,100.5,104.0,2500.0)
        val r=ViopBreakoutPolicy.evaluate(base+last,2.0)
        assertEquals("YUKARI KIRILIM",r.state)
        assertTrue(r.confirmed)
        assertEquals(8,r.longContribution)
        assertTrue((r.volumeAnomalyPct ?: 0.0)>0.0)
    }
}
