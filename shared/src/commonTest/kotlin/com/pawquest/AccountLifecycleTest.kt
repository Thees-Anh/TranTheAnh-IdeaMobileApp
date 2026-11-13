package com.pawquest

import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.AuthenticationRepository
import com.pawquest.domain.usecase.ManageAuthentication
import com.pawquest.presentation.auth.AuthenticationViewModel
import com.pawquest.presentation.components.portraitFrameWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLifecycleTest {
    private class Fake : AuthenticationRepository {
        val account = UserAccount("one", "a@example.invalid", "Adventurer", "cat-one", "Mochi", 0, 0)
        override val state = MutableStateFlow<AuthenticationState>(AuthenticationState.SignedOut)
        val resetRequests = mutableListOf<String>()
        var completed: Triple<String, String, String>? = null
        var deletedWith: String? = null
        var failure: AuthFailure? = null
        override suspend fun login(email: String, password: String) = Unit
        override suspend fun register(email: String, password: String) = RegistrationOutcome.CONFIRM_EMAIL
        override suspend fun logout(): LogoutOutcome = LogoutOutcome.SIGNED_OUT
        override suspend fun retryRestoration() = Unit
        override suspend fun requestPasswordReset(email: String) { resetRequests += email }
        override suspend fun completePasswordReset(email: String, code: String, newPassword: String) {
            failure?.let { throw AuthenticationException(it) }
            completed = Triple(email, code, newPassword)
            state.value = AuthenticationState.SignedIn(account)
        }
        override suspend fun deleteAccount(password: String) {
            failure?.let { throw AuthenticationException(it) }
            deletedWith = password
            state.value = AuthenticationState.SignedOut
        }
    }

    private fun withModel(block: suspend TestScope.(Fake, AuthenticationViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { val fake = Fake(); block(fake, AuthenticationViewModel(ManageAuthentication(fake))) }
        finally { Dispatchers.resetMain() }
    }

    @Test fun resetRequestValidatesEmailAndMovesToCodeStep() = withModel { fake, model ->
        model.requestPasswordReset("not-an-email")
        assertNotNull(model.action.value.error)
        assertFalse(model.action.value.resetCodeSent)
        model.requestPasswordReset(" a@example.invalid ")
        advanceUntilIdle()
        assertEquals(listOf("a@example.invalid"), fake.resetRequests)
        assertTrue(model.action.value.resetCodeSent)
        assertNotNull(model.action.value.notice)
    }

    @Test fun resetCompletionValidatesCodeAndPasswordThenSignsIn() = withModel { fake, model ->
        model.requestPasswordReset("a@example.invalid"); advanceUntilIdle()
        model.completePasswordReset("a@example.invalid", "12", "password1", "password1")
        assertEquals("Enter the code from the email.", model.action.value.error)
        assertTrue(model.action.value.resetCodeSent, "validation errors keep the code step")
        model.completePasswordReset("a@example.invalid", "123456", "password1", "different")
        assertEquals("The passwords don't match.", model.action.value.error)
        model.completePasswordReset("a@example.invalid", "123 456", "password1", "password1")
        advanceUntilIdle()
        assertEquals(Triple("a@example.invalid", "123456", "password1"), fake.completed)
        assertIs<AuthenticationState.SignedIn>(model.authenticationState.value)
    }

    @Test fun expiredCodeStaysOnCodeStepWithMessage() = withModel { fake, model ->
        model.requestPasswordReset("a@example.invalid"); advanceUntilIdle()
        fake.failure = AuthFailure.INVALID_CODE
        model.completePasswordReset("a@example.invalid", "123456", "password1", "password1")
        advanceUntilIdle()
        assertEquals(AuthFailure.INVALID_CODE.userMessage, model.action.value.error)
        assertTrue(model.action.value.resetCodeSent)
    }

    @Test fun deletionRequiresPasswordAndReportsFailure() = withModel { fake, model ->
        fake.state.value = AuthenticationState.SignedIn(fake.account)
        model.deleteAccount("")
        assertNull(fake.deletedWith)
        assertNotNull(model.action.value.error)
        fake.failure = AuthFailure.DELETION
        model.deleteAccount("secret-pass"); advanceUntilIdle()
        assertEquals(AuthFailure.DELETION.userMessage, model.action.value.error)
        assertIs<AuthenticationState.SignedIn>(model.authenticationState.value)
        fake.failure = null
        model.deleteAccount("secret-pass"); advanceUntilIdle()
        assertEquals("secret-pass", fake.deletedWith)
        assertEquals(AuthenticationState.SignedOut, model.authenticationState.value)
        assertEquals("Your account was deleted.", model.action.value.notice)
    }

    @Test fun unconfiguredRepositoryRejectsAccountOperations() = runTest {
        val repository = com.pawquest.data.auth.UnconfiguredAuthenticationRepository()
        assertFailsWith<AuthenticationException> { repository.requestPasswordReset("a@example.invalid") }
        assertFailsWith<AuthenticationException> { repository.deleteAccount("password") }
    }

    @Test fun phoneWindowsUseFullWidthAndWideWindowsGetPortraitColumn() {
        assertNull(portraitFrameWidth(360.dp, 800.dp))
        assertNull(portraitFrameWidth(600.dp, 960.dp))
        assertEquals(450.dp, portraitFrameWidth(1280.dp, 800.dp))   // landscape tablet
        assertEquals(520.dp, portraitFrameWidth(800.dp, 1280.dp))   // portrait tablet, capped
        assertEquals(360.dp, portraitFrameWidth(1000.dp, 400.dp))   // short desktop window, floor
    }
}
