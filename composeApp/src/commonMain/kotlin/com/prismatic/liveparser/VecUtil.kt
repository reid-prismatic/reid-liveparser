package com.prismatic.liveparser

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.math.Vec3
import org.prismatic.opus.math.Vec4
import org.prismatic.opus.math.mix
import org.prismatic.opus.math.times
import org.prismatic.opus.math.xyz

fun String.toVec2OrNull() : Vec2? {
    val parts = split(",")
    if(parts.size != 2) return null
    val x = parts[0].trim().toFloatOrNull() ?: return null
    val y = parts[1].trim().toFloatOrNull() ?: return null
    return Vec2(x,y)
}

fun String.toVec4OrNull() : Vec4? {
    val parts = split(",")
    if(parts.size != 4) return null
    val x = parts[0].trim().toFloatOrNull() ?: return null
    val y = parts[1].trim().toFloatOrNull() ?: return null
    val z = parts[1].trim().toFloatOrNull() ?: return null
    val w = parts[1].trim().toFloatOrNull() ?: return null
    return Vec4(x,y,z,w)
}

fun String.toVec3OrNull() : Vec3? {
    val parts = split(",")
    if(parts.size != 3) return null
    val x = parts[0].trim().toFloatOrNull() ?: return null
    val y = parts[1].trim().toFloatOrNull() ?: return null
    val z = parts[1].trim().toFloatOrNull() ?: return null
    return Vec3(x,y,z)
}

private val builtInColors = mapOf(
    "red" to Vec4(1f,0f,0f,1f),
    "orange" to Vec4(1f,0.5f,0f,1f),
    "green" to Vec4(0f,1f,0f,1f),
    "blue" to Vec4(0f,0f,1f,1f),
    "yellow" to Vec4(1f,1f,0f,1f),
    "cyan" to Vec4(0f,1f,1f,1f),
    "azure" to Vec4(0f,0.5f,1f,1f),
    "purple" to Vec4(0.5f,0f,1f,1f),
    "magenta" to Vec4(1f,0f,1f,1f),
    "black" to Vec4(0f,0f,0f,1f),
    "white" to Vec4(1f,1f,1f,1f),
    "gray" to Vec4(0.5f,0.5f,0.5f,1f),
)

fun String.toColorVec4OrNull() : Vec4? {
    if(trim().length < length) return trim().toColorVec4OrNull()
    val percentPrefix = takeWhile { it.isDigit() || it == '.' || it == '%' }
    if( percentPrefix.any { it.isDigit() } && percentPrefix.endsWith('%')) {
        val digits = percentPrefix.dropLast(1)
        if(digits.isNotEmpty()) {
            val percent = digits.toFloatOrNull()
            if( percent!=null ) {
                //if(percent==0f) return Vec4(0f,0f,0f,0f)
                drop(percentPrefix.length).toColorVec4OrNull()?.let { return it.copy(w = it.w*(percent.coerceIn(0f..100f)/100f)) }
            }
        }
    }
    if(this.lowercase().startsWith("medium")) {
        builtInColors[this.lowercase().filter { !it.isWhitespace() }.removePrefix("medium")]?.let {
            return it.copy(x = it.x * 0.7f, y = it.y * 0.7f, z = it.z * 0.7f)
        }
    }
    if(this.lowercase().startsWith("dark")) {
        builtInColors[this.lowercase().filter { !it.isWhitespace() }.removePrefix("dark")]?.let {
            return it.copy(x = it.x * 0.3f, y = it.y * 0.3f, z = it.z * 0.3f)
        }
    }
    if(this.lowercase().startsWith("light")) {
        builtInColors[this.lowercase().filter { !it.isWhitespace() }.removePrefix("light")]?.let {
            return it.copy(x = it.x * 0.6f + 0.4f, y = it.y * 0.6f + 0.4f, z = it.z * 0.6f + 0.4f)
        }
    }
    builtInColors[this.lowercase().filter { !it.isWhitespace() }]?.let { return it }
    if(startsWith('#') || startsWith("0x") || startsWith("0X") || all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        val hexCode = when {
            startsWith("#") -> drop(1)
            startsWith("0x") -> drop(2)
            startsWith("0X") -> drop(2)
            else -> this
        }
        val colorCode = when(hexCode.length) {
            8 -> hexCode.toLongOrNull(16)
            6 -> hexCode.toLongOrNull(16)?.or(0xFF000000L)
            4 -> hexCode.doubleUp().toLongOrNull(16)
            3 -> hexCode.doubleUp().toLongOrNull(16)?.or(0xFF000000L)
            else -> null
        }
        if(colorCode!=null) {
            val color = Color(colorCode)
            return Vec4(color.red, color.green, color.blue, color.alpha)
        } else return null
    } else if( ',' in this) {
        this.toVec4OrNull()?.let { return it }
        this.toVec3OrNull()?.let { return Vec4(it.x,it.y,it.z,1f) }
    }
    return null
}

fun Vec4.toColor() = Color(x,y,z,w)
fun Vec4.toColorString() : String {
    val value = this
    val colorValue = Color(value.x,value.y,value.z, value.w).toArgb().toULong()
    val colorStr = colorValue.toString(16).padStart(6,'0').takeLast(6).uppercase()
    return (if(value.w<1f) "${(value.w*100).toInt()}% " else "") + "#$colorStr"
}

private fun String.doubleUp() = buildString {
    for(char in this@doubleUp) {
        append(char)
        append(char)
    }
}

fun Offset.toVec2() = Vec2(x,y)
fun Vec2.toOffset() = Offset(x,y)