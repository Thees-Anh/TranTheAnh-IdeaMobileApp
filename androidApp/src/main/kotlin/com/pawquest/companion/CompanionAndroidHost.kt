package com.pawquest.companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.presentation.platform.*

@Composable fun CompanionAndroidHost(coordinator: CompanionCoordinator,content: @Composable ()->Unit) {
    val context=LocalContext.current
    val state by coordinator.state.collectAsStateWithLifecycle()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(granted) coordinator.setNotifications(true) else coordinator.permissionDenied()
    }
    LifecycleResumeEffect(coordinator) {
        coordinator.foreground()
        onPauseOrDispose {}
    }
    val settings=CompanionSettingsController(state,enable={
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else coordinator.setNotifications(true)
    },disable={coordinator.setNotifications(false)},iconEnabled=coordinator::setDynamicIcon,systemSettings={
        val intent=if(Build.VERSION.SDK_INT>=26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    },setSoundEnabled=coordinator::setSoundEnabled,pinWidget=coordinator::pinWidget,allowExactAlarms={
        if(Build.VERSION.SDK_INT>=31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.parse("package:${context.packageName}")))
    })
    CompositionLocalProvider(LocalCompanionPlatform provides coordinator,LocalCompanionSettings provides settings,content=content)
}
