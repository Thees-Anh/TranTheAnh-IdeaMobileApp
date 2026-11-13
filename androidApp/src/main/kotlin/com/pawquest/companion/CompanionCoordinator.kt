package com.pawquest.companion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.pawquest.MainActivity
import com.pawquest.R
import com.pawquest.data.auth.AndroidRepositories
import com.pawquest.domain.model.*
import com.pawquest.presentation.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Installation-local consent/deduplication. All pet/quest facts come from authenticated repositories. */
class CompanionCoordinator(private val context: Context,private val repositories: AndroidRepositories): CompanionPlatform {
    private fun text(value: String) = com.pawquest.localization.translate(value, com.pawquest.LanguagePreferences.read(context))
    private fun updateChannelNames() {
        if (Build.VERSION.SDK_INT >= 26) {
            for ((id, name) in listOf("quests" to "Quest reminders", "pet_care" to "Pet care", "focus_presence" to "Focus company",
                    "focus_done" to "Focus finished")) {
                val channel = notifications.getNotificationChannel(id) ?: continue
                channel.name = text(name)
                notifications.createNotificationChannel(channel)
            }
        }
    }
    fun languageChanged() { scope.launch { lock.withLock {
        // Clear old-language drawer messages without resetting consent or daily deduplication.
        notifications.cancelAll()
        updateChannelNames()
        publish()
        widgets.renderAll()
    } } }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=Mutex()
    private val prefs=context.getSharedPreferences("pawquest_companion",Context.MODE_PRIVATE)
    private val launcher=LauncherMood(context)
    private val widgets=com.pawquest.widget.PetWidgetStore(context)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private val mutableState=MutableStateFlow(CompanionSettingsState())
    val state=mutableState.asStateFlow()
    private var owner: String?=null
    private var ownerGeneration=0L
    private var lastPet: PetSnapshot?=null
    // No Activity has started yet (e.g. the process was launched by an alarm or WorkManager).
    private var away=true
    private var focusPresence: FocusPresence?=null
    private val alarms=CompanionAlarms(context)
    private var levelPlayer: MediaPlayer?=null
    private fun optedIn(user: String)=prefs.getBoolean("notifications.$user",false)
    private fun dynamic(user: String)=prefs.getBoolean("icon.$user",true)

    init {
        if(Build.VERSION.SDK_INT>=26) {
            notifications.createNotificationChannel(NotificationChannel("quests",text("Nhắc nhiệm vụ"),NotificationManager.IMPORTANCE_DEFAULT))
            notifications.createNotificationChannel(NotificationChannel("pet_care",text("Chăm sóc thú cưng"),NotificationManager.IMPORTANCE_DEFAULT))
            notifications.createNotificationChannel(NotificationChannel("focus_presence",text("Focus company"),NotificationManager.IMPORTANCE_LOW))
            notifications.createNotificationChannel(NotificationChannel("focus_done",text("Focus finished"),NotificationManager.IMPORTANCE_HIGH))
        }
        scope.launch {repositories.authentication.state.collect {authenticationChanged(it)}}
    }

