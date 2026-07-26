package com.prismatic.liveparser

actual fun blockingSleep(millis: Long) {
    Thread.sleep(millis)
}