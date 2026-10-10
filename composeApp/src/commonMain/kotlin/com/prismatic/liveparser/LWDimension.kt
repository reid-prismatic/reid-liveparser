package com.prismatic.liveparser

import kotlin.jvm.JvmInline

sealed interface LWDimension {
    @JvmInline
    value class Points(val value: Float) : LWDimension

    @JvmInline
    value class Millimeters(val value: Double) : LWDimension
}

inline val Float.pt get() = LWDimension.Points(this)
inline val Int.pt get() = LWDimension.Points(this.toFloat())
inline val Double.mm get() = LWDimension.Millimeters(this)
inline val Int.mm get() = LWDimension.Millimeters(this.toDouble())
inline val Float.mm get() = LWDimension.Millimeters(this.toDouble())