package tr.borsatakip.v5.analysis

/**
 * Gross ViOP TARGET-1 minimum for manual-review screening. This is never net of
 * commissions, fees, slippage or stop-gapping; it is not proof of executability.
 * Live bid/ask repricing must independently pass this floor after a plan is built.
 */
object ViopRiskRewardPolicy {
    const val MIN_TARGET1_GROSS_RR: Double = 1.5
}
