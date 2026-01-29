package com.example.solar_bt

/*                                                                                                                                                                                                                                                                  │
 * This project serves as an open-source replacement for the official Renogy Bluetooth app.                                                                                                                                                                         │
 * It aims to be more reliable and not require internet or any accounts.
 *                                                                                                                                                                                                                                                                  │
 * Renogy Bluetooth Protocol Overview:                                                                                                                                                                                                                              │
 * The communication with Renogy devices over Bluetooth Low Energy (BLE) follows the                                                                                                                                                                                │
 * Modbus RTU protocol built on top of the GATT profile. It mimics a traditional
 * Modbus serial bus (like RS232 or RS485) over BLE.                                                                                                                                                                                                                │
 * - Bluetooth Characteristics:                                                                                                                                                                                                                                     │
 *     - RX (Receive): TX from app perspective, but RX for the device.                                                                                                                                                                                              │
 *     - TX (Transmit): RX from app perspective, but TX for the device.
 *
 * For contributors:
 * This `MainActivity.kt` file is the entry point of the application, managing Bluetooth
 * scanning, device connection states, data acquisition, and navigation between UI screens.
 * Device-specific parsing logic is located in the `devices` package.
*/

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.example.solar_bt.devices.ConnectionStatus
import com.example.solar_bt.devices.DeviceConnectionState
import com.example.solar_bt.devices.RenogyDeviceFactory
import com.example.solar_bt.ui.theme.SolarbtTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MainDebug"

// Renogy Service and Characteristic UUIDs
private val RX_SERVICE_UUID = UUID.fromString("0000ffd0-0000-1000-8000-00805f9b34fb")
private val RX_CHARACTERISTIC_UUID = UUID.fromString("0000ffd1-0000-1000-8000-00805f9b34fb")
private val TX_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val TX_CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

data class RegisterInfo(val address: Int, val length: Int, val description: String)

enum class RenogyDeviceType {
    DC_CHARGER,
    BATTERY,
}

fun ByteArray.toHexString(): String =
    joinToString(separator = " ") { eachByte -> "½02x".format(eachByte) }

data class SavedBluetoothDevice(
    val device: BluetoothDevice,
    var customName: String? = null,
    var deviceType: RenogyDeviceType? = null
)

@OptIn(ExperimentalStdlibApi::class)
class MainActivity : ComponentActivity() {

    private var isScanning by mutableStateOf(false)

    // New state management for multiple devices
    val deviceStates = mutableStateMapOf<String, DeviceConnectionState>()

    private val _devices = mutableStateListOf<BluetoothDevice>()
    private val _savedDevices = mutableStateListOf<SavedBluetoothDevice>()
    private val _savingDevices = mutableStateListOf<BluetoothDevice>()

