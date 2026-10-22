/*
 * This is a property expression parser. The main function used here is [parsePropertyExpression]. Normally,
 * you would want to use the property string parser instead, which is a wrapper for this and handles typed
 * properties, enum properties, and so on.
 *
 * Everything in this file was implemented using AI (Fable 5.1 Max)
 */

package com.prismatic.liveparser.weave.properties

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.prismatic.liveparser.WeaveDimension
import com.prismatic.liveparser.toStringAsFixed
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.math.Vec3
import org.prismatic.opus.math.Vec4
import org.prismatic.opus.time.FrameRate
import org.prismatic.opus.time.MediaTime
import org.prismatic.opus.time.Time
import org.prismatic.opus.time.fps
import org.prismatic.opus.time.millis
import org.prismatic.opus.time.seconds
import org.prismatic.opus.time.takeOrElse
import kotlin.time.DurationUnit

/**
 * Parses a property expression into a parse tree.
 *
 * Nothing is evaluated or resolved: names are kept as written, and it is up to the consumer of the tree to
 * decide what a variable, function, easing name or semantic tag refers to.
 *
 * ### Grammar
 *
 * Lowest precedence first; every binary operator is left-associative.
 *
 * ```
 * expression := animation | operand ("," operand)+  two to four operands make a Vector
 * animation  := operand | keyframe (range keyframe)+
 * keyframe   := operand ("@" operand)?              a value and, optionally, its time
 * range      := ".."  |  ".." easing ".."
 * easing     := NAME                                ..linear..
 *             | NAME "(" arguments? ")"             ..step(3)..
 *             | "(" arguments ")"                   ..(0.25, 0.1, 0.25, 1)..   exactly four values
 *             | "{" (NAME ("," NAME)* "->")? expression "}"                    ..{ t -> t*t }..
 * operand    := or
 * or         := and ("||" and)*
 * and        := sum ("&&" sum)*
 * sum        := product (("+" | "-") product)*
 * product    := unary (("*" | "/") unary)*
 * unary      := ("-" | "!") unary | primary
 * primary    := literal | NAME | NAME "(" arguments? ")" | "(" expression ")"
 * arguments  := animation ("," animation)*
 * ```
 *
 * ### Literals
 *
 * | Kind         | Examples                         | Node                                    |
 * |--------------|----------------------------------|-----------------------------------------|
 * | integer      | `42`, `0xFF`                     | [PropertyExpression.IntegerLiteral]     |
 * | number       | `1.5`, `.5`, `2.5e-3`, `1e6`     | [PropertyExpression.NumberLiteral]      |
 * | boolean      | `true`, `false`                  | [PropertyExpression.BooleanLiteral]     |
 * | string       | `"text"`, `'text'`               | [PropertyExpression.StringLiteral]      |
 * | color        | `#RRGGBB`, `#AARRGGBB`           | [PropertyExpression.ColorLiteral]       |
 * | time         | `250ms`, `1.5s`, `2min`, `1h`    | [PropertyExpression.TimeLiteral]        |
 * | dimension    | `12pt`, `2.5cm`, `10mm`, `1in`   | [PropertyExpression.DimensionLiteral]   |
 * | percentage   | `50%`, `12.5%`                   | [PropertyExpression.PercentageLiteral]  |
 * | semantic tag | `{#foo}`, `{#_barAnd_stuff3}`    | [PropertyExpression.SemanticTag]        |
 *
 * A literal with a decimal point or an exponent is a number; otherwise it is an integer. Unit and percent
 * suffixes must follow the digits directly. String escapes are `\\ \" \' \n \r \t \b` and `\uXXXX`.
 *
 * A NAME starts with a letter or an underscore and continues with letters, digits and underscores. Letters are
 * not limited to ASCII, and the combining marks and zero-width joiners that some scripts put inside a word are
 * accepted there too; names are taken exactly as written, without Unicode normalization. `true` and `false` are
 * the only reserved words.
 *
 * ### Rules worth knowing
 *
 * * **Vectors need parentheses wherever a comma could mean something else.** `1, 2, 3` on its own is a 3-vector,
 *   but as a function argument it is `f((1, 2), 3)` and as an operand `(1, 2) * 2`. Commas and ranges do not mix
 *   without them either: `0, 0 .. 10, 10` could be read two ways and is rejected, so write `(0, 0) .. (10, 10)`
 *   for a range between vectors or `0, (0 .. 10), 10` for a range inside a vector. Parentheses only group; they
 *   leave no node in the tree.
 * * **An eased range contains no whitespace.** `a ..linear.. b` is one range eased by `linear`, while
 *   `a .. linear .. b` is three keyframes. This holds for every easing form, which makes `a..b..c` and
 *   `p..(1, 2, 3, 4)..q` eased ranges too: put spaces around `..` when the item in the middle is a keyframe.
 *   Whitespace *inside* an easing's parentheses or braces is fine.
 * * **Timing** (`value @ time`) binds looser than arithmetic and logic but tighter than `..`, so in
 *   `a + 1 @ t0 + 2s .. b` the first keyframe has the value `a + 1` and the time `t0 + 2s`. It is only accepted
 *   on the keyframes of an animation.
 * * **Depth is limited.** An expression that nests, or chains binary operators, more than 100 levels deep is
 *   rejected. The returned tree is therefore never deeper than that, which bounds the stack that walking it
 *   recursively takes.
 *
 * @throws PropertyExpressionParseException if [input] is not a valid property expression
 */
@Throws(PropertyExpressionParseException::class)
fun parsePropertyExpression(input: String): PropertyExpression =
    PropertyExpressionParser(input).parse()

/**
 * Thrown by [parsePropertyExpression] when the input is not a valid property expression.
 *
 * @property reason what is wrong, without position information
 * @property range the offending part of the input, as character indices. It is empty (and positioned at the end
 * of the input) when the input ended too early.
 */
class PropertyExpressionParseException(
    val reason: String,
    val range: IntRange,
) : IllegalArgumentException("$reason (at offset ${range.first})")

/**
 * What a property string parses to: a single [PropertyExpression], for a compound property a [Compound] of its
 * parts, and for a property with alternatives the [Alternate] that the string turned out to be.
 */
sealed interface ParsedProperty {

    /**
     * The parts of a compound property, one entry per component of its type and in the same order. An optional
     * component that the string left out is `null`.
     */
    data class Compound(val components: List<ParsedProperty?>) : ParsedProperty

    /**
     * The value of a property that can be written in several ways: the string was read as the component at
     * [index] among the alternatives of its type, and [value] is what it is when read that way. The value alone
     * need not show which one it was: the option `Bold` and the expression `"Bold"` are the same string literal.
     * It is `null` if that component is one that may be left out, and the string left it out.
     */
    data class Alternate(val index: Int, val value: ParsedProperty?) : ParsedProperty
    data class Error(val error: String, val default: ParsedProperty?) : ParsedProperty
}

/**
 * A node of the parse tree produced by [parsePropertyExpression].
 *
 * Nodes are values with structural equality. Where a node came from in the source text ([sourceRange]) is
 * deliberately *not* part of that equality: a node equals any other occurrence of the same expression. Code that
 * needs to tell occurrences apart, such as a table of per-node results, should key on [sourceRange] instead.
 */
sealed class PropertyExpression : ParsedProperty {

    /**
     * The characters of the parsed input this node was read from, or [IntRange.EMPTY] for a node that was not
     * created by the parser (which includes the result of `copy()`).
     *
     * Enclosing parentheses are not part of a node's own range.
     */
    var sourceRange: IntRange = IntRange.EMPTY
        internal set

    abstract fun toDebugString(): String
    open fun toFlatString(): String = toDebugString()

