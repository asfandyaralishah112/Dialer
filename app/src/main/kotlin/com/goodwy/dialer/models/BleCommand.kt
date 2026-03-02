package com.goodwy.dialer.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class BleCommand {

    @Serializable
    data class Ping(
        val timestamp: Long = System.currentTimeMillis()
    ) : BleCommand()

    @Serializable
    data class CallRequest(
        val number: String,
        val simSlot: Int
    ) : BleCommand()

    @Serializable
    data class IncomingCall(
        val number: String
    ) : BleCommand()

    @Serializable
    data class Acknowledgment(
        val success: Boolean
    ) : BleCommand()

    @Serializable
    data class SimInfoSync(
        val sims: List<SimInfo>
    ) : BleCommand()

    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): BleCommand? {
            return try {
                Json.decodeFromString<BleCommand>(json)
            } catch (e: Exception) {
                null
            }
        }
    }
}
