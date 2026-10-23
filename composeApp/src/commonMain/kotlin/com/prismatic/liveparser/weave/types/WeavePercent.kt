package com.prismatic.liveparser.weave.types

import com.prismatic.liveparser.toStringAsFixed
import kotlin.jvm.JvmInline

@JvmInline
value class WeavePercent(val fraction: Float) {
    val percent get() = fraction*100f
    override fun toString(): String = "${percent.toStringAsFixed(2)}%"
}

inline val Float.percent get() = WeavePercent(this/100f)
inline val Double.percent get() = WeavePercent(this.toFloat()/100f)
inline val Int.percent get() = WeavePercent(this.toFloat()/100f)

fun Float.asPercent() = WeavePercent(this)
fun Double.asPercent() = WeavePercent(this.toFloat())

