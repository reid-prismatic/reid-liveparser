package com.prismatic.liveparser.weave.properties

import androidx.compose.ui.graphics.Color
import org.prismatic.opus.math.Vec4

val weaveColorNames = mapOf(
    "red" to Color(1f,0f,0f),
    "orange" to Color(1f,0.5f,0f),
    "brown" to Color(0.75f, 0.25f, 0f),
    "green" to Color(0f,1f,0f),
    "blue" to Color(0f,0f,1f),
    "yellow" to Color(1f,1f,0f),
    "cyan" to Color(0f,1f,1f),
    "teal" to Color(0.25f,.77f,.65f),
    "azure" to Color(0f,0.5f,1f),
    "purple" to Color(0.5f,0f,1f),
    "violet" to Color(0.70f,.35f,1f),
    "magenta" to Color(1f,0f,1f),
    "crimson" to Color(1f,0f,0.5f),
    "black" to Color(0f,0f,0f),
    "white" to Color(1f,1f,1f),
    "gray" to Color(0.5f,0.5f,0.5f),
)

fun Color.mediumVariant() = this.copy(red = this.red * 0.7f, green = this.green * 0.7f, blue = this.blue * 0.7f)

fun Color.darkVariant() = this.copy(red = this.red * 0.3f, green = this.green * 0.3f, blue = this.blue * 0.3f)

fun Color.lightVariant() = this.copy(red = this.red * 0.6f + 0.4f, green = this.green * 0.6f + 0.4f, blue = this.blue * 0.6f + 0.4f)