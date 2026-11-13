package com.pawquest.presentation.pet

import androidx.compose.runtime.compositionLocalOf
import com.pawquest.domain.model.PetCareAction

/** Presentation-only bridge; rendering never owns or mutates persistent stats. */
data class PetController(val state: PetState,val care: (PetCareAction)->Unit,val retry: ()->Unit,
    val loadCollection: ()->Unit={},val selectSpecies: (String)->Unit={},val companionVisible: (Boolean)->Unit={})
// Pet state changes on every care response; nested species scenes must track reads.
val LocalPetController=compositionLocalOf<PetController?> { null }
