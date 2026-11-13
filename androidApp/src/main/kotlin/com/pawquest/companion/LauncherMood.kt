package com.pawquest.companion

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pawquest.domain.model.PetIconMood

/** Only launcher aliases change; MainActivity stays enabled for deep links and IDE launches. */
class LauncherMood(private val context: Context) {
    fun apply(mood: PetIconMood) {
        val variants=mapOf(PetIconMood.NORMAL to "NormalLauncher",PetIconMood.HUNGRY to "HungryLauncher",PetIconMood.BORED to "BoredLauncher")
        val manager=context.packageManager
        fun component(name: String)=ComponentName(context.packageName,"${context.packageName}.$name")
        val selected=variants.getValue(mood)
        val changes=variants.values.map {name->
            val desired=if(name==selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            component(name) to desired
        }
        if(changes.all {(name,desired)->
            val current=manager.getComponentEnabledSetting(name)
            val resolved=if(current==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                if(name.className.endsWith(".NormalLauncher")) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else current
            resolved==desired
        }) return
        if(Build.VERSION.SDK_INT>=33) {
            manager.setComponentEnabledSettings(changes.map {(name,state)->
                PackageManager.ComponentEnabledSetting(name,state,PackageManager.DONT_KILL_APP)
            })
        } else {
            // Older APIs have no batch operation: enable the replacement before disabling others.
            manager.setComponentEnabledSetting(component(selected),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
            changes.filter {it.first!=component(selected)}.forEach {(name,state)->
                manager.setComponentEnabledSetting(name,state,PackageManager.DONT_KILL_APP)
            }
        }
    }
}
