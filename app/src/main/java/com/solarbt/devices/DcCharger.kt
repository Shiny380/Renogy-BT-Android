package com.solarbt.devices

import android.util.Log
import com.solarbt.RegisterInfo

object DcCharger : RenogyDevice {

    private val batteryChargingStateMap = mapOf(
        0 to "deactivated", 1 to "activated", 2 to "mppt", 3 to "equalizing",
        4 to "boost", 5 to "floating", 6 to "current limiting", 8 to "alternator direct"
    )
    private val batteryTypeMap = mapOf(
        1 to "open", 2 to "sealed", 3 to "gel", 4 to "lithium", 5 to "custom"
    )

    override val deviceInfoRegister = RegisterInfo(12, 8, "DC Charger Device Info")
    private val chargingInfoRegister = RegisterInfo(256, 30, "DC Charger Charging Info")
    private val stateRegisters = RegisterInfo(288, 3, "DC Charger Alarms & Status")
    private val settingsRegister = RegisterInfo(0xE001, 33, "DC Charger Settings")

    override val dataRegisters = listOf(
        chargingInfoRegister,
        stateRegisters,
        settingsRegister
    )

    override fun getInitialData(): List<RenogyData> {
        return listOf(
            RenogyData("Model", "N/A", sourceRegister = deviceInfoRegister),
            RenogyData("Battery Charging State", "N/A", sourceRegister = stateRegisters),
            RenogyData("Alarms", "None", sourceRegister = stateRegisters),
            RenogyData("Battery SOC", 0, "%", sourceRegister = chargingInfoRegister),
            RenogyData("Battery Voltage", 0.0f, "V", sourceRegister = chargingInfoRegister),
            RenogyData("Charging Current", 0.0f, "A", sourceRegister = chargingInfoRegister),
            RenogyData("Controller Temp", 0, "°C", sourceRegister = chargingInfoRegister),
            RenogyData("Battery Temp", 0, "°C", sourceRegister = chargingInfoRegister),
            RenogyData("Alternator Voltage", 0.0f, "V", sourceRegister = chargingInfoRegister),
            RenogyData("Alternator Current", 0.0f, "A", sourceRegister = chargingInfoRegister),
            RenogyData("Alternator Power", 0, "W", sourceRegister = chargingInfoRegister),
            RenogyData("PV Voltage", 0.0f, "V", sourceRegister = chargingInfoRegister),
            RenogyData("PV Current", 0.0f, "A", sourceRegister = chargingInfoRegister),
            RenogyData("PV Power", 0, "W", sourceRegister = chargingInfoRegister),
            RenogyData("Battery Min V Today", 0.0f, "V", sourceRegister = chargingInfoRegister),
            RenogyData("Battery Max V Today", 0.0f, "V", sourceRegister = chargingInfoRegister),
            RenogyData("Max Charge A Today", 0.0f, "A", sourceRegister = chargingInfoRegister),
            RenogyData("Max Charge W Today", 0, "W", sourceRegister = chargingInfoRegister),
            RenogyData("Charging Ah Today", 0, "Ah", sourceRegister = chargingInfoRegister),
            RenogyData("Power Gen Today", 0.0f, "kWh", sourceRegister = chargingInfoRegister),
            RenogyData("Total Working Days", 0, "days", sourceRegister = chargingInfoRegister),
            RenogyData("Over-discharge Count", 0, sourceRegister = chargingInfoRegister),
            RenogyData("Full Charge Count", 0, sourceRegister = chargingInfoRegister),
            RenogyData("Total Ah Accumulated", 0L, "Ah", sourceRegister = chargingInfoRegister),
            RenogyData("Total Power Gen", 0.0f, "kWh", sourceRegister = chargingInfoRegister),
            RenogyData(
                key = "Charging Current Setting",
                value = 0.0f,
                unit = "A",
                isWritable = true,
                validationRule = AllowedValuesRule(
                    mapOf(
                        "10.0 A" to 10.0f,
                        "20.0 A" to 20.0f,
                        "30.0 A" to 30.0f,
                        "40.0 A" to 40.0f,
                        "50.0 A" to 50.0f,
                    )
                ),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Battery Capacity",
                value = 0,
                unit = "Ah",
                isWritable = true,
                validationRule = MinMaxRule(10f, 9999f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "System Voltage Setting",
                value = 0,
                unit = "V",
                isWritable = true,
                sourceRegister = settingsRegister,
                validationRule = AllowedValuesRule(
                    mapOf(
                        "12 V" to 12,
                        "24 V" to 24,
                        "36 V" to 36,
                        "48 V" to 48,
                    )
                ),
            ),
            RenogyData(
                key = "Recognized Battery Voltage",
                value = 0,
                unit = "V",
                isWritable = true,
                validationRule = AllowedValuesRule(
                    mapOf(
                        "12 V" to 12,
                        "24 V" to 24,
                        "36 V" to 36,
                        "48 V" to 48,
                    )
                ),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Battery Type",
                value = "N/A",
                isWritable = true,
                validationRule = AllowedValuesRule(batteryTypeMap.entries.associate { (k, v) -> v to k }),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Over-voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Charging Limit Voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Equalization Voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Boost Voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Float Voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Boost Return Voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Over-discharge Return",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Under-voltage Warning",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Over-discharge Voltage",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Discharging Limit",
                value = 0.0f,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(8.0f, 60.0f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Over-discharge Delay",
                value = 0,
                unit = "s",
                isWritable = true,
                validationRule = MinMaxRule(0f, 600f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Equalization Time",
                value = 0,
                unit = "min",
                isWritable = true,
                validationRule = MinMaxRule(0f, 600f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Boost Time",
                value = 0,
                unit = "min",
                isWritable = true,
                validationRule = MinMaxRule(0f, 600f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Equalization Interval",
                value = 0,
                unit = "days",
                isWritable = true,
                validationRule = MinMaxRule(0f, 365f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Temp Comp Coeff",
                value = 0,
                unit = "mV/℃/2V",
                isWritable = true,
                validationRule = MinMaxRule(0f, 100f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Light Control Delay",
                value = 0,
                unit = "min",
                isWritable = true,
                validationRule = MinMaxRule(0f, 600f),
                sourceRegister = settingsRegister
            ),
            RenogyData(
                key = "Light Control Voltage",
                value = 0,
                unit = "V",
                isWritable = true,
                validationRule = MinMaxRule(0f, 60.0f),
                sourceRegister = settingsRegister
            )
        )
    }

    override fun parseData(
        register: RegisterInfo,
        data: ByteArray,
        currentData: List<RenogyData>
    ): Boolean {
        val success = when (register.address) {
            deviceInfoRegister.address -> parseDeviceInfo(data, currentData)
            chargingInfoRegister.address -> parseChargingInfo(data, currentData)
            stateRegisters.address -> parseState(data, currentData)
            settingsRegister.address -> parseSettings(data, currentData)
            else -> false
        }
        if (success) {
            Log.d(
                "DcCharger",
                "parseData for address ${register.address} successful"
            )
        }
        return success
    }

    private fun parseDeviceInfo(data: ByteArray, currentData: List<RenogyData>): Boolean {
        currentData.find { it.key == "Model" }?.value = data.decodeToString(0, 16)
        return true
    }

    private fun parseState(data: ByteArray, currentData: List<RenogyData>): Boolean {
        val chargingStatusCode = data[1].toInt() and 0xFF
        currentData.find { it.key == "Battery Charging State" }?.value =
            batteryChargingStateMap[chargingStatusCode] ?: "Unknown ($chargingStatusCode)"

        val activeAlarms = mutableListOf<String>()
        val faults1 = data.toUInt16(2)
        if ((faults1 shr 11) and 1 == 1) activeAlarms.add("Low Temp Shutdown")
        if ((faults1 shr 10) and 1 == 1) activeAlarms.add("BMS Overcharge Protection")
        if ((faults1 shr 9) and 1 == 1) activeAlarms.add("Starter Reverse Polarity")
        if ((faults1 shr 8) and 1 == 1) activeAlarms.add("Alternator Over Voltage")
        if ((faults1 shr 5) and 1 == 1) activeAlarms.add("Alternator Over Current")
        if ((faults1 shr 4) and 1 == 1) activeAlarms.add("Controller Over Temp 2")

        val faults2 = data.toUInt16(4)
        if ((faults2 shr 12) and 1 == 1) activeAlarms.add("Solar Reverse Polarity")
        if ((faults2 shr 9) and 1 == 1) activeAlarms.add("Solar Over Voltage")
        if ((faults2 shr 7) and 1 == 1) activeAlarms.add("Solar Over Current")
        if ((faults2 shr 6) and 1 == 1) activeAlarms.add("Battery Over Temperature")
        if ((faults2 shr 5) and 1 == 1) activeAlarms.add("Controller Over Temp")
        if ((faults2 shr 2) and 1 == 1) activeAlarms.add("Battery Low Voltage")
        if ((faults2 shr 1) and 1 == 1) activeAlarms.add("Battery Over Voltage")
        if (faults2 and 1 == 1) activeAlarms.add("Battery Over Discharge")

        currentData.find { it.key == "Alarms" }?.value =
            if (activeAlarms.isEmpty()) "None" else activeAlarms.joinToString(", ")

        return true
    }

    private fun parseChargingInfo(data: ByteArray, currentData: List<RenogyData>): Boolean {
        currentData.find { it.key == "Battery SOC" }?.value = data.toUInt16(0)
        currentData.find { it.key == "Battery Voltage" }?.value = data.toUInt16(2) * 0.1f
        currentData.find { it.key == "Charging Current" }?.value = data.toUInt16(4) * 0.01f
        currentData.find { it.key == "Controller Temp" }?.value = parseTemperature(data[6])
        currentData.find { it.key == "Battery Temp" }?.value = parseTemperature(data[7])
        currentData.find { it.key == "Alternator Voltage" }?.value = data.toUInt16(8) * 0.1f
        currentData.find { it.key == "Alternator Current" }?.value = data.toUInt16(10) * 0.01f
        currentData.find { it.key == "Alternator Power" }?.value = data.toUInt16(12)
        currentData.find { it.key == "PV Voltage" }?.value = data.toUInt16(14) * 0.1f
        currentData.find { it.key == "PV Current" }?.value = data.toUInt16(16) * 0.01f
        currentData.find { it.key == "PV Power" }?.value = data.toUInt16(18)
        currentData.find { it.key == "Battery Min V Today" }?.value = data.toUInt16(22) * 0.1f
        currentData.find { it.key == "Battery Max V Today" }?.value = data.toUInt16(24) * 0.1f
        currentData.find { it.key == "Max Charge A Today" }?.value = data.toUInt16(26) * 0.01f
        currentData.find { it.key == "Max Charge W Today" }?.value = data.toUInt16(30)
        currentData.find { it.key == "Charging Ah Today" }?.value = data.toUInt16(34)
        currentData.find { it.key == "Power Gen Today" }?.value = data.toUInt16(38) / 1000.0f
        currentData.find { it.key == "Total Working Days" }?.value = data.toUInt16(42)
        currentData.find { it.key == "Over-discharge Count" }?.value = data.toUInt16(44)
        currentData.find { it.key == "Full Charge Count" }?.value = data.toUInt16(46)
        currentData.find { it.key == "Total Ah Accumulated" }?.value = data.toUInt32(48)
        currentData.find { it.key == "Total Power Gen" }?.value = data.toUInt32(56) / 1000.0f
        return true
    }

    private fun parseSettings(data: ByteArray, currentData: List<RenogyData>): Boolean {
        val typeCode = data.toUInt16(6)
        val batteryType = batteryTypeMap[typeCode]

        currentData.find { it.key == "Charging Current Setting" }?.value = data.toUInt16(0) * 0.01f
        currentData.find { it.key == "Battery Capacity" }?.value = data.toUInt16(2)
        currentData.find { it.key == "System Voltage Setting" }?.value = data[4].toUByte().toInt()
        currentData.find { it.key == "Recognized Battery Voltage" }?.value =
            data[5].toUByte().toInt()
        currentData.find { it.key == "Battery Type" }?.value = batteryType ?: "Unknown ($typeCode)"
        currentData.find { it.key == "Over-voltage" }?.value = data.toUInt16(8) * 0.1f
        currentData.find { it.key == "Charging Limit Voltage" }?.value = data.toUInt16(10) * 0.1f
        currentData.find { it.key == "Equalization Voltage" }?.value = data.toUInt16(12) * 0.1f
        currentData.find { it.key == "Boost Voltage" }?.value = data.toUInt16(14) * 0.1f
        currentData.find { it.key == "Float Voltage" }?.value = data.toUInt16(16) * 0.1f
        currentData.find { it.key == "Boost Return Voltage" }?.value = data.toUInt16(18) * 0.1f
        currentData.find { it.key == "Over-discharge Return" }?.value = data.toUInt16(20) * 0.1f
        currentData.find { it.key == "Under-voltage Warning" }?.value = data.toUInt16(22) * 0.1f
        currentData.find { it.key == "Over-discharge Voltage" }?.value = data.toUInt16(24) * 0.1f
        currentData.find { it.key == "Discharging Limit" }?.value = data.toUInt16(26) * 0.1f
        currentData.find { it.key == "Over-discharge Delay" }?.value = data.toUInt16(30)
        currentData.find { it.key == "Equalization Time" }?.value = data.toUInt16(32)
        currentData.find { it.key == "Boost Time" }?.value = data.toUInt16(34)
        currentData.find { it.key == "Equalization Interval" }?.value = data.toUInt16(36)
        currentData.find { it.key == "Temp Comp Coeff" }?.value = data.toUInt16(38)
        currentData.find { it.key == "Light Control Delay" }?.value = data.toUInt16(58)
        currentData.find { it.key == "Light Control Voltage" }?.value = data.toUInt16(60)

        // The visibility of some settings depends on the battery type,
        // but we can't remove them from the list. We'll handle this in the UI.
        // For now, we just parse everything.

        return true
    }
}
