package com.goodwy.dialer.helpers

import android.content.Context
import com.goodwy.dialer.extensions.config
import java.security.MessageDigest

class IdentityManager(private val context: Context) {
    companion object {
        private const val STATIC_SALT = "GoodwyRelaySalt_2024" // Static salt for deterministic UID
    }

    fun computeAndStoreUid(email: String) {
        val normalizedEmail = email.lowercase().trim()
        val input = normalizedEmail + STATIC_SALT
        val uid = hashString(input)
        context.config.relayUid = uid
    }

    fun getUid(): String {
        return context.config.relayUid
    }

    private fun hashString(input: String): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun isLoggedIn(): Boolean {
        return getUid().isNotEmpty()
    }

    fun logout() {
        context.config.relayUid = ""
    }
}
