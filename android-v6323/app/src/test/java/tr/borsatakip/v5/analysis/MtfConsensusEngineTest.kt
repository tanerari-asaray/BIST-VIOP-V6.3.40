package tr.borsatakip.v5.analysis

import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class MtfConsensusEngineTest {
    @Test fun alignedUpFramesBoostLongAndPenalizeShort() {
        val up = candles(60, 100.0, 0.2)
        val r=MtfConsensusEngine.evaluate(listOf(
            MtfConsensusEngine.Input("1 DK",up,0.1),
            MtfConsensusEngine.Input("5 DK",up,0.2),
            MtfConsensusEngine.Input("15 DK",up,0.3),
            MtfConsensusEngine.Input("1 SA",up,0.4)
        ))
        assertTrue(r.score > 20)
        assertTrue(MtfConsensusEngine.rankingAdjustment("LONG",r) > 0)
        assertTrue(MtfConsensusEngine.rankingAdjustment("SHORT",r) < 0)
    }

    @Test fun fewerThanThreeFramesDoesNotAdjustRanking() {
        val up=candles(60,100.0,0.2)
        val r=MtfConsensusEngine.evaluate(listOf(MtfConsensusEngine.Input("1 DK",up,0.5),MtfConsensusEngine.Input("1 SA",up,0.5)))
        assertTrue(MtfConsensusEngine.rankingAdjustment("LONG",r)==0)
    }

    private fun candles(n:Int,start:Double,step:Double)=List(n){i->val c=start+i*step;Candle(1_700_000_000_000L+i*60_000L,c,c+0.2,c-0.2,c,1000.0)}
}
