package com.example.solar_bt

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.solar_bt.devices.DeviceConnectionState
import com.example.solar_bt.devices.RenogyData

@Composable
fun DataPoint(label: String, data: RenogyData?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (data?.value != null) {
            val text = when (val value = data.value) {
                is Float -> if (label == "SOC") "%.1f".format(value) else "%.2f".format(value)
                is Double -> if (label == "SOC") "%.1f".format(value) else "%.2f".format(value)
                else -> value.toString()
            }
            Text(
                text = "$text${data.unit ?: ""}",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(text = "- -", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// --- DC Charger Composables ---

@SuppressLint("MissingPermission")
@Composable
fun DcChargerOverview(
    deviceState: DeviceConnectionState?
) {
    val chargingCurrentData = deviceState?.data?.find { it.key == "Charging Current" }
    val batteryVoltageData = deviceState?.data?.find { it.key == "Battery Voltage" }
    val controllerTempData = deviceState?.data?.find { it.key == "Controller Temp" }

    val chargingWattsData = remember(chargingCurrentData, batteryVoltageData) {
        val voltageStr = batteryVoltageData?.value?.toString()
        val currentStr = chargingCurrentData?.value?.toString()

        if (voltageStr != null && currentStr != null) {
            val voltage = voltageStr.replace(",", ".").toFloatOrNull()
            val current = currentStr.replace(",", ".").toFloatOrNull()
            if (voltage != null && current != null) {
                val wattage = voltage * current
                RenogyData("Charging Power", "%.2f".format(wattage), "W")
            } else {
                null
            }
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DataPoint(label = "Charge Watts", data = chargingWattsData)
        DataPoint(label = "Charge Amps", data = chargingCurrentData)
        DataPoint(label = "Controller Temp", data = controllerTempData)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DcChargerFullView(
    deviceState: DeviceConnectionState?
) {
    val allData = deviceState?.data ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val chargingStateValue = allData.find { it.key == "Charging State" }?.value
        val chargingStateString = chargingStateValue as? String ?: "N/A"
        val batteryChargingStateValue = allData.find { it.key == "Battery Charging State" }?.value
        @Suppress("DEPRECATION")
        val batteryChargingStateString =
            (batteryChargingStateValue as? String)?.capitalize(java.util.Locale.ROOT) ?: "N/A"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Status: $chargingStateString", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Mode: $batteryChargingStateString",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Solar/PV section
        SourceDataView(
            title = "Solar/PV",
            allData = allData,
            powerKeys = listOf("Solar Power", "PV Power"),
            voltageKeys = listOf("Solar Voltage", "PV Voltage"),
            currentKeys = listOf("Solar Current", "PV Current")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Alternator section
        SourceDataView(
            title = "Alternator",
            allData = allData,
            powerKeys = listOf("Alternator Power"),
            voltageKeys = listOf("Alternator Voltage"),
            currentKeys = listOf("Alternator Current")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Rest of the data
        Text(text = "Details", style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val displayedKeys = listOf(
            "Charging State",
            "Battery Charging State",
            "Solar Power", "PV Power",
            "Solar Voltage", "PV Voltage",
            "Solar Current", "PV Current",
            "Alternator Power", "Alternator Voltage", "Alternator Current"
        )
        val restOfData = allData.filter { it.key !in displayedKeys }

        if (restOfData.isNotEmpty()) {
            LazyColumn {
                items(restOfData) { data ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = data.key, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${data.value}${data.unit?.let { " $it" } ?: ""}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No other details available.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SourceDataView(
    title: String,
    allData: List<RenogyData>,
    powerKeys: List<String>,
    voltageKeys: List<String>,
    currentKeys: List<String>
) {
    val power = allData.find { it.key in powerKeys }
    val voltage = allData.find { it.key in voltageKeys }
    val current = allData.find { it.key in currentKeys }

    if (power != null || voltage != null || current != null) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DataInfoColumn(label = "Power", data = power)
                DataInfoColumn(label = "Voltage", data = voltage)
                DataInfoColumn(label = "Current", data = current)
            }
        }
    }
}

@Composable
private fun DataInfoColumn(label: String, data: RenogyData?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = data?.let { "${it.value}${it.unit?.let { " $it" } ?: ""}" } ?: "N/A",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}

// --- Smart Battery Composables ---

@SuppressLint("MissingPermission")
@Composable
fun SmartBatteryOverview(
    deviceState: DeviceConnectionState?
) {
    val soc = deviceState?.data?.find { it.key == "State of Charge" }
    val voltageData = deviceState?.data?.find { it.key.contains("Voltage") }
    val currentData = deviceState?.data?.find { it.key == "Current" }

    val wattageData = remember(voltageData, currentData) {
        val voltageStr = voltageData?.value?.toString()
        val currentStr = currentData?.value?.toString()

        if (voltageStr != null && currentStr != null) {
            val voltage = voltageStr.replace(",", ".").toFloatOrNull()
            val current = currentStr.replace(",", ".").toFloatOrNull()
            if (voltage != null && current != null) {
                val wattage = voltage * current
                RenogyData("Watts", "%.1f".format(wattage), "W")
            } else {
                null
            }
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DataPoint(label = "SOC", data = soc)
        DataPoint(label = "Voltage", data = voltageData)
        DataPoint(label = "Watts", data = wattageData)
    }
}

private data class CellInfo(val cellNumber: Int, val voltage: String?, val temp: String?)

@SuppressLint("MissingPermission")
@Composable
fun SmartBatteryFullView(
    deviceState: DeviceConnectionState?
) {
    val allData = deviceState?.data ?: emptyList()

    val cellInfoList = remember(allData) {
        val maxCellNumber = allData.mapNotNull {
            val key = it.key
            if (key.startsWith("Cell ") && (key.endsWith(" Voltage") || key.endsWith(" Temp"))) {
                key.substringAfter("Cell ").substringBefore(" ").toIntOrNull()
            } else {
                null
            }
        }.maxOrNull() ?: 0

        (1..maxCellNumber).map { cellNum ->
            val voltage =
                allData.find { it.key == "Cell $cellNum Voltage" }?.value?.toString()
            val temp = allData.find { it.key == "Cell $cellNum Temp" }?.value?.toString()
            CellInfo(cellNum, voltage, temp)
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val soc = allData.find { it.key == "State of Charge" }?.value
        val currentStr = allData.find { it.key == "Current" }?.value?.toString()
        val voltageStr =
            allData.find { it.key.contains("Voltage") && !it.key.startsWith("Cell") }?.value?.toString()

        val wattage = remember(currentStr, voltageStr) {
            val current = currentStr?.replace(",", ".")?.toFloatOrNull()
            val voltage = voltageStr?.replace(",", ".")?.toFloatOrNull()
            if (current != null && voltage != null) {
                current * voltage
            } else {
                null
            }
        }

        val powerColor = when {
            wattage == null -> MaterialTheme.colorScheme.onSurface // Default or error color
            wattage > 0.1f -> Color.Green
            wattage < -0.1f -> Color.Red
            else -> MaterialTheme.colorScheme.onSurface // Normal color
        }

        Text(
            text = soc?.let {
                val socFloat = it.toString().replace(",", ".").toFloatOrNull()
                socFloat?.let { "%.1f%%".format(it) } ?: "N/A"
            } ?: "N/A",
            style = MaterialTheme.typography.displayLarge // Large text for SoC
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            // Power (Wattage)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Power", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = wattage?.let { "%.1f W".format(it) } ?: "N/A",
                    style = MaterialTheme.typography.headlineSmall,
                    color = powerColor
                )
            }

            // Voltage
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Voltage", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = voltageStr?.let { v ->
                        val vFloat = v.replace(",", ".").toFloatOrNull()
                        vFloat?.let { "%.2f V".format(it) } ?: (v + "V")
                    } ?: "N/A",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            // Current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Current", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentStr?.let { c ->
                        val cFloat = c.replace(",", ".").toFloatOrNull()
                        cFloat?.let { "%.2f A".format(it) } ?: (c + "A")
                    } ?: "N/A",
                    style = MaterialTheme.typography.headlineSmall,
                    color = powerColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (cellInfoList.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cellInfoList.size) { cellInfo ->
                    CellItem(cellInfo = cellInfoList[cellInfo])
                }
            }
        }
    }
}

@Composable
private fun CellItem(cellInfo: CellInfo) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Cell ${cellInfo.cellNumber}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${cellInfo.voltage ?: "N/A"} V",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 14.sp
            )
            Text(
                text = "${cellInfo.temp ?: "N/A"} °C",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp
            )
        }
    }
}