    fun systemPermissionGranted(): Boolean =
        (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun publish(message: String?=null) {
        val user=owner
        mutableState.value=CompanionSettingsState(user!=null && optedIn(user) && systemPermissionGranted(),
            user?.let(::dynamic) ?: true,!systemPermissionGranted(),message,user?.let(::optedIn) ?: false,
            user?.let {prefs.getBoolean("sound.$it",true)} ?: true,
            Build.VERSION.SDK_INT>=26 && android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported,
            alarms.exactAllowed())
    }
    override fun authenticationChanged(state: AuthenticationState) {
        scope.launch { lock.withLock {
            when(state) {
                is AuthenticationState.SignedIn -> {
                    val user=state.account.userId
                    if((repositories.authentication.state.value as? AuthenticationState.SignedIn)?.account?.userId!=user) return@withLock
                    if(owner!=user) {
                        val previous=prefs.getString("owner",null)
                        if(previous!=user) {notifications.cancelAll();safeIcon(PetIconMood.NORMAL)}
                        ownerGeneration++;owner=user;lastPet=null
                        widgets.bind(user)
                        // Persist only the owner binding, never auth/session secrets.
                        prefs.edit().putString("owner",user).commit()
                        schedule(user);enqueueNow()
                    }
                    publish()
                }
                AuthenticationState.SignedOut -> {
                    if(repositories.authentication.state.value !is AuthenticationState.SignedOut) return@withLock
                    ownerGeneration++;owner=null;lastPet=null;prefs.edit().remove("owner").commit()
                    widgets.clear()
                    WorkManager.getInstance(context).cancelUniqueWork("companion-periodic")
                    WorkManager.getInstance(context).cancelUniqueWork("companion-now")
                    notifications.cancelAll();safeIcon(PetIconMood.NORMAL);publish()
                    focusPresence=null;alarms.clearAll();stopLevelSound()
                }
                else -> Unit // Connection/refresh failures do not become an account switch.
            }
        } }
    }

    private fun schedule(user: String) {
        val work=WorkManager.getInstance(context)
        if(!dynamic(user) && !optedIn(user) && !widgets.hasWidgets()) {
            work.cancelUniqueWork("companion-periodic");work.cancelUniqueWork("companion-now");return
        }
        val request=PeriodicWorkRequestBuilder<CompanionWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("owner" to user)).build()
        work.enqueueUniquePeriodicWork("companion-periodic",ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    private fun enqueueNow() {
        val user=owner ?: return
        if(!dynamic(user) && !optedIn(user) && !widgets.hasWidgets()) return
        WorkManager.getInstance(context).enqueueUniqueWork("companion-now",ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CompanionWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("owner" to user)).build())
    }
    override fun questsChanged() {scope.launch {lock.withLock {enqueueNow()}}}
    fun widgetChanged() {scope.launch {lock.withLock {owner?.let {schedule(it);enqueueNow()}}}}
    fun pinWidget() {
        if(Build.VERSION.SDK_INT>=26) try {
            android.appwidget.AppWidgetManager.getInstance(context).requestPinAppWidget(
                android.content.ComponentName(context,com.pawquest.widget.PetWidgetProvider::class.java),null,null)
        } catch(_: RuntimeException) {scope.launch {lock.withLock {publish("Long-press your home screen → Widgets → PawQuest.")}}}
    }
    private fun focusNotificationsAllowed(user: String?)=user!=null && optedIn(user) && systemPermissionGranted()
    private fun channelOff(id: String)=Build.VERSION.SDK_INT>=26 && notifications.getNotificationChannel(id)?.importance==NotificationManager.IMPORTANCE_NONE
    private fun focusIntent(code: Int)=PendingIntent.getActivity(context,code,
        Intent(context,MainActivity::class.java).putExtra("pawquest_destination","focus")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Null means the UI went away (not that the session stopped), so the end alarm is kept. */
    override fun focusChanged(presence: FocusPresence?) {scope.launch {lock.withLock {
        focusPresence=presence
        val user=owner ?: return@withLock
        if(presence==null) return@withLock
        if(!presence.running) {alarms.clearFocusEnd();notifications.cancel(FOCUS_ONGOING);return@withLock}
        val end=System.currentTimeMillis()+presence.remainingSeconds*1000
        val previous=alarms.focusEnd()
        if(previous==null || previous.sessionId!=presence.sessionId || kotlin.math.abs(previous.endAtMillis-end)>3_000 ||
            previous.petName!=presence.petName) alarms.armFocusEnd(FocusEndAlarm(user,presence.sessionId,end,presence.petName))
        if(away) showFocusCountdown()
    }}}

    /** Ongoing countdown while the user is in other apps; Android renders the ticking time itself. */
    private fun showFocusCountdown() {
        val alarm=alarms.focusEnd() ?: return
        val remaining=(alarm.endAtMillis-System.currentTimeMillis())/1000
        val presence=FocusPresence(alarm.sessionId,alarm.petName,remaining,active=false,running=true)
        if(alarm.owner!=owner || !FocusNotificationRules.showOngoing(focusNotificationsAllowed(owner),away,presence) ||
            channelOff("focus_presence")) return
        val publicVersion=NotificationCompat.Builder(context,"focus_presence").setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle(text("PawQuest")).setContentText(text("Focus is running")).build()
        val notification=NotificationCompat.Builder(context,"focus_presence").setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle(text("${alarm.petName} is focusing with you"))
            .setContentText(text("Tap to return to Focus."))
            .setWhen(alarm.endAtMillis).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(remaining*1000).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setContentIntent(focusIntent(FOCUS_ONGOING))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
        try {notifications.notify(FOCUS_ONGOING,notification)} catch(_: SecurityException) {}
    }

    fun appBackground(value: Boolean) {scope.launch {lock.withLock {
        away=value
        if(!value) {notifications.cancel(FOCUS_ONGOING);notifications.cancel(FOCUS_DONE);return@withLock}
        stopLevelSound()
        showFocusCountdown()
    }}}

    /** The Focus end alarm fired; announce it only when the user is not looking at the app. */
    suspend fun focusAlarmFired() {lock.withLock {
        val alarm=alarms.focusEnd() ?: return@withLock
        alarms.clearFocusEnd()
        notifications.cancel(FOCUS_ONGOING)
        val user=owner ?: prefs.getString("owner",null)
        val presence=FocusPresence(alarm.sessionId,alarm.petName,0,active=false,running=true)
        if(alarm.owner!=user || !FocusNotificationRules.alertAtEnd(user!=null && optedIn(user) && systemPermissionGranted(),away,presence) ||
            channelOff("focus_done")) return@withLock
        val publicVersion=NotificationCompat.Builder(context,"focus_done").setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle(text("PawQuest")).setContentText(text("Focus finished!")).build()
        val body=text("${alarm.petName} is proud of you. Open PawQuest to claim your reward.")
        val notification=NotificationCompat.Builder(context,"focus_done").setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle(text("Focus finished!")).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(focusIntent(FOCUS_DONE)).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
        try {notifications.notify(FOCUS_DONE,notification)} catch(_: SecurityException) {}
    }}

    /** A reminder alarm fired: prefer the live server context, fall back to the cached schedule offline. */
    suspend fun reminderAlarmFired() {
        val synced=withTimeoutOrNull(8_000) {synchronize()}==true
        if(!synced) lock.withLock {
            val user=owner ?: prefs.getString("owner",null) ?: return@withLock
            val cached=alarms.loadSchedule(user) ?: return@withLock
            if(optedIn(user) && systemPermissionGranted()) {
                val context=ReminderScheduleRules.contextAt(cached,kotlin.time.Clock.System.now())
                // Pet needs are unknown offline; only quest reminders are derived from the cache.
                deliver(user,CompanionReminderRules.candidates(context,PetStats())
                    .filter {it.kind==ReminderKind.QUESTS_DUE || it.kind==ReminderKind.DAILY_IDEA})
                refreshGroups()
            }
            alarms.armNextReminder(cached)
        }
    }

    suspend fun restoreAlarms() {lock.withLock {
        val user=owner ?: prefs.getString("owner",null)
        if(user==null || !optedIn(user)) alarms.cancelReminders()
        alarms.restore(user)
        publish()
    }}

    fun setSoundEnabled(enabled: Boolean) {scope.launch {lock.withLock {
        owner?.let {prefs.edit().putBoolean("sound.$it",enabled).commit()};if(!enabled) stopLevelSound();publish()
    }}}
    private fun stopLevelSound() {scope.launch(Dispatchers.Main) {levelPlayer?.release();levelPlayer=null}}
    override fun playLevelUpSound() {scope.launch {
        val allowed=lock.withLock {owner?.let {prefs.getBoolean("sound.$it",true)}==true && !away}
        if(!allowed) return@launch
        withContext(Dispatchers.Main) {
            val audio=context.getSystemService(AudioManager::class.java)
            if(audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)==0 ||
                notifications.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL) return@withContext
            try {
                levelPlayer?.release()
                val player=MediaPlayer();levelPlayer=player
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                context.resources.openRawResourceFd(R.raw.level_up).use {player.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
                player.setOnCompletionListener {it.release();if(levelPlayer===it)levelPlayer=null}
                player.setOnErrorListener {p,_,_->p.release();if(levelPlayer===p)levelPlayer=null;true}
                player.prepare();player.start()
            } catch(_: Exception) {levelPlayer?.release();levelPlayer=null}
        }
    }}
    override fun petChanged(snapshot: PetSnapshot) {
        scope.launch {lock.withLock {
            val user=owner ?: return@withLock
            if(snapshot.pet.userId!=user || (repositories.authentication.state.value as? AuthenticationState.SignedIn)?.account?.userId!=user) return@withLock
            if(lastPet?.serverNow?.let {it>snapshot.serverNow}==true) return@withLock
            lastPet=snapshot
            widgets.pet(snapshot)
            safeIcon(if(dynamic(user)) petIconMood(snapshot.pet.stats) else PetIconMood.NORMAL)
            if(snapshot.pet.stats.hunger>25) notifications.cancel(1000+ReminderKind.HUNGRY.ordinal)
            if(snapshot.pet.stats.happiness>25) notifications.cancel(1000+ReminderKind.BORED.ordinal)
            if(snapshot.pet.stats.hydration>25) notifications.cancel(1000+ReminderKind.THIRSTY.ordinal)
            refreshGroups()
        }}
    }
    fun foreground() {scope.launch {lock.withLock {publish();enqueueNow()}}}
    fun setNotifications(enabled: Boolean) {
        scope.launch {lock.withLock {
            val user=owner ?: return@withLock
            val accepted=enabled && systemPermissionGranted()
            prefs.edit().putBoolean("notifications.$user",accepted).commit()
            if(!accepted) {notifications.cancelAll();alarms.cancelReminders()}
            publish(if(enabled && !accepted) "Thông báo chưa được Android cho phép. Bạn có thể bật trong cài đặt." else null)
            schedule(user);if(accepted) enqueueNow()
        }}
    }
    fun permissionDenied() {
        scope.launch {lock.withLock {
            val user=owner ?: return@withLock
            prefs.edit().putBoolean("notifications.$user",false).commit()
            notifications.cancelAll();alarms.cancelReminders();publish("Bạn chưa cho phép thông báo. Có thể bật lại trong cài đặt Android.")
            schedule(user)
        }}
    }
    fun setDynamicIcon(enabled: Boolean) {
        scope.launch {lock.withLock {
            val user=owner ?: return@withLock
            prefs.edit().putBoolean("icon.$user",enabled).commit()
            if(!enabled) safeIcon(PetIconMood.NORMAL)
            publish();schedule(user);if(enabled) enqueueNow()
        }}
    }
    private fun safeIcon(mood: PetIconMood) {
        try {launcher.apply(mood)} catch(_: RuntimeException) {
            // Changing a cosmetic icon must never crash or block pet/quest synchronization.
        }
    }

    suspend fun synchronize(expectedOwner: String?=null): Boolean {
        val auth=withTimeoutOrNull(30_000) {repositories.authentication.state.first {it !is AuthenticationState.Restoring}}
        if(auth !is AuthenticationState.SignedIn) return auth is AuthenticationState.SignedOut
        if(expectedOwner!=null && expectedOwner!=auth.account.userId) return true
        // The process may have been started by WorkManager with no Activity.
        authenticationChanged(auth)
        val binding=lock.withLock {
            val user=owner ?: prefs.getString("owner",null)
            if(user!=auth.account.userId) return@withLock null
            user to ownerGeneration
        } ?: return false
        try {
            val pet=repositories.pets.restore()
            val wantsNotifications=lock.withLock {owner==binding.first && optedIn(binding.first) && systemPermissionGranted()}
            val reminderContext=if(wantsNotifications) repositories.reminders.context(ZoneId.systemDefault().id) else null
            val schedule=if(wantsNotifications) try {repositories.reminders.schedule(ZoneId.systemDefault().id)}
                catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {null} else null
            val needsWidget=widgets.hasWidgets()
            var widgetFailed=false
            val widgetContext=if(needsWidget) try {repositories.reminders.widgetContext(ZoneId.systemDefault().id)}
                catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {widgetFailed=true;null} else null
            lock.withLock {
                val user=binding.first
                if(owner!=user || ownerGeneration!=binding.second || pet.pet.userId!=user ||
                    (repositories.authentication.state.value as? AuthenticationState.SignedIn)?.account?.userId!=user) return true
                val currentPet=lastPet?.takeIf {it.serverNow>pet.serverNow} ?: pet
                lastPet=currentPet
                widgets.pet(currentPet)
                if(widgetContext!=null && widgetContext.userId==user && widgetContext.timezone==ZoneId.systemDefault().id) widgets.quests(widgetContext)
                else if(widgetFailed) widgets.failed()
                safeIcon(if(dynamic(user)) petIconMood(currentPet.pet.stats) else PetIconMood.NORMAL)
                if(reminderContext!=null && reminderContext.userId==user && optedIn(user) && systemPermissionGranted()) {
                    val candidates=CompanionReminderRules.candidates(reminderContext,currentPet.pet.stats,currentPet.pet.sleepUntil!=null)
                    val desired=candidates.map {it.kind}.toSet()
                    // Remove stale drawer messages when the need has been resolved.
                    ReminderKind.entries.filter {it !in desired}.forEach {notifications.cancel(1000+it.ordinal)}
                    deliver(user,candidates)
                    refreshGroups()
                    if(schedule!=null && schedule.userId==user && schedule.timezone==ZoneId.systemDefault().id) {
                        alarms.saveSchedule(schedule);alarms.armNextReminder(schedule)
                    } else alarms.loadSchedule(user)?.let {alarms.armNextReminder(it)}
                } else if(!systemPermissionGranted()) notifications.cancelAll()
                publish()
            }
            return !widgetFailed
        } catch(cancelled: CancellationException) {throw cancelled}
        catch(_: Exception) {
            lock.withLock {if(owner==binding.first && ownerGeneration==binding.second) widgets.failed()}
            return false
        } // No stale cached notification claims during an outage.
    }
    private fun deliver(user: String,candidates: List<CompanionReminder>) {
        val sent=prefs.getStringSet("sent.$user",emptySet())!!.toMutableSet()
        for(reminder in ReminderDeliveryRules.pending(candidates,optedIn(user),systemPermissionGranted(),sent)) {
            if(post(reminder)) {
                sent.add(ReminderDeliveryRules.key(reminder))
                prefs.edit().putStringSet("sent.$user",sent.sorted().takeLast(90).toSet()).commit()
            }
        }
    }
    private fun post(reminder: CompanionReminder): Boolean {
        if(!systemPermissionGranted()) return false
        val channel=if(reminder.kind in listOf(ReminderKind.DAILY_IDEA,ReminderKind.QUESTS_DUE)) "quests" else "pet_care"
        if(Build.VERSION.SDK_INT>=26 && notifications.getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE) return false
        val intent=Intent(context,MainActivity::class.java).putExtra("pawquest_destination",reminder.destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,1000+reminder.kind.ordinal,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle(text("PawQuest")).setContentText(text("Bạn có lời nhắc mới.")).build()
        val topic=when(reminder.kind) {
            ReminderKind.HUNGRY->DialogueTopic.HUNGRY;ReminderKind.THIRSTY->DialogueTopic.THIRSTY
            ReminderKind.BORED->DialogueTopic.HAPPY
            else->PetDialogues.timeTopic(java.time.LocalTime.now().hour)
        }
        val phraseKey="dialogue.$owner.${reminder.kind}"
        val phrase=PetDialogues.choose(topic,prefs.getString(phraseKey,null))
        val body=text(reminder.body)+" "+text(phrase)
        val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle(text(reminder.title)).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setGroup("pawquest-$channel").setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
        return try {notifications.notify(1000+reminder.kind.ordinal,notification);prefs.edit().putString(phraseKey,phrase).apply();true} catch(_: SecurityException) {false}
    }

    /** Own summaries provide a meaningful destination; Android's automatic summary only opens the app. */
    private fun refreshGroups() {
        if(!systemPermissionGranted()) return
        for((channel,summaryId) in listOf("quests" to 2000,"pet_care" to 2001)) {
            val children=notifications.activeNotifications.filter {it.id in 1000..1004 && it.notification.group=="pawquest-$channel"}
            if(children.isEmpty()) {notifications.cancel(summaryId);continue}
            if(Build.VERSION.SDK_INT>=26 && notifications.getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE) continue
            val route=if(channel=="quests") "quests" else "pet_home"
            val intent=Intent(context,MainActivity::class.java).putExtra("pawquest_destination",route)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(context,summaryId,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val inbox=NotificationCompat.InboxStyle()
            children.forEach {inbox.addLine(it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE))}
            val publicVersion=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_notification_cat)
                .setContentTitle(text("PawQuest")).setContentText(text("Bạn có lời nhắc mới.")).build()
            val summary=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_notification_cat)
                .setContentTitle(text(if(channel=="quests") "Nhiệm vụ của bạn" else "Bạn nhỏ cần bạn"))
                .setContentText(text("${children.size} lời nhắc từ PawQuest.")).setStyle(inbox).setContentIntent(pending)
                .setGroup("pawquest-$channel").setGroupSummary(true).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
            try {notifications.notify(summaryId,summary)} catch(_: SecurityException) {return}
        }
    }
    private companion object { const val FOCUS_ONGOING=3000; const val FOCUS_DONE=3001 }
}
