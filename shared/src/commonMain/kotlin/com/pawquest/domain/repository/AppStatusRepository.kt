package com.pawquest.domain.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/** Server-side minimum app version (public.app_status). Works before sign-in. */
interface AppStatusRepository {
    suspend fun updateRequired(versionCode: Int): Boolean
}

class UnconfiguredAppStatusRepository: AppStatusRepository {
    override suspend fun updateRequired(versionCode: Int)=false
}

/**
 * Fail-open: only an explicit server answer blocks the app. Offline, timeouts, an older
 * backend without app_status or an unknown local version never lock anyone out.
 */
class AppUpdateCheck(private val repository: AppStatusRepository,private val timeoutMillis: Long=5_000) {
    suspend fun required(versionCode: Int): Boolean {
        if(versionCode<=0) return false
        return try {withTimeout(timeoutMillis) {repository.updateRequired(versionCode)}}
        catch(timeout: kotlinx.coroutines.TimeoutCancellationException) {false}
        catch(cancelled: CancellationException) {throw cancelled}
        catch(_: Exception) {false}
    }
}
