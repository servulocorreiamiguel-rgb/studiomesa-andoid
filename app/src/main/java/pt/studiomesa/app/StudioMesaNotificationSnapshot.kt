package pt.studiomesa.app

import java.util.Locale

data class StudioMesaNotificationEvent(
    val key: String,
    val state: String,
    val title: String,
    val message: String,
    val notifyOnNew: Boolean = false,
    val notifyWhenStateIs: Set<String> = emptySet()
)

data class StudioMesaNotificationSnapshotResult(
    val snapshot: Map<String, String>,
    val notifications: List<StudioMesaNotificationEvent>
)

/** Pure snapshot comparison so event notifications can be tested without Android services. */
object StudioMesaNotificationSnapshot {
    fun compare(
        previous: Map<String, String>,
        current: List<StudioMesaNotificationEvent>,
        initialized: Boolean,
        maxEntries: Int = 400
    ): StudioMesaNotificationSnapshotResult {
        require(maxEntries > 0) { "maxEntries must be positive" }

        val next = LinkedHashMap<String, String>()
        val notifications = mutableListOf<StudioMesaNotificationEvent>()
        val seen = HashSet<String>()

        current.forEach { event ->
            val key = event.key.trim()
            val state = event.state.trim().lowercase(Locale.ROOT)
            if (key.isBlank() || state.isBlank() || !seen.add(key)) return@forEach

            val previousState = previous[key]?.trim()?.lowercase(Locale.ROOT)
            if (initialized) {
                val isNew = previousState == null && event.notifyOnNew
                val isRelevantTransition = previousState != null &&
                    previousState != state &&
                    event.notifyWhenStateIs.any { it.trim().lowercase(Locale.ROOT) == state }
                if (isNew || isRelevantTransition) notifications.add(event)
            }
            next[key] = state
        }

        val bounded = LinkedHashMap<String, String>()
        next.entries.toList().takeLast(maxEntries).forEach { bounded[it.key] = it.value }
        return StudioMesaNotificationSnapshotResult(bounded, notifications)
    }
}
