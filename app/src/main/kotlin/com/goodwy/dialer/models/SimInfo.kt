package com.goodwy.dialer.models

import kotlinx.serialization.Serializable

@Serializable
data class SimInfo(
    val slotIndex: Int,
    val carrierName: String,
    val subscriptionId: Int
)
