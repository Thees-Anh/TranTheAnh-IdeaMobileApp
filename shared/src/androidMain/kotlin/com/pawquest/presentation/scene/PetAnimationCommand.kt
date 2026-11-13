package com.pawquest.presentation.scene

import com.pawquest.domain.model.PetAnimationState
import com.pawquest.domain.model.PetCareAction
import com.pawquest.presentation.pet.PetState

internal data class PetAnimationCommand(val state: PetAnimationState,val token: String?=null)

/** Presentation projection of confirmed PetViewModel state; no gameplay writes. */
internal fun petAnimationCommand(state: PetState?): PetAnimationCommand {
    state?.snapshot?.pet?.sleepUntil?.let {
        return PetAnimationCommand(PetAnimationState.SLEEPING,"sleep:$it")
    }
    val result=state?.lastActionResult
    val reaction=state?.reaction
    if(result?.applied!=true || reaction==null || result.action!=reaction) return PetAnimationCommand(PetAnimationState.IDLE)
    return PetAnimationCommand(when(reaction) {
        PetCareAction.DRINK -> PetAnimationState.DRINKING
        PetCareAction.FEED -> PetAnimationState.EATING
        PetCareAction.PLAY -> PetAnimationState.PLAYING
        PetCareAction.TOUCH -> PetAnimationState.HAPPY
        PetCareAction.SLEEP -> PetAnimationState.IDLE // Sleep is driven exclusively by persisted state.
    },result.requestId)
}
