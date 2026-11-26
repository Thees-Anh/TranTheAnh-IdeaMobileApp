package com.pawquest.data.repository

import com.pawquest.domain.repository.AppStatusRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseAppStatusRepository(private val client: SupabaseClient): AppStatusRepository {
    override suspend fun updateRequired(versionCode: Int): Boolean {
        val body=client.postgrest.rpc("app_status",buildJsonObject {put("p_version_code",versionCode)}).decodeAs<JsonObject>()
        return body.getValue("update_required").jsonPrimitive.boolean
    }
}
