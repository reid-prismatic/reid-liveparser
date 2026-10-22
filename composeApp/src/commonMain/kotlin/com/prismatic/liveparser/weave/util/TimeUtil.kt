package com.prismatic.liveparser

import org.prismatic.opus.time.Time
import org.prismatic.opus.time.fps
import org.prismatic.opus.time.frames
import org.prismatic.opus.time.millis
import org.prismatic.opus.time.nanos
import org.prismatic.opus.time.seconds

fun String.toTimeOrNull() : Time? {
    val rawUnitSpec = takeLastWhile { it.isLetter() || it.isWhitespace() }
    val value = dropLast(rawUnitSpec.length).trim()
    val unitSpec = rawUnitSpec.trim().lowercase()
    return when(unitSpec) {
        "s" ->
            if ('/' in value) {
                val a = value.substringBefore('/').toLongOrNull()
                val b = value.substringAfter('/').toIntOrNull()
                if (a != null && b != null) a.frames at b.fps else null
            } else {
                value.toDoubleOrNull()?.seconds
            }
        "ms" -> value.toLongOrNull()?.millis
        "ns" -> value.toLongOrNull()?.nanos
        else -> null
    }
}