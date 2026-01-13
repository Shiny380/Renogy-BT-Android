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

    private val chargingStateValuesMap = mapOf(
        0 to "Standby",
        1 to "Alternator -> Battery",
        2 to "Battery <- Alternator",
        3 to "Solar -> Battery",
        4 to "Solar + Alternator -> Battery",
        5 to "Solar -> Alternator"
    )

    override val deviceInfoRegister = RegisterInfo(12, 8, "DC Charger Device Info")
    private val chargingInfoRegister = RegisterInfo(256, 30, "DC Charger Charging Info")
    private val stateRegisters = RegisterInfo(288, 3, "DC Charger Alarms & Status")
//    private val state2Registers = RegisterInfo(293, 1, "DC Charger Charging State")
    private val batteryTypeRegister = RegisterInfo(57348, 1, "DC Charger Battery Type")

    override val dataRegisters = listOf(
        chargingInfoRegister,
        stateRegisters,
//        state2Registers,
        batteryTypeRegister
    )

    override fun parseData(register: RegisterInfo, data: ByteArray): List<RenogyData>? {
        val parsedData = when (register.address) {
            deviceInfoRegister.address -> parseDeviceInfo(data)
            chargingInfoRegister.address -> parseChargingInfo(data)
            stateRegisters.address -> parseState(data)
//            state2Registers.address -> parseChargingState(data)
            batteryTypeRegister.address -> parseBatteryType(data)
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

    private fun parseBatteryType(data: ByteArray): List<RenogyData> {
        val typeCode = data.toUInt16(0)
        return listOf(
            RenogyData("Battery Type", batteryTypeMap[typeCode] ?: "Unknown ($typeCode)")
        )
    }

    private fun parseChargingState(data: ByteArray): List<RenogyData> {
        val chargingStateCode = data.toUInt16(0)
        val chargingStateString = chargingStateValuesMap[chargingStateCode] ?: "Unknown ($chargingStateCode)"
        Log.d(
            "DcCharger",
            "Charging State Code: $chargingStateCode, Mapped Value: $chargingStateString"
        )
        return listOf(
            RenogyData(
                "Charging State",
                chargingStateString
            )
        )
    }

    private fun parseState(data: ByteArray): List<RenogyData> {
        val results = mutableListOf<RenogyData>()
        val chargingStatusCode = data[0].toInt() and 0xFF
        results.add(
            RenogyData(
                "Battery Charging State",
                batteryChargingStateMap[chargingStatusCode] ?: "Unknown ($chargingStatusCode)"
            )
        )

        // Alarms
        val alarmByte1 =
            data.toUInt16(1) // Bytes 2 and 3 of the response, but index 1 and 2 of the data
        if ((alarmByte1 shr 11) and 1 == 1) results.add(RenogyData("Alarm", "Low Temp Shutdown"))
        if ((alarmByte1 shr 10) and 1 == 1) results.add(
            RenogyData(
                "Alarm",
                "BMS Overcharge Protection"
            )
        )
        if ((alarmByte1 shr 9) and 1 == 1) results.add(
            RenogyData(
                "Alarm",
                "Starter Reverse Polarity"
            )
        )
        if ((alarmByte1 shr 8) and 1 == 1) results.add(
            RenogyData(
                "Alarm",
                "Alternator Over Voltage"
            )
        )
        if ((alarmByte1 shr 4) and 1 == 1) results.add(
            RenogyData(
                "Alarm",
                "Alternator Over Current"
            )
        )
        if ((alarmByte1 shr 3) and 1 == 1) results.add(RenogyData("Alarm", "Controller Over Temp"))

        val alarmByte2 = data.toUInt16(3) // Bytes 4 and 5
        if ((alarmByte2 shr 12) and 1 == 1) results.add(
            RenogyData(
                "Alarm",
                "Solar Reverse Polarity"
            )
        )
        if ((alarmByte2 shr 9) and 1 == 1) results.add(RenogyData("Alarm", "Solar Over Voltage"))
        if ((alarmByte2 shr 7) and 1 == 1) results.add(RenogyData("Alarm", "Solar Over Current"))
        if ((alarmByte2 shr 6) and 1 == 1) results.add(
            RenogyData(
                "Alarm",
                "Battery Over Temperature"
            )
        )
        if ((alarmByte2 shr 5) and 1 == 1) results.add(RenogyData("Alarm", "Controller Over Temp"))
        if ((alarmByte2 shr 2) and 1 == 1) results.add(RenogyData("Alarm", "Battery Low Voltage"))
        if ((alarmByte2 shr 1) and 1 == 1) results.add(RenogyData("Alarm", "Battery Over Voltage"))
        if (alarmByte2 and 1 == 1) results.add(RenogyData("Alarm", "Battery Over Discharge"))

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
            RenogyData("Power Gen Today", data.toUInt16(38), "Wh"),
            RenogyData("Total Working Days", data.toUInt16(42), "days"),
            RenogyData("Over-discharge Count", data.toUInt16(44)),
            RenogyData("Full Charge Count", data.toUInt16(46)),
            RenogyData("Total Ah Accumulated", data.toUInt32(48), "Ah"),
            RenogyData("Total Power Gen", data.toUInt32(56), "Wh")
        )
    }
}
