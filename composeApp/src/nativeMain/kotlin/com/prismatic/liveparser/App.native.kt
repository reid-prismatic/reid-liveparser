package com.prismatic.liveparser

import platform.posix.nanosleep
import platform.posix.usleep

actual fun blockingSleep(millis: Long) {
    usleep((millis * 1000L).toUInt())
}