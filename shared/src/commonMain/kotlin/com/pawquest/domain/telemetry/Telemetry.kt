package com.pawquest.domain.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Crash and stability reporting, independent of any vendor SDK. Presentation and data code
 * depend only on this interface; the Android host supplies Firebase Crashlytics.
 *
 * Privacy rule: never pass user-entered text, e-mail, tokens, response bodies or exception
 * messages. Only fixed operation names, HTTP status codes and screen routes.
 */
interface Telemetry {
    /** False when the platform has no crash-reporting backend configured. */
    val available: Boolean
    /** The user's choice (Settings); reports are sent only while true. */
    val collectionEnabled: StateFlow<Boolean>
    fun setCollectionEnabled(enabled: Boolean)
    /** Attached to later crash reports as context, e.g. "quests". */
    fun setScreen(route: String)
    /** A server-side failure the user could not see as a crash, e.g. HTTP 503 from an RPC. */
    fun recordServerError(operation: String,status: Int)
}

object NoOpTelemetry: Telemetry {
    override val available=false
    private val disabled=MutableStateFlow(false)
    override val collectionEnabled: StateFlow<Boolean> = disabled.asStateFlow()
    override fun setCollectionEnabled(enabled: Boolean)=Unit
    override fun setScreen(route: String)=Unit
    override fun recordServerError(operation: String,status: Int)=Unit
}

/**
 * Reduces a Supabase request path to a fixed operation name. Query strings (which can carry
 * row filters such as user IDs) and anything after the resource name are dropped.
 */
fun supabaseOperation(path: String): String {
    val parts=path.substringBefore('?').trim('/').split('/').filter {it.isNotEmpty()}
    fun safe(name: String?)=name?.takeIf {Regex("[A-Za-z0-9_]{1,64}").matches(it)} ?: "unknown"
    return when {
        parts.size>=4 && parts[0]=="rest" && parts[2]=="rpc" -> "rpc:${safe(parts[3])}"
        parts.size>=3 && parts[0]=="rest" -> "table:${safe(parts[2])}"
        parts.size>=3 && parts[0]=="auth" -> "auth:${safe(parts[2])}"
        else -> "other"
    }
}
