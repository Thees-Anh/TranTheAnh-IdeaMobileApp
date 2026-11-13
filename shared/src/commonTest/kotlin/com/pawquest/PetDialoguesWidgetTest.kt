package com.pawquest

import com.pawquest.domain.model.*
import kotlin.random.Random
import kotlin.test.*

class PetDialoguesWidgetTest {
    @Test fun catalogHas44UniqueShortLinesAndAllTopicsCanAvoidImmediateRepetition() {
        val all=PetDialogues.lines.values.flatten()
        assertEquals(44,all.size);assertEquals(all.size,all.toSet().size)
        assertTrue(all.all {it.isNotBlank() && it.length<=75})
        for(topic in DialogueTopic.entries) {
            var previous: String?=null
            repeat(100) {
                val next=PetDialogues.choose(topic,previous,Random(it))
                assertNotEquals(previous,next);assertTrue(next in PetDialogues.lines.getValue(topic));previous=next
            }
        }
    }
    @Test fun localHourBoundariesSelectTheRightTone() {
        for(hour in 0..23) assertEquals(when(hour) {
            in 6..10->DialogueTopic.MORNING;in 11..13->DialogueTopic.NOON;in 14..17->DialogueTopic.AFTERNOON
            in 18..21->DialogueTopic.EVENING;else->DialogueTopic.NIGHT
        },PetDialogues.timeTopic(hour))
        assertFailsWith<IllegalArgumentException> {PetDialogues.timeTopic(24)}
        assertEquals(DialogueTopic.NIGHT,PetDialogues.timeTopic(23))
    }
    @Test fun careActionsTakePriorityOverHungerAndDaypart() {
        val hungry=PetStats(hunger=0,hydration=0)
        assertEquals(DialogueTopic.EATING,PetDialogues.topic(8,PetAnimationState.EATING,hungry))
        assertEquals(DialogueTopic.DRINKING,PetDialogues.topic(23,PetAnimationState.DRINKING,hungry))
        assertEquals(DialogueTopic.SLEEPING,PetDialogues.topic(8,PetAnimationState.SLEEPING,hungry))
        assertEquals(DialogueTopic.HUNGRY,PetDialogues.topic(8,PetAnimationState.IDLE,hungry))
        assertEquals(DialogueTopic.THIRSTY,PetDialogues.topic(8,PetAnimationState.IDLE,PetStats(hydration=25)))
        assertEquals(DialogueTopic.MORNING,PetDialogues.topic(8,PetAnimationState.IDLE,PetStats()))
    }
    @Test fun widgetMoodMatchesNeedsWithoutChangingStats() {
        val stats=PetStats(hunger=25,hydration=25,energy=25)
        assertEquals(WidgetPetMood.SLEEPING,WidgetPetRules.mood(stats,true))
        assertEquals(WidgetPetMood.HUNGRY,WidgetPetRules.mood(stats,false))
        assertEquals(WidgetPetMood.THIRSTY,WidgetPetRules.mood(stats.copy(hunger=26),false))
        assertEquals(WidgetPetMood.TIRED,WidgetPetRules.mood(stats.copy(hunger=26,hydration=26),false))
        assertEquals(WidgetPetMood.HAPPY,WidgetPetRules.mood(PetStats(),false))
        assertEquals(25,stats.hunger)
    }
    @Test fun unknownOrOldQuestDayAndChangedTimezoneNeverLookLikeCurrentZero() {
        assertEquals(3,WidgetPetRules.visiblePending("2026-11-09","Asia/Bangkok","2026-11-09","Asia/Bangkok",3))
        assertNull(WidgetPetRules.visiblePending("2026-11-08","Asia/Bangkok","2026-11-09","Asia/Bangkok",3))
        assertNull(WidgetPetRules.visiblePending("2026-11-09","UTC","2026-11-09","Asia/Bangkok",0))
        assertNull(WidgetPetRules.visiblePending(null,null,"2026-11-09","UTC",null))
        assertNull(WidgetPetRules.visiblePending("2026-11-09","UTC","2026-11-09","UTC",-1))
    }
}
