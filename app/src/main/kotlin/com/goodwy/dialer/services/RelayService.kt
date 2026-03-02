package com.goodwy.dialer.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.widget.Toast
import com.goodwy.dialer.R
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.BleClientManager
import com.goodwy.dialer.helpers.BleConstants
import com.goodwy.dialer.helpers.BleServerManager
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionInfo
import com.goodwy.dialer.models.BleCommand
import com.goodwy.dialer.models.SimInfo
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@SuppressLint("MissingPermission")
class RelayService : Service() {
    private val TAG = "RelayService"
    private var serverManager: BleServerManager? = null
    private var clientManager: BleClientManager? = null
    
    private val subscriptionManager by lazy { getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager }
    private var localSimList = mutableListOf<SimInfo>()
    private var remoteSimList = mutableListOf<SimInfo>()

    private val subscriptionListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            updateLocalSims()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                if (state == android.bluetooth.BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "Bluetooth turned ON, restarting relay...")
                    startRelay()
                } else if (state == android.bluetooth.BluetoothAdapter.STATE_OFF) {
                    Log.d(TAG, "Bluetooth turned OFF, stopping relay...")
                    stopRelay()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RelayService onCreate")
        EventBus.getDefault().register(this)
        registerReceiver(bluetoothReceiver, IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED))
        subscriptionManager.addOnSubscriptionsChangedListener(subscriptionListener)
        startRelay()
        updateLocalSims()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RelayService onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RelayService onDestroy")
        EventBus.getDefault().unregister(this)
        unregisterReceiver(bluetoothReceiver)
        subscriptionManager.removeOnSubscriptionsChangedListener(subscriptionListener)
        stopRelay()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun startRelay() {
        if (!config.isRelayEnabled) return

        Log.d(TAG, "Starting BLE Relay (Bi-directional)...")
        
        // Start Server
        serverManager = BleServerManager(this)
        serverManager?.start()
        
        // Start Client
        clientManager = BleClientManager(this)
    }

    private fun stopRelay() {
        serverManager?.stop()
        serverManager = null
        clientManager = null
    }


    private fun updateLocalSims() {
        val sims = try {
            subscriptionManager.activeSubscriptionInfoList?.map {
                SimInfo(
                    slotIndex = it.simSlotIndex,
                    carrierName = it.displayName.toString(),
                    subscriptionId = it.subscriptionId
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (sims != localSimList) {
            Log.d(TAG, "Local SIMs changed: $sims")
            localSimList.clear()
            localSimList.addAll(sims)
            syncSims()
        }
    }

    private fun syncSims() {
        if (config.isRelayEnabled && config.relayDeviceAddress.isNotEmpty()) {
            val command = BleCommand.SimInfoSync(localSimList)
            clientManager?.sendCommand(config.relayDeviceAddress, command)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBleCommand(command: BleCommand) {
        when (command) {
            is BleCommand.Ping -> {
                Log.d(TAG, "Received Ping")
                Toast.makeText(this, "Ping received!", Toast.LENGTH_SHORT).show()
                
                val localStatus = if (localSimList.isEmpty()) "None" else localSimList.joinToString(", ") { it.carrierName }
                Toast.makeText(this, "Local SIMs: $localStatus", Toast.LENGTH_SHORT).show()
                
                val remoteSims = config.remoteSims
                val remoteStatus = if (remoteSims.isEmpty()) "None" else remoteSims.joinToString(", ") { it.carrierName }
                Toast.makeText(this, "Remote SIMs: $remoteStatus", Toast.LENGTH_SHORT).show()
            }
            is BleCommand.SimInfoSync -> {
                Log.d(TAG, "Received Remote SIMs: ${command.sims}")
                config.remoteSims = command.sims
            }
            else -> {}
        }
    }

    // This can be triggered by UI to send a command
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onOutgoingCommand(event: OutgoingCommandEvent) {
        if (config.relayDeviceAddress.isNotEmpty()) {
            clientManager?.sendCommand(config.relayDeviceAddress, event.command)
        }
    }
}

data class OutgoingCommandEvent(val command: BleCommand)
