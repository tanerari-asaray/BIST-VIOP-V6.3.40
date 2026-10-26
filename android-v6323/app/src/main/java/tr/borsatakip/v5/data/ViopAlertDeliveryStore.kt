package tr.borsatakip.v5.data

import android.content.Context

/** Persistent Android handoff state. OS submission is never called user delivery. */
class ViopAlertDeliveryStore(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("viop_event_delivery_v2", Context.MODE_PRIVATE)
    private fun key(e: AlertEventStore.Event) =
        "delivery:${e.identity}:${e.type.name}:${e.createdAt}"
    fun pending(events: List<AlertEventStore.Event>, now: Long = System.currentTimeMillis()): List<AlertEventStore.Event> =
        events.filter {
            it.identity.isNotBlank() && it.createdAt in (now - 7L * 86400000L)..now &&
                prefs.getString(key(it), "PENDING") != "SUBMITTED_TO_ANDROID"
        }
    fun mark(e: AlertEventStore.Event, status: String): Boolean =
        prefs.edit().putString(key(e), status).putLong("${key(e)}:attemptAt",System.currentTimeMillis()).commit()
    fun state(e: AlertEventStore.Event): String = prefs.getString(key(e),"PENDING") ?: "PENDING"
}
