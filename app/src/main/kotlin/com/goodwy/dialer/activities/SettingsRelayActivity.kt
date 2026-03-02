package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.beVisibleIf
import com.goodwy.commons.models.RadioItem
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.ActivitySettingsRelayBinding
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.BleConstants
import com.goodwy.dialer.services.OutgoingCommandEvent
import com.goodwy.dialer.models.BleCommand
import com.goodwy.dialer.services.RelayService
import org.greenrobot.eventbus.EventBus

@SuppressLint("MissingPermission")
class SettingsRelayActivity : SimpleActivity() {
    private lateinit var binding: ActivitySettingsRelayBinding
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsRelayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.relaySettingsToolbar)
        updateUI()
        setupListeners()
    }

    private fun updateUI() {
        binding.apply {
            relayEnabledSwitch.isChecked = config.isRelayEnabled
            
            relayDeviceValue.text = config.relayDeviceName.ifEmpty { getString(R.string.none) }
        }
    }

    private fun setupListeners() {
        binding.apply {
            relayEnabledHolder.setOnClickListener {
                relayEnabledSwitch.toggle()
                config.isRelayEnabled = relayEnabledSwitch.isChecked
                toggleRelayService()
            }

            relayDeviceHolder.setOnClickListener {
                val bondedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                if (bondedDevices.isEmpty()) {
                    Toast.makeText(this@SettingsRelayActivity, R.string.no_bonded_devices, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val items = ArrayList(bondedDevices.mapIndexed { index, device ->
                    RadioItem(index, "${device.name} (${device.address})")
                })

                val currentIdx = bondedDevices.indexOfFirst { it.address == config.relayDeviceAddress }
                RadioGroupDialog(this@SettingsRelayActivity, items, currentIdx) {
                    val selectedDevice = bondedDevices[it as Int]
                    config.relayDeviceAddress = selectedDevice.address
                    config.relayDeviceName = selectedDevice.name ?: selectedDevice.address
                    updateUI()
                }
            }

            relayTestPingBtn.setOnClickListener {
                if (config.relayDeviceAddress.isNotEmpty()) {
                    val ping = BleCommand.Ping()
                    EventBus.getDefault().post(OutgoingCommandEvent(ping))
                    Toast.makeText(this@SettingsRelayActivity, R.string.ping_sent, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleRelayService() {
        val intent = Intent(this, RelayService::class.java)
        if (config.isRelayEnabled) {
            startService(intent)
        } else {
            stopService(intent)
        }
    }
}
