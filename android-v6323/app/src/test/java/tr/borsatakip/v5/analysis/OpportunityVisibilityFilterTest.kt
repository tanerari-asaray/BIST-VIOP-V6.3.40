package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot

class OpportunityVisibilityFilterTest {
    @Test fun allKeepsRejectedAndInsufficientVisibleButActionFiltersStaySafe() {
        val rejected=o("R",99,"LONG",SignalValidity.REJECTED)
        val insufficient=o("I",0,"NEUTRAL",SignalValidity.INSUFFICIENT)
        val all=OpportunityFilterPolicy.apply(listOf(rejected, insufficient),OpportunityFilter.ALL)
        assertEquals(setOf("R","I"), all.map{it.symbol}.toSet())
        assertTrue(OpportunityFilterPolicy.apply(listOf(rejected, insufficient),OpportunityFilter.LONG).isEmpty())
        assertTrue(OpportunityFilterPolicy.apply(listOf(rejected, insufficient),OpportunityFilter.HIGH_POWER).isEmpty())
    }

    private fun o(symbol:String,final:Int,direction:String,validity:SignalValidity)=Opportunity(
        symbol=symbol,companyName=null,price=10.0,dailyChangePct=0.0,score=50,riskScore=30,direction=direction,
        technicalLabel="",volumeLabel="1.0x",kapLabel="Veri yok",liquidityLabel="",support=null,resistance=null,
        source="unit",dataTimestamp=1L,candles=emptyList(),technical=TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null),
        finalSignalScore=final,dataConfidenceScore=70,dataMode=DataMode.REALTIME,signalValidity=validity,
        decisionState=when(validity){
            SignalValidity.INSUFFICIENT -> DecisionState.INSUFFICIENT_DATA
            SignalValidity.REJECTED -> DecisionState.REJECTED
            SignalValidity.VALID -> DecisionState.VERIFIED_OPPORTUNITY
            SignalValidity.WATCH -> DecisionState.WATCH
        }
    )
}
