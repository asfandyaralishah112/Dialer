package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.models.BleCommand
import org.greenrobot.eventbus.EventBus
import java.nio.charset.StandardCharsets

@SuppressLint("MissingPermission")
class BleServerManager(private val context: Context) {
    private val TAG = "BleServerManager"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "BLE Advertising started successfully")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "BLE Advertising failed: $errorCode")
            isAdvertising = false
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(TAG, "Connection state change: ${device.address} -> $newState")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            
            if (characteristic.uuid == BleConstants.COMMAND_CHARACTERISTIC_UUID) {
                value?.let {
                    val json = String(it, StandardCharsets.UTF_8)
                    Log.d(TAG, "Received command: $json")
                    val command = BleCommand.fromJson(json)
                    
                    if (command != null) {
                        Log.d(TAG, "Processing command: $json")
                        if (command is BleCommand.Ping) {
                            (context as? android.app.Service)?.let {
                                android.os.Handler(it.mainLooper).post {
                                    android.widget.Toast.makeText(it, "Ping Received", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        EventBus.getDefault().post(command)
                    }
                }
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }
    }

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or available")
            return
        }

        setupGattServer()
        startAdvertising()
    }

    fun stop() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
    }

    private fun setupGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(BleConstants.RELAY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val characteristic = BluetoothGattCharacteristic(
            BleConstants.COMMAND_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    private fun startAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleConstants.RELAY_SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        advertiser.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }
}
