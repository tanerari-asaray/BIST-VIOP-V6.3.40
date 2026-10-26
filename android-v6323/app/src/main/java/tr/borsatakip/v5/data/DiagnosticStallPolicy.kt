package tr.borsatakip.v5.data

/** Evidence classification, not proof of actual Android scheduler execution or a hung thread. */
object DiagnosticStallPolicy {
    enum class Status {
        DISABLED_BY_USER,
        NO_MONITORING_EVIDENCE,
        AWAITING_FIRST_HEARTBEAT,
        FIRST_HEARTBEAT_OVERDUE,
        HEARTBEAT_OVERDUE,
        CLOCK_INCONSISTENT,
        RECENT_HEARTBEAT_NOT_DELIVERY_PROOF
    }

    fun evaluate(
        enabled: Boolean,
        cadenceMinutes: Int,
        lastHeartbeatAt: Long,
        monitoringStartedAt: Long,
        reportTime: Long
    ): Status {
        if (!enabled) return Status.DISABLED_BY_USER
        val timeout = cadenceMinutes.coerceIn(1, 1440).toLong() * 180000L
        if (lastHeartbeatAt > reportTime || monitoringStartedAt > reportTime) return Status.CLOCK_INCONSISTENT
        if (lastHeartbeatAt > 0L) {
            return if (reportTime - lastHeartbeatAt > timeout) Status.HEARTBEAT_OVERDUE
            else Status.RECENT_HEARTBEAT_NOT_DELIVERY_PROOF
        }
        if (monitoringStartedAt <= 0L) return Status.NO_MONITORING_EVIDENCE
        return if (reportTime - monitoringStartedAt > timeout) Status.FIRST_HEARTBEAT_OVERDUE
        else Status.AWAITING_FIRST_HEARTBEAT
    }
}
