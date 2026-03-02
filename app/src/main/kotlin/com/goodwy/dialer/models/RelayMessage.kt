package com.goodwy.dialer.models

import kotlinx.serialization.Serializable

@Serializable
data class RelayMessage(
    val uid: String,
    val cmd: String,
    val payload: String = ""
)
