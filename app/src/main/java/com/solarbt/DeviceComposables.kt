package com.solarbt

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.solarbt.devices.AllowedValuesRule
import com.solarbt.devices.DeviceConnectionState
import com.solarbt.devices.MinMaxRule
import com.solarbt.devices.RenogyData

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
    deviceState: DeviceConnectionState,
    onWriteSetting: (key: String, value: Any) -> Unit,
    onClearError: () -> Unit,
    onSettingsVisibilityChange: (Boolean) -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val allData = deviceState.data.filter { it.isVisible }

    LaunchedEffect(showSettingsDialog) {
        onSettingsVisibilityChange(showSettingsDialog)
    }

    if (deviceState.writeError != null && !showSettingsDialog) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Write Error") },
            text = { Text(deviceState.writeError) },
            confirmButton = {
                TextButton(onClick = onClearError) {
                    Text("OK")
                }
            }
        )
    }

    val (writableSettings, readOnlyDetails) = remember(allData) {
        val displayedKeys = listOf(
            "Charging State",
            "Battery Charging State",
            "Solar Power", "PV Power",
            "Solar Voltage", "PV Voltage",
            "Solar Current", "PV Current",
            "Alternator Power", "Alternator Voltage", "Alternator Current"
        )
        val restOfData = allData.filter { it.key !in displayedKeys }
        restOfData.partition { it.isWritable }
    }

    if (showSettingsDialog) {
        DcChargerSettingsDialog(
            deviceState = deviceState,
            writableSettings = writableSettings,
            onDismissRequest = { showSettingsDialog = false },
            onSave = onWriteSetting,
            onClearError = onClearError
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // This is a simplified header. You might want to enhance this.
        val chargingStateValue = allData.find { it.key == "Charging State" }?.value
        val chargingStateString = chargingStateValue as? String ?: "N/A"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Status: $chargingStateString",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = { showSettingsDialog = true },
                enabled = !deviceState.isWriting,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Open Settings")
            }
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

        Text(text = "Details", style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (readOnlyDetails.isNotEmpty()) {
            LazyColumn {
                items(readOnlyDetails) { data ->
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
private fun DcChargerSettingsDialog(
    deviceState: DeviceConnectionState,
    writableSettings: List<RenogyData>,
    onDismissRequest: () -> Unit,
    onSave: (key: String, value: Any) -> Unit,
    onClearError: () -> Unit
) {
    var editingSetting by remember { mutableStateOf<RenogyData?>(null) }
    var wasPreviouslyWriting by remember { mutableStateOf(deviceState.isWriting) }

    LaunchedEffect(deviceState.isWriting) {
        if (wasPreviouslyWriting && !deviceState.isWriting) {
            // Write has just completed
            if (deviceState.writeError == null) { // and was successful
                onDismissRequest()
            }
        }
        wasPreviouslyWriting = deviceState.isWriting
    }

    // When a write error occurs, the dialog should not dismiss automatically.
    // This allows the user to see the error and decide what to do.
    if (deviceState.writeError != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Write Error") },
            text = { Text(deviceState.writeError) },
            confirmButton = {
                TextButton(onClick = onClearError) {
                    Text("OK")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                if (deviceState.isWriting) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn {
                        items(writableSettings) { data ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = data.key,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${data.value}${data.unit?.let { " $it" } ?: ""}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { editingSetting = data },
                                        // Disable editing if a write is in progress
                                        enabled = !deviceState.isWriting
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit ${data.key}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingSetting?.let { setting ->
        EditSettingDialog(
            setting = setting,
            isWriting = deviceState.isWriting,
            onDismiss = { editingSetting = null },
            onSave = { newValue ->
                onSave(setting.key, newValue)
                // Do not dismiss the main dialog here, wait for write confirmation
                editingSetting = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSettingDialog(
    setting: RenogyData,
    isWriting: Boolean,
    onDismiss: () -> Unit,
    onSave: (Any) -> Unit
) {
    var currentValue by remember { mutableStateOf(setting.value.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedAllowedValue by remember { mutableStateOf<Number?>(null) }


    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Edit ${setting.key}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                val rule = setting.validationRule
                if (rule is AllowedValuesRule) {
                    var expanded by remember { mutableStateOf(false) }
                    // Find the initial display text for the current value
                    val initialText =
                        rule.values.entries.find { it.value.toString() == setting.value.toString() }?.key
                            ?: setting.value.toString()
                    var selectedText by remember { mutableStateOf(initialText) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("New value") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            rule.values.forEach { (text, value) ->
                                DropdownMenuItem(
                                    text = { Text(text) },
                                    onClick = {
                                        selectedText = text
                                        selectedAllowedValue = value
                                        expanded = false
                                        error = null
                                    }
                                )
                            }
                        }
                    }

                } else {
                    OutlinedTextField(
                        value = currentValue,
                        onValueChange = {
                            currentValue = it
                            error = null // Clear error on change
                        },
                        label = { Text("New value") },
                        isError = error != null,
                        enabled = !isWriting // Disable input while writing
                    )
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isWriting) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            var isValid = true
                            var finalValue: Any? = null

                            when (rule) {
                                is MinMaxRule -> {
                                    val num = currentValue.toFloatOrNull()
                                    if (num == null) {
                                        error = "Invalid number"
                                        isValid = false
                                    } else if (num < rule.min || num > rule.max) {
                                        error = "Value must be between ${rule.min} and ${rule.max}"
                                        isValid = false
                                    } else {
                                        finalValue = num
                                    }
                                }

                                is AllowedValuesRule -> {
                                    finalValue = selectedAllowedValue
                                    if (finalValue == null) {
                                        // If user hasn't selected anything new, and the original value is valid, use it
                                        val initialValueKey =
                                            rule.values.entries.find { it.value.toString() == setting.value.toString() }?.key
                                        if (initialValueKey != null) {
                                            finalValue = setting.value
                                        } else {
                                            error = "Please select a value."
                                            isValid = false
                                        }
                                    }
                                }

                                null -> {
                                    finalValue = currentValue
                                }
                            }

                            if (isValid && finalValue != null) {
                                onSave(finalValue)
                            }
                        },
                        enabled = !isWriting // Disable save button while writing
                    ) {
                        if (isWriting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
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
    val power = allData.find { it.key in powerKeys && it.isVisible }
    val voltage = allData.find { it.key in voltageKeys && it.isVisible }
    val current = allData.find { it.key in currentKeys && it.isVisible }

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
    val visibleData = deviceState?.data?.filter { it.isVisible } ?: emptyList()

    val soc = visibleData.find { it.key == "State of Charge" }
    val voltageData = visibleData.find { it.key.contains("Voltage") }
    val currentData = visibleData.find { it.key == "Current" }

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

    val cellTemperatures = remember(visibleData) {
        visibleData
            .filter { it.key.startsWith("Cell ") && it.key.endsWith(" Temp") }
            .mapNotNull { it.value.toString().replace(",", ".").toFloatOrNull() }
    }

    val minMaxTempData = remember(cellTemperatures) {
        if (cellTemperatures.isNotEmpty()) {
            val minTemp = cellTemperatures.minOrNull()
            val maxTemp = cellTemperatures.maxOrNull()
            if (minTemp != null && maxTemp != null) {
                RenogyData("Temp", "%.0f-%.0f".format(minTemp, maxTemp), "°C")
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
        DataPoint(label = "Current", data = currentData)
        DataPoint(label = "Watts", data = wattageData)
        DataPoint(label = "Temp", data = minMaxTempData)
    }
}

private data class CellInfo(val cellNumber: Int, val voltage: String?, val temp: String?)

@SuppressLint("MissingPermission")
@Composable
fun SmartBatteryFullView(
    deviceState: DeviceConnectionState?,
    onSettingsVisibilityChange: (Boolean) -> Unit
) {
    val allData = deviceState?.data?.filter { it.isVisible } ?: emptyList()

    val cellInfoList = remember(allData) {
        allData
            .filter { it.key.startsWith("Cell ") && it.key.endsWith(" Voltage") }
            .mapNotNull {
                val cellNum = it.key.substringAfter("Cell ").substringBefore(" ").toIntOrNull()
                if (cellNum != null) {
                    val temp =
                        allData.find { t -> t.key == "Cell $cellNum Temp" }?.value?.toString()
                    CellInfo(cellNum, it.value.toString(), temp)
                } else {
                    null
                }
            }
            .sortedBy { it.cellNumber }
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