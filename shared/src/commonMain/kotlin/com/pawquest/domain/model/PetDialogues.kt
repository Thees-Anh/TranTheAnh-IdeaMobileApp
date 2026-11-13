package com.pawquest.domain.model

import kotlin.random.Random

enum class DialogueTopic { MORNING, NOON, AFTERNOON, EVENING, NIGHT, HUNGRY, THIRSTY, EATING, DRINKING, PLAYING, HAPPY, SLEEPING }

/** Species-neutral original Vietnamese dialogue; selection never changes pet stats. */
object PetDialogues {
    val lines: Map<DialogueTopic,List<String>> = mapOf(
        DialogueTopic.MORNING to listOf("Chào buổi sáng! Hôm nay mình làm gì nhỉ?","Một mục tiêu nhỏ cho ngày mới nhé?","Dậy rồi à? Tớ chờ bạn nãy giờ!","Nắng lên rồi, mình cùng cố gắng nhé!","Bạn ăn sáng chưa? Tớ sẵn sàng rồi!","Ngày mới, thêm một chút tiến bộ thôi!"),
        DialogueTopic.NOON to listOf("Đến giờ nghỉ trưa một chút rồi!","Uống nước với tớ nào!","Buổi sáng của bạn thế nào rồi?","Ăn trưa ngon miệng nhé, bạn thân!","Nghỉ một lát rồi mình làm tiếp nhé!","Một cái vươn vai trước nhiệm vụ tiếp theo!"),
        DialogueTopic.AFTERNOON to listOf("Chiều rồi, mình làm thêm chút nhé?","Tớ cổ vũ bạn từ góc nhỏ này!","Một việc một lúc, chậm cũng được!","Còn mục tiêu nào mình muốn làm nhỉ?","Bạn tập trung, tớ ngoan ngoãn ngồi đây!","Một bước nhỏ nữa là gần đích rồi!"),
        DialogueTopic.EVENING to listOf("Chào buổi tối, bạn về rồi à!","Hôm nay có điều gì làm bạn vui?","Mình cùng nhìn lại những việc đã làm nhé!","Bạn đã cố gắng nhiều rồi đó!","Tớ thích những buổi tối có bạn bên cạnh!","Xong việc rồi thì chơi với tớ nhé!"),
        DialogueTopic.NIGHT to listOf("Bạn vất vả cả ngày rồi, ngủ sớm nhé!","Khuya rồi, mai mình làm tiếp cũng được!","Chúc bạn một giấc mơ thật êm!","Tắt bớt đèn, nghỉ ngơi thôi nào!","Bạn xứng đáng có một giấc ngủ ngon!","Tớ vẫn ở đây, bạn cứ yên tâm nghỉ nhé!"),
        DialogueTopic.HUNGRY to listOf("Bụng tớ đang réo, cho tớ ăn chút nhé?","Một phần thức ăn nhỏ được không bạn?"),
        DialogueTopic.THIRSTY to listOf("Tớ khát rồi, uống nước cùng tớ nhé?","Một ngụm nước mát sẽ tuyệt lắm!"),
        DialogueTopic.EATING to listOf("Ngon quá! Cảm ơn bạn đã chăm tớ!","Măm măm, bụng tớ vui rồi!"),
        DialogueTopic.DRINKING to listOf("Ừm, nước mát thật dễ chịu!","Ực ực! Tớ thấy khỏe hơn rồi!"),
        DialogueTopic.PLAYING to listOf("Chơi cùng bạn là vui nhất!","Thêm một vòng nữa nào, bạn thân!"),
        DialogueTopic.HAPPY to listOf("Có bạn ở đây, tớ vui lắm!","Một cái chạm nhỏ, một ngày dễ thương!"),
        DialogueTopic.SLEEPING to listOf("Tớ chợp mắt một chút nhé...","Zzz... hẹn bạn trong giấc mơ!")
    )
    fun timeTopic(hour: Int): DialogueTopic {
        require(hour in 0..23)
        return when(hour) {in 6..10->DialogueTopic.MORNING;in 11..13->DialogueTopic.NOON;in 14..17->DialogueTopic.AFTERNOON;in 18..21->DialogueTopic.EVENING;else->DialogueTopic.NIGHT}
    }
    fun topic(hour: Int,animation: PetAnimationState,stats: PetStats): DialogueTopic = when(animation) {
        PetAnimationState.EATING->DialogueTopic.EATING;PetAnimationState.DRINKING->DialogueTopic.DRINKING
        PetAnimationState.PLAYING->DialogueTopic.PLAYING;PetAnimationState.HAPPY->DialogueTopic.HAPPY
        PetAnimationState.SLEEPING->DialogueTopic.SLEEPING
        else->when {stats.hunger<=25->DialogueTopic.HUNGRY;stats.hydration<=25->DialogueTopic.THIRSTY;else->timeTopic(hour)}
    }
    fun choose(topic: DialogueTopic,previous: String?=null,random: Random=Random.Default): String {
        val pool=lines.getValue(topic).filter {it!=previous}
        return pool.random(random)
    }
}

/** [completedToday]/[currentStreak] are null when the server predates migration 20261112003400. */
data class WidgetQuestContext(val userId: String,val serverNow: kotlin.time.Instant,val timezone: String,val pendingCount: Int,
    val completedToday: Int?=null,val currentStreak: Int?=null)
enum class WidgetPetMood(val label: String) { SLEEPING("Đang ngủ"),HUNGRY("Đói bụng"),THIRSTY("Khát nước"),TIRED("Buồn ngủ"),HAPPY("Vui vẻ") }
object WidgetPetRules {
    fun mood(stats: PetStats,sleeping: Boolean)=when {
        sleeping->WidgetPetMood.SLEEPING
        stats.hunger<=25->WidgetPetMood.HUNGRY
        stats.hydration<=25->WidgetPetMood.THIRSTY
        stats.energy<=25->WidgetPetMood.TIRED
        else->WidgetPetMood.HAPPY
    }
    fun visiblePending(savedDay: String?,savedZone: String?,today: String,zone: String,count: Int?): Int? =
        count?.takeIf {it>=0 && savedDay==today && savedZone==zone}
}
