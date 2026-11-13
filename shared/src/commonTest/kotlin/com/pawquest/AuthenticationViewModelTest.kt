package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.AuthenticationRepository
import com.pawquest.domain.usecase.ManageAuthentication
import com.pawquest.presentation.auth.AuthenticationViewModel
import com.pawquest.presentation.auth.validateCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    private class Fake : AuthenticationRepository {
        override val state = MutableStateFlow<AuthenticationState>(AuthenticationState.SignedOut)
        var logins = 0
        var emailUsed = ""
        var failure: AuthFailure? = null
        override suspend fun login(email: String, password: String) {
            logins++; emailUsed = email; delay(10)
            failure?.let { throw AuthenticationException(it) }
            state.value = AuthenticationState.SignedIn(UserAccount("one", email,"Adventurer","cat-one","Mochi",0,0))
        }
        override suspend fun register(email: String, password: String) = RegistrationOutcome.CONFIRM_EMAIL
        override suspend fun logout(): LogoutOutcome { state.value = AuthenticationState.SignedOut; return LogoutOutcome.LOCAL_ONLY }
        override suspend fun retryRestoration() = Unit
    }

    @Test fun validatesRegistrationButAllowsExistingShortPasswordLogin() {
        assertNotNull(validateCredentials("not-an-email", "password", "password", true))
        assertNotNull(validateCredentials("a@example.invalid", "short", "short", true))
        assertNotNull(validateCredentials("a@example.invalid", "password", "different", true))
        assertNull(validateCredentials(" a@example.invalid ", "password", "password", true))
        assertNull(validateCredentials("a@example.invalid", "short", "", false))
    }

    @Test fun duplicateSubmissionIsIgnoredAndEmailTrimmed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Fake()
            val model = AuthenticationViewModel(ManageAuthentication(repository))
            repeat(3) { model.submit(" a@example.invalid ", "password", "", false) }
            assertTrue(model.action.value.busy)
            advanceUntilIdle()
            assertEquals(1, repository.logins)
            assertEquals("a@example.invalid", repository.emailUsed)
            assertIs<AuthenticationState.SignedIn>(model.authenticationState.value)
            assertFalse(model.action.value.busy)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun confirmationAndAuthenticationFailuresStayOutsideProtectedState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Fake()
            val model = AuthenticationViewModel(ManageAuthentication(repository))
            model.submit("a@example.invalid", "password", "password", true)
            advanceUntilIdle()
            assertNotNull(model.action.value.notice)
            assertIs<AuthenticationState.SignedOut>(model.authenticationState.value)
            repository.failure = AuthFailure.INVALID_CREDENTIALS
            model.submit("a@example.invalid", "incorrect", "", false)
            advanceUntilIdle()
            assertEquals(AuthFailure.INVALID_CREDENTIALS.userMessage, model.action.value.error)
            assertIs<AuthenticationState.SignedOut>(model.authenticationState.value)
            assertFalse(model.action.value.busy)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun offlineLogoutRemovesProtectedStateAndReportsLocalOnly() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Fake()
            val model = AuthenticationViewModel(ManageAuthentication(repository))
            model.submit("a@example.invalid", "password", "", false)
            advanceUntilIdle()
            model.logout()
            advanceUntilIdle()
            assertIs<AuthenticationState.SignedOut>(model.authenticationState.value)
            assertTrue(model.action.value.notice!!.contains("this device"))
        } finally { Dispatchers.resetMain() }
    }
}