    private val sharedPrefs by lazy {
        getSharedPreferences("saved_devices", MODE_PRIVATE)
    }

    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: BluetoothDevice,
        isBeingSaved: Boolean = false,
        deviceType: RenogyDeviceType? = null
    ) {
        val address = device.address
        val existingState = deviceStates[address]

        if (existingState != null) {
            // If the device is already connecting or connected, do nothing.
            if (existingState.connectionStatus == ConnectionStatus.CONNECTING ||
                existingState.connectionStatus == ConnectionStatus.CONNECTED
            ) {
                Log.w(TAG, "Connection attempt already in progress or connected for $address")
                return
            }
            // If the device was disconnected or failed, remove its old state to allow a fresh connection.
            else if (existingState.connectionStatus == ConnectionStatus.DISCONNECTED ||
                existingState.connectionStatus == ConnectionStatus.FAILED
            ) {
                Log.d(TAG, "Initiating re-connection for $address. Clearing old state.")
                // Disconnect any existing GATT connection cleanly before removing the state
                existingState.bluetoothGatt?.close()
                deviceStates.remove(address)
            }
        }

        val state = DeviceConnectionState(
            address = address,
            connectionStatus = ConnectionStatus.CONNECTING,
            isBeingSaved = isBeingSaved,
            knownDeviceType = deviceType // Initialize knownDeviceType here
        )
        deviceStates[address] = state
        device.connectGatt(this, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        val currentState = deviceStates[address] ?: return

        currentState.readTimeoutJob?.cancel()
        currentState.interPacketTimeoutJob?.cancel()
        currentState.bluetoothGatt?.disconnect() // This will trigger onConnectionStateChange to clean up state
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: return

            val currentState = deviceStates[address] ?: return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT error for $address: $status")
                deviceStates[address] = currentState.copy(
                    connectionStatus = ConnectionStatus.FAILED,
                    error = "GATT Error $status",
                    bluetoothGatt = null
                )
                gatt.close()
                return
            }

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to $address.")
                    deviceStates[address] = currentState.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        bluetoothGatt = gatt,
                        connectionStatusMessage = "Discovering services..."
                    )
                    gatt.discoverServices()
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from $address.")
                    deviceStates[address] = currentState.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        bluetoothGatt = null,
                        data = emptyList() // Clear data on disconnect
                    )
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val address = gatt?.device?.address ?: return
            val currentState = deviceStates[address]

            if (currentState == null) {
                Log.e(TAG, "onServicesDiscovered for unknown device: $address")
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for $address.")
                val rxService = gatt.getService(RX_SERVICE_UUID)
                val txService = gatt.getService(TX_SERVICE_UUID)
                val newWriteCharacteristic = rxService?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                val newNotifyCharacteristic = txService?.getCharacteristic(TX_CHARACTERISTIC_UUID)

                if (newWriteCharacteristic != null && newNotifyCharacteristic != null) {
                    deviceStates[address] = currentState.copy(
                        writeCharacteristic = newWriteCharacteristic,
                        notifyCharacteristic = newNotifyCharacteristic
                    )
                    enableNotifications(gatt, newNotifyCharacteristic)
                } else {
                    Log.e(TAG, "Could not find required characteristics for $address.")
                    deviceStates[address] = currentState.copy(
                        connectionStatus = ConnectionStatus.FAILED,
                        error = "Required services not found."
                    )
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received error $status for $address")
                deviceStates[address] = currentState.copy(
                    connectionStatus = ConnectionStatus.FAILED,
                    error = "Service discovery failed."
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val address = gatt.device.address
            val currentState = deviceStates[address] ?: return

            currentState.readTimeoutJob?.cancel()
            currentState.interPacketTimeoutJob?.cancel()

            if (characteristic.uuid == currentState.notifyCharacteristic?.uuid) {
                val buffer = currentState.responseBuffer
                buffer.addAll(value.toTypedArray())

                if (buffer.size < 3) {
                    startInterPacketTimeout(address)
                    return
                }

                val byteCount = buffer[2].toInt() and 0xFF
                val expectedLength = 3 + byteCount + 2 // Header(3) + Data(N) + CRC(2)

                if (buffer.size < expectedLength) {
                    startInterPacketTimeout(address)
                    return
                }

                val message = buffer.subList(0, expectedLength).toByteArray()
                buffer.subList(0, expectedLength).clear()

                val payload = message.copyOfRange(0, message.size - 2)

                val receivedCrc =
                    (message[message.size - 1].toInt() and 0xFF shl 8) or (message[message.size - 2].toInt() and 0xFF)
                val calculatedCrc = calculateCrc16(payload)

                if (receivedCrc != calculatedCrc) {
                    val errorMessage =
                        "CRC check failed. Expected: ${"%04x".format(calculatedCrc)}, Received: ${
                            "%04x".format(receivedCrc)
                        }"
                    deviceStates[address] = currentState.copy(
                        error = errorMessage,
                        connectionStatus = ConnectionStatus.FAILED
                    )
                    Log.e(TAG, errorMessage)
                    // If CRC fails, we should still try the next register, but mark the current as failed.
                    readNextRegister(address)
                    return
                }

                val data = extractModbusReadResponseData(payload)
                if (data == null) {
                    val errorMessage =
                        "Invalid data packet received. Payload: ${payload.toHexString()}"
                    deviceStates[address] = currentState.copy(
                        error = errorMessage,
                        connectionStatus = ConnectionStatus.FAILED
                    )
                    Log.e(TAG, errorMessage)
                    // If data extraction fails, we should still try the next register.
                    readNextRegister(address)
                    return
                }

                val currentReadRegister = currentState.currentReadRegister

                if (currentState.isBeingSaved && currentReadRegister != null) {
                    // This is the response from a device type discovery read.
                    val discoveredDeviceType =
                        RenogyDeviceType.entries[currentState.deviceInfoRegisterIndex]
                    // Now save the device, and then initiate the full data read.
                    saveDevice(gatt.device, discoveredDeviceType)
                    // Update state with known device type and clear isBeingSaved
                    deviceStates[address] = currentState.copy(
                        knownDeviceType = discoveredDeviceType,
                        isBeingSaved = false,
                        connectionStatusMessage = "Device type identified. Loading data..."
                    )
                    // Now, initiate the full data read for this newly discovered and saved device
                    startFullDataRead(address, discoveredDeviceType)
                } else if (currentReadRegister != null) {
                    // This is a regular data read (either device info after initial connect, or data registers)
                    Log.d(
                        TAG,
                        "onCharacteristicChanged: currentReadRegister=${currentReadRegister.description}, knownDeviceType=${currentState.knownDeviceType}"
                    )
                    currentState.knownDeviceType?.let { knownType ->
                        val renogyDevice = RenogyDeviceFactory.getDevice(knownType)
                        renogyDevice.parseData(currentReadRegister, data)?.let { parsedData ->
                            parsedData.forEach { newItem ->
                                val index = currentState.aggregatedData.indexOfFirst { it.key == newItem.key }
                                if (index != -1) {
                                    currentState.aggregatedData[index] = newItem
                                } else {
                                    currentState.aggregatedData.add(newItem)
                                }
                            }
                        }
                    }
                    readNextRegister(address)
                } else {
                    Log.w(
                        TAG,
                        "Received data on $address but no current read register was set or known type."
                    )
                    // Just move to next register, but this indicates a potential issue in flow
                    readNextRegister(address)
                }
            } else {
                Log.w(
                    TAG,
                    "Received data on unexpected characteristic ${characteristic.uuid} for $address"
                )
                readNextRegister(address) // Try next read anyway
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            val address = gatt?.device?.address ?: return
            val currentState = deviceStates[address] ?: return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notifications enabled for $address.")
                deviceStates[address] = currentState.copy(connectionStatusMessage = "Connected")

                // Once notifications are enabled, the device is ready for communication.
                // If we know the device type (i.e., it's a saved device), start reading data.
                // Otherwise, if it's a new device being saved, start by finding its type.
                if (currentState.knownDeviceType != null) {
                    currentState.knownDeviceType.let { deviceType ->
                        // Don't start a new read if one is already in progress or data is already loaded.
                        val readInProgress = currentState.registersToRead?.hasNext() == true ||
                                currentState.connectionStatusMessage?.startsWith("Reading") == true
                        if (currentState.data.isEmpty() && !readInProgress) {
                            startFullDataRead(address, deviceType)
                        }
                    }
                } else if (currentState.isBeingSaved) {
                    findDeviceType(address)
                }
            } else {
                Log.e(TAG, "Descriptor write failed for $address: $status")
                deviceStates[address] = currentState.copy(
                    connectionStatus = ConnectionStatus.FAILED,
                    error = "Failed to enable notifications."
                )
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful for ${characteristic?.uuid}")
            } else {
                Log.e(
                    TAG,
                    "Characteristic write failed with status: $status for ${characteristic?.uuid}"
                )
            }
        }
    }

    private fun readNextRegister(address: String) {

        val state = deviceStates[address] ?: return



        if (state.registersToRead?.hasNext() == true) {

            val registerToRead = state.registersToRead.next()
            Log.d(TAG, "Reading next register: ${registerToRead.description} @${registerToRead.address}")

            state.bluetoothGatt?.let { gatt ->

                readRegister(gatt, registerToRead)

            } ?: run {

                Log.e(TAG, "GATT object is null for $address when trying to read next register.")

                deviceStates[address] = state.copy(

                    error = "GATT not available for read."

                )

            }

        } else {

            val finalData = state.aggregatedData.toList()

            Log.d(
                TAG,
                "readNextRegister: Data set for ${address}: ${finalData.size} items, data=${finalData}"
            )

            deviceStates[address] = state.copy(

                data = finalData, // Update the UI-bound data

                connectionStatusMessage = null, // Clear message when data is fully loaded

                currentReadRegister = null

                // Optionally set connectionStatus to CONNECTED if it was READING

            )

        }

    }


    @SuppressLint("MissingPermission")

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {

        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val descriptor = characteristic.getDescriptor(cccdUuid)

        val address = gatt.device.address



        if (descriptor == null) {

            Log.e(TAG, "Could not get CCCD for ${characteristic.uuid} on device $address")

            deviceStates[address]?.let {

                deviceStates[address] = it.copy(

                    connectionStatus = ConnectionStatus.FAILED,

                    error = "Could not enable notifications."

                )

            }

            return

        }



        gatt.setCharacteristicNotification(characteristic, true)

        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

    }


    @SuppressLint("MissingPermission")
    private fun readRegister(gatt: BluetoothGatt, registerInfo: RegisterInfo) {

        val address = gatt.device.address

        val state = deviceStates[address] ?: return



        state.readTimeoutJob?.cancel() // Cancel any existing timeout for this device


        // Update state to reflect that a read is in progress

        var newState = state.copy(

            connectionStatusMessage = "Reading: ${registerInfo.description}",

            currentReadRegister = registerInfo,

            // Clear any previous error before starting a new read

            error = null

        )

        deviceStates[address] = newState // Update map to trigger UI refresh

        val command = createModbusCommand(
            isRead = true,
            deviceAddress = 0xFF,
            startRegister = registerInfo.address,
            registerCount = registerInfo.length
        )

        state.writeCharacteristic?.let { char ->
            Log.d(
                TAG,
                "Attempting to write characteristic for ${registerInfo.description} on $address"
            )

            if (state.bluetoothGatt?.writeCharacteristic(
                    char,
                    command,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            ) {
                // Set a new timeout
                newState = newState.copy(
                    readTimeoutJob = lifecycleScope.launch {
                        delay(3000)
                        Log.e(TAG, "Read timeout for ${registerInfo.description} on $address")
                        deviceStates[address] = deviceStates[address]?.copy(
                            error = "Read timed out for ${registerInfo.description}",
                            currentReadRegister = null // Clear current read register
                        )!!
                        readNextRegister(address) // Try reading next register even after a timeout
                    }
                )
                deviceStates[address] = newState // Update state with the new Job
            } else {
                Log.e(
                    TAG,
                    "Failed to write characteristic for ${registerInfo.description} on $address"
                )
                deviceStates[address] = state.copy(
                    error = "Failed to write characteristic for ${registerInfo.description}",
                    currentReadRegister = null
                )
                readNextRegister(address) // Attempt next read
            }
        } ?: run {
            Log.e(TAG, "Write characteristic is null for $address")

            deviceStates[address] = state.copy(
                error = "Write characteristic is null",
                currentReadRegister = null
            )

            readNextRegister(address) // Attempt next read
        }
    }

    @SuppressLint("MissingPermission")
    private fun findDeviceType(address: String) {
        val state = deviceStates[address] ?: return

        deviceStates[address] = state.copy(connectionStatusMessage = "Determining device type...")

        val devicesToTry = RenogyDeviceType.entries

        if (state.deviceInfoRegisterIndex >= devicesToTry.size) {
            deviceStates[address] = state.copy(
                error = "Failed to get device info from known registers.",
                connectionStatus = ConnectionStatus.FAILED,
                connectionStatusMessage = null
            )
            Log.e(TAG, "All device info registers failed for $address.")
            return
        }

        val deviceType = devicesToTry[state.deviceInfoRegisterIndex]
        val renogyDevice = RenogyDeviceFactory.getDevice(deviceType)
        val registerInfo = renogyDevice.deviceInfoRegister

        state.bluetoothGatt?.let { gatt ->
            readRegister(gatt, registerInfo)

            // The readTimeoutJob is now part of the device's state
            deviceStates[address] = deviceStates[address]?.copy(
                readTimeoutJob = lifecycleScope.launch {
                    delay(3000)
                    Log.d(TAG, "Read timeout for ${deviceType.name} on $address, trying next...")
                    deviceStates[address]?.let { currentState ->
                        deviceStates[address] = currentState.copy(
                            responseBuffer = mutableListOf(), // Clear buffer on timeout
                            deviceInfoRegisterIndex = currentState.deviceInfoRegisterIndex + 1,
                            currentReadRegister = null // Clear current read register
                        )
                    }
                    findDeviceType(address) // Recursive call for the same device
                }
            )!!
        } ?: run {
            Log.e(TAG, "GATT object is null for $address when finding device type.")
            deviceStates[address] = state.copy(
                error = "GATT not available for type discovery.",
                connectionStatus = ConnectionStatus.FAILED
            )
        }
    }

    private fun startInterPacketTimeout(address: String) {
        val currentState = deviceStates[address] ?: return
        currentState.interPacketTimeoutJob?.cancel()

        deviceStates[address] = currentState.copy(
            interPacketTimeoutJob = lifecycleScope.launch {
                delay(500) // 500ms between packets
                val state = deviceStates[address]
                if (state != null && state.responseBuffer.isNotEmpty()) {
                    Log.e(
                        TAG,
                        "Packet assembly timeout for $address. Buffer content: ${
                            state.responseBuffer.toByteArray().toHexString()
                        }"
                    )
                    deviceStates[address] = state.copy(
                        error = "Response timed out: Incomplete packet.",
                        connectionStatus = ConnectionStatus.FAILED,
                        responseBuffer = mutableListOf()
                    )
                }
            }
        )
    }


    private fun initialConnectAll() {
        _savedDevices.forEach { savedDevice ->
            connectToDevice(savedDevice.device, deviceType = savedDevice.deviceType)
        }
    }

    private fun startFullDataRead(address: String, deviceType: RenogyDeviceType, isRefresh: Boolean = false) {
        val state = deviceStates[address] ?: return
        val renogyDevice = RenogyDeviceFactory.getDevice(deviceType)

        val initialAggregatedData = if (isRefresh && state.data.isNotEmpty()) state.data.toMutableList() else mutableListOf()

        deviceStates[address] = state.copy(
            aggregatedData = initialAggregatedData, // Clear previous aggregated data
            registersToRead = (listOf(renogyDevice.deviceInfoRegister) + renogyDevice.dataRegisters).iterator(),
            connectionStatusMessage = if (isRefresh) "Refreshing..." else "Fetching device data..."
        )
        // Start the sequential read process
        readNextRegister(address)
    }

    private fun reconnectDevice(savedDevice: SavedBluetoothDevice) {
        connectToDevice(savedDevice.device, deviceType = savedDevice.deviceType)
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                val name = device.name ?: result.scanRecord?.deviceName
                if (name?.startsWith("BT-", ignoreCase = true) == true ||
                    name?.contains("Renogy", ignoreCase = true) == true
                ) {
                    if (!_devices.any { it.address == device.address }) {
                        _devices.add(device)
                    }
                    Log.d(TAG, "Found BLE device: ${device.name} ${device.address}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        loadSavedDevices()
        requestBluetoothPermissions()
        initialConnectAll()

        setContent {
            SolarbtTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "saved_devices") {
                    composable("saved_devices") {
                        SavedDevicesScreen(
                            savedDevices = _savedDevices,
                            onAddNewDeviceClick = { navController.navigate("add_device") },
                            onRenameDevice = { device ->
                                navController.navigate("rename_device/${device.device.address}")
                            },
                            onRemoveDevice = { device -> removeDevice(device) },
                            onDeviceClick = { device ->
                                navController.navigate("device_info/${device.device.address}")
                            },
                            onReconnectDevice = { savedDevice -> reconnectDevice(savedDevice) },
                            deviceStates = deviceStates, // Pass the deviceStates map
                            startFullDataRead = { addr, type -> startFullDataRead(addr, type, true) }
                        )
                    }
                    composable("device_info/{deviceAddress}") { backStackEntry ->
                        val deviceAddress = backStackEntry.arguments?.getString("deviceAddress")
                        val savedDevice = _savedDevices.find { it.device.address == deviceAddress }
                        DeviceInfoScreen(
                            navController = navController,
                            savedDevice = savedDevice,
                            deviceStates = deviceStates,
                            startFullDataRead = { addr, type -> startFullDataRead(addr, type, true) }
                        )
                    }
                    dialog("add_device") {
                        AddDeviceDialog(
                            isScanning = isScanning,
                            scannedDevices = _devices,
                            savedDevices = _savedDevices,
                            savingDevices = _savingDevices,
                            onScanClick = {
                                if (isScanning) stopBleScan() else startBleScan()
                            },
                            onSaveDevice = { device -> determineDeviceTypeAndSave(device) },
                            onDismiss = {
                                if (isScanning) stopBleScan()
                                navController.popBackStack()
                            }
                        )
                    }
                    dialog("rename_device/{deviceAddress}") { backStackEntry ->
                        val deviceAddress = backStackEntry.arguments?.getString("deviceAddress")
                        val deviceToRename =
                            _savedDevices.find { it.device.address == deviceAddress }
                        if (deviceToRename != null) {
                            RenameDeviceDialog(
                                device = deviceToRename,
                                onDismiss = { navController.popBackStack() },
                                onRename = { newName ->
                                    renameDevice(deviceToRename, newName)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bleScanner == null) {
            Log.e(TAG, "Bluetooth not supported on this device or is turned off.")
            return
        }

        val requiredPermissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            _devices.clear()
            bleScanner?.startScan(scanCallback)
            isScanning = true
            Log.d(TAG, "BLE scan started.")
        } else {
            Log.e(
                TAG,
                "Cannot start scan, missing permissions: ${missingPermissions.joinToString()}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "BLE scan stopped.")
    }

    @SuppressLint("MissingPermission")
    private fun determineDeviceTypeAndSave(device: BluetoothDevice) {
        if (_savingDevices.any { it.address == device.address }) return
        _savingDevices.add(device)
        connectToDevice(device, true) // Pass a flag indicating it's for saving
    }

    @SuppressLint("MissingPermission")
    private fun saveDevice(device: BluetoothDevice, deviceType: RenogyDeviceType) {
        if (!_savedDevices.any { it.device.address == device.address }) {
            val newDevice = SavedBluetoothDevice(device, deviceType = deviceType)
            _savedDevices.add(newDevice)
            Log.d(TAG, "Saved device: ${device.name} ${device.address} as ${deviceType.name}")

            sharedPrefs.edit {
                val currentAddresses =
                    sharedPrefs.getStringSet("device_addresses", emptySet())?.toMutableSet()
                        ?: mutableSetOf()
                currentAddresses.add(device.address)
                putStringSet("device_addresses", currentAddresses)
                putString("type_${device.address}", deviceType.name)
            }
        }
        _savingDevices.removeIf { it.address == device.address }
    }

    @SuppressLint("MissingPermission")
    private fun renameDevice(deviceToRename: SavedBluetoothDevice, newName: String) {
        val index = _savedDevices.indexOf(deviceToRename)
        if (index != -1) {
            sharedPrefs.edit {
                if (newName.isNotBlank()) {
                    _savedDevices[index] = deviceToRename.copy(customName = newName)
                    putString("name_${deviceToRename.device.address}", newName)
                    Log.d(TAG, "Renamed device ${deviceToRename.device.address} to $newName")
                } else {
                    _savedDevices[index] = deviceToRename.copy(customName = null)
                    remove("name_${deviceToRename.device.address}")
                    Log.d(TAG, "Removed custom name for device ${deviceToRename.device.address}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeDevice(deviceToRemove: SavedBluetoothDevice) {
        _savedDevices.remove(deviceToRemove)
        sharedPrefs.edit {
            val currentAddresses =
                sharedPrefs.getStringSet("device_addresses", emptySet())?.toMutableSet()
                    ?: mutableSetOf()
            currentAddresses.remove(deviceToRemove.device.address)
            putStringSet("device_addresses", currentAddresses)
            remove("name_${deviceToRemove.device.address}")
            remove("type_${deviceToRemove.device.address}")
        }
        Log.d(TAG, "Removed device ${deviceToRemove.device.address}")
    }

    @SuppressLint("MissingPermission")
    private fun loadSavedDevices() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is not available, can't load devices.")
            return
        }
        val savedAddresses = sharedPrefs.getStringSet("device_addresses", emptySet())
        savedAddresses?.forEach { address ->
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                val device = bluetoothAdapter!!.getRemoteDevice(address)
                if (!_savedDevices.any { it.device.address == device.address }) {
                    val customName = sharedPrefs.getString("name_$address", null)
                    val deviceTypeName = sharedPrefs.getString("type_$address", null)
                    val deviceType = deviceTypeName?.let {
                        try {
                            RenogyDeviceType.valueOf(it)
                        } catch (_: IllegalArgumentException) {
                            null // In case the enum name is removed or changed
                        }
                    }
                    _savedDevices.add(SavedBluetoothDevice(device, customName, deviceType))
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsNotGranted.toTypedArray())
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    navController: NavController,
    savedDevice: SavedBluetoothDevice?,
    deviceStates: Map<String, DeviceConnectionState>,
    startFullDataRead: (String, RenogyDeviceType) -> Unit
) {
    val deviceAddress = savedDevice?.device?.address
    val deviceType = savedDevice?.deviceType
    val deviceState = deviceAddress?.let { deviceStates[it] }

    LaunchedEffect(deviceAddress) {
        if (deviceAddress != null && deviceType != null) {
            while (true) {
                val currentState = deviceStates[deviceAddress] // get the latest state
                val readInProgress = currentState?.currentReadRegister != null ||
                        currentState?.connectionStatusMessage?.startsWith("Reading") == true

                if (currentState?.connectionStatus == ConnectionStatus.CONNECTED && !readInProgress) {
                    Log.d(TAG, "Periodic refresh for $deviceAddress on detail view")
                    startFullDataRead(deviceAddress, deviceType)
                }
                delay(2000)
            }
        }
    }

    // Handle nullability and invalid states early
    if (deviceAddress == null || deviceType == null || deviceState == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            savedDevice?.customName ?: savedDevice?.device?.name
                            ?: "Device Info Error"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(text = "Error: Device information incomplete or not found.")
            }
        }
        return // Exit early if essential info is missing
    }

    // From here onwards, deviceAddress, deviceType, and deviceState are guaranteed to be non-nullable
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        savedDevice.customName ?: savedDevice.device.name ?: "Device Info"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Log.d(
                TAG,
                "DeviceInfoScreen: deviceAddress=${deviceAddress}, data.size=${deviceState.data.size}, data=${deviceState.data}"
            )
            if (deviceState.data.isNotEmpty()) {
                when (deviceType) {
                    RenogyDeviceType.DC_CHARGER -> {
                        DcChargerFullView(deviceState = deviceState)
                    }

                    RenogyDeviceType.BATTERY -> {
                        SmartBatteryFullView(deviceState = deviceState)
                    }

                    else -> {
                        // Fallback to generic display if deviceType is unknown or null
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(deviceState.data) { data ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = data.key,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = data.value.toString(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

            } else if (deviceState.connectionStatus == ConnectionStatus.CONNECTING || (deviceState.connectionStatus == ConnectionStatus.CONNECTED && deviceState.data.isEmpty())) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    deviceState.connectionStatusMessage?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it)
                    }
                }
            } else if (deviceState.error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = deviceState.error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = {
                        startFullDataRead(deviceAddress, deviceType)
                    }) {
                        Text("Retry Reading Data")
                    }
                }
            } else {
                Text(text = "No data received from device.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameDeviceDialog(
    device: SavedBluetoothDevice,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(device.customName ?: "") }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Rename device", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Custom name") }
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onRename(name) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun SavedDevicesScreen(
    savedDevices: List<SavedBluetoothDevice>,
    onAddNewDeviceClick: () -> Unit,
    onRenameDevice: (SavedBluetoothDevice) -> Unit,
    onRemoveDevice: (SavedBluetoothDevice) -> Unit,
    onDeviceClick: (SavedBluetoothDevice) -> Unit,
    onReconnectDevice: (SavedBluetoothDevice) -> Unit,
    deviceStates: Map<String, DeviceConnectionState>,
    startFullDataRead: (String, RenogyDeviceType) -> Unit,
) {
    LaunchedEffect(Unit) {
        while(true) {
            val refreshableDevices = deviceStates.values.filter {
                it.connectionStatus == ConnectionStatus.CONNECTED
            }
            if (refreshableDevices.isNotEmpty()) {
                Log.d(TAG, "Periodic refresh for all devices on overview screen")
                refreshableDevices.forEach { deviceToRefresh ->
                    val readInProgress = deviceToRefresh.currentReadRegister != null ||
                            deviceToRefresh.connectionStatusMessage?.startsWith("Reading") == true
                    if (!readInProgress) {
                        deviceToRefresh.knownDeviceType?.let { deviceType ->
                            startFullDataRead(deviceToRefresh.address, deviceType)
                        }
                    }
                }
            }
            delay(5000)
        }
    }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNewDeviceClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add new device")
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(savedDevices) { savedDevice ->
                SavedDeviceItem(
                    savedDevice = savedDevice,
                    deviceState = deviceStates[savedDevice.device.address],
                    onRenameDevice = onRenameDevice,
                    onRemoveDevice = onRemoveDevice,
                    onDeviceClick = onDeviceClick,
                    onReconnectDevice = onReconnectDevice
                )
            }
        }
        }
    }
    
    @Composable
    fun DeviceSummaryView(savedDevice: SavedBluetoothDevice, deviceState: DeviceConnectionState) {
        when (savedDevice.deviceType) {
            RenogyDeviceType.DC_CHARGER -> DcChargerOverview(
                deviceState = deviceState
            )
            RenogyDeviceType.BATTERY -> SmartBatteryOverview(
                deviceState = deviceState
            )
            null -> {
                // Do nothing, as per requirement to not show anything else.
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    @Composable
    private fun SavedDeviceItem(    savedDevice: SavedBluetoothDevice,
    deviceState: DeviceConnectionState?,
    onRenameDevice: (SavedBluetoothDevice) -> Unit,
    onRemoveDevice: (SavedBluetoothDevice) -> Unit,
    onDeviceClick: (SavedBluetoothDevice) -> Unit,
    onReconnectDevice: (SavedBluetoothDevice) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(160.dp) // Enforce uniform height
            .clickable { onDeviceClick(savedDevice) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top part: Name, Status, and Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = savedDevice.customName ?: savedDevice.device.name ?: "Unnamed Device",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val statusText = deviceState?.connectionStatusMessage ?: deviceState?.connectionStatus?.name ?: "Unknown"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Status: $statusText",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (deviceState?.connectionStatusMessage != null) {
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp)
                        }
                    }
                    savedDevice.deviceType?.let {
                        Text(
                            text = "Type: ${it.name.replace("_", " ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (deviceState?.connectionStatus == ConnectionStatus.DISCONNECTED ||
                    deviceState?.connectionStatus == ConnectionStatus.FAILED
                ) {
                    IconButton(onClick = { onReconnectDevice(savedDevice) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect device")
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                onRenameDevice(savedDevice)
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                onRemoveDevice(savedDevice)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
            HorizontalDivider()

            // Bottom part: Data Overview
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (deviceState?.data?.isNotEmpty() == true) {
                    DeviceSummaryView(savedDevice = savedDevice, deviceState = deviceState)
                } else {
                    val message = deviceState?.error ?: when (deviceState?.connectionStatus) {
                        ConnectionStatus.CONNECTING -> "Connecting..."
                        ConnectionStatus.CONNECTED -> "Loading initial data..."
                        ConnectionStatus.DISCONNECTED -> "Device is disconnected"
                        ConnectionStatus.FAILED -> "Connection failed"
                        else -> "Status unavailable"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (deviceState?.connectionStatus == ConnectionStatus.CONNECTING ||
                            (deviceState?.connectionStatus == ConnectionStatus.CONNECTED && deviceState.data.isEmpty() && deviceState.error == null)) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    isScanning: Boolean,
    scannedDevices: List<BluetoothDevice>,
    savedDevices: List<SavedBluetoothDevice>,
    savingDevices: List<BluetoothDevice>,
    onScanClick: () -> Unit,
    onSaveDevice: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = onScanClick) {
                    Text(text = if (isScanning) "Stop Scan" else "Scan for BLE Devices")
                }
                Spacer(modifier = Modifier.height(16.dp))
                DeviceList(
                    devices = scannedDevices,
                    savedDevices = savedDevices,
                    savingDevices = savingDevices,
                    onSaveDevice = onSaveDevice,
                    isScanning = isScanning
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceList(
    devices: List<BluetoothDevice>,
    savedDevices: List<SavedBluetoothDevice>,
    savingDevices: List<BluetoothDevice>,
    onSaveDevice: (BluetoothDevice) -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(devices) { device ->
            val isSaved = savedDevices.any { it.device.address == device.address }
            val isSaving = savingDevices.any { it.address == device.address }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.name ?: "Unnamed Device")
                    Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = { onSaveDevice(device) },
                    enabled = !isSaving && !isSaved
                ) {
                    when {
                        isSaving -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }

                        isSaved -> {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Device saved"
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Save device"
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
        if (isScanning) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}