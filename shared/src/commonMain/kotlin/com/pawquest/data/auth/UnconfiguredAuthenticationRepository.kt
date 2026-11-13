package com.pawquest.data.auth

import com.pawquest.domain.model.AuthFailure
import com.pawquest.domain.model.AuthenticationException
import com.pawquest.domain.model.AuthenticationState
import com.pawquest.domain.model.LogoutOutcome
import com.pawquest.domain.model.RegistrationOutcome
import com.pawquest.domain.repository.AuthenticationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UnconfiguredAuthenticationRepository : AuthenticationRepository {
    override val state = MutableStateFlow<AuthenticationState>(
        AuthenticationState.Unavailable(AuthFailure.CONFIGURATION.userMessage, false),
    ).asStateFlow()
    override suspend fun register(email: String, password: String): RegistrationOutcome = unavailable()
    override suspend fun login(email: String, password: String): Unit = unavailable()
    override suspend fun logout(): LogoutOutcome = unavailable()
    override suspend fun retryRestoration(): Unit = unavailable()
    private fun unavailable(): Nothing = throw AuthenticationException(AuthFailure.CONFIGURATION)
}
