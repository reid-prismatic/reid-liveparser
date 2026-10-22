package com.prismatic.liveparser

import kotlin.jvm.JvmInline

sealed interface WeaveDimension {
    @JvmInline
    value class Points(val value: Float) : WeaveDimension

    @JvmInline
    value class Millimeters(val value: Double) : WeaveDimension
}

inline val Float.pt get() = WeaveDimension.Points(this)
inline val Int.pt get() = WeaveDimension.Points(this.toFloat())
inline val Double.mm get() = WeaveDimension.Millimeters(this)
inline val Int.mm get() = WeaveDimension.Millimeters(this.toDouble())
inline val Float.mm get() = WeaveDimension.Millimeters(this.toDouble())