    sealed class PropertyExpressionLiteral : PropertyExpression()

    /** `42`, `0xFF` */
    data class IntegerLiteral(val value: Long) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = "$value"
    }

    /** `1.5`, `.5`, `2.5e-3`: a floating point value (no distinction is made between float and double). */
    data class NumberLiteral(val value: Double) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = value.toFloat().toStringAsFixed(3)
    }

    /** `true` or `false` */
    data class BooleanLiteral(val value: Boolean) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = "$value"
    }

    /** A ratio such as 16:9 (for visual ratios, the numerator is the width part, the denominator is the height part).
     */
    data class RatioLiteral(val numerator: Int, val denominator: Int) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = "$numerator:$denominator"
    }

    /** `"text"` or `'text'`; [value] has its escape sequences resolved. */
    data class StringLiteral(val value: String) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = "\"$value\""
        override fun toFlatString(): String = value
    }

    /** `#RRGGBB` (fully opaque) or `#AARRGGBB`; [argb] is always the full `0xAARRGGBB` value. */
    data class ColorLiteral(val argb: UInt) : PropertyExpressionLiteral() {
        val alpha: Int get() = (argb shr 24).toInt()
        val red: Int get() = ((argb shr 16) and 0xFFu).toInt()
        val green: Int get() = ((argb shr 8) and 0xFFu).toInt()
        val blue: Int get() = (argb and 0xFFu).toInt()

        override fun toString(): String = "ColorLiteral(#${argb.toString(16).padStart(8, '0')})"
        override fun toDebugString(): String = "ColorLiteral($alpha, $red, $green, $blue)"
        override fun toFlatString(): String = "#${argb.toString(16).padStart(8, '0')}"
    }

    /** `250ms`, `1.5s`, `2min` (or `2m`), `1h`: [value] is the number as written, in [unit]s. */
    data class TimeLiteral(val value: Time) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = "$value"
    }

    /** `60fps`, `120fps` etc */
    data class FrameRateLiteral(val value: FrameRate) : PropertyExpressionLiteral() {
        override fun toDebugString(): String = "$value"
    }

    /** `12pt`, `2.5cm`, `10mm`, `1in`: a length; [value] is the number as written, in [unit]s. */
    data class DimensionLiteral(val value: Double, val unit: DimensionUnit) : PropertyExpressionLiteral() {
        /** The same length in points. */
        val points: Double get() = value * unit.points
        override fun toDebugString(): String = "$value${unit.symbol}"
    }

    /** `50%`: [value] is the number as written (`50.0`); [fraction] is the same amount as a ratio (`0.5`). */
    data class PercentageLiteral(val value: Double) : PropertyExpressionLiteral() {
        val fraction: Double get() = value / 100.0
        override fun toDebugString(): String = "${value*100}%"
    }

    /** `{#name}`; [name] excludes the hash. Braces around a `#name` always make a tag, never a color (`{#decade}`). */
    data class SemanticTag(val name: String) : PropertyExpression() {
        override fun toDebugString(): String = "{#$name}"
    }

    /** A bare name such as `width`. */
    data class Variable(val name: String) : PropertyExpression() {
        override fun toDebugString(): String = name
    }

    /** `name(arguments)`. A vector argument needs its own parentheses: `f((1, 2), 3)`. */
    data class FunctionCall(val name: String, val arguments: List<PropertyExpression>) : PropertyExpression() {
        override fun toDebugString(): String = "$name(${arguments.size}):\n" +
                arguments.asSequence().flatMap { it.toDebugString().lineSequence() }.joinToString("\n") { "  $it" }
    }

    /** `-operand` or `!operand`. Negative numbers are a [Unary] around a literal; nothing is folded. */
    data class Unary(val operator: UnaryOperator, val operand: PropertyExpression) : PropertyExpression() {
        override fun toDebugString(): String = "$operator:\n" +
                sequenceOf(operand).flatMap { it.toDebugString().lineSequence() }.joinToString("\n") { "  $it" }
    }

    /** `left operator right` */
    data class Binary(
        val operator: BinaryOperator,
        val left: PropertyExpression,
        val right: PropertyExpression,
    ) : PropertyExpression() {
        override fun toDebugString(): String = "$operator:\n" +
                sequenceOf(left,right).flatMap { it.toDebugString().lineSequence() }.joinToString("\n") { "  $it" }
    }

    /** `x, y`, `x, y, z` or `x, y, z, w` */
    data class Vector(val components: List<PropertyExpression>) : PropertyExpression() {
        init {
            require(components.size in 2..4) { "A vector has 2 to 4 components, not ${components.size}" }
        }
        override fun toDebugString(): String {
            val componentStrings = components.asSequence().map { it.toDebugString() }
            return if( components.size <= 4 && componentStrings.all { '\n' !in it && it.length < 10 })
                "Vec${components.size}(${componentStrings.joinToString(", ")})"
            else "Vec${components.size}:\n" +
                    components.asSequence().flatMap { it.toDebugString().lineSequence() }.joinToString("\n") { "  $it" }
        }
    }

    /**
     * `a .. b`, or a longer chain such as `a @ 0s ..linear.. b @ 1s .. c`: the [start] keyframe followed by one
     * [Segment] per range operator, each leading to the next keyframe.
     */
    data class Animation(val start: Keyframe, val segments: List<Segment>) : PropertyExpression() {
        init {
            require(segments.isNotEmpty()) { "An animation has at least one segment" }
        }

        /** Every keyframe in order: [start], then the [end][Segment.end] of each segment. */
        val keyframes: List<Keyframe> get() = listOf(start) + segments.map { it.end }

        override fun toDebugString(): String = "Animation:\n" +
                sequence {
                    yield(start.value.toDebugString())
                    start.time?.let { yield(" @" + it.toDebugString()) }
                    segments.forEach {
                        yield(it.easing?.toString()?.let { "..$it.." } ?: "..")
                        yield(it.end.value.toDebugString())
                        it.end.time?.let { yield(" @" + it.toDebugString()) }
                    }
                }.flatMap { it.lineSequence() }.joinToString("\n") { "  $it" }

    }

    /** One keyframe of an [Animation]: its [value] and, if `@` was used, its [time]. */
    data class Keyframe(val value: PropertyExpression, val time: PropertyExpression? = null)

    /**
     * One range operator of an [Animation] together with the keyframe it leads to. [easing] is `null` for the
     * plain `..` operator.
     */
    data class Segment(val easing: Easing?, val end: Keyframe)

    /** The easing written inside a range operator, as in `..easing..`. */
    sealed class Easing {

        /** The characters of the parsed input this easing was read from (without the surrounding dots). */
        var sourceRange: IntRange = IntRange.EMPTY
            internal set

        /** `..linear..` */
        data class Named(val name: String) : Easing()

        /** `..step(3)..` */
        data class FunctionCall(val name: String, val arguments: List<PropertyExpression>) : Easing()

        /** `..(x1, y1, x2, y2)..`: the two control points of a cubic Bezier curve from (0, 0) to (1, 1). */
        data class CubicBezier(
            val x1: PropertyExpression,
            val y1: PropertyExpression,
            val x2: PropertyExpression,
            val y2: PropertyExpression,
        ) : Easing()

        /**
         * `..{ t -> t*t }..`: an expression to evaluate for each frame. [parameters] is empty when the braces
         * hold only an expression, as in `..{ t*t }..`.
         */
        data class Lambda(val parameters: List<String>, val body: PropertyExpression) : Easing()
    }

    /** The units of a [DimensionLiteral]. [symbol] is how the unit is written; [points] is its size in points. */
    enum class DimensionUnit(val symbol: String, val points: Double) {
        POINT("pt", 1.0),
        CENTIMETER("cm", 72.0 / 2.54),
        MILLIMETER("mm", 72.0 / 25.4),
        INCH("in", 72.0),
    }

    enum class UnaryOperator(val symbol: String) {
        NEGATE("-"),
        UNARY_POSITIVE("+"),
        NOT("!"),
    }

    enum class BinaryOperator(val symbol: String) {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        AND("&&"),
        OR("||"),
        RATIO(":"),
        EQUAL("=="),
        NOT_EQUAL("!="),
        LESS_OR_EQUAL("<="),
        GREATER_OR_EQUAL(">="),
        LESS("<"),
        GREATER(">"),
    }

    companion object {
        fun of(value: Any?): PropertyExpression {
            return when (value) {
                is Int -> IntegerLiteral(value.toLong())
                is UInt -> IntegerLiteral(value.toLong())
                is Short -> IntegerLiteral(value.toLong())
                is UShort -> IntegerLiteral(value.toLong())
                is ULong -> IntegerLiteral(value.toLong())
                is Long -> IntegerLiteral(value)
                is String -> StringLiteral(value)
                is Boolean -> BooleanLiteral(value)
                is Color -> ColorLiteral(value.toArgb().toUInt())
                is Time -> TimeLiteral(value.takeOrElse { MediaTime.ZERO })
                is Float -> NumberLiteral(value.toDouble())
                is Double -> NumberLiteral(value)
                is Vec2 -> Vector(listOf(of(value.x), of(value.y)))
                is Vec3 -> Vector(listOf(of(value.x), of(value.y), of(value.z)))
                is Vec4 -> Vector(listOf(of(value.x), of(value.y), of(value.z), of(value.w)))
                is WeaveDimension.Points -> DimensionLiteral(value.value.toDouble(), DimensionUnit.POINT)
                is WeaveDimension.Millimeters -> DimensionLiteral(value.value, DimensionUnit.MILLIMETER)
                is FrameRate -> FrameRateLiteral(value)
                is FontWeight -> IntegerLiteral(value.weight.toLong())
                is FontStyle -> StringLiteral(value.value.toString())
                is Pair<*,*> if value.first is Int && value.second is Int -> RatioLiteral(value.first as Int, value.second as Int)
                else -> throw IllegalArgumentException("Cannot convert ${value?.let { it::class.simpleName}} to PropertyExpression")
            }
        }
    }
}

