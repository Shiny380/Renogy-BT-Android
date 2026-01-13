package com.example.solar_bt.devices

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extracts a 16-bit unsigned integer (a single Modbus register) from the byte array.
 * @param index The starting index of the 2-byte integer.
 */
fun ByteArray.toUInt16(index: Int): Int {
    if (index + 1 >= this.size) return 0
    return ((this[index].toInt() and 0xFF) shl 8) or (this[index + 1].toInt() and 0xFF)
}

/**
 * Extracts a 32-bit unsigned integer (two Modbus registers) from the byte array.
 * @param index The starting index of the 4-byte integer.
 */
fun ByteArray.toUInt32(index: Int): Long {
    if (index + 3 >= this.size) return 0
    return ((this[index].toLong() and 0xFF) shl 24) or
            ((this[index + 1].toLong() and 0xFF) shl 16) or
            ((this[index + 2].toLong() and 0xFF) shl 8) or
            (this[index + 3].toLong() and 0xFF)
}

/**
 * Extracts a signed 16-bit integer from the byte array.
 * @param index The starting index of the 2-byte integer.
 */
fun ByteArray.toInt16(index: Int): Short {
    if (index + 1 >= this.size) return 0
    return ByteBuffer.wrap(this, index, 2).order(ByteOrder.BIG_ENDIAN).short
}

/**
 * Extracts a string from the byte array, cleaning up null terminators.
 * @param index The starting index of the string.
 * @param length The number of bytes to decode.
 */
fun ByteArray.decodeToString(index: Int, length: Int): String {
    if (index + length > this.size) return ""
    return this.copyOfRange(index, index + length).toString(Charsets.UTF_8).trimEnd('\u0000')
}

/**
 * Parses a temperature value from a single byte.
 * The Python reference is not provided, but it's often a signed byte.
 */
fun parseTemperature(byte: Byte): String {
    return "${byte.toInt()}°C"
}
