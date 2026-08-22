package com.thejas.fleetmanagementtask.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Reads and writes are synchronous by design: OkHttp's interceptor and
 * authenticator run on a background thread and cannot suspend.
 */
interface TokenStore {
    val accessToken: String?
    val refreshToken: String?
    val userId: String?
    val role: String?
    val isLoggedIn: Boolean get() = accessToken != null

    fun saveSession(access: String, refresh: String, userId: String, role: String)
    fun updateTokens(access: String, refresh: String)
    fun clear()
}

/**
 * Backed by a key held in the Android Keystore, so a device backup or a rooted
 * filesystem does not hand over a usable session.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    override val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    override val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    override val role: String? get() = prefs.getString(KEY_ROLE, null)

    override fun saveSession(access: String, refresh: String, userId: String, role: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ROLE, role)
            .apply()
    }

    override fun updateTokens(access: String, refresh: String) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
    }

    override fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE_NAME = "fleet_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_ROLE = "role"
    }
}
