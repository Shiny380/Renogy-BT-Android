package com.solarbt.devices

import com.solarbt.RenogyDeviceType

object RenogyDeviceFactory {
    fun getDevice(deviceType: RenogyDeviceType): RenogyDevice {
        return when (deviceType) {
            RenogyDeviceType.DC_CHARGER -> DcCharger
            RenogyDeviceType.BATTERY -> SmartBattery
        }
    }
}
