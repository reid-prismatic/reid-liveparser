package com.prismatic.liveparser

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Color
import com.prismatic.liveparser.PropertyExpression.*
import org.prismatic.opus.time.Time
import org.prismatic.opus.time.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import androidx.compose.animation.core.Easing as ComposeEasing

interface PropertyExpressionContext {
    val duration: Time
    val progress: Float
    fun resolveVariable(symbol: String): PropertyExpression?
    fun callFunction(symbol: String, args: List<PropertyExpression>): PropertyExpression?
}

class PropertyExpressionLambdaContext(val parameters: List<String>, val baseContext: PropertyExpressionContext) : PropertyExpressionContext by baseContext {
    override fun resolveVariable(symbol: String): PropertyExpression? =
        if(symbol in parameters) PropertyExpression.Variable(symbol) else this.baseContext.resolveVariable(symbol)
}

class PropertyExpressionContextWithVars( val vars: Map<String, PropertyExpression>, val baseContext: PropertyExpressionContext ) : PropertyExpressionContext by baseContext {
    override fun resolveVariable(symbol: String): PropertyExpression? = vars[symbol] ?: baseContext.resolveVariable(symbol)
}

fun PropertyExpression.Easing.evaluate(context: PropertyExpressionContext): Easing = when (this) {
    is Easing.CubicBezier -> Easing.CubicBezier(
        x1.evaluate(context),
        y1.evaluate(context),
        x2.evaluate(context),
        y2.evaluate(context)
    )
    is Easing.FunctionCall -> Easing.FunctionCall(name, arguments.map { it.evaluate(context) })
    is Easing.Lambda -> Easing.Lambda(parameters, body.evaluate(PropertyExpressionLambdaContext(parameters, context)))
    is Easing.Named -> this
}

val SnapEasing = ComposeEasing { fraction -> if(fraction>=1f) 1f else 0f }
val AccelEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
val DecelEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
val SmoothEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

fun PropertyExpression.toFloatOrNull() : Float? = when (this) {
    is PropertyExpression.IntegerLiteral -> this.value.toFloat()
    is PropertyExpression.NumberLiteral -> this.value.toFloat()
    else -> null
}

fun PropertyExpression.toIntOrNull() : Int? = when (this) {
    is PropertyExpression.IntegerLiteral -> this.value.toInt()
    is PropertyExpression.NumberLiteral -> this.value.toInt()
    else -> null
}

fun PropertyExpression.toLongOrNull() : Long? = when (this) {
    is PropertyExpression.IntegerLiteral -> this.value
    is PropertyExpression.NumberLiteral -> this.value.toLong()
    else -> null
}

fun PropertyExpression.toDoubleOrNull() : Double? = when (this) {
    is PropertyExpression.IntegerLiteral -> this.value.toDouble()
    is PropertyExpression.NumberLiteral -> this.value
    else -> null
}

fun PropertyExpression.toPercentOrNull() : Double? = when (this) {
    is PropertyExpression.IntegerLiteral -> this.value.toDouble().coerceIn(0.0, 1.0)
    is PropertyExpression.NumberLiteral -> this.value.coerceIn(0.0, 1.0)
    is PropertyExpression.PercentageLiteral -> this.fraction
    else -> null
}

fun PropertyExpression.toTimeOrNull() : Time? = when (this) {
    is PropertyExpression.TimeLiteral -> value.toDuration(unit).toDouble(DurationUnit.SECONDS).seconds
    else -> null
}

fun StepEasing(steps: Int) = ComposeEasing { a ->
    ((a+0.5f/steps) - (a+0.5f/steps)%(1f/steps)).coerceIn(0f,1f)
}

fun PropertyExpression.Easing.resolve(context: PropertyExpressionContext): ComposeEasing? = when (this) {
    is PropertyExpression.Easing.CubicBezier ->
        CubicBezierEasing(
            x1.toFloatOrNull() ?: 0f,
            y1.toFloatOrNull() ?: 0f,
            x2.toFloatOrNull() ?: 1f,
            y2.toFloatOrNull() ?: 1f
        )
    is PropertyExpression.Easing.FunctionCall -> when(name) {
        "step" if arguments.size==1 -> StepEasing(arguments[0].toIntOrNull() ?: 1)
        else -> null
    }
    is PropertyExpression.Easing.Lambda -> when(parameters.size) {
        0, 1 ->  {
            val param = parameters.firstOrNull() ?: "t"
            ComposeEasing { p ->
                val symbolTable = mapOf(param to PropertyExpression.of(p))
                val symbolContext = PropertyExpressionContextWithVars(symbolTable, context)
                body.evaluate(symbolContext).toFloatOrNull() ?: 1f
            }
        }
        else -> null
    }
    is PropertyExpression.Easing.Named -> when(name) {
        "accel" -> AccelEasing
        "decel" -> DecelEasing
        "smooth" -> SmoothEasing
        "linear" -> LinearEasing
        "snap" -> SnapEasing
        else -> null
    }
}

fun interpolate(a: PropertyExpression, b: PropertyExpression, t: Float): PropertyExpression = when {
    a is NumberLiteral && b is NumberLiteral -> NumberLiteral(a.value * (1f-t) + b.value * t)
    a is Vector && b is Vector -> Vector(a.components.zip(b.components).map { (a, b) -> interpolate(a,b,t) })
    a is ColorLiteral && b is ColorLiteral -> {
        val colorA = Color(a.argb.toInt())
        val colorB = Color(b.argb.toInt())
        val mixed = Color(
            red = colorA.red * (1f-t) + colorB.red * t,
            green = colorA.green * (1f-t) + colorB.green * t,
            blue = colorA.blue * (1f-t) + colorB.blue * t,
            alpha = colorA.alpha * (1f-t) + colorB.alpha * t)
        PropertyExpression.of(mixed)
    }
    // TODO: ColorLiteral, vectors, etc.
    t > 0.5f -> b
    else -> a
}

