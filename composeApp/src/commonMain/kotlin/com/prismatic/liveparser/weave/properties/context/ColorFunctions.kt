package com.prismatic.liveparser

import androidx.compose.ui.graphics.Color
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyExpressionContext
import com.prismatic.liveparser.weave.properties.toFloatOrNull

private fun PropertyExpression.toFractionalColorComponentOrNull() : Float? = when (this) {
    is PropertyExpression.IntegerLiteral -> (this.value.toFloat() / 255f).coerceIn(0.0f, 1.0f)
    is PropertyExpression.NumberLiteral -> this.value.toFloat().coerceIn(0.0f, 1.0f)
    is PropertyExpression.PercentageLiteral -> this.fraction.toFloat().coerceIn(0f,1f)
    else -> null
}

private class PropertyExpressionColorContext(
    val baseContext : PropertyExpressionContext
) : PropertyExpressionContext by baseContext {
    override fun callFunction(symbol: String, args: List<PropertyExpression>): PropertyExpression? {
        return when(symbol) {
            "rgb" if args.size == 3 -> PropertyExpression.of(
                Color(
                    args[0].toFractionalColorComponentOrNull() ?: 0f,
                    args[1].toFractionalColorComponentOrNull() ?: 0f,
                    args[2].toFractionalColorComponentOrNull() ?: 0f
                )
            )
            "rgb" if args.size == 4 -> PropertyExpression.of(
                Color(
                    args[0].toFractionalColorComponentOrNull() ?: 0f,
                    args[1].toFractionalColorComponentOrNull() ?: 0f,
                    args[2].toFractionalColorComponentOrNull() ?: 0f,
                    args[3].toFractionalColorComponentOrNull() ?: 1f,
                )
            )
            "hsv" if args.size == 3 -> PropertyExpression.of(
                Color.hsv(
                    args[0].toFloatOrNull() ?: 0f,
                    args[1].toFractionalColorComponentOrNull() ?: 0f,
                    args[2].toFractionalColorComponentOrNull() ?: 0f
                )
            )
            "hsv" if args.size == 4 -> PropertyExpression.of(
                Color.hsv(
                    args[0].toFloatOrNull() ?: 0f,
                    args[1].toFractionalColorComponentOrNull() ?: 0f,
                    args[2].toFractionalColorComponentOrNull() ?: 0f,
                    args[3].toFractionalColorComponentOrNull() ?: 1f,
                )
            )
            else -> baseContext.callFunction(symbol, args)
        }
    }
}

fun PropertyExpressionContext.withColorConstructors() : PropertyExpressionContext = PropertyExpressionColorContext(this)
