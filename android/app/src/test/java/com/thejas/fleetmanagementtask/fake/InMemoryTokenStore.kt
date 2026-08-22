package com.thejas.fleetmanagementtask.fake

import com.thejas.fleetmanagementtask.data.local.TokenStore

class InMemoryTokenStore : TokenStore {
    override var accessToken: String? = null
        private set
    override var refreshToken: String? = null
        private set
    override var userId: String? = null
        private set
    override var role: String? = null
        private set

    var cleared = false
        private set

    override fun saveSession(access: String, refresh: String, userId: String, role: String) {
        accessToken = access
        refreshToken = refresh
        this.userId = userId
        this.role = role
    }

    override fun updateTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
        userId = null
        role = null
        cleared = true
    }

    fun seed(access: String = "old-access", refresh: String = "old-refresh") =
        saveSession(access, refresh, "user-1", "DRIVER")
}
