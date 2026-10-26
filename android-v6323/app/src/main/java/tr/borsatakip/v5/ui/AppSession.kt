package tr.borsatakip.v5.ui

import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.scan.ScanState

object AppSession {
    var lastOpportunities: List<Opportunity> = emptyList()
    var selected: Opportunity? = null
    var lastScanState: ScanState? = null
    var lastCheapScanReport: String? = null
    var lastViopOpportunities: List<ViopOpportunity> = emptyList()
    var selectedViopOpportunity: ViopOpportunity? = null
    var selectedViopContract: ViopContract? = null
}