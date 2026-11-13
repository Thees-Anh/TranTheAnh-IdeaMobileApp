package com.pawquest.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-backed AES-GCM, atomic writes, excluded from Android backup. No passwords stored. */
class EncryptedSessionManager(context: Context, projectUrl: String) : SessionManager {
    private val projectId = MessageDigest.getInstance("SHA-256").digest(projectUrl.toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
    private val alias = "pawquest.supabase.$projectId"
    private val file = AtomicFile(File(context.noBackupFilesDir, "supabase-session-$projectId.bin"))
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        lock.withLock {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8))
            val bytes = (Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)).toByteArray(Charsets.UTF_8)
            val stream = file.startWrite()
            try { stream.write(bytes); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val parts = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split(':', limit = 2)
                require(parts.size == 2)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                }
                json.decodeFromString<UserSession>(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8))
            } catch (_: Exception) {
                // Missing/corrupt data or an invalidated Keystore key requires a new login.
                file.delete()
                throw NoSessionFoundException()
            }
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        lock.withLock { file.delete() }
    }
}
