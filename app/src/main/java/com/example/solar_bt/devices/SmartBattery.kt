package com.example.solar_bt.devices

import android.util.Log
import com.example.solar_bt.RegisterInfo

object SmartBattery : RenogyDevice {

    // The register to read to get the device model/info.
    override val deviceInfoRegister = RegisterInfo(5122, 8, "Smart Battery Device Info")

    // A list of other registers/sections to read for detailed data.
    // For the battery, this includes key operational data.
    private val batteryInfoRegister = RegisterInfo(5042, 6, "Smart Battery Main Info")
    private val cellVoltageInfoRegister = RegisterInfo(5000, 17, "Cell Voltage Info")
    private val cellTemperatureInfoRegister = RegisterInfo(5017, 17, "Cell Temperature Info")

    override val dataRegisters = listOf(
        batteryInfoRegister,
        cellVoltageInfoRegister,
        cellTemperatureInfoRegister
        // We could add other registers like cell voltages here in the future
    )

    /**
     * Parses the raw response from a Modbus read command for the Smart Battery.
     */
    override fun parseData(register: RegisterInfo, data: ByteArray): List<RenogyData>? {
        val parsedData = when (register.address) {
            deviceInfoRegister.address -> parseDeviceInfo(data)
            batteryInfoRegister.address -> parseBatteryInfo(data)
            cellVoltageInfoRegister.address -> parseCellVoltageInfo(data)
            cellTemperatureInfoRegister.address -> parseCellTemperatureInfo(data)
            else -> null // Unknown register for this device
        }
        Log.d(
            "SmartBattery",
            "parseData for address ${register.address}: ${parsedData?.size ?: 0} items"
        )
        return parsedData
    }

    private fun parseDeviceInfo(data: ByteArray): List<RenogyData> {
        // The model is a 16-byte string (8 words)
        val model = data.decodeToString(0, 16)
        return listOf(
            RenogyData("Model", model)
        )
    }

    private fun parseBatteryInfo(data: ByteArray): List<RenogyData> {
        // Based on BatteryClient.py, this section contains:
        // current(2 bytes), voltage(2 bytes), remaining_charge(4 bytes), capacity(4 bytes)
        // Total of 12 bytes (6 words)
        val current = data.toInt16(0) * 0.01f
        val voltage = data.toUInt16(2) * 0.1f
        val remainingCapacity = data.toUInt32(4) * 0.001
        val fullCapacity = data.toUInt32(8) * 0.001
        val stateOfCharge =
            if (fullCapacity > 0) (remainingCapacity / fullCapacity * 100).toInt() else 0

        return listOf(
            RenogyData("Voltage", "%.1f".format(voltage), "V"),
            RenogyData("Current", "%.2f".format(current), "A"),
            RenogyData("State of Charge", stateOfCharge, "%"),
            RenogyData("Remaining Capacity", "%.3f".format(remainingCapacity), "Ah"),
            RenogyData("Full Capacity", "%.3f".format(fullCapacity), "Ah")
        )
    }

    private fun parseCellVoltageInfo(data: ByteArray): List<RenogyData> {
        val results = mutableListOf<RenogyData>()
        val cellCount = data.toUInt16(0) // First word is cell count

        Log.d("SmartBattery", "parseCellVoltageInfo: cellCount=$cellCount")

        for (i in 0 until cellCount) {
            // Voltages start after the cell count word (2 bytes offset)
            val voltage = data.toUInt16(2 + i * 2) / 10.0f
            Log.d("SmartBattery", "Cell ${i + 1} Voltage: $voltage V")
            results.add(RenogyData("Cell ${i + 1} Voltage", "%.3f".format(voltage), "V"))
        }
        return results
    }

    private fun parseCellTemperatureInfo(data: ByteArray): List<RenogyData> {
        val results = mutableListOf<RenogyData>()
        val cellCount = data.toUInt16(0) // First word is cell count

        Log.d("SmartBattery", "parseCellTemperatureInfo: cellCount=$cellCount")

        for (i in 0 until cellCount) {
            // Temperatures start after the cell count word (2 bytes offset)
            val temperature = data.toInt16(2 + i * 2) / 10.0f
            Log.d("SmartBattery", "Cell ${i + 1} Temp: $temperature °C")
            results.add(RenogyData("Cell ${i + 1} Temp", "$temperature", "°C"))
        }
        return results
    }
}
