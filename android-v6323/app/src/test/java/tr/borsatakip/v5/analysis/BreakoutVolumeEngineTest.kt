package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class BreakoutVolumeEngineTest {
 @Test fun upwardBreakoutWithVolumeIsConfirmed(){
  val c=(0 until 21).map{i-> val close=if(i==20)120.0 else 100.0+i*0.2; Candle((i+1).toLong(),close,close+1,close-1,close,1000.0)}
  val r=BreakoutVolumeEngine.evaluate(c,1.8)
  assertEquals("YUKARI KIRILIM",r.state); assertTrue(r.confirmed); assertTrue(r.longContribution>0); assertEquals(80.0,r.volumeAnomalyPct!!,0.001)
 }
}
