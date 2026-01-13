package com.example.solar_bt.devices

import com.example.solar_bt.RegisterInfo

data class RenogyData(val key: String, val value: Any, val unit: String? = null) {
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
     * Parses the raw response from a Modbus read command.
     * @param register The RegisterInfo that was used to make the read request. This allows the
     * function to know which parsing logic to apply.
     * @param data The raw data payload from the Modbus response (after header and CRC have been handled).
     * @return A list of RenogyData objects representing the parsed key-value pairs, or null if parsing fails.
     */
    fun parseData(register: RegisterInfo, data: ByteArray): List<RenogyData>?
}
