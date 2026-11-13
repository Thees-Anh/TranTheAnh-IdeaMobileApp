package com.pawquest.data.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Last confirmed server responses for read-only offline viewing (account, pet + wallet, quests).
 * Stored app-private on the device for the single signed-in account; cleared on sign-out or
 * when another account signs in. Nothing here is ever sent back to the server or used to grant
 * rewards — the server stays authoritative for every change.
 */
interface OfflineCache {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun clear()
}

class MemoryOfflineCache : OfflineCache {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun clear() = values.clear()
}

object OfflineKeys {
    const val ACCOUNT = "account"
    const val PET = "pet_state"
    const val QUESTS = "quests"
    const val SAVED_AT = "saved_at."
}

fun OfflineCache.save(key: String, value: String, now: Instant = Clock.System.now()) {
    write(key, value)
    write(OfflineKeys.SAVED_AT + key, now.toString())
}
fun OfflineCache.savedAt(key: String): Instant? = read(OfflineKeys.SAVED_AT + key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** App-wide connectivity as observed by server calls; drives the offline banner. */
object ConnectionStatus {
    /** When non-null, the UI shows cached data last confirmed at this instant. */
    var offlineDataFrom by mutableStateOf<Instant?>(null)
        private set
    fun usingCache(savedAt: Instant?) { if (offlineDataFrom == null) offlineDataFrom = savedAt ?: Clock.System.now() }
    fun online() { offlineDataFrom = null }
}
