package com.pawquest.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Prototype-inspired warm kawaii palette. User-approved 2D visual direction. */
@Immutable
data class PawQuestColors(
    val primary: Color = Color(0xFFF9A8D4),
    val primaryDark: Color = Color(0xFF9D174D),
    val primaryContainer: Color = Color(0xFFFCE7F3),
    val onPrimary: Color = Color(0xFF592512),
    
    val secondary: Color = Color(0xFFF3E8FF),
    val secondaryDark: Color = Color(0xFFD8B4ED),
    val onSecondary: Color = Color(0xFF70419B),
    
    val background: Color = Color(0xFFFFF9F5),
    val backgroundAlt: Color = Color(0xFFFDF2F8),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceVariant: Color = Color(0xFFFFF1F5),
    val surfaceElevated: Color = Color(0xFFFFFBF6),
    
    val textPrimary: Color = Color(0xFF592512),
    val textSecondary: Color = Color(0xFF8C5233),
    val textMuted: Color = Color(0xFF8C6350),
    
    val accentGold: Color = Color(0xFFFACC15),
    val accentGoldDark: Color = Color(0xFF874B14),
    val accentGoldContainer: Color = Color(0xFFFEF3C7),
    
    val petXp: Color = Color(0xFFB79CED),
    val petXpDark: Color = Color(0xFF70419B),
    val petXpContainer: Color = Color(0xFFF1E7FF),
    
    val success: Color = Color(0xFF75C6A1),
    val successDark: Color = Color(0xFF286648),
    val successContainer: Color = Color(0xFFE3F6EB),
    
    val hunger: Color = Color(0xFFFDB874),
    val hungerDark: Color = Color(0xFFA44918),
    val hungerContainer: Color = Color(0xFFFFF0DA),
    
    val energy: Color = Color(0xFF9DB5ED),
    val energyDark: Color = Color(0xFF455F9B),
    val energyContainer: Color = Color(0xFFE8EDFF),
    
    val error: Color = Color(0xFFB9314B),
    val errorContainer: Color = Color(0xFFFFE2E9),
    val onError: Color = Color(0xFFFFFFFF),
    
    val borderLight: Color = Color(0xFF592512),
    val borderFocus: Color = Color(0xFFB55782),
    val shadowColor: Color = Color(0x28592512),
)

val LocalPawQuestColors = staticCompositionLocalOf { PawQuestColors() }

/**
 * Warm night palette. Fills (primary, accentGold, secondary...) are deepened so the light
 * text that buttons and chips pair with them keeps ≥4.5:1 contrast; "*Dark" accent tokens
 * become light tints because they are drawn as text/icons on dark surfaces.
 */
fun pawQuestDarkColors() = PawQuestColors(
    primary = Color(0xFF8E3A64), primaryDark = Color(0xFFF9A8D4), primaryContainer = Color(0xFF3A2230), onPrimary = Color(0xFFFFE8F2),
    secondary = Color(0xFF3B2E4F), secondaryDark = Color(0xFF6E5A8C), onSecondary = Color(0xFFE3D0FF),
    background = Color(0xFF1C1416), backgroundAlt = Color(0xFF22181C), surface = Color(0xFF2A1F23),
    surfaceVariant = Color(0xFF33252B), surfaceElevated = Color(0xFF30242A),
    textPrimary = Color(0xFFF6E7DF), textSecondary = Color(0xFFD9BBAA), textMuted = Color(0xFFB8998A),
    accentGold = Color(0xFF7A5A0C), accentGoldDark = Color(0xFFFCD34D), accentGoldContainer = Color(0xFF3A2E12),
    petXp = Color(0xFF8B6FC9), petXpDark = Color(0xFFCDB8FF), petXpContainer = Color(0xFF2E2540),
    success = Color(0xFF4E9C78), successDark = Color(0xFF9BE3C0), successContainer = Color(0xFF1E3329),
    hunger = Color(0xFFC98A4E), hungerDark = Color(0xFFFFC48A), hungerContainer = Color(0xFF3A2A1A),
    energy = Color(0xFF6D86C2), energyDark = Color(0xFFB9CBFF), energyContainer = Color(0xFF232B40),
    error = Color(0xFFFF8FA6), errorContainer = Color(0xFF4A1C27), onError = Color(0xFF2A0A12),
    borderLight = Color(0xFF9C7B6C), borderFocus = Color(0xFFF9A8D4), shadowColor = Color(0x66000000),
)
