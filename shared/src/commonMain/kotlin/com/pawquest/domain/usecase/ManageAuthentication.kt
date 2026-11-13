package com.pawquest.domain.usecase

import com.pawquest.domain.repository.AuthenticationRepository

class ManageAuthentication(private val repository: AuthenticationRepository) {
    val state get() = repository.state
    suspend fun login(email: String, password: String) = repository.login(email.trim(), password)
    suspend fun register(email: String, password: String) = repository.register(email.trim(), password)
    suspend fun logout() = repository.logout()
    suspend fun retry() = repository.retryRestoration()
    suspend fun requestPasswordReset(email: String) = repository.requestPasswordReset(email.trim())
    suspend fun completePasswordReset(email: String, code: String, newPassword: String) =
        repository.completePasswordReset(email.trim(), code.filter(Char::isDigit), newPassword)
    suspend fun deleteAccount(password: String) = repository.deleteAccount(password)
}
