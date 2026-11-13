package com.pawquest.data.auth

import com.pawquest.domain.model.AuthFailure
import com.pawquest.domain.model.AuthenticationException
import com.pawquest.domain.model.AuthenticationState
import com.pawquest.domain.model.LogoutOutcome
import com.pawquest.domain.model.RegistrationOutcome
import com.pawquest.domain.model.UserAccount
import com.pawquest.domain.repository.AuthenticationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.pawquest.data.offline.ConnectionStatus
import com.pawquest.data.offline.MemoryOfflineCache
import com.pawquest.data.offline.OfflineCache
import com.pawquest.data.offline.OfflineKeys
import com.pawquest.data.offline.save
import com.pawquest.data.offline.savedAt

@Serializable private data class ProfileRecord(val id: String, @SerialName("display_name") val displayName: String)
@Serializable private data class PetRecord(val id: String, val name: String, val xp: Long)
@Serializable private data class WalletRecord(val coins: Long)
@Serializable private data class AccountBundle(val profile: ProfileRecord, val pet: PetRecord, val wallet: WalletRecord)
@Serializable private data class CachedAccount(val userId: String, val email: String?, val displayName: String,
    val petId: String, val petName: String, val coins: Long, val petXp: Long) {
    fun domain() = UserAccount(userId, email, displayName, petId, petName, coins, petXp)
}

