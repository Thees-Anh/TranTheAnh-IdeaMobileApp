package com.pawquest.data.auth

import android.content.Context
import com.pawquest.domain.repository.AuthenticationRepository
import com.pawquest.domain.repository.QuestRepository
import com.pawquest.data.repository.SupabaseQuestRepository
import com.pawquest.data.repository.UnconfiguredQuestRepository
import com.pawquest.domain.repository.QuestSessionRepository
import com.pawquest.data.repository.SupabaseQuestSessionRepository
import com.pawquest.data.repository.UnconfiguredQuestSessionRepository
import com.pawquest.domain.repository.FocusRepository
import com.pawquest.data.repository.SupabaseFocusRepository
import com.pawquest.data.repository.UnconfiguredFocusRepository
import com.pawquest.domain.repository.PetRepository
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.repository.InventoryRepository
import com.pawquest.data.repository.SupabaseItemRepository
import com.pawquest.data.repository.SupabaseInventoryRepository
import com.pawquest.data.repository.UnconfiguredItemRepository
import com.pawquest.data.repository.UnconfiguredInventoryRepository
import com.pawquest.data.repository.SupabasePetRepository
import com.pawquest.data.repository.UnconfiguredPetRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.seconds

data class AndroidRepositories(val authentication: AuthenticationRepository, val quests: QuestRepository,
    val questSessions: QuestSessionRepository, val focus: FocusRepository,val pets: PetRepository,
    val items: ItemRepository = UnconfiguredItemRepository(),
    val inventory: InventoryRepository = UnconfiguredInventoryRepository(),
    val equipment: com.pawquest.domain.repository.EquipmentRepository = com.pawquest.data.repository.UnconfiguredEquipmentRepository(),
    val room: com.pawquest.domain.repository.RoomRepository = com.pawquest.data.repository.UnconfiguredRoomRepository(),
    val reminders: com.pawquest.domain.repository.ReminderRepository = com.pawquest.data.repository.UnconfiguredReminderRepository(),
    val progress: com.pawquest.domain.repository.ProgressRepository = com.pawquest.data.repository.UnconfiguredProgressRepository(),
    val itemCases: com.pawquest.domain.repository.ItemCaseRepository = com.pawquest.data.repository.UnconfiguredItemCaseRepository(),
    val shards: com.pawquest.domain.repository.ShardRepository = com.pawquest.data.repository.UnconfiguredShardRepository(),
    val pendingItemCases: com.pawquest.domain.repository.PendingItemCaseStore = com.pawquest.domain.repository.UnavailablePendingItemCaseStore(),
    val pendingItemExchange: com.pawquest.domain.repository.PendingItemExchangeStore = com.pawquest.domain.repository.UnavailableItemExchangeStore(),
    val luckyWheel: com.pawquest.domain.repository.LuckyWheelRepository = com.pawquest.data.repository.UnconfiguredLuckyWheelRepository(),
    val petExchange: com.pawquest.domain.repository.PetShardExchangeRepository = com.pawquest.data.repository.UnconfiguredLuckyWheelRepository(),
    val pendingLucky: com.pawquest.domain.repository.PendingLuckyOperationStore = com.pawquest.domain.repository.UnavailableLuckyOperationStore())

fun createAndroidRepositories(context: Context, url: String, publishableKey: String, debug: Boolean) =
    if (url.isBlank() || publishableKey.isBlank()) {
        AndroidRepositories(UnconfiguredAuthenticationRepository(), UnconfiguredQuestRepository(), UnconfiguredQuestSessionRepository(), UnconfiguredFocusRepository(),UnconfiguredPetRepository())
    } else {
        val endpoint = runCatching { Url(url) }.getOrNull()
        val localDebug = debug && endpoint?.host in listOf("10.0.2.2", "127.0.0.1", "localhost")
        require(endpoint != null && (endpoint.protocol.name == "https" || localDebug && endpoint.protocol.name == "http")) {
            "Supabase requires HTTPS outside local debug builds"
        }
        require(!publishableKey.startsWith("sb_secret_")) { "A public Supabase client key is required" }
        val client = createSupabaseClient(url, publishableKey) {
            defaultLogLevel = LogLevel.NONE
            httpEngine = OkHttp.create()
            requestTimeout = 15.seconds
            install(Auth) {
                sessionManager = EncryptedSessionManager(context.applicationContext, url)
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
        }
        val offline = com.pawquest.data.offline.AndroidOfflineCache(context)
        AndroidRepositories(
            SupabaseAuthenticationRepository(client, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), { diagnostic ->
                if (debug) android.util.Log.w("PawQuestAccount", diagnostic)
            }, offline),
            SupabaseQuestRepository(client, offline),
            SupabaseQuestSessionRepository(client),
            SupabaseFocusRepository(client),
            SupabasePetRepository(client,{message -> if(debug) android.util.Log.d("PawQuestPet",message)},
                com.pawquest.data.repository.AndroidBistroIntentStore(context,url),offline),
            SupabaseItemRepository(client),
            SupabaseInventoryRepository(client),
            com.pawquest.data.repository.SupabaseEquipmentRepository(client),
            com.pawquest.data.repository.SupabaseRoomRepository(client),
            com.pawquest.data.repository.SupabaseReminderRepository(client),
            com.pawquest.data.repository.SupabaseProgressRepository(client),
            com.pawquest.data.repository.SupabaseItemCaseRepository(client),
            com.pawquest.data.repository.SupabaseShardRepository(client),
            com.pawquest.data.repository.AndroidPendingItemCaseStore(context,url),
            com.pawquest.data.repository.AndroidPendingItemExchangeStore(context,url),
            com.pawquest.data.repository.SupabaseLuckyWheelRepository(client),
            com.pawquest.data.repository.SupabaseLuckyWheelRepository(client),
            com.pawquest.data.repository.AndroidPendingLuckyOperationStore(context,url),
        )
    }
