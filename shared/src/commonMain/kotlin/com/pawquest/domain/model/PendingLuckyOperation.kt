package com.pawquest.domain.model

import kotlinx.serialization.Serializable

@Serializable enum class LuckyOperationKind { SPIN, PET_REDEMPTION }
@Serializable data class PendingLuckyOperation(
    val userId: String,val requestId: String,val kind: LuckyOperationKind,
    val bannerId: String?=null,val payment: WheelPayment?=null,val speciesCode: String?=null,val confirmed: Boolean=false,
) {
    init {
        requireCaseUuid(userId);requireCaseUuid(requestId)
        if(kind==LuckyOperationKind.SPIN) {requireCaseUuid(checkNotNull(bannerId));require(payment!=null && speciesCode==null && confirmed)}
        else require(speciesCode in redeemablePetCodes && bannerId==null && payment==null)
    }
    fun spinRequest()=LuckyWheelSpinRequest(checkNotNull(bannerId),checkNotNull(payment),requestId)
    fun petRequest()=PetShardRequest(checkNotNull(speciesCode),requestId)
}
