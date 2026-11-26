package com.pawquest

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pawquest.domain.telemetry.NoOpTelemetry
import com.pawquest.domain.telemetry.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Thrown only to carry a server failure into Crashlytics as a non-fatal; never crashes the app. */
class ServerErrorReport(operation: String,status: Int): RuntimeException("HTTP $status from $operation")

/**
 * Firebase Crashlytics behind [Telemetry]. Collection starts disabled in the manifest and is
 * switched on only from the stored user choice (default on, disclosed in the privacy policy,
 * can be turned off in Settings). No user ID, e-mail or user-entered text is ever attached.
 */
class CrashlyticsTelemetry private constructor(context: Context,private val crashlytics: FirebaseCrashlytics): Telemetry {
    private val prefs=context.getSharedPreferences("pawquest_privacy",Context.MODE_PRIVATE)
    private val enabled=MutableStateFlow(prefs.getBoolean(KEY,true))
    override val available=true
    override val collectionEnabled: StateFlow<Boolean> = enabled.asStateFlow()

    init {apply(enabled.value)}

    override fun setCollectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY,enabled).apply()
        this.enabled.value=enabled
        apply(enabled)
    }
    private fun apply(on: Boolean) {
        crashlytics.isCrashlyticsCollectionEnabled=on
        // Reports cached while collection was off are discarded, never sent later.
        if(!on) crashlytics.deleteUnsentReports()
    }
    override fun setScreen(route: String) {
        if(enabled.value) crashlytics.setCustomKey("screen",route.take(32))
    }
    override fun recordServerError(operation: String,status: Int) {
        if(!enabled.value) return
        crashlytics.setCustomKey("last_server_error","$operation $status")
        crashlytics.recordException(ServerErrorReport(operation,status))
    }

    companion object {
        private const val KEY="crash_reports_enabled"
        /** Crashlytics when google-services.json was present at build time, otherwise a no-op. */
        fun create(context: Context): Telemetry =
            if(!BuildConfig.CRASHLYTICS_CONFIGURED || FirebaseApp.getApps(context).isEmpty()) NoOpTelemetry
            else CrashlyticsTelemetry(context.applicationContext,FirebaseCrashlytics.getInstance())
    }
}
