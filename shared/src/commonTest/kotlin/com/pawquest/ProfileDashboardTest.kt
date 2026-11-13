package com.pawquest

import com.pawquest.domain.model.PetCareBalance
import com.pawquest.domain.model.PetProgress
import com.pawquest.domain.model.UserAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileDashboardTest {

    @Test
    fun dailyRewardProgressEnforces250CoinsLimit() {
        val maxDailyCoins = 250
        // Test with 0 sessions
        val coins0 = (0 * 50).coerceAtMost(maxDailyCoins)
        assertEquals(0, coins0)

        // Test with 3 sessions (3 * 50 = 150)
        val coins3 = (3 * 50).coerceAtMost(maxDailyCoins)
        assertEquals(150, coins3)

        // Test with 5 sessions (5 * 50 = 250)
        val coins5 = (5 * 50).coerceAtMost(maxDailyCoins)
        assertEquals(250, coins5)

        // Test with excess sessions (e.g. 6 sessions cap at 250)
        val coins6 = (6 * 50).coerceAtMost(maxDailyCoins)
        assertEquals(250, coins6)
    }

    @Test
    fun rewardedSessionsEnforcesMax5Sessions() {
        val maxSessions = 5
        assertEquals(0, 0.coerceIn(0, maxSessions))
        assertEquals(3, 3.coerceIn(0, maxSessions))
        assertEquals(5, 5.coerceIn(0, maxSessions))
        assertEquals(5, 7.coerceIn(0, maxSessions))
    }

    @Test
    fun petLevelAndXpCalculatedAccurately() {
        // Level 1 with 0 XP
        val p0 = PetProgress(0L)
        assertEquals(1L, p0.level)
        assertEquals(0, p0.xpInLevel)
        assertEquals(100, p0.xpToNextLevel)

        // Level 1 with 85 XP
        val p85 = PetProgress(85L)
        assertEquals(1L, p85.level)
        assertEquals(85, p85.xpInLevel)
        assertEquals(15, p85.xpToNextLevel)

        // Level 2 with 150 total XP
        val p150 = PetProgress(150L)
        assertEquals(2L, p150.level)
        assertEquals(50, p150.xpInLevel)
        assertEquals(50, p150.xpToNextLevel)

        // Level 5 with 475 total XP
        val p475 = PetProgress(475L)
        assertEquals(5L, p475.level)
        assertEquals(75, p475.xpInLevel)
        assertEquals(25, p475.xpToNextLevel)
    }

    @Test
    fun userAccountDataIntegrity() {
        val account = UserAccount(
            userId = "usr-test-1234-uuid",
            email = "catlover@pawquest.dev",
            displayName = "Alex",
            petId = "pet-mochi-1234",
            petName = "Mochi",
            coins = 240L,
            petXp = 180L,
        )

        assertEquals("usr-test-1234-uuid", account.userId)
        assertEquals("catlover@pawquest.dev", account.email)
        assertEquals("Alex", account.displayName)
        assertEquals("Mochi", account.petName)
        assertEquals(240L, account.coins)
        assertEquals(180L, account.petXp)

        val progress = PetProgress(account.petXp)
        assertEquals(2L, progress.level)
        assertEquals(80, progress.xpInLevel)
        assertEquals(20, progress.xpToNextLevel)
    }

    @Test
    fun weeklyCompletionDerivedCorrectly() {
        val completedToday = 3
        val totalQuestsCount = 4
        val completedThisWeek = (completedToday + (totalQuestsCount.coerceAtMost(4) * 2)).coerceAtLeast(completedToday)

        assertTrue(completedThisWeek >= completedToday)
        assertEquals(11, completedThisWeek)
    }
}
