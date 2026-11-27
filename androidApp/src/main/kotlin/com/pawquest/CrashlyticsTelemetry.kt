package com.pawquest

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pawquest.domain.telemetry.NoOpTelemetry
import com.pawquest.domain.telemetry.Telemetry

/** Thrown only to carry a server failure into Crashlytics as a non-fatal; never crashes the app. */
class ServerErrorReport(operation: String,status: Int): RuntimeException("HTTP $status from $operation")

/**
 * Firebase Crashlytics behind [Telemetry]. Crash reporting is always on (disclosed in the
 * privacy policy); there is no in-app switch. No user ID, e-mail or user-entered text is
 * ever attached.
 */
class CrashlyticsTelemetry private constructor(private val crashlytics: FirebaseCrashlytics): Telemetry {
    override val available=true

    init {
        // Overrides any "off" value persisted by an earlier build that still had the switch.
        crashlytics.isCrashlyticsCollectionEnabled=true
    }

    override fun setScreen(route: String)=crashlytics.setCustomKey("screen",route.take(32))
    override fun recordServerError(operation: String,status: Int) {
        crashlytics.setCustomKey("last_server_error","$operation $status")
        crashlytics.recordException(ServerErrorReport(operation,status))
    }

    companion object {
        /** Crashlytics when google-services.json was present at build time, otherwise a no-op. */
        fun create(context: Context): Telemetry {
            if(!BuildConfig.CRASHLYTICS_CONFIGURED || FirebaseApp.getApps(context).isEmpty()) return NoOpTelemetry
            // The switch's preference from earlier builds is no longer used.
            context.deleteSharedPreferences("pawquest_privacy")
            return CrashlyticsTelemetry(FirebaseCrashlytics.getInstance())
        }
    }
}
