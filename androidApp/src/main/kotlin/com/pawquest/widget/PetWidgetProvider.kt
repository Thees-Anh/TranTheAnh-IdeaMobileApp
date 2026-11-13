package com.pawquest.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.pawquest.PawQuestApplication

class PetWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {
        PetWidgetStore(context).renderAll()
        (context.applicationContext as PawQuestApplication).companion.widgetChanged()
    }
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {PetWidgetStore(context).renderAll()}
    override fun onEnabled(context: Context) {(context.applicationContext as PawQuestApplication).companion.widgetChanged()}
    override fun onDisabled(context: Context) {(context.applicationContext as PawQuestApplication).companion.widgetChanged()}
    override fun onReceive(context: Context,intent: Intent) {
        super.onReceive(context,intent)
        if(intent.action in setOf(REFRESH,Intent.ACTION_DATE_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED)) {
            PetWidgetStore(context).renderAll()
            (context.applicationContext as PawQuestApplication).companion.widgetChanged()
        }
    }
    companion object {const val REFRESH="com.pawquest.widget.REFRESH"}
}
