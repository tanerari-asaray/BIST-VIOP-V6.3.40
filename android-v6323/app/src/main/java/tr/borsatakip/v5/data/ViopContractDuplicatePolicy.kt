package tr.borsatakip.v5.data

/** One deterministic duplicate rule shared by the provider parser and scan discovery.
 *  Conflicting identity fields are never silently selected.
 */
object ViopContractDuplicatePolicy {
    enum class Decision { FIRST, IDENTICAL_REPEAT, CONFLICT }

    fun register(seen: MutableMap<String, String>, symbol: String, identity: String): Decision {
        val previous = seen[symbol]
        if (previous == null) {
            seen[symbol] = identity
            return Decision.FIRST
        }
        return if (previous == identity) Decision.IDENTICAL_REPEAT else Decision.CONFLICT
    }
}
