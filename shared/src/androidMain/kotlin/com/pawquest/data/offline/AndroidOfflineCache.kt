package com.pawquest.data.offline

import android.content.Context

/** App-private storage (backups are disabled for this app); cleared on sign-out/account switch. */
class AndroidOfflineCache(context: Context) : OfflineCache {
    private val prefs = context.applicationContext.getSharedPreferences("pawquest_offline", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}
