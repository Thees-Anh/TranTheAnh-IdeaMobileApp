package com.pawquest.presentation.gacha

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

internal actual fun gachaAnimationDialogProperties()=DialogProperties(
    usePlatformDefaultWidth=false,dismissOnClickOutside=false,decorFitsSystemWindows=false)

@Suppress("DEPRECATION")
@Composable internal actual fun GachaAnimationSystemBars(darkIcons: Boolean) {
    val window=(LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.statusBarColor=Color.TRANSPARENT
        window.navigationBarColor=Color.TRANSPARENT
        if(Build.VERSION.SDK_INT in 28..34) window.attributes=window.attributes.apply {
            layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if(Build.VERSION.SDK_INT>=29) {
            window.isStatusBarContrastEnforced=false
            window.isNavigationBarContrastEnforced=false
        }
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=darkIcons
            isAppearanceLightNavigationBars=darkIcons
        }
    }
}
