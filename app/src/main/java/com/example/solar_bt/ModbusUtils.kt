package com.example.solar_bt

fun createModbusCommand(
    isRead: Boolean,           // true = read, false = write
    deviceAddress: Int = 0x01, // almost always 1 on Renogy BLE
    startRegister: Int,        // e.g. 0x000A or 10
    registerCount: Int = 1,    // how many 16-bit registers
    writeValue: Int? = null    // only used when isRead = false and writing 1 register
): ByteArray {

    val functionCode = if (isRead) 0x03 else 0x06 // 0x03 = Read Holding, 0x06 = Write Single

    val payload = mutableListOf<Byte>()

    payload.add(deviceAddress.toByte())
    payload.add(functionCode.toByte())

    if (isRead) {
        // Read Holding Registers
        payload.add((startRegister shr 8).toByte())  // high byte
        payload.add((startRegister and 0xFF).toByte()) // low byte
        payload.add((registerCount shr 8).toByte())
        payload.add((registerCount and 0xFF).toByte())
    } else {
        // Write Single Register
        payload.add((startRegister shr 8).toByte())
        payload.add((startRegister and 0xFF).toByte())
        payload.add((writeValue!! shr 8).toByte())
        payload.add((writeValue and 0xFF).toByte())
    }

    // Calculate CRC16/MODBUS
    val crc = calculateCrc16(payload.toByteArray())
    payload.add((crc and 0xFF).toByte())        // CRC low
    payload.add(((crc shr 8) and 0xFF).toByte()) // CRC high

    return payload.toByteArray()
}

// CRC16/MODBUS (polynomial 0xA001)
fun calculateCrc16(data: ByteArray): Int {
    var crc = 0xFFFF
    for (b in data) {
        crc = crc xor (b.toInt() and 0xFF)
        repeat(8) {
            val carry = crc and 0x0001
            crc = crc shr 1
            if (carry != 0) crc = crc xor 0xA001
        }
    }
    return crc
}

fun extractModbusReadResponseData(responsePayload: ByteArray): ByteArray? {
    // A valid response payload (after CRC removal) must have at least
    // Device Address (1 byte) + Function Code (1 byte) + Byte Count (1 byte)
    if (responsePayload.size < 3) {
        return null // Invalid packet
    }

    // Extract relevant fields
    // val deviceAddress = responsePayload[0] // Not needed for extraction
    // val functionCode = responsePayload[1] // Not needed for extraction
    val byteCount = responsePayload[2].toInt() and 0xFF // Convert to unsigned int

    // Verify the actual size of the data matches the byteCount
    // responsePayload.size = 1 (addr) + 1 (func) + 1 (count) + N (data)
    // So, N should be responsePayload.size - 3
    if (byteCount != (responsePayload.size - 3)) {
        // Mismatch between declared byte count and actual data length
        return null
    }

    // Extract the data bytes
    // Data starts at index 3 and goes for `byteCount` bytes
    return responsePayload.copyOfRange(3, 3 + byteCount)
}