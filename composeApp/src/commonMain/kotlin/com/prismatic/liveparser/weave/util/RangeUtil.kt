package com.prismatic.liveparser.weave.util

fun IntRange.offset(offset: Int): IntRange =
    when {
        isEmpty() -> offset..offset
        else -> IntRange(first + offset, last + offset)
    }
fun IntRange.offset(other: IntRange): IntRange = when {
    isEmpty() && !other.isEmpty() -> other.first..other.first
    isEmpty() || other.isEmpty() -> this
    else -> IntRange(first + other.first, last + other.first)
}