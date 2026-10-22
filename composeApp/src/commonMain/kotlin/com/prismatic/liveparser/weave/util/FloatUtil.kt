package com.prismatic.liveparser

import kotlin.math.max
import kotlin.math.pow

internal fun Float.toStringAsFixed(digits: Int): String {
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this < 0f) "-Infinity" else "Infinity"

    val clampedDigits: Int = max(digits, 0) // Accept positive numbers and 0 only
    val pow = 10f.pow(clampedDigits)
    val shifted = this * pow // shift the given value by the corresponding power of 10
    val decimal = shifted - shifted.toInt() // obtain the decimal of the shifted value
    // Manually round up if the decimal value is greater than or equal to 0.5f.
    // because kotlin.math.round(0.5f) rounds down
    val roundedShifted =
        if (decimal >= 0.5f) {
            shifted.toInt() + 1
        } else {
            shifted.toInt()
        }

    val rounded = roundedShifted / pow // divide off the corresponding power of 10 to shift back
    return if (clampedDigits > 0) {
        // If we have any decimal points, convert the float to a string
        rounded.toString()
    } else {
        // If we do not have any decimal points, return the int
        // based string representation
        rounded.toInt().toString()
    }
}
