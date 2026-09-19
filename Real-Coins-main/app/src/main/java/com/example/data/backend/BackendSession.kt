package com.example.data.backend

import android.content.Context

class BackendSession(context: Context) {
    private val prefs = context.getSharedPreferences("realcoin_backend_session", Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(value) { prefs.edit().putString("token", value).apply() }
    var accountId: String?
        get() = prefs.getString("account_id", null)
        set(value) { prefs.edit().putString("account_id", value).apply() }
    var localUserId: String?
        get() = prefs.getString("local_user_id", null)
        set(value) { prefs.edit().putString("local_user_id", value).apply() }
    fun authHeader(): String? = token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    fun clear() = prefs.edit().clear().apply()
}
