package com.example.solar_bt.devices

import com.example.solar_bt.RegisterInfo

sealed class ValidationRule
data class MinMaxRule(val min: Float, val max: Float) : ValidationRule()
data class AllowedValuesRule(val values: Map<String, Number>) : ValidationRule()

data class RenogyData(
    val key: String,
    var value: Any,
    val unit: String? = null,
    val isWritable: Boolean = false,
    val validationRule: ValidationRule? = null,
    var isVisible: Boolean = true,
    val sourceRegister: RegisterInfo? = null
) {
    override fun toString(): String {
        return "$key: $value${unit?.let { " $it" } ?: ""}"
    }
}

interface RenogyDevice {
    /**
     * The specific register to read to identify the device model.
     * This is used during the initial connection to determine the device type.
     */
    val deviceInfoRegister: RegisterInfo

    /**
     * A list of all other data registers/sections to read from this device
     * after it has been identified.
     */
    val dataRegisters: List<RegisterInfo>

    /**
     * Creates and returns the complete list of RenogyData objects that this device supports,
     * initialized with default or "N/A" values.
     * @return A list of all possible RenogyData objects for this device.
     */
    fun getInitialData(): List<RenogyData>

    /**
     * Parses the raw response from a Modbus read command and updates the provided data list.
     * @param register The RegisterInfo that was used to make the read request.
     * @param data The raw data payload from the Modbus response.
     * @param currentData The list of RenogyData to be updated with the new values.
     * @return `true` if parsing was successful, `false` otherwise.
     */
    fun parseData(register: RegisterInfo, data: ByteArray, currentData: List<RenogyData>): Boolean
}