class SupabaseAuthenticationRepository(
    private val client: SupabaseClient,
    scope: CoroutineScope,
    private val onAccountDiagnostic: (String) -> Unit = {},
    private val offline: OfflineCache = MemoryOfflineCache(),
) : AuthenticationRepository {
    private val cacheJson = Json { ignoreUnknownKeys = true }
    private fun cachedAccount(): UserAccount? = offline.read(OfflineKeys.ACCOUNT)?.let {
        runCatching { cacheJson.decodeFromString<CachedAccount>(it).domain() }.getOrNull()
    }
    /** Read-only offline entry with the last confirmed account of this device's stored session. */
    private fun signInFromCache(expectedUser: String?): Boolean {
        val cached = cachedAccount() ?: return false
        if (expectedUser != null && cached.userId != expectedUser) return false
        ConnectionStatus.usingCache(offline.savedAt(OfflineKeys.ACCOUNT))
        mutableState.value = AuthenticationState.SignedIn(cached)
        return true
    }
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.Restoring)
    override val state = mutableState.asStateFlow()
    private val operations = Mutex()
    private var signingOut = false
    // Recovery verification creates a session before the new password is saved; keep it out of the app until then.
    private var recovering = false
    private var generation = 0

    private fun diagnose(operation: String, error: Throwable) {
        val causes = generateSequence(error) { it.cause }.take(5).toList()
        val types = causes.asSequence()
            .joinToString(" -> ") { it::class.simpleName ?: "unknown" }
        // SDK 3.8 flattens transport causes into HttpRequestException.message.
        // Classify only fixed phrases; never output raw errors, URLs, headers or tokens.
        val details=causes.mapNotNull {it.message}.joinToString(" ").lowercase()
        val transport=when {
            "unable to resolve host" in details || "unknownhost" in details -> "dns"
            "certificate" in details || "trust anchor" in details || "sslhandshake" in details -> "tls"
            "connection refused" in details || "failed to connect" in details || "econnrefused" in details -> "connection"
            "timed out" in details || "timeout" in details -> "timeout"
            "eperm" in details || "permission denied" in details -> "network_permission"
            else -> "unspecified"
        }
        onAccountDiagnostic("$operation failed: type=$types, http=${(error as? RestException)?.statusCode ?: "none"}, transport=$transport")
    }

    init {
        scope.launch {
            client.auth.sessionStatus.collectLatest { status ->
                when (status) {
                    SessionStatus.Initializing -> {
                        mutableState.value = AuthenticationState.Restoring
                        // An expired stored session keeps the SDK initializing while it retries the network.
                        // collectLatest cancels this wait as soon as the status changes.
                        kotlinx.coroutines.delay(OFFLINE_START_AFTER_MILLIS)
                        if (mutableState.value == AuthenticationState.Restoring && !signingOut) signInFromCache(null)
                    }
                    is SessionStatus.NotAuthenticated -> mutableState.value = AuthenticationState.SignedOut
                    is SessionStatus.RefreshFailure -> {
                        @Suppress("DEPRECATION")
                        val cause=status.cause
                        diagnose("refresh_session",when(cause) {
                            is RefreshFailureCause.NetworkError -> cause.exception
                            is RefreshFailureCause.InternalServerError -> cause.exception
                        })
                        if (!signInFromCache(null)) mutableState.value = AuthenticationState.Unavailable(
                            "We couldn't restore your session. Check your connection and retry.", true,
                        )
                    }
                    is SessionStatus.Authenticated -> {
                        if (!signingOut && !recovering) prepareAccount(status)
                    }
                }
            }
        }
    }

    private suspend fun prepareAccount(status: SessionStatus.Authenticated) {
        val expectedGeneration = generation
        val user = status.session.user
        val current = mutableState.value as? AuthenticationState.SignedIn
        val sameUser = current != null && current.account.userId == user?.id
        // Same account already shown: only re-confirm it when it came from the offline cache.
        if (sameUser && ConnectionStatus.offlineDataFrom == null) return
        // Keep the signed-in UI (and its ViewModels) while confirming a cached account in the background.
        if (!sameUser) mutableState.value = AuthenticationState.Restoring
        try {
            val bundle = client.postgrest.rpc("ensure_account").decodeAs<AccountBundle>()
            check(bundle.profile.id == user?.id) { "Unexpected account owner" }
            if (!signingOut && generation == expectedGeneration) {
                val account = UserAccount(
                    userId = bundle.profile.id, email = user?.email, displayName = bundle.profile.displayName,
                    petId = bundle.pet.id, petName = bundle.pet.name, coins = bundle.wallet.coins, petXp = bundle.pet.xp,
                )
                // Another account's cached data must never be shown to this one.
                if (cachedAccount()?.userId != account.userId) offline.clear()
                offline.save(OfflineKeys.ACCOUNT, cacheJson.encodeToString(CachedAccount.serializer(), CachedAccount(
                    account.userId, account.email, account.displayName, account.petId, account.petName, account.coins, account.petXp)))
                ConnectionStatus.online()
                mutableState.value = AuthenticationState.SignedIn(account)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Never log exception messages: HTTP errors can contain credentials or account data.
            diagnose("ensure_account", error)
            if (!signingOut && generation == expectedGeneration &&
                (error is RestException || !signInFromCache(user?.id))) {
                mutableState.value = AuthenticationState.Unavailable(AuthFailure.ACCOUNT.userMessage, true)
            }
        }
    }

    override suspend fun login(email: String, password: String) = operations.withLock {
        translateErrors {
            client.auth.awaitInitialization()
            client.auth.signInWith(Email) { this.email = email; this.password = password }
        }
    }

    override suspend fun register(email: String, password: String): RegistrationOutcome = operations.withLock {
        translateErrors {
            client.auth.awaitInitialization()
            client.auth.signUpWith(Email) { this.email = email; this.password = password }
            if (client.auth.currentSessionOrNull() == null) RegistrationOutcome.CONFIRM_EMAIL else RegistrationOutcome.SIGNED_IN
        }
    }

    override suspend fun logout(): LogoutOutcome = operations.withLock {
        generation++
        signingOut = true
        mutableState.value = AuthenticationState.Restoring
        var outcome = LogoutOutcome.SIGNED_OUT
        try {
            client.auth.signOut()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            outcome = LogoutOutcome.LOCAL_ONLY
        } finally {
            offline.clear(); ConnectionStatus.online()
            // Never leave a reusable local refresh token after an offline sign-out.
            try {
                withContext(NonCancellable) { client.auth.clearSession() }
                mutableState.value = AuthenticationState.SignedOut
            } catch (_: Exception) {
                mutableState.value = AuthenticationState.Unavailable("We couldn't finish signing out. Please retry.", true)
                throw AuthenticationException(AuthFailure.SERVICE)
            } finally { signingOut = false }
        }
        outcome
    }

    override suspend fun requestPasswordReset(email: String) = operations.withLock {
        translateErrors {
            client.auth.awaitInitialization()
            // No redirect: the email carries a one-time code that is entered in the app.
            client.auth.resetPasswordForEmail(email, redirectUrl = null)
        }
    }

    override suspend fun completePasswordReset(email: String, code: String, newPassword: String) = operations.withLock {
        translateErrors {
            client.auth.awaitInitialization()
            recovering = true
            try {
                client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email = email, token = code)
                client.auth.updateUser(redirectUrl = null) { password = newPassword }
            } catch (error: Throwable) {
                // Never leave a half-recovered session that skipped the new password.
                withContext(NonCancellable) { runCatching { client.auth.clearSession() } }
                throw error
            } finally { recovering = false }
            (client.auth.sessionStatus.value as? SessionStatus.Authenticated)?.let { prepareAccount(it) }
            Unit
        }
    }

    override suspend fun deleteAccount(password: String) = operations.withLock {
        val email = (mutableState.value as? AuthenticationState.SignedIn)?.account?.email
            ?: throw AuthenticationException(AuthFailure.DELETION)
        // Re-authenticate so an unlocked phone alone cannot erase the account.
        translateErrors { client.auth.signInWith(Email) { this.email = email; this.password = password } }
        try {
            client.postgrest.rpc("delete_my_account")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnose("delete_account", error)
            throw AuthenticationException(if (error is RestException) AuthFailure.DELETION else AuthFailure.NETWORK)
        }
        // The identity and its refresh tokens are gone server-side; only local cleanup remains.
        offline.clear(); ConnectionStatus.online()
        generation++
        signingOut = true
        try { withContext(NonCancellable) { runCatching { client.auth.clearSession() } } }
        finally { signingOut = false }
        mutableState.value = AuthenticationState.SignedOut
    }

    override suspend fun retryRestoration() = operations.withLock {
        try { translateErrors {
            // An expired stored session can keep SDK initialization waiting on
            // its network retry loop. Explicit retry must restart that restore,
            // not wait forever for the very operation the user is retrying.
            if(client.auth.sessionStatus.value !is SessionStatus.RefreshFailure) client.auth.awaitInitialization()
            val status = client.auth.sessionStatus.value
            if (status is SessionStatus.Authenticated) prepareAccount(status)
            else {
                mutableState.value = AuthenticationState.Restoring
                if (!client.auth.loadFromStorage()) mutableState.value = AuthenticationState.SignedOut
            }
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutableState.value = AuthenticationState.Unavailable(AuthFailure.NETWORK.userMessage, true)
            throw error
        }
    }

    private suspend fun <T> translateErrors(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: AuthRestException) {
        diagnose("authentication", error)
        val failure = when (error.error) {
            "invalid_credentials" -> AuthFailure.INVALID_CREDENTIALS
            "email_not_confirmed" -> AuthFailure.EMAIL_NOT_CONFIRMED
            "otp_expired" -> AuthFailure.INVALID_CODE
            "same_password" -> AuthFailure.SAME_PASSWORD
            "weak_password" -> AuthFailure.WEAK_PASSWORD
            "over_request_rate_limit", "over_email_send_rate_limit", "over_sms_send_rate_limit" -> AuthFailure.RATE_LIMITED
            else -> if (error.statusCode == 429) AuthFailure.RATE_LIMITED else AuthFailure.SERVICE
        }
        throw AuthenticationException(failure)
    } catch (error: RestException) {
        diagnose("authentication", error)
        throw AuthenticationException(AuthFailure.SERVICE)
    } catch (error: Exception) {
        diagnose("authentication", error)
        throw AuthenticationException(AuthFailure.NETWORK)
    }
}

private const val OFFLINE_START_AFTER_MILLIS = 5_000L
