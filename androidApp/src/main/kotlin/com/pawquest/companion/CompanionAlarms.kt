package com.pawquest.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pawquest.PawQuestApplication
import com.pawquest.domain.model.PendingQuestReminder
import com.pawquest.domain.model.ReminderSchedule
import com.pawquest.domain.model.ReminderScheduleRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Instant

/** A Focus session whose end the device must announce even if the app process is gone. */
data class FocusEndAlarm(val owner: String,val sessionId: String,val endAtMillis: Long,val petName: String)

/**
 * On-device alarms for quest reminders and the end of Focus. Alarms fire in Doze and offline;
 * exact timing is used when Android allows it, otherwise Android may defer by a few minutes.
 * Cached data is app-private and cleared on sign-out.
 */
class CompanionAlarms(private val context: Context) {
    private val manager=context.getSystemService(AlarmManager::class.java)
    private val prefs=context.getSharedPreferences("pawquest_alarms",Context.MODE_PRIVATE)

    fun exactAllowed()=Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms()

    private fun intent(action: String,requestCode: Int)=PendingIntent.getBroadcast(context,requestCode,
        Intent(context,CompanionAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun set(atMillis: Long,operation: PendingIntent) {
        try {
            if(exactAllowed()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,atMillis,operation)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,atMillis,operation)
        } catch(_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,atMillis,operation)
        }
    }

    // ---- Quest reminders -------------------------------------------------------------------

    fun saveSchedule(schedule: ReminderSchedule) {
        val upcoming=JSONArray()
        schedule.upcoming.forEach {upcoming.put(JSONObject().put("id",it.id).put("title",it.title).put("due",it.dueAt.toString()))}
        prefs.edit().putString("schedule",JSONObject().put("user",schedule.userId).put("now",schedule.serverNow.toString())
            .put("tz",schedule.timezone).put("daily",schedule.hasDailyQuest).put("upcoming",upcoming).toString()).apply()
    }

    fun loadSchedule(owner: String): ReminderSchedule? = try {
        prefs.getString("schedule",null)?.let(::JSONObject)?.takeIf {it.getString("user")==owner}?.let { json ->
            val items=json.getJSONArray("upcoming")
            ReminderSchedule(owner,Instant.parse(json.getString("now")),json.getString("tz"),json.getBoolean("daily"),
                (0 until items.length()).map {items.getJSONObject(it)}.map {
                    PendingQuestReminder(it.getString("id"),it.getString("title"),Instant.parse(it.getString("due")))
                })
        }
    } catch(_: Exception) {null}

    /** Arms the next reminder after [nowMillis]; later ones are armed when it fires. */
    fun armNextReminder(schedule: ReminderSchedule,nowMillis: Long=System.currentTimeMillis()) {
        val next=ReminderScheduleRules.alarms(schedule,Instant.fromEpochMilliseconds(nowMillis)).firstOrNull()
        val operation=intent(ACTION_REMINDER,4000)
        val prepare=intent(ACTION_REMINDER_PREPARE,4002)
        if(next==null) {manager.cancel(operation);manager.cancel(prepare);return}
        // A small delay keeps the server's inclusive two-hour window satisfied despite clock skew.
        val at=next.at.toEpochMilliseconds()+30_000
        // Without exact-alarm access Android may defer an alarm by up to 75% of the wait (max 1h).
        // Re-arming in hops that always land before the target narrows that to Android's ~10 minute window.
        val wait=if(exactAllowed()) null else ReminderScheduleRules.inexactPrepareWait(at-nowMillis)
        if(wait!=null) {manager.cancel(operation);set(nowMillis+wait,prepare)}
        else {manager.cancel(prepare);set(at,operation)}
    }

    fun cancelReminders() {
        manager.cancel(intent(ACTION_REMINDER,4000));manager.cancel(intent(ACTION_REMINDER_PREPARE,4002))
        prefs.edit().remove("schedule").apply()
    }

    // ---- Focus end -------------------------------------------------------------------------

    fun focusEnd(): FocusEndAlarm? = prefs.getString("focus.session",null)?.let {
        FocusEndAlarm(prefs.getString("focus.owner","")!!,it,prefs.getLong("focus.end",0),prefs.getString("focus.pet","")!!)
    }

    fun armFocusEnd(alarm: FocusEndAlarm) {
        prefs.edit().putString("focus.owner",alarm.owner).putString("focus.session",alarm.sessionId)
            .putLong("focus.end",alarm.endAtMillis).putString("focus.pet",alarm.petName).apply()
        set(alarm.endAtMillis,intent(ACTION_FOCUS_END,4001))
    }

    fun clearFocusEnd() {
        manager.cancel(intent(ACTION_FOCUS_END,4001))
        prefs.edit().remove("focus.owner").remove("focus.session").remove("focus.end").remove("focus.pet").apply()
    }

    fun clearAll() {cancelReminders();clearFocusEnd()}

    /** Alarms do not survive reboot, and time zone or permission changes alter their times. */
    fun restore(owner: String?) {
        if(owner!=null) loadSchedule(owner)?.let {armNextReminder(it)}
        focusEnd()?.let { alarm ->
            if(alarm.owner!=owner || alarm.endAtMillis<System.currentTimeMillis()-60_000) clearFocusEnd()
            else set(maxOf(alarm.endAtMillis,System.currentTimeMillis()+1_000),intent(ACTION_FOCUS_END,4001))
        }
    }

    companion object {
        const val ACTION_REMINDER="com.pawquest.action.REMINDER_ALARM"
        const val ACTION_FOCUS_END="com.pawquest.action.FOCUS_END_ALARM"
        const val ACTION_REMINDER_PREPARE="com.pawquest.action.REMINDER_PREPARE"
    }
}

class CompanionAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val companion=(context.applicationContext as PawQuestApplication).companion
        val result=goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when(intent.action) {
                    CompanionAlarms.ACTION_REMINDER -> companion.reminderAlarmFired()
                    CompanionAlarms.ACTION_FOCUS_END -> companion.focusAlarmFired()
                    CompanionAlarms.ACTION_REMINDER_PREPARE -> companion.restoreAlarms()
                    else -> companion.restoreAlarms() // boot, app update, time/zone or exact-alarm permission change
                }
            } finally {result.finish()}
        }
    }
}
