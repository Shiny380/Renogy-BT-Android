package com.example.solar_bt.devices

import android.util.Log
import com.example.solar_bt.RegisterInfo

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

    override fun parseData(register: RegisterInfo, data: ByteArray): List<RenogyData>? {
        val parsedData = when (register.address) {
            deviceInfoRegister.address -> parseDeviceInfo(data)
            chargingInfoRegister.address -> parseChargingInfo(data)
            stateRegisters.address -> parseState(data)
            settingsRegister.address -> parseSettings(data)
            else -> null
        }
        Log.d(
            "DcCharger",
            "parseData for address ${register.address}: ${parsedData?.size ?: 0} items"
        )
        return parsedData
    }

    private fun parseDeviceInfo(data: ByteArray): List<RenogyData> {
        return listOf(
            RenogyData("Model", data.decodeToString(0, 16))
        )
    }

    private fun parseState(data: ByteArray): List<RenogyData> {
        val results = mutableListOf<RenogyData>()
        // Per spec, charging state is the low byte of register 0x0120.
        // Data is [high0120, low0120, high0121, low0121, ...], so we want data[1].
        val chargingStatusCode = data[1].toInt() and 0xFF
        results.add(
            RenogyData(
                "Battery Charging State",
                batteryChargingStateMap[chargingStatusCode] ?: "Unknown ($chargingStatusCode)"
            )
        )

        // Alarms
        // Register 0x0121 is at offset 2 (bytes 2, 3)
        val faults1 = data.toUInt16(2)
        if ((faults1 shr 11) and 1 == 1) results.add(RenogyData("Alarm", "Low Temp Shutdown")) // b11
        if ((faults1 shr 10) and 1 == 1) results.add(RenogyData("Alarm", "BMS Overcharge Protection")) // b10
        if ((faults1 shr 9) and 1 == 1) results.add(RenogyData("Alarm", "Starter Reverse Polarity")) // b9
        if ((faults1 shr 8) and 1 == 1) results.add(RenogyData("Alarm", "Alternator Over Voltage")) // b8
        // b6-b7 are reserved
        if ((faults1 shr 5) and 1 == 1) results.add(RenogyData("Alarm", "Alternator Over Current")) // b5
        if ((faults1 shr 4) and 1 == 1) results.add(RenogyData("Alarm", "Controller Over Temp 2")) // b4

        // Register 0x0122 is at offset 4 (bytes 4, 5)
        val faults2 = data.toUInt16(4)
        if ((faults2 shr 12) and 1 == 1) results.add(RenogyData("Alarm", "Solar Reverse Polarity")) // b12
        if ((faults2 shr 9) and 1 == 1) results.add(RenogyData("Alarm", "Solar Over Voltage")) // b9
        if ((faults2 shr 7) and 1 == 1) results.add(RenogyData("Alarm", "Solar Over Current")) // b7 ("too high")
        if ((faults2 shr 6) and 1 == 1) results.add(RenogyData("Alarm", "Battery Over Temperature")) // b6
        if ((faults2 shr 5) and 1 == 1) results.add(RenogyData("Alarm", "Controller Over Temp")) // b5
        if ((faults2 shr 2) and 1 == 1) results.add(RenogyData("Alarm", "Battery Low Voltage")) // b2
        if ((faults2 shr 1) and 1 == 1) results.add(RenogyData("Alarm", "Battery Over Voltage")) // b1
        if (faults2 and 1 == 1) results.add(RenogyData("Alarm", "Battery Over Discharge")) // b0

        return results
    }

    private fun parseChargingInfo(data: ByteArray): List<RenogyData> {
        return listOf(
            RenogyData("Battery SOC", data.toUInt16(0), "%"),
            RenogyData("Battery Voltage", data.toUInt16(2) * 0.1f, "V"),
            RenogyData("Charging Current", data.toUInt16(4) * 0.01f, "A"),
            RenogyData("Controller Temp", parseTemperature(data[6])),
            RenogyData("Battery Temp", parseTemperature(data[7])),
            RenogyData("Alternator Voltage", data.toUInt16(8) * 0.1f, "V"),
            RenogyData("Alternator Current", data.toUInt16(10) * 0.01f, "A"),
            RenogyData("Alternator Power", data.toUInt16(12), "W"),
            RenogyData("PV Voltage", data.toUInt16(14) * 0.1f, "V"),
            RenogyData("PV Current", data.toUInt16(16) * 0.01f, "A"),
            RenogyData("PV Power", data.toUInt16(18), "W"),
            RenogyData("Battery Min V Today", data.toUInt16(22) * 0.1f, "V"),
            RenogyData("Battery Max V Today", data.toUInt16(24) * 0.1f, "V"),
            RenogyData("Max Charge A Today", data.toUInt16(26) * 0.01f, "A"),
            RenogyData("Max Charge W Today", data.toUInt16(30), "W"),
            RenogyData("Charging Ah Today", data.toUInt16(34), "Ah"),
            RenogyData("Power Gen Today", data.toUInt16(38) / 1000.0f, "kWh"),
            RenogyData("Total Working Days", data.toUInt16(42), "days"),
            RenogyData("Over-discharge Count", data.toUInt16(44)),
            RenogyData("Full Charge Count", data.toUInt16(46)),
            RenogyData("Total Ah Accumulated", data.toUInt32(48), "Ah"),
            RenogyData("Total Power Gen", data.toUInt32(56) / 1000.0f, "kWh")
        )
    }

    private fun parseSettings(data: ByteArray): List<RenogyData> {
        val typeCode = data.toUInt16(6)
        val batteryType = batteryTypeMap[typeCode]

        val allSettings = mutableListOf(
            RenogyData("Charging Current Setting", data.toUInt16(0) * 0.01f, "A"),
            RenogyData("Battery Capacity", data.toUInt16(2), "Ah"),
            RenogyData("System Voltage Setting", data[4].toUByte().toInt(), "V"),
            RenogyData("Recognized Battery Voltage", data[5].toUByte().toInt(), "V"),
            RenogyData("Battery Type", batteryType ?: "Unknown ($typeCode)"),
            RenogyData("Over-voltage", data.toUInt16(8) * 0.1f, "V"),
            RenogyData("Charging Limit Voltage", data.toUInt16(10) * 0.1f, "V"),
            RenogyData("Equalization Voltage", data.toUInt16(12) * 0.1f, "V"),
            RenogyData("Boost Voltage", data.toUInt16(14) * 0.1f, "V"),
            RenogyData("Float Voltage", data.toUInt16(16) * 0.1f, "V"),
            RenogyData("Boost Return Voltage", data.toUInt16(18) * 0.1f, "V"),
            RenogyData("Over-discharge Return", data.toUInt16(20) * 0.1f, "V"),
            RenogyData("Under-voltage Warning", data.toUInt16(22) * 0.1f, "V"),
            RenogyData("Over-discharge Voltage", data.toUInt16(24) * 0.1f, "V"),
            RenogyData("Discharging Limit", data.toUInt16(26) * 0.1f, "V"),
            RenogyData("Over-discharge Delay", data.toUInt16(30), "s"),
            RenogyData("Equalization Time", data.toUInt16(32), "min"),
            RenogyData("Boost Time", data.toUInt16(34), "min"),
            RenogyData("Equalization Interval", data.toUInt16(36), "days"),
            RenogyData("Temp Comp Coeff", data.toUInt16(38), "mV/℃/2V"),
            RenogyData("Light Control Delay", data.toUInt16(58), "min"),
            RenogyData("Light Control Voltage", data.toUInt16(60), "V")
        )

        if (batteryType == "lithium") {
            allSettings.removeIf {
                it.key in listOf(
                    "Equalization Voltage",
                    "Float Voltage",
                    "Equalization Time",
                    "Equalization Interval",
                    "Temp Comp Coeff"
                )
            }
        }

        return allSettings
    }
}
