package com.pawquest.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.AuthenticationException
import com.pawquest.domain.model.AuthenticationState
import com.pawquest.domain.model.LogoutOutcome
import com.pawquest.domain.model.RegistrationOutcome
import com.pawquest.domain.usecase.ManageAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthActionState(
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /** True after a recovery code was emailed; the screen then asks for the code and a new password. */
    val resetCodeSent: Boolean = false,
)

class AuthenticationViewModel(private val authentication: ManageAuthentication) : ViewModel() {
    val authenticationState = authentication.state
    private val mutableAction = MutableStateFlow(AuthActionState())
    val action = mutableAction.asStateFlow()

    fun clearMessages() { if (!mutableAction.value.busy) mutableAction.value = AuthActionState() }

    fun submit(email: String, password: String, confirmation: String, registering: Boolean) {
        if (mutableAction.value.busy) return
        val validation = validateCredentials(email, password, confirmation, registering)
        if (validation != null) { mutableAction.value = AuthActionState(error = validation); return }
        runAction {
            if (registering) {
                if (authentication.register(email, password) == RegistrationOutcome.CONFIRM_EMAIL) {
                    "Check your email to confirm your account, then sign in. If you already have an account, try signing in."
                } else null
            } else { authentication.login(email, password); null }
        }
    }

    fun requestPasswordReset(email: String) {
        if (mutableAction.value.busy) return
        validateEmail(email)?.let { mutableAction.value = AuthActionState(error = it); return }
        runAction(codeSent = true) {
            authentication.requestPasswordReset(email)
            "If an account exists for this email, we sent a 6-digit code. Enter it below with your new password."
        }
    }

    fun completePasswordReset(email: String, code: String, password: String, confirmation: String) {
        if (mutableAction.value.busy) return
        val validation = when {
            validateEmail(email) != null -> validateEmail(email)
            code.filter(Char::isDigit).length !in 6..10 -> "Enter the code from the email."
            else -> validateCredentials(email, password, confirmation, registering = true)
        }
        if (validation != null) { mutableAction.value = AuthActionState(error = validation, resetCodeSent = true); return }
        runAction(codeSent = true) { authentication.completePasswordReset(email, code, password); null }
    }

    fun deleteAccount(password: String) {
        if (mutableAction.value.busy) return
        if (password.isEmpty()) { mutableAction.value = AuthActionState(error = "Enter your password."); return }
        runAction { authentication.deleteAccount(password); "Your account was deleted." }
    }

    fun retry() = runAction { authentication.retry(); null }
    fun logout() = runAction {
        if (authentication.logout() == LogoutOutcome.LOCAL_ONLY) {
            "Signed out on this device. We couldn't contact the server to revoke the remote session."
        } else null
    }

    /** [codeSent] keeps the reset form on its code step while the action runs and after it fails. */
    private fun runAction(codeSent: Boolean = false, block: suspend () -> String?) {
        if (mutableAction.value.busy) return
        val staysOnCodeStep = codeSent && mutableAction.value.resetCodeSent
        mutableAction.value = AuthActionState(busy = true, resetCodeSent = staysOnCodeStep)
        viewModelScope.launch {
            try { mutableAction.value = AuthActionState(notice = block(), resetCodeSent = codeSent) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AuthenticationException) { mutableAction.value = AuthActionState(error = error.failure.userMessage, resetCodeSent = staysOnCodeStep) }
            catch (_: Exception) { mutableAction.value = AuthActionState(error = "Something went wrong. Please try again.", resetCodeSent = staysOnCodeStep) }
        }
    }
}

internal fun validateEmail(email: String): String? =
    if (email.trim().length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())) "Enter a valid email address." else null

internal fun validateCredentials(email: String, password: String, confirmation: String, registering: Boolean): String? = when {
    validateEmail(email) != null -> validateEmail(email)
    password.isEmpty() -> "Enter your password."
    password.length > 4096 -> "This password is too long."
    registering && password.length < 8 -> "Use at least 8 characters for your password."
    registering && password != confirmation -> "The passwords don't match."
    else -> null
}
