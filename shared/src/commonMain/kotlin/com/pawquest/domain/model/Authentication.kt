package com.pawquest.domain.model

data class UserAccount(
    val userId: String,
    val email: String?,
    val displayName: String,
    val petId: String,
    val petName: String,
    val coins: Long,
    val petXp: Long,
)

sealed interface AuthenticationState {
    data object Restoring : AuthenticationState
    data object SignedOut : AuthenticationState
    data class SignedIn(val account: UserAccount) : AuthenticationState
    data class Unavailable(val message: String, val canRetry: Boolean) : AuthenticationState
}

enum class AuthFailure(val userMessage: String) {
    INVALID_CREDENTIALS("The email or password is incorrect."),
    EMAIL_NOT_CONFIRMED("Confirm your email before signing in."),
    WEAK_PASSWORD("Choose a stronger password and try again."),
    RATE_LIMITED("Too many attempts. Please wait a moment and try again."),
    NETWORK("We couldn't connect. Check your internet connection and try again."),
    SERVICE("Sign-in is temporarily unavailable. Please try again."),
    CONFIGURATION("Sign-in is unavailable in this build."),
    ACCOUNT("We couldn't prepare your account. Please try again."),
    INVALID_CODE("The code is incorrect or has expired. Request a new code."),
    SAME_PASSWORD("Choose a password different from your current one."),
    DELETION("We couldn't delete your account. Nothing was removed. Please try again."),
}

class AuthenticationException(val failure: AuthFailure) : Exception(failure.userMessage)
enum class RegistrationOutcome { SIGNED_IN, CONFIRM_EMAIL }
enum class LogoutOutcome { SIGNED_OUT, LOCAL_ONLY }
