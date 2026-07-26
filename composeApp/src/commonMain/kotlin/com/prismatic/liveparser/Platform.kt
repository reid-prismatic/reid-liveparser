package com.prismatic.liveparser

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform