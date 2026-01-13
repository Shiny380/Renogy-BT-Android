package com.example.solar_bt.devices

import com.example.solar_bt.RenogyDeviceType

object RenogyDeviceFactory {
    fun getDevice(deviceType: RenogyDeviceType): RenogyDevice {
        return when (deviceType) {
            RenogyDeviceType.DC_CHARGER -> DcCharger
            RenogyDeviceType.BATTERY -> SmartBattery
        }
    }
}
