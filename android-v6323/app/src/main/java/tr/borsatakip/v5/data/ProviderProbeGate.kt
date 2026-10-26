package tr.borsatakip.v5.data

/** Process-local ownership prevents an abandoned or superseded test from publishing readiness. */
internal class ProviderProbeGate(private val timeoutMs: Long = 120_000L) {
    class Ticket internal constructor(val revision: String, val startedAt: Long)
    private var current: Ticket? = null

    @Synchronized fun begin(revision: String, nowMs: Long, publish: () -> Unit): Ticket {
        val ticket = Ticket(revision, nowMs)
        current = ticket
        try { publish() } catch (e: Exception) { current = null; throw e }
        return ticket
    }

    @Synchronized fun finish(ticket: Ticket, publish: () -> Unit): Boolean {
        if (current !== ticket) return false
        current = null
        publish()
        return true
    }

    @Synchronized fun recoverIfIdle(revision: String, nowMs: Long, recover: () -> Unit): Boolean {
        val active = current
        if (active != null && active.revision != revision) return false
        if (active != null && active.revision == revision && nowMs - active.startedAt in 0..timeoutMs) return false
        current = null
        recover()
        return true
    }
}
