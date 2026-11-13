package com.pawquest.domain.repository

import com.pawquest.domain.model.AuthFailure
import com.pawquest.domain.model.AuthenticationException
import com.pawquest.domain.model.AuthenticationState
import com.pawquest.domain.model.LogoutOutcome
import com.pawquest.domain.model.RegistrationOutcome
import kotlinx.coroutines.flow.StateFlow

interface AuthenticationRepository {
    val state: StateFlow<AuthenticationState>
    suspend fun register(email: String, password: String): RegistrationOutcome
    suspend fun login(email: String, password: String)
    suspend fun logout(): LogoutOutcome
    suspend fun retryRestoration()

    /** Emails a one-time recovery code. Succeeds silently for unknown emails (no account enumeration). */
    suspend fun requestPasswordReset(email: String): Unit = throw AuthenticationException(AuthFailure.CONFIGURATION)

    /** Verifies the emailed code, sets [newPassword] and signs in. */
    suspend fun completePasswordReset(email: String, code: String, newPassword: String): Unit =
        throw AuthenticationException(AuthFailure.CONFIGURATION)

    /** Re-checks [password], permanently deletes the signed-in identity and clears the local session. */
    suspend fun deleteAccount(password: String): Unit = throw AuthenticationException(AuthFailure.CONFIGURATION)
}
