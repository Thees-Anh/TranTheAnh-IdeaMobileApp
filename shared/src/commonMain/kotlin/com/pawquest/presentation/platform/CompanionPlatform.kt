package com.pawquest.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.pawquest.domain.model.*

/** Platform services observe confirmed shared state; they never grant gameplay effects. */
interface CompanionPlatform {
    fun authenticationChanged(state: AuthenticationState)
    fun petChanged(snapshot: PetSnapshot)
    fun questsChanged()
    fun focusChanged(presence: FocusPresence?) {}
    fun playLevelUpSound() {}
}
val LocalCompanionPlatform=staticCompositionLocalOf<CompanionPlatform?> {null}
data class CompanionSettingsState(val notificationsEnabled: Boolean=false,val dynamicIcon: Boolean=true,
    val permissionMissing: Boolean=false,val message: String?=null,val notificationsOptIn: Boolean=false,val soundEnabled: Boolean=true,
    val widgetPinSupported: Boolean=false,val exactAlarmsAllowed: Boolean=true)
data class CompanionSettingsController(val state: CompanionSettingsState,
    val enable: ()->Unit,val disable: ()->Unit,val iconEnabled: (Boolean)->Unit,val systemSettings: ()->Unit,
    val setSoundEnabled: (Boolean)->Unit={},val pinWidget: ()->Unit={},val allowExactAlarms: ()->Unit={})
val LocalCompanionSettings=staticCompositionLocalOf<CompanionSettingsController?> {null}
