package com.pawquest.presentation.profile

import com.pawquest.localization.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.components.PawQuestCard
import com.pawquest.presentation.platform.LocalCompanionSettings
import com.pawquest.presentation.theme.PawQuestTheme

@Composable fun CompanionSettingsCard() {
    val controller=LocalCompanionSettings.current ?: return
    var confirm by remember {mutableStateOf(false)}
    PawQuestCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(tr("Reminders & app icon"),style=PawQuestTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {contentDescription=tr("Cho phép thông báo")}
                .toggleable(controller.state.notificationsOptIn,role=Role.Switch) {if(it) confirm=true else controller.disable()},verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Cho phép thông báo"),style=PawQuestTheme.typography.titleMedium)
                    Text(tr("Daily quests at 9:00, reminders 2 hours before deadlines and when your pet needs care. While Focus runs in the background, a countdown stays in your notifications and an alert sounds when time is up."),style=PawQuestTheme.typography.bodyMedium)
                }
                Switch(checked=controller.state.notificationsOptIn,onCheckedChange=null)
            }
            Text(tr("Quiet hours 23:00–8:00. Quest reminders are scheduled on this phone and also work offline."),style=PawQuestTheme.typography.labelMedium)
            if(controller.state.notificationsOptIn && !controller.state.exactAlarmsAllowed) {
                Text(tr("Android may delay reminders by a few minutes to save battery."),style=PawQuestTheme.typography.labelMedium)
                TextButton(onClick=controller.allowExactAlarms) {Text(tr("Allow exact reminder times"))}
            }
            if(controller.state.permissionMissing) TextButton(onClick=controller.systemSettings) {Text(tr("Mở cài đặt thông báo Android"))}
            controller.state.message?.let {Text(tr(it),style=PawQuestTheme.typography.bodyMedium)}
            HorizontalDivider()
            Text(tr("Bạn nhỏ ngoài màn hình chính"),style=PawQuestTheme.typography.titleMedium)
            Text(tr("A 2×2 widget with your pet's mood, food, water and today's quests. Notifications are not required."),style=PawQuestTheme.typography.bodyMedium)
            if(controller.state.widgetPinSupported) TextButton(onClick=controller.pinWidget) {Text(tr("Add home screen widget"))}
            else Text(tr("Nhấn giữ màn hình chính → Tiện ích → PawQuest."),style=PawQuestTheme.typography.bodyMedium)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {contentDescription=tr("Mood-based app icon")}
                .toggleable(controller.state.dynamicIcon,role=Role.Switch,onValueChange=controller.iconEnabled),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Mood-based app icon"),style=PawQuestTheme.typography.titleMedium)
                    Text(tr("Hungry when Hunger ≤25, bored when Happiness ≤25. Notification permission is not required."),style=PawQuestTheme.typography.bodyMedium)
                }
                Switch(checked=controller.state.dynamicIcon,onCheckedChange=null)
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(tr("Bật thông báo PawQuest?"))},
        text={Text(tr("PawQuest reminds you about quests and pet care, and shows your Focus countdown while you use other apps. Other apps are not tracked. You can turn this off anytime in Profile. Only enabled with your consent and Android permission."))},
        confirmButton={TextButton(onClick={confirm=false;controller.enable()}) {Text(tr("Cho phép"))}},
        dismissButton={TextButton(onClick={confirm=false}) {Text(tr("Để sau"))}})
}
