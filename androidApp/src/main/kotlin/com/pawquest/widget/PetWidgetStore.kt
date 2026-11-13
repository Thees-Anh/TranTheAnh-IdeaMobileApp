package com.pawquest.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.pawquest.MainActivity
import com.pawquest.R
import com.pawquest.domain.model.*
import java.time.*
import java.time.format.DateTimeFormatter

/** A last-verified display cache. No local decay, rewards, quest completion or care. */
class PetWidgetStore(private val context: Context) {
    private val prefs=context.getSharedPreferences("pawquest_widget",Context.MODE_PRIVATE)
    private val manager=AppWidgetManager.getInstance(context)
    private fun ids()=manager.getAppWidgetIds(ComponentName(context,PetWidgetProvider::class.java))
    fun hasWidgets()=ids().isNotEmpty()
    fun bind(owner: String) {if(prefs.getString("owner",null)!=owner) {prefs.edit(commit=true) {clear();putString("owner",owner)};renderAll()}}
    fun clear() {prefs.edit(commit=true) {clear()};renderAll()}
    fun pet(snapshot: PetSnapshot) {
        if(prefs.getString("owner",null)!=snapshot.pet.userId) return
        val p=snapshot.pet
        val mood=WidgetPetRules.mood(p.stats,p.sleepUntil!=null)
        prefs.edit(commit=true) {putString("name",p.name);putString("species",p.rendererKey)
            .putInt("hunger",p.stats.hunger).putInt("water",p.stats.hydration)
            .putString("mood",mood.label).putString("moodKey",mood.name).putLong("level",p.progress.level)
            .putString("petAt",snapshot.serverNow.toString())}
        renderAll()
    }
    fun quests(value: WidgetQuestContext) {
        if(prefs.getString("owner",null)!=value.userId) return
        val previous=prefs.getString("questAt",null)
        if(prefs.getString("zone",null)==value.timezone && previous!=null && Instant.parse(previous)>Instant.parse(value.serverNow.toString())) return
        val date=Instant.parse(value.serverNow.toString()).atZone(ZoneId.of(value.timezone)).toLocalDate()
        prefs.edit(commit=true) {putString("day",date.toString());putString("zone",value.timezone)
            .putInt("count",value.pendingCount).putString("questAt",value.serverNow.toString()).putBoolean("failed",false)
            value.completedToday?.let {putInt("done",it)} ?: remove("done")
            value.currentStreak?.let {putInt("streak",it)} ?: remove("streak")}
        renderAll()
    }
    fun failed() {prefs.edit {putBoolean("failed",true)};renderAll()}
    fun renderAll() {for(id in ids()) manager.updateAppWidget(id,views())}
    private fun activity(route: String,request: Int)=PendingIntent.getActivity(context,request,
        Intent(context,MainActivity::class.java).putExtra("pawquest_destination",route)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    internal fun views(): RemoteViews {
        val config=android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(com.pawquest.LanguagePreferences.read(context).tag))
        val localizedContext=context.createConfigurationContext(config)
        fun text(value: String?)=com.pawquest.localization.translate(value.orEmpty(),com.pawquest.LanguagePreferences.read(context))
        val v=RemoteViews(context.packageName,R.layout.pet_widget)
        v.setOnClickPendingIntent(R.id.widget_root,activity("pet_home",4100))
        v.setOnClickPendingIntent(R.id.widget_quests,activity("quests",4101))
        v.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(context,4102,
            Intent(context,PetWidgetProvider::class.java).setAction(PetWidgetProvider.REFRESH),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val signedIn=prefs.contains("owner")
        val known=signedIn && prefs.contains("petAt")
        val hunger=if(known) prefs.getInt("hunger",0) else 0
        val water=if(known) prefs.getInt("water",0) else 0
        v.setTextViewText(R.id.widget_name,if(known) prefs.getString("name","PawQuest") else "PawQuest")
        val moodText=text(prefs.getString("mood",""))
        val level=prefs.getLong("level",0)
        v.setTextViewText(R.id.widget_mood,when {
            known && level>0 -> text("Lv. $level")+" · "+moodText
            known -> moodText
            signedIn -> text("Đang đồng bộ…")
            else -> localizedContext.getString(R.string.widget_sign_in)
        })
        // Old widget caches only contain the label; keep them readable without a migration.
        val mood=WidgetPetMood.entries.firstOrNull {it.name==prefs.getString("moodKey",null)}
            ?: WidgetPetMood.entries.firstOrNull {it.label==prefs.getString("mood",null)} ?: WidgetPetMood.HAPPY
        v.setImageViewResource(R.id.widget_pet,PetWidgetArtwork.portrait(prefs.getString("species",null),mood,known))
        v.setImageViewResource(R.id.widget_mood_icon,PetWidgetArtwork.badge(mood))
        v.setViewVisibility(R.id.widget_mood_icon,if(known) android.view.View.VISIBLE else android.view.View.GONE)
        v.setTextViewText(R.id.widget_hunger_text,if(known) text("Food")+" · $hunger%" else localizedContext.getString(R.string.widget_food_unknown))
        v.setTextViewText(R.id.widget_water_text,if(known) text("Water")+" · $water%" else localizedContext.getString(R.string.widget_water_unknown))
        v.setProgressBar(R.id.widget_hunger,100,hunger,false);v.setProgressBar(R.id.widget_water,100,water,false)
        val zone=ZoneId.systemDefault()
        val count=if(signedIn) WidgetPetRules.visiblePending(prefs.getString("day",null),prefs.getString("zone",null),LocalDate.now(zone).toString(),zone.id,
            if(prefs.contains("count")) prefs.getInt("count",0) else null) else null
        // Day-scoped numbers are only shown for the cached local day; the streak survives until the next sync.
        val sameDay=count!=null
        v.setTextViewText(R.id.widget_left,count?.toString() ?: "—")
        v.setTextViewText(R.id.widget_done,if(sameDay && prefs.contains("done")) prefs.getInt("done",0).toString() else "—")
        v.setTextViewText(R.id.widget_streak,if(signedIn && prefs.contains("streak")) prefs.getInt("streak",0).toString() else "—")
        v.setTextViewText(R.id.widget_streak_label,text("Streak"))
        v.setTextViewText(R.id.widget_done_label,text("Done"))
        v.setTextViewText(R.id.widget_left_label,text("Left"))
        v.setContentDescription(R.id.widget_quests,text("Streak")+" ${prefs.getInt("streak",0)}, "+
            text("Done")+" ${if(sameDay) prefs.getInt("done",0) else 0}, "+text("Left")+" ${count ?: 0}")
        val petAt=prefs.getString("petAt",null);val questAt=prefs.getString("questAt",null)
        val latest=if(petAt!=null && questAt!=null) minOf(Instant.parse(petAt),Instant.parse(questAt)) else null
        val stamp=latest?.atZone(zone)?.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
        v.setTextViewText(R.id.widget_updated,text(when {
            !signedIn->localizedContext.getString(R.string.widget_open_app)
            prefs.getBoolean("failed",false)->stamp?.let {"Offline · $it"} ?: "Chưa kết nối · chạm ↻"
            stamp!=null->"Đã cập nhật $stamp"
            else->"Chạm ↻ để đồng bộ"
        }))
        return v
    }
}