private val PropertyExpression.Keyframe.isLiteralSubexpression: Boolean get() = value.isLiteralSubexpression && (time == null || time.isLiteralSubexpression)
private val PropertyExpression.Segment.isLiteralSubexpression: Boolean get() = end.isLiteralSubexpression && (easing==null || easing.isLiteralSubexpression)
private val PropertyExpression.Easing.isLiteralSubexpression: Boolean get() = when(this) {
    is PropertyExpression.Easing.CubicBezier -> x1.isLiteralSubexpression && y1.isLiteralSubexpression && x2.isLiteralSubexpression && y2.isLiteralSubexpression
    is PropertyExpression.Easing.FunctionCall -> arguments.all { it.isLiteralSubexpression }
    is PropertyExpression.Easing.Lambda -> body.isLiteralSubexpression
    is PropertyExpression.Easing.Named -> true
}

val PropertyExpression.isLiteralSubexpression : Boolean get() = when {
    this is PropertyExpression.PropertyExpressionLiteral -> true
    this is PropertyExpression.Vector && this.components.all { it.isLiteralSubexpression } -> true
    this is PropertyExpression.Animation && this.start.isLiteralSubexpression && this.segments.all { it.isLiteralSubexpression } -> true
    else -> false
}

/**
 * The tokenizer and recursive-descent parser behind [parsePropertyExpression]. It reads the characters of [input]
 * from [start] up to [end]; the offsets in nodes and errors always count from the beginning of [input].
 *
 * With [isComponent] set, the text is a whitespace-separated component of a compound property string. Outside of
 * brackets, whitespace then separates where it would otherwise let two things join: `name (x)` is a name followed
 * by another component rather than a call, and so is the sign in `a -b`, while `a - b` and `a-b` stay subtractions.
 *
 * An instance is good for one of [parse], [parseLeading], [parseOperand], [parseLeadingOperand] and
 * [parseRangeOperator]. Parsers that read the same [input] up to the same [end] can share [groupEnds], so that
 * what one of them has found out about the brackets ahead need not be found out again by the next.
 */
