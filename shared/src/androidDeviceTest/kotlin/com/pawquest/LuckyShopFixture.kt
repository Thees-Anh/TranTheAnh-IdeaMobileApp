package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.presentation.gacha.*

internal interface LuckyShopBackend : LuckyWheelRepository,PetShardExchangeRepository
/** Test-only economy. No Supabase client, network or real account. */
internal class LuckyShopFixture {
    var coins=1000L;var tickets=1L;var shards=210L;var spinCalls=0;var redeemCalls=0
    val owned=mutableSetOf<String>()
    val id="ab280000-0000-0000-0000-000000000001"
    val stamp="2026-11-10T10:00:00Z"
    val configuration=LuckyWheelConfiguration(LuckyWheelViewModel.BANNER,true,id,1,100,1,50,
        WheelSector.entries.mapIndexed {i,s->WheelSectorConfiguration(s,i,listOf(2500,2000,1800,1200,1000,800,500,200)[i],
            listOf(1,50,5,10,1,20,50,1)[i],if(i==0) "e1000000-0000-0000-0000-000000000001" else null)})
    val backend=object : LuckyShopBackend {
        private val receipts=mutableMapOf<String,LuckyWheelReceipt>()
        override suspend fun configuration(bannerId: String)=configuration
        override suspend fun balances()=LuckyWheelBalances(coins,tickets,shards)
        override suspend fun spin(request: LuckyWheelSpinRequest,expectedOwner: String): LuckyWheelResponse {
            val receipt=receipts.getOrPut(request.requestId) {
                spinCalls++;if(request.payment==WheelPayment.COINS) coins-=100 else tickets--
                val pet=redeemablePetCodes.firstOrNull {it !in owned}
                if(pet==null) shards+=50 else owned.add(pet)
                LuckyWheelReceipt(request.requestId,request.requestId,request.bannerId,id,1,WheelSector.PET_UNLOCK,request.payment,
                    if(request.payment==WheelPayment.COINS) 100 else 0,if(request.payment==WheelPayment.TICKET) 1 else 0,
                    if(pet==null) WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS else WheelReward.PET_UNLOCKED,
                    null,0,pet,if(pet==null) 50 else 0,0,0,coins,shards,tickets,stamp)
            }
            return LuckyWheelResponse.Spun(receipt)
        }
        override suspend fun history(beforeCreatedAt: String?,beforeTransactionId: String?,limit: Int)=receipts.values.toList()
        override suspend fun catalog()=listOf("rabbit" to 50,"puppy" to 70,"capybara" to 90).map {(code,price)->
            PetShardCatalogEntry(id,code,price,code,code,code in owned)
        }
        override suspend fun quote(request: PetShardRequest,expectedOwner: String)=PetShardResponse.Quoted(PetShardQuote(request.requestId,id,request.speciesCode,
            catalog().single {it.speciesCode==request.speciesCode}.price,"2026-11-10T10:10:00Z"))
        override suspend fun redeem(request: PetShardRequest,expectedOwner: String): PetShardResponse {
            check(owned.add(request.speciesCode));redeemCalls++;val price=catalog().single {it.speciesCode==request.speciesCode}.price;shards-=price
            return PetShardResponse.Redeemed(PetShardReceipt(request.requestId,request.requestId,id,request.speciesCode,price,shards,stamp))
        }
    }
    val journal=object : PendingLuckyOperationStore {
        var row: PendingLuckyOperation?=null
        override suspend fun load(owner: String)=row
        override suspend fun save(request: PendingLuckyOperation): PendingLuckyOperation {row=request;return request}
        override suspend fun clear(request: PendingLuckyOperation) {if(row==request) row=null}
    }
}
