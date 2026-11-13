package com.pawquest.presentation.gacha

import com.pawquest.domain.model.ItemCaseReceipt
import com.pawquest.domain.model.ItemCaseReward

/** Presentation only; economic authority stays in the existing receipt/ViewModel. */
data class ItemCaseAnimation(
    val receipt: ItemCaseReceipt,
    val rewards: List<ItemCaseReward>,
    val skipRequested: Boolean,
    val onStarted: (String) -> Unit,
    val onFinished: (String) -> Unit,
    val itemNames: Map<String,String> = emptyMap(),
)
