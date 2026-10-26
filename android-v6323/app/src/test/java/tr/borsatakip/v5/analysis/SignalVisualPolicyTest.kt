package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalVisualPolicyTest {
    @Test fun long95UsesMaximumGreen() = assertStyle("LONG", 95, 10, 95, SignalIntensity.MAXIMUM, true)
    @Test fun long82UsesHighGreen() = assertStyle("LONG", 82, 10, 82, SignalIntensity.HIGH, true)
    @Test fun long68UsesMediumGreen() = assertStyle("LONG", 68, 10, 68, SignalIntensity.MEDIUM, true)
    @Test fun long45UsesLowGreen() = assertStyle("LONG", 45, 10, 45, SignalIntensity.LOW, true)

    @Test fun short95UsesMaximumRed() = assertStyle("SHORT", 10, 95, 95, SignalIntensity.MAXIMUM, false)
    @Test fun short82UsesHighRed() = assertStyle("SHORT", 10, 82, 82, SignalIntensity.HIGH, false)
    @Test fun short68UsesMediumRed() = assertStyle("SHORT", 10, 68, 68, SignalIntensity.MEDIUM, false)
    @Test fun short45UsesLowRed() = assertStyle("SHORT", 10, 45, 45, SignalIntensity.LOW, false)

    @Test fun qualityComesFromDirectionalScoreNotOpportunityRanking() {
        assertEquals(72, SignalVisualPolicy.qualityFor("LONG", 72, 99))
        assertEquals(61, SignalVisualPolicy.qualityFor("SHORT", 99, 61))
    }

    @Test fun brightnessIncreasesMonotonicallyWithinEachDirection() {
        val long = listOf(20, 45, 68, 82, 95).map { SignalVisualPolicy.resolve("LONG", it, 0)!! }
        val short = listOf(20, 45, 68, 82, 95).map { SignalVisualPolicy.resolve("SHORT", 0, it)!! }
        assertTrue(long.zipWithNext().all { brightness(it.second) > brightness(it.first) })
        assertTrue(short.zipWithNext().all { brightness(it.second) > brightness(it.first) })
    }

    @Test fun unknownDirectionDoesNotInventSignal() {
        assertNull(SignalVisualPolicy.resolve("WATCH", 90, 90))
    }

    private fun assertStyle(direction:String,longScore:Int,shortScore:Int,quality:Int,intensity:SignalIntensity,isLong:Boolean) {
        val s=SignalVisualPolicy.resolve(direction,longScore,shortScore)
        assertNotNull(s)
        s!!
        assertEquals(quality,s.quality)
        assertEquals(intensity,s.intensity)
        assertEquals(if(isLong) "▲" else "▼",s.arrow)
        if(isLong) assertTrue(s.green > s.red) else assertTrue(s.red > s.green)
    }

    private fun brightness(s:SignalVisualStyle):Double = 0.2126*s.red + 0.7152*s.green + 0.0722*s.blue
}
