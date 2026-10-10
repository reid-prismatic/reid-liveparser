package com.prismatic.liveparser

import com.prismatic.liveparser.PropertyExpression.*

interface PropertyExpressionContext {
    fun resolveVariable(symbol: String): PropertyExpression?
    fun callFunction(symbol: String, args: List<PropertyExpression>): PropertyExpression?
}

fun PropertyExpression.evaluate(context: PropertyExpressionContext) : PropertyExpression {
    return when(this) {
        is Animation -> {
            (listOf(Segment(null,this.start)) + this.segments).map {
//                val easing = it.easing?.evaluate(context)
                val value = it.end.value
                val time = it.end.time
            }
            TODO()
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