internal class PropertyExpressionParser(
    private val input: String,
    start: Int = 0,
    private val end: Int = input.length,
    private val isComponent: Boolean = false,
    private val groupEnds: MutableMap<Int, Int> = HashMap(),
    private val enableColorOperators: Boolean = true
) {

    private enum class Kind {
        LITERAL, NAME,
        COLON, EQUAL_EQUAL, BANG_EQUAL, LESS_EQUAL, GREATER_EQUAL, LESS, GREATER,
        PLUS, MINUS, STAR, SLASH, AND_AND, OR_OR, BANG,
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
        COMMA, AT, DOT_DOT, ARROW,
        ERROR, END,
    }

    /**
     * The token at `input[start, end)`. A [Kind.LITERAL] token carries its finished node as [literal]; a
     * [Kind.ERROR] token stands for text that is not a token at all and carries the reason as [error].
     */
    private class Token(
        val kind: Kind,
        val start: Int,
        val end: Int,
        val literal: PropertyExpression? = null,
        val error: PropertyExpressionParseException? = null,
    )

    companion object {
        /**
         * How deep an expression may go. It limits both the parser's recursion and the depth of the tree that is
         * returned, which bounds the stack that the parser and a recursive consumer of the tree need, whatever
         * the input.
         */
        const val MAX_DEPTH = 100

        const val TOO_DEEP =
            "The expression is too complex (more than $MAX_DEPTH levels of nesting or chained operators)"

        private const val END_OF_INPUT = Char.MIN_VALUE
        private const val ZERO_WIDTH_NON_JOINER = 0x200C
        private const val ZERO_WIDTH_JOINER = 0x200D

        private val TIME_CONSTRUCTOR = mapOf<String,(Double) -> Time>(
            "ms" to {it.toLong().millis},
            "s" to {it.seconds},
            "m" to {(it*60).seconds},
            "min" to {(it*60).seconds},
            "h" to {(it*60*60).seconds},
        )

        private val FRAME_RATE_CONSTRUCTOR = mapOf<String,(Double) -> FrameRate>(
            "fps" to {it.toInt().fps},
        )

        private val DIMENSION_UNITS = PropertyExpression.DimensionUnit.entries.associateBy { it.symbol }

        const val PRECEDENCE_LOGICAL_OR = 1
        const val PRECEDENCE_LOGICAL_AND = 2
        const val PRECEDENCE_EQUALITY = 3
        const val PRECEDENCE_ARITHMETIC = 4
        const val PRECEDENCE_MULTIPLICATIVE = 5
        const val PRECEDENCE_RATIO = 6

        /** The binary operators and their precedence: a higher one binds tighter. All are left-associative. */
        private val BINARY_OPERATORS = mapOf(
            Kind.OR_OR to Pair(PropertyExpression.BinaryOperator.OR, PRECEDENCE_LOGICAL_OR),
            Kind.AND_AND to Pair(PropertyExpression.BinaryOperator.AND, PRECEDENCE_LOGICAL_AND),
            Kind.PLUS to Pair(PropertyExpression.BinaryOperator.ADD, PRECEDENCE_ARITHMETIC),
            Kind.MINUS to Pair(PropertyExpression.BinaryOperator.SUBTRACT, PRECEDENCE_ARITHMETIC),
            Kind.STAR to Pair(PropertyExpression.BinaryOperator.MULTIPLY, PRECEDENCE_MULTIPLICATIVE),
            Kind.SLASH to Pair(PropertyExpression.BinaryOperator.DIVIDE, PRECEDENCE_MULTIPLICATIVE),
            Kind.EQUAL_EQUAL to Pair(PropertyExpression.BinaryOperator.EQUAL, PRECEDENCE_EQUALITY),
            Kind.BANG_EQUAL to Pair(PropertyExpression.BinaryOperator.NOT_EQUAL, PRECEDENCE_EQUALITY),
            Kind.LESS_EQUAL to Pair(PropertyExpression.BinaryOperator.LESS_OR_EQUAL, PRECEDENCE_EQUALITY),
            Kind.GREATER_EQUAL to Pair(PropertyExpression.BinaryOperator.GREATER_OR_EQUAL, PRECEDENCE_EQUALITY),
            Kind.LESS to Pair(PropertyExpression.BinaryOperator.LESS, PRECEDENCE_EQUALITY),
            Kind.GREATER to Pair(PropertyExpression.BinaryOperator.GREATER, PRECEDENCE_EQUALITY),
            Kind.COLON to Pair(PropertyExpression.BinaryOperator.RATIO, PRECEDENCE_RATIO),
        )

        private fun fail(reason: String, start: Int, end: Int): Nothing =
            throw PropertyExpressionParseException(reason, start until end)
    }

    // ---------------------------------------------------------------------------------------------------
    // Tokenizer
    // ---------------------------------------------------------------------------------------------------

    private class Lexer(private val input: String, start: Int, private val end: Int, private val enableColorOperators: Boolean) {
        private var position = start
        private var isFinished = false

        /**
         * The next token. When the text is used up it is a [Kind.END] token, however often it is asked for.
         *
         * Text that cannot be tokenized makes a [Kind.ERROR] token instead of failing right away, and only
         * [Kind.END] tokens after that. The parser reports the error when it gets there (or when it would have to
         * guess what that text was meant to be), so that a problem earlier in the input is normally the one that
         * gets reported.
         */
        fun next(): Token {
            if (!isFinished) {
                while (peek().isWhitespace()) position++
                if (position < end) {
                    try {
                        return nextToken()
                    } catch (e: PropertyExpressionParseException) {
                        isFinished = true
                        return Token(Kind.ERROR, e.range.first, e.range.last + 1, error = e)
                    }
                }
                isFinished = true
            }
            return Token(Kind.END, end, end)
        }

        private fun nextToken(): Token {
            val c = peek()
            val next = peek(1)
            return when {
                c.isAsciiDigit() || (c == '.' && next.isAsciiDigit()) -> number()
                c.isNameStart() -> nameOrKeyword()
                c == '"' || c == '\'' -> string()
                c == '#' -> color()
                c == '{' -> semanticTag() ?: symbol(Kind.LEFT_BRACE, 1)
                c == '}' -> symbol(Kind.RIGHT_BRACE, 1)
                c == '(' -> symbol(Kind.LEFT_PAREN, 1)
                c == ')' -> symbol(Kind.RIGHT_PAREN, 1)
                c == ',' -> symbol(Kind.COMMA, 1)
                c == '@' -> symbol(Kind.AT, 1)
                c == '+' -> symbol(Kind.PLUS, 1)
                c == '*' -> symbol(Kind.STAR, 1)
                c == '/' -> symbol(Kind.SLASH, 1)
                c == '!' && next == '=' -> symbol(Kind.BANG_EQUAL, 2)
                c == '=' && next == '=' -> symbol(Kind.EQUAL_EQUAL, 2)
                c == '<' && next == '=' -> symbol(Kind.LESS_EQUAL, 2)
                c == '>' && next == '=' -> symbol(Kind.GREATER_EQUAL, 2)
                c == '<' -> symbol(Kind.LESS, 1)
                c == '>' -> symbol(Kind.GREATER, 1)
                c == ':' -> symbol(Kind.COLON, 1)
                c == '!' -> symbol(Kind.BANG, 1)
                c == '-' -> if (next == '>') symbol(Kind.ARROW, 2) else symbol(Kind.MINUS, 1)
                c == '.' && next == '.' -> range()
                c == '&' && next == '&' -> symbol(Kind.AND_AND, 2)
                c == '|' && next == '|' -> symbol(Kind.OR_OR, 2)
                c == '&' || c == '|' ->
                    fail("Unexpected '$c' (the logical operators are '&&' and '||')", position, position + 1)
                c == '%' ->
                    fail("Unexpected '%' (a percentage is a number directly followed by '%')", position, position + 1)
                else -> fail(
                    "Unexpected character ${describeCharacter(position)}",
                    position,
                    position + characterLength(position),
                )
            }
        }

        /** `..`. A third dot is refused, because `1...5` would otherwise quietly mean `1 .. .5`. */
        private fun range(): Token {
            if (peek(2) == '.') fail("Unexpected '...' (a range is written with two dots)", position, position + 3)
            return symbol(Kind.DOT_DOT, 2)
        }

        /** An integer, number, time, dimension or percentage literal. A sign is a separate unary operator. */
        private fun number(): Token {
            val start = position
            if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) return hexInteger()

            while (peek().isAsciiDigit()) position++
            var isInteger = true
            // A dot only belongs to the number when a digit follows, which keeps `1..2` a range from 1 to 2.
            if (peek() == '.' && peek(1).isAsciiDigit()) {
                isInteger = false
                position++
                while (peek().isAsciiDigit()) position++
            }
            // Likewise an `e` is only an exponent when digits follow.
            if (peek() == 'e' || peek() == 'E') {
                val signLength = if (peek(1) == '+' || peek(1) == '-') 1 else 0
                if (peek(1 + signLength).isAsciiDigit()) {
                    isInteger = false
                    position += 1 + signLength
                    while (peek().isAsciiDigit()) position++
                }
            }
            val digits = input.substring(start, position)

            if (peek() == '%') {
                position++
                return literal(PropertyExpression.PercentageLiteral(toDouble(digits, start)), start)
            }
            if (peek().isNameStart()) {
                while (peek().isNamePart()) position++
                val suffix = input.substring(start + digits.length, position)
                val node = TIME_CONSTRUCTOR[suffix]?.let { PropertyExpression.TimeLiteral(it(toDouble(digits, start))) }
                    ?: DIMENSION_UNITS[suffix]?.let { PropertyExpression.DimensionLiteral(toDouble(digits, start), it) }
                    ?: FRAME_RATE_CONSTRUCTOR[suffix]?.let { PropertyExpression.FrameRateLiteral(it(toDouble(digits, start))) }
                    ?: fail(
                        "Invalid number '${input.substring(start, position)}' (the digits can only be followed " +
                            "directly by '%', a time unit (${TIME_CONSTRUCTOR.keys.joinToString()}) " +
                            "or a dimension unit (${DIMENSION_UNITS.keys.joinToString()}))",
                        start,
                        position,
                    )
                return literal(node, start)
            }
            if (isInteger) {
                val value = digits.toLongOrNull() ?: fail("Integer literal is out of range", start, position)
                return literal(PropertyExpression.IntegerLiteral(value), start)
            }
            return literal(PropertyExpression.NumberLiteral(toDouble(digits, start)), start)
        }

        private fun toDouble(digits: String, start: Int): Double {
            // The digits are already validated. A zero goes before a leading dot so that every platform accepts them.
            val value = (if (digits.startsWith('.')) "0$digits" else digits).toDoubleOrNull()
            if (value == null || value.isInfinite()) {
                fail("Number literal is out of range", start, start + digits.length)
            }
            return value
        }

        private fun hexInteger(): Token {
            val start = position
            position += 2
            while (peek().isNamePart()) position++
            val digits = input.substring(start + 2, position)
            if (digits.isEmpty() || !digits.all { it.isHexDigit() }) {
                fail("Invalid hexadecimal literal '${input.substring(start, position)}'", start, position)
            }
            val value = digits.toLongOrNull(16) ?: fail("Integer literal is out of range", start, position)
            return literal(PropertyExpression.IntegerLiteral(value), start)
        }

        private fun nameOrKeyword(): Token {
            val start = position
            position = endOfName(start)
            return when (input.substring(start, position)) {
                "true" -> literal(PropertyExpression.BooleanLiteral(true), start)
                "false" -> literal(PropertyExpression.BooleanLiteral(false), start)
                else -> Token(Kind.NAME, start, position)
            }
        }

        private fun string(): Token {
            val start = position
            val quote = input[position++]
            val value = StringBuilder()
            while (true) {
                val c = peek()
                when {
                    position >= end || c == '\n' || c == '\r' ->
                        fail("Unterminated string literal", start, position)
                    c == quote -> {
                        position++
                        return literal(PropertyExpression.StringLiteral(value.toString()), start)
                    }
                    c == '\\' -> value.append(escapeSequence(start))
                    else -> {
                        value.append(c)
                        position++
                    }
                }
            }
        }

        private fun escapeSequence(stringStart: Int): Char {
            val start = position
            if (start + 1 >= end) fail("Unterminated string literal", stringStart, end)
            val c = input[start + 1]
            position += 2
            return when (c) {
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'b' -> '\b'
                '\\', '"', '\'' -> c
                'u' -> {
                    var code = 0
                    repeat(4) {
                        val digit = peek().takeIf { it.isHexDigit() }?.digitToInt(16)
                            ?: fail("Invalid escape (expected \\u and four hexadecimal digits)", start, position)
                        code = code * 16 + digit
                        position++
                    }
                    code.toChar()
                }
                else -> {
                    val end = start + 1 + characterLength(start + 1)
                    // Shown as written, unless what follows the backslash would not be visible
                    val escaped = start + 1
                    val shown =
                        if (isVisible(escaped)) "'${input.substring(start, end)}'" else describeCharacter(escaped)
                    fail("Unknown escape sequence $shown", start, end)
                }
            }
        }

        private fun color(): Token {
            val start = position++
            while (peek().isNamePart()) position++
            val digits = input.substring(start + 1, position)
            val isHex = digits.all { it.isHexDigit() }
            if (!isHex || (digits.length != 6 && digits.length != 8)) {
                val hint = if (!isHex && digits[0].isNameStart()) "; a semantic tag is written {#$digits}" else ""
                fail("Invalid color '#$digits' (expected #RRGGBB or #AARRGGBB$hint)", start, position)
            }
            val value = digits.toUInt(16)
            val argb = if (digits.length == 6) value or 0xFF000000u else value
            return literal(PropertyExpression.ColorLiteral(argb), start)
        }

        /**
         * `{#name}`, optionally padded with whitespace inside the braces. Returns null if the brace opens something
         * else, which can only be an easing expression, possibly one that begins with a color: `{ #ff0000 ... }`.
         */
        private fun semanticTag(): Token? {
            val start = position
            var index = start + 1
            while (charAt(index).isWhitespace()) index++
            if (charAt(index) != '#') return null
            val nameStart = index + 1
            val nameEnd = endOfName(nameStart)
            val name = input.substring(nameStart, nameEnd)
            index = nameEnd
            while (charAt(index).isWhitespace()) index++
            val isClosed = charAt(index) == '}'
            if (isClosed && name.isNotEmpty() && name[0].isNameStart()) {
                position = index + 1
                return literal(PropertyExpression.SemanticTag(name), start)
            }
            // Not a tag. The brace may still open an easing expression that starts with a color, as in
            // { #ff0000 * t }. That is assumed for a well-formed color, and for other hex digits when what follows
            // them looks like the rest of an expression rather than like more of a name.
            val isHex = name.isNotEmpty() && name.all { it.isHexDigit() }
            val isColor = isHex && (name.length == 6 || name.length == 8)
            val next = charAt(index)
            val isExpressionAhead = !isClosed && next != END_OF_INPUT && !next.isNameStart()
            if (isColor || (isHex && isExpressionAhead)) return null
            val colorRule = if (isHex) "; a color is #RRGGBB or #AARRGGBB" else ""
            fail(
                "Invalid semantic tag (a tag is a name in braces after a '#', as in {#name}$colorRule)",
                start,
                nameEnd,
            )
        }

        private fun symbol(kind: Kind, length: Int): Token {
            val start = position
            position += length
            return Token(kind, start, position)
        }

        private fun literal(node: PropertyExpression, start: Int): Token {
            node.sourceRange = start until position
            return Token(Kind.LITERAL, start, position, node)
        }

        private fun charAt(index: Int): Char = if (index < end) input[index] else END_OF_INPUT

        private fun peek(offset: Int = 0): Char = charAt(position + offset)

        /** 2 if a surrogate pair (a character outside the basic multilingual plane) starts at [index], else 1. */
        private fun characterLength(index: Int): Int =
            if (input[index].isHighSurrogate() && charAt(index + 1).isLowSurrogate()) 2 else 1

        /** Whether the character at [index] shows when printed on its own, unlike a control code or a lone accent. */
        private fun isVisible(index: Int): Boolean = when (input[index].category) {
            CharCategory.CONTROL, CharCategory.FORMAT, CharCategory.PRIVATE_USE, CharCategory.UNASSIGNED,
            CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK,
            CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR,
            -> false
            CharCategory.SURROGATE -> characterLength(index) == 2
            else -> true
        }

        /** The character at [index] as an error message shows it: quoted, or as U+XXXX if it would not be visible. */
        private fun describeCharacter(index: Int): String =
            if (isVisible(index)) {
                "'" + input.substring(index, index + characterLength(index)) + "'"
            } else {
                "U+" + input[index].code.toString(16).uppercase().padStart(4, '0')
            }

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

        private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        // Names are not limited to ASCII. Letters and digits are whatever the platform's Unicode tables say they are,
        // and after the first character a name may also contain what many scripts need inside a word: combining marks
        // (Thai, Devanagari, decomposed accents) and, between two other characters of the name, the zero-width joiner
        // and non-joiner (Persian, Indic scripts).
        private fun Char.isNameStart(): Boolean = this == '_' || isLetter()

        private fun Char.isNamePart(): Boolean = this == '_' || isLetterOrDigit() ||
            category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

        /** The index just past the name that starts at [start]. */
        private fun endOfName(start: Int): Int {
            var index = start
            while (true) {
                val c = charAt(index)
                val isJoiner = c.code == ZERO_WIDTH_NON_JOINER || c.code == ZERO_WIDTH_JOINER
                // An invisible joiner at the end of a name would make it a different name that looks the same
                if (!c.isNamePart() && !(isJoiner && charAt(index + 1).isNamePart())) return index
                index++
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Parser
    // ---------------------------------------------------------------------------------------------------

    /** The tokens of the text, read from the tokenizer only as far as the parser has looked. */
    private class Tokens(private val lexer: Lexer) {
        private val read = ArrayList<Token>()

        /** Where the text stops being made of tokens, if the tokens read so far reach that place; -1 if not. */
        var unreadableFrom = -1
            private set

        operator fun get(index: Int): Token {
            while (read.size <= index) {
                val token = lexer.next()
                if (token.kind == Kind.ERROR) unreadableFrom = token.start
                read += token
            }
            return read[index]
        }

        /** Whether one of the tokens read so far starts at [offset]. */
        fun hasTokenAt(offset: Int): Boolean {
            var low = 0
            var high = read.size - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val start = read[middle].start
                when {
                    start < offset -> low = middle + 1
                    start > offset -> high = middle - 1
                    else -> return true
                }
            }
            return false
        }

        /** How far into the text the tokens read so far reach. */
        val readEnd: Int get() = if (read.isEmpty()) -1 else read[read.size - 1].end
    }

    private val tokens = Tokens(Lexer(input, start, end, enableColorOperators))
    private var index = 0

    /** How many parentheses, calls, unary operators and easing expressions the parser is currently inside of. */
    private var depth = 0

    /** How many of those are brackets: inside of them, whitespace cannot separate the components of a compound. */
    private var brackets = 0

    /**
     * The first `..` of the animation that [parseAnimation] returned last, or null if it returned a plain operand.
     * It tells [parseExpression] whether an item of a comma-separated list is a range without its own parentheses.
     */
    private var bareRange: Token? = null

    private val current: Token get() = tokens[index]
    private val previous: Token get() = tokens[index - 1]

    /** Parses all of the text as one expression. */
    fun parse(): PropertyExpression {
        val expression = parseExpression()
        if (current.kind != Kind.END) fail("Unexpected ${describe(current)}", current)
        checkDepth(expression)
        return expression
    }

    /**
     * Parses the longest expression that the text starts with and leaves what follows it alone; [consumedEnd] then
     * tells where the expression ended.
     */
    fun parseLeading(): PropertyExpression = parseExpression().also { checkDepth(it) }

    /** Parses all of the text as what may follow an `@`: an expression that is not itself a vector or a range. */
    fun parseOperand(): PropertyExpression {
        val operand = parseBinary()
        if (current.kind != Kind.END) fail("Unexpected ${describe(current)}", current)
        checkDepth(operand)
        return operand
    }

    /**
     * Parses the longest operand, as [parseOperand] understands it, that the text starts with and leaves what
     * follows it alone; [consumedEnd] then tells where the operand ended.
     */
    fun parseLeadingOperand(): PropertyExpression = parseBinary().also { checkDepth(it) }

    /**
     * Parses the `..` or `..easing..` that the text starts with and returns the easing, if it has one;
     * [consumedEnd] then tells where the operator ended.
     */
    fun parseRangeOperator(): PropertyExpression.Easing? {
        if (current.kind != Kind.DOT_DOT) fail("Expected '..' but found ${describe(current)}", current)
        if (!isAdjacent(1)) {
            // A plain range. What comes after the whitespace is not looked at: it need not be an expression
            index++
            return null
        }
        return parseRange()?.also { easing -> easing.expressions().forEach { checkDepth(it) } }
    }

    /**
     * For text that starts with `..` but need not be an expression: the offset just past `..easing..` if all of
     * that is there, in outline at least, and -1 if the dots do not come with an easing. Nothing is consumed.
     */
    fun easedRangeEnd(): Int {
        if (current.kind != Kind.DOT_DOT) fail("Expected '..' but found ${describe(current)}", current)
        if (!isAdjacent(1)) return -1
        var i = 1
        when (tokens[i].kind) {
            Kind.NAME -> {
                i++
                val isCall = tokens[i].kind == Kind.LEFT_PAREN && isAdjacent(i)
                if (!isCall) return if (tokens[i].kind == Kind.DOT_DOT && isAdjacent(i)) tokens[i].end else -1
            }
            Kind.LEFT_PAREN, Kind.LEFT_BRACE -> {}
            else -> return -1
        }
        val groupEnd = groupEnd(i)
        if (groupEnd < 0) return -1
        val next = Lexer(input, groupEnd, end, enableColorOperators).next()
        return if (next.kind == Kind.DOT_DOT && next.start == groupEnd) next.end else -1
    }

    /** The offset just past the last token that has been consumed, or where the text starts if none has been. */
    val consumedEnd: Int get() = if (index == 0) tokens[0].start else previous.end

    /** Whether a token starts at [offset], among those that have been read so far. */
    fun hasTokenAt(offset: Int): Boolean = tokens.hasTokenAt(offset)

    /** How far into the text this parser has looked, which is at least as far as it has consumed. */
    val readEnd: Int get() = maxOf(tokens.readEnd, tokens[0].start)

    /** Why the text that follows what has been consumed cannot be read, if that is the case. */
    val unreadableNext: PropertyExpressionParseException? get() = current.error

    /**
     * Where the text stops being readable, if this parser has looked that far, and -1 if it has not. Nothing that
     * starts before that place can be read beyond it.
     */
    val unreadableFrom: Int get() = tokens.unreadableFrom

    /** Whether the text starts with something that an expression can start with. */
    fun startsExpression(): Boolean = when (tokens[0].kind) {
        Kind.LITERAL, Kind.NAME, Kind.MINUS, Kind.BANG, Kind.LEFT_PAREN -> true
        else -> false
    }

    /** The literal that the text starts with, looking past any opening parentheses and signs in front of it. */
    fun leadingLiteral(): PropertyExpression? = tokens[leadingIndex()].literal

    /** Why the text cannot be read at the place where [leadingLiteral] looks, if that is the case. */
    fun leadingError(): PropertyExpressionParseException? = tokens[leadingIndex()].error

    private fun leadingIndex(): Int {
        var i = 0
        while (tokens[i].kind == Kind.LEFT_PAREN || tokens[i].kind == Kind.MINUS) i++
        return i
    }

    /** `animation | operand ("," operand)+` */
    private fun parseExpression(): PropertyExpression {
        val start = current.start
        val first = parseAnimation()
        if (current.kind != Kind.COMMA) return first

        failOnBareRange()
        val components = buildList<PropertyExpression> {
            add(first)
            while (match(Kind.COMMA)) {
                val componentStart = current.start
                add(parseAnimation())
                failOnBareRange()
                if (size > 4) fail("A vector can have at most 4 components", componentStart, previous.end)
            }
        }
        return PropertyExpression.Vector(components).at(start, previous.end)
    }

    /**
     * Refuses a range that sits among the commas of a vector without parentheses of its own, as in `a, b .. c, d`:
     * that could just as well be a range between two vectors as a vector that contains a range.
     */
    private fun failOnBareRange() {
        val range = bareRange ?: return
        fail(
            "',' and '..' cannot be mixed without parentheses: write (a, b) .. (c, d) for a range between vectors, " +
                "or a, (b .. c), d for a range inside a vector",
            range,
        )
    }

    /** `operand | keyframe (range keyframe)+` */
    private fun parseAnimation(): PropertyExpression {
        val start = current.start
        val value = parseBinary()
        if (current.kind != Kind.AT && current.kind != Kind.DOT_DOT) {
            bareRange = null
            return value
        }

        val timing = current
        val first = parseKeyframe(value)
        if (current.kind != Kind.DOT_DOT) {
            // Unreadable text here is its own problem (it may well be a mistyped '..'), not the fault of the '@'
            current.error?.let { throw it }
            // Among the components of a compound, the time may have ended at whitespace that it was meant to contain
            val isCutShort = isComponentBreak() && (current.kind == Kind.LEFT_PAREN || current.kind == Kind.MINUS)
            val hint = if (isCutShort) " (the time ends at the whitespace before ${describe(current)} here)" else ""
            fail("'@' gives a keyframe its time, so it is only allowed next to a '..' range$hint", timing)
        }
        val firstRange = current
        val segments = buildList {
            while (current.kind == Kind.DOT_DOT) {
                val easing = parseRange()
                add(PropertyExpression.Segment(easing, parseKeyframe(parseBinary())))
            }
        }
        bareRange = firstRange
        return PropertyExpression.Animation(first, segments).at(start, previous.end)
    }

    /** Completes the keyframe whose [value] has just been parsed: `("@" operand)?` */
    private fun parseKeyframe(value: PropertyExpression): PropertyExpression.Keyframe {
        if (!match(Kind.AT)) return PropertyExpression.Keyframe(value)
        val time = parseBinary()
        if (current.kind == Kind.AT) fail("A keyframe can only have one '@' time", current)
        return PropertyExpression.Keyframe(value, time)
    }

    /**
     * Consumes `..` or `..easing..` and returns the easing, if there is one. A range is eased when the dots, an
     * easing and more dots follow each other without any whitespace in between.
     */
    private fun parseRange(): PropertyExpression.Easing? {
        index++
        if (!isAdjacent(index)) {
            if (current.kind == Kind.LEFT_BRACE) {
                fail(
                    "Unexpected '{' (a semantic tag is written {#name}, and an easing has to follow its '..' " +
                        "directly, as in ..{ t -> t*t }..)",
                    current,
                )
            }
            return null
        }
        // A brace cannot start a keyframe, so right after the dots it has to be an easing. Committing to that
        // reading lets a mistake inside the braces be reported for what it is.
        if (current.kind != Kind.LEFT_BRACE && !isEasingAhead()) return null

        val easing = parseEasing()
        if (current.kind == Kind.DOT_DOT && !isAdjacent(index)) {
            fail("An eased range cannot contain whitespace: remove it before the '..'", previous.end, current.start)
        }
        if (!match(Kind.DOT_DOT)) {
            // `{name}` on its own is more likely a semantic tag that lost its '#' than an easing
            val isBareBraces = easing is PropertyExpression.Easing.Lambda && easing.parameters.isEmpty()
            val hint = if (isBareBraces) " (a semantic tag is written {#name})" else ""
            fail("Expected '..' directly after the easing but found ${describe(current)}$hint", current)
        }
        return easing
    }

    /**
     * Whether the current token starts a name, a call or a parenthesized group that is directly followed by `..`,
     * all of it without whitespace: the outline of the easing in an eased range.
     */
    private fun isEasingAhead(): Boolean {
        var i = index
        when (tokens[i].kind) {
            Kind.NAME -> i++
            Kind.LEFT_PAREN -> {}
            else -> return false
        }
        val next = if (tokens[i].kind == Kind.LEFT_PAREN && (i == index || isAdjacent(i))) {
            val groupEnd = groupEnd(i)
            // A group without an end cannot be told apart. Reading it as the arguments of an easing is the lenient
            // choice: the parser then reports whatever is wrong in the order in which it appears.
            if (groupEnd < 0) return true
            Lexer(input, groupEnd, end, enableColorOperators).next().also { if (it.start != groupEnd) return false }
        } else {
            if (!isAdjacent(i)) return false
            tokens[i]
        }
        // Unreadable text right where the closing dots would be: nothing can be said about the range before it
        next.error?.let { throw it }
        return next.kind == Kind.DOT_DOT
    }

    /**
     * Returns the offset just past the bracket that closes the group opened by token [open], or -1 if the tokens
     * end first: the group is never closed, or unreadable text hides how it continues. Looking this up for one
     * group settles it for the groups inside it as well, which [groupEnds] remembers. That keeps the cost of
     * looking ahead in proportion to the text, however many ranges with groups behind them there are.
     */
    private fun groupEnd(open: Int): Int {
        groupEnds[tokens[open].start]?.let { return it }
        val opened = ArrayList<Int>()
        var i = open
        while (true) {
            val token = tokens[i]
            when (token.kind) {
                Kind.LEFT_PAREN, Kind.LEFT_BRACE -> opened += token.start
                Kind.RIGHT_PAREN, Kind.RIGHT_BRACE -> {
                    groupEnds[opened.removeAt(opened.lastIndex)] = token.end
                    if (opened.isEmpty()) return token.end
                }
                Kind.END -> {
                    opened.forEach { groupEnds[it] = -1 }
                    return -1
                }
                else -> {}
            }
            i++
        }
    }

    /** Whether no whitespace separates token [i] from the token before it. */
    private fun isAdjacent(i: Int): Boolean = tokens[i].start == tokens[i - 1].end

    /**
     * Whether the whitespace before the current token is the kind that separates the components of a compound
     * property, which it is outside of brackets.
     */
    private fun isComponentBreak(): Boolean = isComponent && brackets == 0 && !isAdjacent(index)

    /** The easing between the dots of an eased range. */
    private fun parseEasing(): PropertyExpression.Easing {
        val token = current
        val easing = when (token.kind) {
            Kind.NAME -> {
                index++
                val name = text(token)
                if (current.kind == Kind.LEFT_PAREN) {
                    PropertyExpression.Easing.FunctionCall(name, parseArguments())
                } else {
                    PropertyExpression.Easing.Named(name)
                }
            }
            Kind.LEFT_PAREN -> {
                val values = parseArguments()
                if (values.size != 4) {
                    fail(
                        "A cubic Bezier easing takes 4 values, not ${values.size} " +
                            "(if this is meant to be a keyframe, put spaces around the '..' operators)",
                        token.start,
                        previous.end,
                    )
                }
                PropertyExpression.Easing.CubicBezier(values[0], values[1], values[2], values[3])
            }
            else -> parseEasingLambda()
        }
        return easing.at(token.start, previous.end)
    }

    private fun Token.asColorLiteral() : PropertyExpression.ColorLiteral? {
        if( kind == Kind.LITERAL && literal is PropertyExpression.ColorLiteral) return literal
        if( kind == Kind.NAME && enableColorOperators ) {
            val name = text(this)
            if( name in weaveColorNames) {
                val color = weaveColorNames[name]
                if( color != null ) {
                    return PropertyExpression.ColorLiteral(color.toArgb().toUInt())
                }
            }
        }
        return null
    }

    /** `"{" (NAME ("," NAME)* "->")? expression "}"` */
    private fun parseEasingLambda(): PropertyExpression.Easing {
        val open = current
        index++
        // The braces begin with parameters only if a list of names is followed by an arrow
        var arrow = index
        while (tokens[arrow].kind == Kind.NAME && tokens[arrow + 1].kind == Kind.COMMA) arrow += 2
        val hasParameters = tokens[arrow].kind == Kind.NAME && tokens[arrow + 1].kind == Kind.ARROW
        val parameters = buildList {
            val seen = HashSet<String>()
            while (hasParameters && index <= arrow) {
                val name = text(current)
                if (!seen.add(name)) fail("Duplicate parameter '$name'", current)
                add(name)
                index += 2
            }
        }
        val body = nested(open) { parseExpression() }
        expect(Kind.RIGHT_BRACE, "'}'")
        return PropertyExpression.Easing.Lambda(parameters, body)
    }

    /**
     * `or`, `and`, `sum` and `product` in one function: `unary (operator unary)*` for the operators of
     * [BINARY_OPERATORS] that bind at least as tightly as [minimumPrecedence].
     */
    private fun parseBinary(minimumPrecedence: Int = 0): PropertyExpression {
        val start = current.start
        var left = parseUnary()
        while (true) {
            val (operator, precedence) = BINARY_OPERATORS[current.kind] ?: return left
            if (precedence < minimumPrecedence) return left
            // Between components, `a -b` is `a` and then a signed `b`, unlike `a - b` and `a-b`. A minus that
            // nothing follows is not a subtraction either, with or without whitespace at the end of the text.
            val isSign = isAdjacent(index + 1) || tokens[index + 1].kind == Kind.END
            if (current.kind == Kind.MINUS && isComponentBreak() && isSign) return left
            if (current.kind == Kind.PLUS && isComponentBreak() && isSign) return left
            index++
            // Only tighter operators may join the right operand, which makes this one left-associative
            val right = parseBinary(precedence + 1)
            left = PropertyExpression.Binary(operator, left, right).at(start, previous.end)
        }
    }

    private fun parseColorLiteral() : PropertyExpression? {
        val token = current
        token.asColorLiteral()?.let {
            index++
            return it
        }
        val next = tokens[index + 1]
        val nextColorLiteral = next.asColorLiteral()
        if( nextColorLiteral != null ) {
            val color = Color(nextColorLiteral.argb.toInt())
            if( token.kind==Kind.LITERAL && token.literal is PropertyExpression.PercentageLiteral) {
                index += 2
                val newColor = color.copy(alpha = (color.alpha * token.literal.fraction.toFloat()).coerceIn(0f,1f))
                return PropertyExpression.ColorLiteral(newColor.toArgb().toUInt())
            } else if( token.kind==Kind.NAME ) {
                val name = text(token)
                when(name) {
                    "medium" -> {
                        index += 2
                        return PropertyExpression.ColorLiteral(color.mediumVariant().toArgb().toUInt())
                    }
                    "dark" -> {
                        index += 2
                        return PropertyExpression.ColorLiteral(color.darkVariant().toArgb().toUInt())
                    }
                    "light" -> {
                        index += 2
                        return PropertyExpression.ColorLiteral(color.lightVariant().toArgb().toUInt())
                    }
                }
            }
        }
        return null
    }

    /** `("-" | "!") unary | primary` */
    private fun parseUnary(): PropertyExpression {
        val token = current
        if( enableColorOperators ) {
            parseColorLiteral()?.let { return it }
        }
        val operator = when (token.kind) {
            Kind.MINUS -> PropertyExpression.UnaryOperator.NEGATE
            Kind.PLUS -> PropertyExpression.UnaryOperator.UNARY_POSITIVE
            Kind.BANG -> PropertyExpression.UnaryOperator.NOT
            else -> return parsePrimary()
        }
        index++
        val operand = nested(token, isBracket = false) { parseUnary() }
        return PropertyExpression.Unary(operator, operand).at(token.start, previous.end)
    }

    /** `literal | NAME | NAME "(" arguments? ")" | "(" expression ")"` */
    private fun parsePrimary(): PropertyExpression {
        val token = current
        when (token.kind) {
            Kind.LITERAL -> {
                index++
                return token.literal!!
            }
            Kind.NAME -> {
                index++
                val name = text(token)
                // A call has its parenthesis right after the name where whitespace separates components
                val isCall = current.kind == Kind.LEFT_PAREN && !isComponentBreak()
                if (!isCall) return PropertyExpression.Variable(name).at(token.start, token.end)
                return PropertyExpression.FunctionCall(name, parseArguments()).at(token.start, previous.end)
            }
            Kind.LEFT_PAREN -> {
                index++
                val inner = nested(token) { parseExpression() }
                expect(Kind.RIGHT_PAREN, "')'")
                return inner
            }
            Kind.END -> fail(
                if (index == 0) "The expression is empty" else "Expected a value after ${describe(previous)}",
                token,
            )
            Kind.LEFT_BRACE -> fail(
                "Unexpected '{' (braces are for semantic tags like {#name} and easings like ..{ t -> t*t }..)",
                token,
            )
            else -> fail("Expected a value but found ${describe(token)}", token)
        }
    }

    /** `"(" (animation ("," animation)*)? ")"`, for the arguments of a call and the values of a Bezier easing. */
    private fun parseArguments(): List<PropertyExpression> {
        val open = current
        index++
        val arguments = buildList {
            if (current.kind != Kind.RIGHT_PAREN) {
                nested(open) {
                    do add(parseAnimation()) while (match(Kind.COMMA))
                }
            }
        }
        expect(Kind.RIGHT_PAREN, "',' or ')'")
        return arguments
    }

    /** Runs [parse] one nesting level down; [opener] is the token that opens the level. */
    private inline fun <T> nested(opener: Token, isBracket: Boolean = true, parse: () -> T): T {
        if (++depth > MAX_DEPTH) fail(TOO_DEEP, opener)
        if (isBracket) brackets++
        return parse().also {
            depth--
            if (isBracket) brackets--
        }
    }

    /**
     * Fails if a node of [root] has more than [MAX_DEPTH] ancestors. Nesting was already limited during parsing;
     * what this adds are long chains such as `a + a + a + ...`, which grow a deep tree without any recursion in
     * the parser. It walks the tree level by level rather than recursively, for the obvious reason.
     */
    fun checkDepth(root: PropertyExpression) {
        var level = listOf(root)
        repeat(MAX_DEPTH + 1) {
            level = level.flatMap { it.children() }
            if (level.isEmpty()) return
        }
        val tooDeep = level.first().sourceRange
        fail(TOO_DEEP, tooDeep.first, tooDeep.last + 1)
    }

    /** The expressions directly below this node, including those inside the easings of an animation. */
    private fun PropertyExpression.children(): List<PropertyExpression> = when (this) {
        is PropertyExpression.FunctionCall -> arguments
        is PropertyExpression.Unary -> listOf(operand)
        is PropertyExpression.Binary -> listOf(left, right)
        is PropertyExpression.Vector -> components
        is PropertyExpression.Animation -> buildList {
            for (keyframe in keyframes) {
                add(keyframe.value)
                keyframe.time?.let { add(it) }
            }
            for (segment in segments) addAll(segment.easing.expressions())
        }
        else -> emptyList()
    }

    /** The expressions that this easing is made of. */
    private fun PropertyExpression.Easing?.expressions(): List<PropertyExpression> = when (this) {
        is PropertyExpression.Easing.FunctionCall -> arguments
        is PropertyExpression.Easing.CubicBezier -> listOf(x1, y1, x2, y2)
        is PropertyExpression.Easing.Lambda -> listOf(body)
        is PropertyExpression.Easing.Named, null -> emptyList()
    }

    private fun match(kind: Kind): Boolean {
        if (current.kind != kind) return false
        index++
        return true
    }

    private fun expect(kind: Kind, description: String) {
        if (!match(kind)) fail("Expected $description but found ${describe(current)}", current)
    }

    private fun text(token: Token): String = input.substring(token.start, token.end)

    private fun describe(token: Token): String =
        if (token.kind == Kind.END) "the end of the expression" else "'${text(token)}'"

    /** Fails at [token]; if the tokenizer already found that token to be malformed, its error takes precedence. */
    private fun fail(reason: String, token: Token): Nothing = throw token.error
        ?: PropertyExpressionParseException(reason, token.start until token.end)

    private fun <T : PropertyExpression> T.at(start: Int, end: Int): T = apply { sourceRange = start until end }

    private fun <T : PropertyExpression.Easing> T.at(start: Int, end: Int): T =
        apply { sourceRange = start until end }
}

val DurationUnit.abbr: String get() = when (this) {
    DurationUnit.NANOSECONDS -> "ns"
    DurationUnit.MICROSECONDS -> "µs"
    DurationUnit.MILLISECONDS -> "ms"
    DurationUnit.SECONDS -> "s"
    DurationUnit.MINUTES -> "m"
    DurationUnit.HOURS -> "h"
    DurationUnit.DAYS -> "d"
    else -> throw IllegalStateException()
}