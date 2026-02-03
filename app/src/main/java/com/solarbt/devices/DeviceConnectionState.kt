package com.solarbt.devices

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.runtime.Stable
import com.solarbt.RegisterInfo
import com.solarbt.RenogyDeviceType
import kotlinx.coroutines.Job

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

@Stable
data class DeviceConnectionState(
    val address: String,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val bluetoothGatt: BluetoothGatt? = null,
    val data: List<RenogyData> = emptyList(),
    val connectionStatusMessage: String? = null,
    val error: String? = null,

    // BLE Characteristics for communication
    val writeCharacteristic: BluetoothGattCharacteristic? = null,
    val notifyCharacteristic: BluetoothGattCharacteristic? = null,

    // State for device type discovery
    val deviceInfoRegisterIndex: Int = 0, // Which RenogyDeviceType to try next
    val knownDeviceType: RenogyDeviceType? = null, // Once device type is identified
    val isBeingSaved: Boolean = false, // True if this connection is part of the "Add Device" flow

    // Internal state for processing
    val responseBuffer: MutableList<Byte> = mutableListOf(),
    val readTimeoutJob: Job? = null,
    val interPacketTimeoutJob: Job? = null,
    val registersToRead: Iterator<RegisterInfo>? = null,
    val currentReadRegister: RegisterInfo? = null,
    val aggregatedData: MutableList<RenogyData> = mutableListOf(),

    // State for writing settings
    val isWriting: Boolean = false,
    val writeError: String? = null
)