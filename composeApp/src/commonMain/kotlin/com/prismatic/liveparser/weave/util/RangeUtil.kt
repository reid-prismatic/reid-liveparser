package com.prismatic.liveparser.weave.util

fun IntRange.offset(offset: Int): IntRange = if(isEmpty()) this else IntRange(first+offset, last+offset)
fun IntRange.offset(other: IntRange): IntRange = if(isEmpty() || other.isEmpty()) this else IntRange(first+other.first, last+other.first)