fun ParsedProperty.evaluate(context: PropertyExpressionContext): ParsedProperty = when (this) {
    is PropertyExpression -> evaluate(context)
    is ParsedProperty.Alternate -> ParsedProperty.Alternate(index, this.value?.evaluate(context))
    is ParsedProperty.Compound -> ParsedProperty.Compound(components.map { it?.evaluate(context) })
}

fun ParsedProperty.toFlatString() : String = when (this) {
    is PropertyExpression -> this.toFlatString()
    is ParsedProperty.Alternate -> value?.toFlatString() ?: ""
    is ParsedProperty.Compound -> components.joinToString(", ") { it?.toFlatString() ?: "" }
}

fun PropertyExpression.evaluate(context: PropertyExpressionContext) : PropertyExpression {
    return when(this) {
        is Animation -> {
            val parts = (listOf(Segment(null,this.start)) + this.segments).mapIndexed { index, segment ->
                val easing: ComposeEasing = segment.easing?.evaluate(context)?.resolve(context) ?: SmoothEasing
                val value = segment.end.value.evaluate(context)
                val time = segment.end.time?.evaluate(context)
                val t : Float? = time?.toTimeOrNull()?.let { t -> (t.toSeconds().asDouble() / context.duration.toSeconds().asDouble()).toFloat() }
                val p: Float = t ?: time?.toPercentOrNull()?.toFloat() ?: (index.toFloat()/this.segments.size.coerceAtLeast(1))
                Triple(p,value,easing)
            }
            var prevP = 0f
            var prevValue : PropertyExpression? = null
            parts.sortedBy { (p,value,easing) -> p }.forEach { (p,value,easing) ->
                if( context.progress <= p ) {
                    if(prevValue==null) return value
                    val t = easing.transform(((context.progress - prevP) / (p-prevP)).coerceIn(0f,1f))
                    return interpolate(prevValue,value,t)
                }
                prevP = p
                prevValue = value
            }
            if(context.progress > 0.5f) parts.last().second else parts.first().second
        }
        is Binary -> {
            val left = left.evaluate(context)
            val right = right.evaluate(context)
            if( left is Vector && right is Vector) {
                Vector(
                    left.components.zip(right.components)
                        .map { (a, b) ->
                            Binary(operator, a.evaluate(context), b.evaluate(context)).evaluate(context)
                        }
                )
            } else if( left is StringLiteral ) {
                StringLiteral(left.value + right.toFlatString())
            } else if ( left is BooleanLiteral && right is BooleanLiteral) {
                when (operator) {
                    BinaryOperator.AND -> BooleanLiteral(left.value && right.value)
                    BinaryOperator.OR -> BooleanLiteral(left.value || right.value)
                    else -> throw IllegalStateException()
                }
            } else if( left is IntegerLiteral && right is IntegerLiteral ) {
                val a = left.value
                val b = right.value
                IntegerLiteral(when(operator) {
                    BinaryOperator.ADD -> a + b
                    BinaryOperator.SUBTRACT -> a - b
                    BinaryOperator.MULTIPLY -> a * b
                    BinaryOperator.DIVIDE -> a / b
                    BinaryOperator.AND -> throw IllegalStateException()
                    BinaryOperator.OR -> throw IllegalStateException()
                })
            } else if( (left is NumberLiteral || left is IntegerLiteral) && (right is NumberLiteral || right is IntegerLiteral) ) {
                val a = (left as? IntegerLiteral)?.value?.toDouble() ?: (left as? NumberLiteral)?.value ?: Double.NaN
                val b = (right as? IntegerLiteral)?.value?.toDouble() ?: (right as? NumberLiteral)?.value ?: Double.NaN
                NumberLiteral(when(operator) {
                    BinaryOperator.ADD -> a + b
                    BinaryOperator.SUBTRACT -> a - b
                    BinaryOperator.MULTIPLY -> a * b
                    BinaryOperator.DIVIDE -> a / b
                    BinaryOperator.AND -> throw IllegalStateException()
                    BinaryOperator.OR -> throw IllegalStateException()
                })
            } else {
                this
            }
        }
        is BooleanLiteral,
        is ColorLiteral,
        is DimensionLiteral,
        is IntegerLiteral,
        is NumberLiteral,
        is StringLiteral,
        is SemanticTag,
        is TimeLiteral -> this
        is PercentageLiteral -> NumberLiteral(this.fraction)
        is FunctionCall -> context.callFunction(name,arguments.map { it.evaluate(context) }) ?: this
        is Unary -> {
            val operand = operand.evaluate(context)
            when (operator) {
                UnaryOperator.NEGATE -> when(operand) {
                    is IntegerLiteral -> IntegerLiteral(-operand.value)
                    is NumberLiteral -> NumberLiteral(-operand.value)
                    is DimensionLiteral -> operand.copy(value = -operand.value)
                    is PercentageLiteral -> operand.copy(value = -operand.value)
                    is TimeLiteral -> operand.copy(value = -operand.value)
                    else -> throw IllegalStateException()
                }
                UnaryOperator.NOT -> when(operand) {
                    is BooleanLiteral -> BooleanLiteral(!operand.value)
                    else -> throw IllegalStateException()
                }
            }
        }
        is Variable -> context.resolveVariable(name) ?: this
        is Vector -> Vector(components.map { it.evaluate(context) })
    }
}