package com.goodwy.dialer.helpers

import java.util.UUID

object BleConstants {

    val RELAY_SERVICE_UUID: UUID = UUID.fromString("0000FEAF-0000-1000-8000-00805f9b34fb")
    val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FEF1-0000-1000-8000-00805f9b34fb")
    val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val COMMAND_NOTIFY_TIMEOUT = 5000L
}
