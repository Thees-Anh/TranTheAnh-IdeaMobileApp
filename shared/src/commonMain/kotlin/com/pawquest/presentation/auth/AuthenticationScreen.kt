package com.pawquest.presentation.auth

import com.pawquest.localization.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.AuthenticationState
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.CardVariant
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.components.PawQuestCard
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
fun AuthenticationScreen(
    state: AuthenticationState,
    action: AuthActionState,
    onSubmit: (String, String, String, Boolean) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onClearMessages: () -> Unit,
    onRequestReset: (String) -> Unit = {},
    onCompleteReset: (String, String, String, String) -> Unit = { _, _, _, _ -> },
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    val registering = mode == AuthMode.REGISTER
    val resetting = mode == AuthMode.RESET
    val codeStep = resetting && action.resetCodeSent
    var code by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately never enter SavedStateHandle, rememberSaveable or a ViewModel.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(mode) { password = ""; confirmation = ""; code = ""; visible = false }
    LaunchedEffect(action.notice) { if (action.notice != null && !codeStep) { password = ""; confirmation = ""; visible = false } }
    val submit = {
        keyboard?.hide()
        when {
            codeStep -> onCompleteReset(email, code, password, confirmation)
            resetting -> onRequestReset(email)
            else -> onSubmit(email, password, confirmation, registering)
        }
    }
    val colors = PawQuestTheme.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primaryDark, unfocusedBorderColor = colors.textSecondary,
        focusedLabelColor = colors.textPrimary, unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.primaryDark,
    )

    Box(Modifier.fillMaxSize().background(colors.backgroundAlt).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr("PawQuest"), style = PawQuestTheme.typography.headlineLarge, color = colors.textPrimary)
            Text(tr("Little quests. A cozy life together."), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
            com.pawquest.localization.LanguageSelector()
            when (state) {
                AuthenticationState.Restoring -> {
                    CircularProgressIndicator(color = colors.primaryDark)
                    Text(tr("Getting your account ready…"))
                }
                is AuthenticationState.Unavailable -> {
                    PawQuestCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.Outlined) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(tr(state.message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            action.error?.let { Text(tr(it), color = colors.textPrimary) }
                            if (state.canRetry) {
                                PawQuestButton("Try again", onRetry, Modifier.fillMaxWidth(), loading = action.busy, labelColor = colors.textPrimary)
                                PawQuestButton("Sign out", onLogout, Modifier.fillMaxWidth(), variant = ButtonVariant.Outlined, enabled = !action.busy)
                            }
                        }
                    }
                }
                AuthenticationState.SignedOut -> {
                    PawQuestCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.Outlined) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(tr(when (mode) { AuthMode.REGISTER -> "Create your account"; AuthMode.RESET -> "Reset your password"; AuthMode.SIGN_IN -> "Welcome back" }),
                                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                            OutlinedTextField(
                                colors = fieldColors,
                                value = email,
                                onValueChange = { email = it; onClearMessages() },
                                label = { Text(tr("Email")) }, singleLine = true, enabled = !action.busy && !codeStep,
                                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email,
                                    imeAction = if (resetting && !codeStep) ImeAction.Done else ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }),
                            )
                            if (codeStep) OutlinedTextField(
                                colors = fieldColors,
                                value = code,
                                onValueChange = { value -> code = value.filter(Char::isDigit).take(10); onClearMessages() },
                                label = { Text(tr("Code from email")) }, singleLine = true, enabled = !action.busy,
                                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                            )
                            if (!resetting || codeStep) OutlinedTextField(
                                colors = fieldColors,
                                value = password,
                                onValueChange = { password = it; onClearMessages() },
                                label = { Text(tr(if (codeStep) "New password" else "Password")) }, singleLine = true, enabled = !action.busy,
                                modifier = Modifier.fillMaxWidth().semantics {
                                    contentType = if (registering || codeStep) ContentType.NewPassword else ContentType.Password
                                },
                                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = !action.busy) { Text(tr(if (visible) "Hide" else "Show"), color = colors.textPrimary) } },
                                supportingText = if (registering || codeStep) {{ Text(tr("At least 8 characters")) }} else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                    imeAction = if (registering || codeStep) ImeAction.Next else ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onNext = { focus.moveFocus(FocusDirection.Down) },
                                    onDone = { submit() },
                                ),
                            )
                            if (registering || codeStep) {
                                OutlinedTextField(
                                    colors = fieldColors,
                                    value = confirmation, onValueChange = { confirmation = it; onClearMessages() },
                                    label = { Text(tr("Confirm password")) }, singleLine = true, enabled = !action.busy,
                                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
                                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submit() }),
                                )
                            }
                            action.error?.let {
                                Text(tr(it), color = colors.textPrimary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                            }
                            action.notice?.let {
                                Text(tr(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            PawQuestButton(
                                text = when {
                                    codeStep -> "Set new password"
                                    resetting -> "Send code"
                                    registering -> "Create account"
                                    else -> "Sign in"
                                },
                                onClick = { submit() },
                                modifier = Modifier.fillMaxWidth(), loading = action.busy,
                                labelColor = colors.textPrimary,
                            )
                            if (mode == AuthMode.SIGN_IN) {
                                TextButton(onClick = { mode = AuthMode.RESET; onClearMessages() }, enabled = !action.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(tr("Forgot password?"), color = colors.textPrimary)
                                }
                            }
                            if (codeStep) {
                                TextButton(onClick = { onRequestReset(email) }, enabled = !action.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(tr("Send a new code"), color = colors.textPrimary)
                                }
                            }
                            TextButton(onClick = {
                                mode = if (mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN; onClearMessages()
                            }, enabled = !action.busy, modifier = Modifier.fillMaxWidth()) {
                                Text(tr(when (mode) {
                                    AuthMode.SIGN_IN -> "New here? Create an account"
                                    AuthMode.REGISTER -> "Already have an account? Sign in"
                                    AuthMode.RESET -> "Back to sign in"
                                }), color = colors.textPrimary)
                            }
                        }
                    }
                }
                is AuthenticationState.SignedIn -> Unit
            }
        }
    }
}

private enum class AuthMode { SIGN_IN, REGISTER, RESET }
