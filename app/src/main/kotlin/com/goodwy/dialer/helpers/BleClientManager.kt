package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.goodwy.dialer.models.BleCommand
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

@SuppressLint("MissingPermission")
class BleClientManager(private val context: Context) {
    private val TAG = "BleClientManager"
    private val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val writeQueue = ConcurrentLinkedQueue<BleCommand>()
    private var isProcessing = false
    private var targetAddress: String? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server. Requesting MTU...")
                    gatt.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server")
                    close()
                }
            } else {
                Log.e(TAG, "GATT error: $status")
                close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed to $mtu. Discovering services...")
                gatt.discoverServices()
            } else {
                Log.e(TAG, "MTU change failed: $status")
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered. Writing command...")
                processQueue()
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                close()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful")
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
            }
            
            // Disconnect after each exchange for energy efficiency
            isProcessing = false
            close()
        }
    }

    fun sendCommand(address: String, command: BleCommand) {
        if (address.isEmpty()) return
        
        targetAddress = address
        writeQueue.add(command)
        
        if (!isProcessing) {
            connect()
        }
    }

    private fun connect() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        val device = bluetoothAdapter.getRemoteDevice(targetAddress) ?: return
        
        Log.d(TAG, "Connecting to ${device.address}...")
        isProcessing = true
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun processQueue() {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(BleConstants.RELAY_SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(BleConstants.COMMAND_CHARACTERISTIC_UUID) ?: return
        
        val command = writeQueue.poll() ?: return
        val json = command.toJson()
        val data = json.toByteArray(StandardCharsets.UTF_8)
        
        Log.d(TAG, "Writing command: $json")
        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(characteristic)
    }

    private fun close() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isProcessing = false
        
        if (writeQueue.isNotEmpty()) {
            connect()
        }
    }
}
