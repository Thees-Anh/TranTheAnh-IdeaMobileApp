package com.pawquest.domain.model

import kotlin.time.Instant
import kotlin.time.TimeSource

enum class FocusStatus { ACTIVE, COMPLETED, CANCELLED, CLAIMED }
data class FocusSession(val id: String, val questId: String, val title: String,
    val durationMinutes: Int, val startedAt: Instant, val endsAt: Instant, val status: FocusStatus,
    val requestId: String? = null)
data class ClaimedReward(val coins: Int, val petXp: Int, val balanceAfter: Long)
data class RewardOffer(val tierCoins: Int,val coins: Int,val petXp: Int,val rewardDay: String,
    val rewardedSessions: Int,val limitReached: Boolean,val canClaim: Boolean)
data class FocusSnapshot(val serverNow: Instant, val session: FocusSession?,
    val eligible: Boolean, val reward: ClaimedReward? = null,val offer: RewardOffer? = null)

fun interface FocusClock { fun milliseconds(): Long }
class MonotonicFocusClock : FocusClock {
    private val origin = TimeSource.Monotonic.markNow()
    override fun milliseconds() = origin.elapsedNow().inWholeMilliseconds
}

/** Device wall clock is deliberately absent. An anchor is valid only for foreground display. */
class FocusCountdown(private val clock: FocusClock) {
    private var snapshot: FocusSnapshot? = null
    private var anchoredAt = 0L
    fun anchor(value: FocusSnapshot) { snapshot=value; anchoredAt=clock.milliseconds() }
    fun remainingSeconds(): Long {
        val value=snapshot ?: return 0
        val session=value.session ?: return 0
        val milliseconds=(session.endsAt-value.serverNow).inWholeMilliseconds -
            (clock.milliseconds()-anchoredAt).coerceAtLeast(0)
        return ((milliseconds.coerceAtLeast(0)+999)/1000)
    }
}
