package tr.borsatakip.v5.data

object ObservationSourcePolicy {
    const val MAX_AGE_MS = 6 * 60 * 60_000L

    fun identity(local: Boolean, config: BackendConnectionConfig): String =
        if (local) "LOCAL_DELAYED|YAHOO" else config.sourceIdentity

    fun canResume(local: Boolean, savedLocal: Boolean, savedIdentity: String,
                  currentIdentity: String, savedAt: Long, nowMs: Long): Boolean =
        local == savedLocal && savedIdentity.isNotBlank() && savedIdentity == currentIdentity &&
            savedAt > 0L && nowMs - savedAt in 0..MAX_AGE_MS
}
