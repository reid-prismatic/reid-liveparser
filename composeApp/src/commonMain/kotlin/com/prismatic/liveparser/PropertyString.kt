/*
 * This is a property string parser, which wraps property expression parsing and handles typed properties, compound
 * properties, and enum properties. The main function used here is [parsePropertyString].
 *
 * Everything in this file was implemented using AI (Fable 5.1 Max)
 */
package com.prismatic.liveparser

/** How [parsePropertyString] is to read the string of a property. */
sealed interface PropertyType {

    /** Any property expression. */
    data object Expression : PropertyType

    /**
     * Any property expression, with the understanding that it evaluates to [expectedType] or to something that
     * converts to it unambiguously. It is parsed exactly like an [Expression]: what a variable or a call evaluates
     * to is not known to the parser, so holding the value to its type is left to whoever evaluates the tree.
     */
    data class TypedExpression(val expectedType: PropertyDataType) : PropertyType

    /**
     * A dimension that can be recognized without evaluating anything: an expression that starts with a dimension
     * literal, as `12pt`, `2.5cm * scale`, `8pt .. 20pt` and `(10pt ..accel.. 20pt)` do. Being recognizable is what
     * lets an optional dimension stand in front of an unquoted string. For a dimension that may also be given by a
     * variable or a call, use `TypedExpression(PropertyDataType.Dimension)`.
     */
    data object Dimension : PropertyType

    /**
     * Text without quotes, such as `Helvetica Neue`. Within a [Compound] it is whatever the other components leave
     * over, which is why a compound can have only one.
     */
    data object UnquotedStringLiteral : PropertyType

    /** One of [options], written without quotes and exactly as listed, such as `Semi-Bold`. */
    data class UnquotedStringEnum(val options: List<String>) : PropertyType {
        constructor(vararg options: String) : this(options.toList())

        init {
            requireOptions(options)
        }
    }

    /** Several values in one string, separated by whitespace and given in the order of [components]. */
    data class Compound(val components: List<PropertyType>) : PropertyType {
        constructor(vararg components: PropertyType) : this(components.toList())

        init {
            requireOneUnquotedString(components)
        }
    }

    /** A [type] that may be left out. */
    data class Optional(val type: PropertyType) : PropertyType
}

/** What a [PropertyType.TypedExpression] is expected to evaluate to. */
enum class PropertyDataType {
    Integer,
    Char,

    /** Integers are allowed as well and converted automatically. */
    Number,
    Time,
    Dimension,
    Color,
    String,
    Percent,
    SemanticTag,
    Boolean,
    Vec2,
    Vec3,
    Vec4
}

private fun requireOptions(options: List<String>) {
    require(options.isNotEmpty()) { "An unquoted string enum needs at least one option" }
    require(options.all { it.isNotEmpty() && it.trim() == it }) {
        "The options of an unquoted string enum cannot be empty or begin or end with whitespace: $options"
    }
}

private fun requireOneUnquotedString(components: List<PropertyType>) {
    require(components.sumOf { it.countUnquotedStringLiterals() } <= 1) {
        "A compound property can have only one UnquotedStringLiteral, because it takes all the text that the " +
            "other components leave over"
    }
}

/** Checks again what the constructors have checked: a type is made of lists, which may have changed since. */
private fun PropertyType.requireValid() {
    when (this) {
        is PropertyType.UnquotedStringEnum -> requireOptions(options)
        is PropertyType.Compound -> {
            requireOneUnquotedString(components)
            components.forEach { it.requireValid() }
        }
        is PropertyType.Optional -> type.requireValid()
        else -> {}
    }
}

private fun PropertyType.countUnquotedStringLiterals(): Int = when (this) {
    PropertyType.UnquotedStringLiteral -> 1
    is PropertyType.Compound -> components.sumOf { it.countUnquotedStringLiterals() }
    is PropertyType.Optional -> type.countUnquotedStringLiterals()
    else -> 0
}

/**
 * Parses the string of a property of the given [type].
 *
 * The result mirrors the type: a [PropertyExpression] for every type except [PropertyType.Compound], for which it
 * is a [ParsedProperty.Compound] with an entry for each component, and `null` for a [PropertyType.Optional] that
 * the string leaves out.
 *
 * ### What each type reads
 *
 * * [Expression][PropertyType.Expression] and [TypedExpression][PropertyType.TypedExpression]: an expression, as
 *   [parsePropertyExpression] reads it.
 * * [Dimension][PropertyType.Dimension]: an expression that starts with a dimension literal, possibly after
 *   opening parentheses or a sign.
 * * [UnquotedStringEnum][PropertyType.UnquotedStringEnum]: one of the options, which gives a
 *   [PropertyExpression.StringLiteral] of it. The match is exact, so `bold` is not `Bold`; only the whitespace
 *   between the words of an option can be any.
 * * [UnquotedStringLiteral][PropertyType.UnquotedStringLiteral]: the text as it stands, which gives a
 *   [PropertyExpression.StringLiteral] in which every run of whitespace is one space. Text that would be taken for
 *   something else can be put in quotes, as in `"Arial Black"` where `Black` is also an option that follows.
 *
 * Unquoted strings can be animated like any other value: `Helvetica .. Avro` gives a
 * [PropertyExpression.Animation] whose keyframes are string literals, and its ranges and keyframes take the same
 * easings and times as in an expression, as in `Helvetica @ 1s ..linear.. Avro`. Dots are only an eased range
 * when all of `..easing..` is there; after plain dots the text goes on, whatever it is. An animation between the
 * options of an enum is always written in parentheses: `(Light .. Bold)`.
 *
 * ### Compound properties
 *
 * The components come in the order of the type and are separated by whitespace. Those in front of the unquoted
 * string literal are read from the left and those behind it from the right; the text that remains between them is
 * the string. Without an unquoted string literal everything is read from the left, and no text may remain.
 * An expression behind the string is the longest run of words at the end of the text that reads as one.
 *
 * * An expression component is the longest expression that can be read where it stands, and it may contain
 *   whitespace: in `10pt @ 1s .. 20pt @ 10s + 500ms Helvetica`, all but the last word is one animation. It ends
 *   before the first word that cannot continue it.
 * * Outside of brackets, whitespace keeps two components apart where an expression on its own would join them:
 *   `size (a .. b)` is `size` followed by another component and not a call, and `1 -1` is two values, while
 *   `1 - 1` and `1-1` are subtractions.
 * * A [Dimension][PropertyType.Dimension] component is only there if the text starts with a dimension literal.
 *   That is why `Times Roman` has no dimension in it although `Times` would be a valid expression. A component
 *   that is a general expression is there whenever an expression can be read, so an optional one that stands in
 *   front of an unquoted string takes the first word of that string.
 * * An optional component is used when it matches, unless the rest of the string cannot be read that way; then it
 *   is taken to be left out. Once a component has been recognized, a mistake inside it is reported as such.
 *
 * @throws PropertyExpressionParseException if [input] is not a valid string for a property of this type
 * @throws IllegalArgumentException if [type] is not a valid type, which its constructors check as well. (A
 * [PropertyExpressionParseException] is an [IllegalArgumentException] too.)
 */
@Throws(IllegalArgumentException::class)
fun parsePropertyString(input: String, type: PropertyType): ParsedProperty? {
    type.requireValid()
    return PropertyStringParser(input).parse(type)
}

/** The matcher behind [parsePropertyString]. An instance parses one string once. */
private class PropertyStringParser(private val input: String) {

    /**
     * For the text from [partnersFrom] on that [findPartners] was last asked about: for each parenthesis, brace and
     * quote in it that has a counterpart, the index of that counterpart, and -1 for every other character.
     */
    private var partners = IntArray(0)
    private var partnersFrom = 0

    // Why the text does not match the type, in case it turns out not to. What counts is the place furthest into
    // the text at which an attempt came to nothing: that is the attempt that understood the most of it.
    private var mismatchAt = -1

    /** What would have been accepted at [mismatchAt] in place of what is there. */
    private val expectedThere = LinkedHashSet<String>()

    /** What should have come in front of the text at [mismatchAt], where nothing of the kind is. */
    private val missingThere = LinkedHashSet<String>()

    /** The reason why the text at [mismatchAt] cannot be read as anything, if that is so. */
    private var unreadableThere: PropertyExpressionParseException? = null

    /**
     * Finds the brackets and quotes that belong together in the text between [lo] and [hi]. It is what tells the
     * text at the top level from the text inside a group or a string. The text may be unquoted, which need not be
     * made of the tokens of an expression, so this is forgiving: a bracket or a quote without a counterpart is an
     * ordinary character, and so is a quote inside a word, like the apostrophe of `O'Neil`.
     */
    private fun findPartners(lo: Int, hi: Int) {
        partners = IntArray(hi - lo) { -1 }
        partnersFrom = lo
        val open = ArrayList<Int>()
        var i = lo
        while (i < hi) {
            when (val c = input[i]) {
                '"', '\'' -> {
                    val close = closingQuote(i, lo, hi)
                    if (close > i) {
                        pair(i, close)
                        i = close
                    }
                }
                '(', '{' -> open += i
                ')', '}' -> {
                    val opener = open.lastOrNull()
                    if (opener != null && input[opener] == (if (c == ')') '(' else '{')) {
                        pair(opener, i)
                        open.removeAt(open.lastIndex)
                    }
                }
            }
            i++
        }
    }

    private fun pair(open: Int, close: Int) {
        partners[open - partnersFrom] = close
        partners[close - partnersFrom] = open
    }

    private fun partner(i: Int): Int = partners[i - partnersFrom]

    /**
     * The index of the quote that closes a string opened at [open], or -1 if there is no such string: a string
     * neither starts nor ends in the middle of a word, and it ends on the line on which it starts.
     */
    private fun closingQuote(open: Int, lo: Int, hi: Int): Int {
        // (After a backslash it is taken to be escaped, as it would be inside of a string)
        if (open > lo && (input[open - 1].isLetterOrDigit() || input[open - 1] == '\\')) return -1
        var i = open + 1
        while (i < hi) {
            val c = input[i]
            if (c == input[open]) return if (i + 1 < hi && input[i + 1].isLetterOrDigit()) -1 else i
            if (c == '\n' || c == '\r') return -1
            i += if (c == '\\') 2 else 1
        }
        return -1
    }

    fun parse(type: PropertyType): ParsedProperty? {
        var result: ParsedProperty? = null
        val isMatch = matchAll(type, 0, input.length) { value ->
            result = value
            true
        }
        if (!isMatch) throw mismatchError()
        return result
    }

    // ---------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------

    private fun skipWhitespace(from: Int, hi: Int): Int {
        var i = from
        while (i < hi && input[i].isWhitespace()) i++
        return i
    }

    private fun trimEnd(lo: Int, hi: Int): Int {
        var i = hi
        while (i > lo && input[i - 1].isWhitespace()) i--
        return i
    }

    private fun isBlank(lo: Int, hi: Int): Boolean = skipWhitespace(lo, hi) == hi

    /** The index after the character at [i], or after the whole group or string that it opens within [hi]. */
    private fun stepOver(i: Int, hi: Int): Int = if (partner(i) in (i + 1) until hi) partner(i) + 1 else i + 1

    /** The end of the run of characters at [start] that are not whitespace. */
    private fun wordEnd(start: Int, hi: Int): Int {
        var i = start
        while (i < hi && !input[i].isWhitespace()) i++
        return i
    }

    /** What an error message says is at [position]: the word there, or that there is none. */
    private fun found(position: Int, hi: Int): String {
        val start = skipWhitespace(position, hi)
        if (start == hi) return "the end of the text"
        val word = input.substring(start, wordEnd(start, hi))
        return if (word.length <= MAX_SHOWN_WORD) "'$word'" else "'${word.take(MAX_SHOWN_WORD)}...'"
    }

    /**
     * Notes that the text at [position] is not the [expected] thing, or with null that nothing more was expected
     * there at all. Returns false, for the match that this was the end of.
     */
    private fun mismatch(expected: String?, position: Int): Boolean {
        if (noteMismatchAt(skipWhitespace(position, input.length)) && expected != null) expectedThere += expected
        return false
    }

    /**
     * Notes that the [expected] thing should have come before [position], where there is nothing that could be
     * it. That is how a component is missed that is looked for from the right. Returns false, like [mismatch].
     */
    private fun missing(expected: String, position: Int): Boolean {
        val start = skipWhitespace(position, input.length)
        if (noteMismatchAt(start)) (if (start < input.length) missingThere else expectedThere) += expected
        return false
    }

    /** Notes that the text could not be read for the reason that [error] gives. Returns false, like [mismatch]. */
    private fun mismatch(error: PropertyExpressionParseException): Boolean {
        if (noteMismatchAt(error.range.first) && unreadableThere == null) unreadableThere = error
        return false
    }

    /** Whether [position] is, or now becomes, the place that the reported mismatch is about. */
    private fun noteMismatchAt(position: Int): Boolean {
        if (position > mismatchAt) {
            mismatchAt = position
            expectedThere.clear()
            missingThere.clear()
            unreadableThere = null
        }
        return position == mismatchAt
    }

    private fun mismatchError(): PropertyExpressionParseException {
        unreadableThere?.let { return it }
        val start = maxOf(mismatchAt, 0)
        val found = found(start, input.length)
        val reason = when {
            missingThere.isNotEmpty() -> "Expected ${listed(missingThere)} before $found"
            expectedThere.isNotEmpty() -> "Expected ${listed(expectedThere)} but found $found"
            else -> "Unexpected $found"
        }
        return PropertyExpressionParseException(reason, start until wordEnd(start, input.length))
    }

    private fun listed(alternatives: Collection<String>): String {
        val all = alternatives.toList()
        return if (all.size == 1) all[0] else all.dropLast(1).joinToString() + " or " + all.last()
    }

    private fun fail(reason: String, start: Int, end: Int): Nothing =
        throw PropertyExpressionParseException(reason, start until end)

    private companion object {
        const val MAX_SHOWN_WORD = 40
        const val A_DIMENSION = "a dimension such as 12pt"

        // How much the search for an expression behind the string may read: this many times its text, and a bit
        const val SEARCH_FACTOR = 16
        const val SEARCH_MINIMUM = 1024
    }

    // ---------------------------------------------------------------------------------------------------
    // Matching a type against the text
    //
    // Each of these functions goes through the ways in which the type can match, the preferred one first, and
    // hands each to `next`, which answers whether the rest of the text could be matched as well. The function
    // stops at the first way for which it could, and returns whether there was one.
    // ---------------------------------------------------------------------------------------------------

    /** Matches [type] against all of the text between [lo] and [hi]. */
    private fun matchAll(type: PropertyType, lo: Int, hi: Int, next: (ParsedProperty?) -> Boolean): Boolean =
        when (type) {
            is PropertyType.Optional -> if (isBlank(lo, hi)) next(null) else matchAll(type.type, lo, hi, next)
            is PropertyType.Compound -> matchComponents(type.components, lo, hi, next)
            PropertyType.UnquotedStringLiteral -> {
                val start = skipWhitespace(lo, hi)
                if (start == hi) missing("text", hi) else next(unquoted(start, trimEnd(start, hi), null))
            }
            is PropertyType.UnquotedStringEnum ->
                enumAtStart(type, lo, hi) { end, value -> if (isBlank(end, hi)) next(value) else leftover(end) }
            PropertyType.Expression, is PropertyType.TypedExpression, PropertyType.Dimension ->
                next(wholeExpression(type, lo, hi))
        }

    /**
     * Matches the components of a compound against all of the text between [lo] and [hi]. The components in front
     * of the one with the unquoted string are matched from the left and those behind it from the right; that one
     * then gets the text that is left between them.
     */
    private fun matchComponents(
        components: List<PropertyType>,
        lo: Int,
        hi: Int,
        next: (ParsedProperty?) -> Boolean,
    ): Boolean {
        val values = arrayOfNulls<ParsedProperty>(components.size)
        val middle = components.indexOfFirst { it.countUnquotedStringLiterals() > 0 }
        val leftCount = if (middle < 0) components.size else middle
        // Where the search has been before without success: with many optional components there are many ways
        // to arrive at the same place
        val deadEnds = HashSet<Triple<Int, Int, Int>>()

        fun finish(): Boolean = next(ParsedProperty.Compound(values.toList()))

        fun fromRight(i: Int, start: Int, end: Int): Boolean {
            if (i == middle) {
                return matchAll(components[i], start, end) { value ->
                    values[i] = value
                    finish()
                }
            }
            if (!deadEnds.add(Triple(i, start, end))) return false
            return matchEnd(components[i], start, end) { valueStart, value ->
                values[i] = value
                fromRight(i - 1, start, valueStart)
            }
        }

        fun fromLeft(i: Int, start: Int): Boolean {
            if (i == leftCount) {
                return when {
                    middle >= 0 -> fromRight(components.lastIndex, start, hi)
                    isBlank(start, hi) -> finish()
                    else -> leftover(start)
                }
            }
            if (!deadEnds.add(Triple(i, start, hi))) return false
            return matchStart(components[i], start, hi) { valueEnd, value ->
                values[i] = value
                fromLeft(i + 1, valueEnd)
            }
        }
        return fromLeft(0, lo)
    }

    /** Notes that the text from [start] on is more than the type has room for. */
    private fun leftover(start: Int): Boolean = mismatch(null, start)

    /**
     * Matches [type], which has no unquoted string in it, at the start of the text between [lo] and [hi]. `next`
     * is given where the match ends, which is at whitespace or at [hi].
     */
    private fun matchStart(
        type: PropertyType,
        lo: Int,
        hi: Int,
        next: (end: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean = when (type) {
        // Present if it can be, and left out if it cannot or if the rest of the text then does not match
        is PropertyType.Optional ->
            matchStart(type.type, lo, hi) { end, value -> end > lo && next(end, value) } || next(lo, null)
        is PropertyType.Compound -> {
            val values = arrayOfNulls<ParsedProperty>(type.components.size)
            val deadEnds = HashSet<Pair<Int, Int>>()
            fun from(i: Int, start: Int): Boolean {
                if (i == values.size) return next(start, ParsedProperty.Compound(values.toList()))
                if (!deadEnds.add(i to start)) return false
                return matchStart(type.components[i], start, hi) { end, value ->
                    values[i] = value
                    from(i + 1, end)
                }
            }
            from(0, lo)
        }
        is PropertyType.UnquotedStringEnum -> enumAtStart(type, lo, hi, next)
        PropertyType.UnquotedStringLiteral -> error("An unquoted string is matched last, not from the left")
        PropertyType.Expression, is PropertyType.TypedExpression, PropertyType.Dimension ->
            expressionAtStart(type, lo, hi, next)
    }

    /**
     * Matches [type], which has no unquoted string in it, at the end of the text between [lo] and [hi]. `next` is
     * given where the match starts, which is after whitespace or at [lo].
     */
    private fun matchEnd(
        type: PropertyType,
        lo: Int,
        hi: Int,
        next: (start: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean = when (type) {
        is PropertyType.Optional ->
            matchEnd(type.type, lo, hi) { start, value -> start < hi && next(start, value) } || next(hi, null)
        is PropertyType.Compound -> {
            val values = arrayOfNulls<ParsedProperty>(type.components.size)
            val deadEnds = HashSet<Pair<Int, Int>>()
            fun from(i: Int, end: Int): Boolean {
                if (i < 0) return next(end, ParsedProperty.Compound(values.toList()))
                if (!deadEnds.add(i to end)) return false
                return matchEnd(type.components[i], lo, end) { start, value ->
                    values[i] = value
                    from(i - 1, start)
                }
            }
            from(values.lastIndex, hi)
        }
        is PropertyType.UnquotedStringEnum -> enumAtEnd(type, lo, hi, next)
        PropertyType.UnquotedStringLiteral -> error("An unquoted string is matched last, not from the right")
        PropertyType.Expression, is PropertyType.TypedExpression, PropertyType.Dimension ->
            expressionAtEnd(type, lo, hi, next)
    }

    // ---------------------------------------------------------------------------------------------------
    // Expressions
    // ---------------------------------------------------------------------------------------------------

    /** All of the text between [lo] and [hi] as one expression, read the way [parsePropertyExpression] reads it. */
    private fun wholeExpression(type: PropertyType, lo: Int, hi: Int): PropertyExpression {
        if (type == PropertyType.Dimension) {
            val start = skipWhitespace(lo, hi)
            val probe = PropertyExpressionParser(input, start, hi)
            // Text that cannot be read at all is better explained by the parser than by its not being a dimension
            if (start < hi && !startsLike(type, probe) && probe.leadingError() == null) {
                fail("Expected $A_DIMENSION but found ${found(start, hi)}", start, wordEnd(start, hi))
            }
        }
        return PropertyExpressionParser(input, lo, hi).parse()
    }

    /** Whether the text of [parser] starts with what an expression of [type] has to start with. */
    private fun startsLike(type: PropertyType, parser: PropertyExpressionParser): Boolean =
        if (type == PropertyType.Dimension) {
            parser.leadingLiteral() is PropertyExpression.DimensionLiteral
        } else {
            parser.startsExpression()
        }

    /** Notes that the text at [start] does not start an expression of [type]. */
    private fun notAnExpression(type: PropertyType, parser: PropertyExpressionParser, start: Int): Boolean {
        val unreadable = parser.leadingError()
        if (unreadable != null) return mismatch(unreadable)
        return mismatch(expectationOf(type), start)
    }

    private fun expectationOf(type: PropertyType): String =
        if (type == PropertyType.Dimension) A_DIMENSION else "a value"

    /** The longest expression at the start of the text is the component; the word after it has to be another one. */
    private fun expressionAtStart(
        type: PropertyType,
        lo: Int,
        hi: Int,
        next: (end: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean {
        val start = skipWhitespace(lo, hi)
        val parser = PropertyExpressionParser(input, start, hi, isComponent = true)
        if (!startsLike(type, parser)) return notAnExpression(type, parser, start)
        // From here on it is this component, and a mistake in it is a mistake rather than a reason to look elsewhere
        val expression = parser.parseLeading()
        val end = parser.consumedEnd
        if (end < hi && !input[end].isWhitespace()) {
            throw parser.unreadableNext ?: PropertyExpressionParseException(
                "Expected whitespace before ${found(end, hi)}",
                end until wordEnd(end, hi),
            )
        }
        return next(end, expression)
    }

    /**
     * The component is the longest run of words at the end of the text that reads as one expression of [type].
     * So the words are tried as its start from the left, and the first from which an expression runs exactly to
     * the end of the text is the one; if the rest of the text then does not match, the search goes on.
     *
     * Two things keep this from reading the text over and over. A word inside an expression that ended early
     * gets no try of its own, since what starts there ends where that expression did. And the search is given up
     * when it has read a multiple of the text, which only happens to text that is made to keep it busy.
     */
    private fun expressionAtEnd(
        type: PropertyType,
        lo: Int,
        hi: Int,
        next: (start: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean {
        val end = trimEnd(lo, hi)
        val isCutOff = end < trimEnd(0, input.length)
        var firstError: PropertyExpressionParseException? = null
        var isFound = false
        val groupEnds = HashMap<Int, Int>()
        // The latest attempt that ended before the end of the text, and where it ended
        var early: PropertyExpressionParser? = null
        var earlyEnd = lo
        var allowance = SEARCH_FACTOR * (end - lo) + SEARCH_MINIMUM
        val firstWord = wordStart(lo, lo, end)
        var candidate = firstWord
        var lastWord = firstWord
        while (candidate < end && allowance >= 0) {
            lastWord = candidate
            // (A word inside a string of that expression is not one of its tokens, and does get a try)
            val endsEarly = candidate < earlyEnd && early != null && early.hasTokenAt(candidate)
            if (!endsEarly) {
                val parser = PropertyExpressionParser(input, candidate, end, isComponent = true, groupEnds)
                if (startsLike(type, parser)) {
                    val expression = try {
                        parser.parseLeading()
                    } catch (e: PropertyExpressionParseException) {
                        // Running out of text where it was cut off for the components behind is not a mistake in it
                        if (firstError == null && !(isCutOff && e.range.first >= end)) firstError = e
                        null
                    }
                    if (expression != null && parser.consumedEnd < end) {
                        early = parser
                        earlyEnd = parser.consumedEnd
                    } else if (expression != null) {
                        isFound = true
                        if (next(candidate, expression)) return true
                    }
                }
                allowance -= parser.readEnd - candidate + 1
            }
            candidate = wordStart(candidate + 1, lo, end)
        }
        // If the expression was there, what did not match is something else, and that has been noted
        if (isFound) return false
        firstError?.let { return mismatch(it) }
        if (firstWord == end) return missing(expectationOf(type), end)
        // The last word is where the expression should have been at the latest
        return notAnExpression(type, PropertyExpressionParser(input, lastWord, end, isComponent = true), lastWord)
    }

    /** The first index from [from] on at which a word begins, or [hi] if none does. */
    private fun wordStart(from: Int, lo: Int, hi: Int): Int {
        var i = from
        while (i < hi && (input[i].isWhitespace() || (i > lo && !input[i - 1].isWhitespace()))) i++
        return i
    }

    // ---------------------------------------------------------------------------------------------------
    // Unquoted strings
    // ---------------------------------------------------------------------------------------------------

    private fun enumAtStart(
        type: PropertyType.UnquotedStringEnum,
        lo: Int,
        hi: Int,
        next: (end: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean {
        val start = skipWhitespace(lo, hi)
        var isFound = false
        // An animation between options is in parentheses
        val close = if (start < hi && input[start] == '(') findPartners(start, hi).let { partner(start) } else -1
        if (close >= 0) {
            val end = close + 1
            val animation = if (end == hi || input[end].isWhitespace()) optionsIn(type, start, end) else null
            if (animation != null) {
                isFound = true
                if (next(end, animation)) return true
            }
        }
        for (option in type.options.sortedByDescending { it.length }) {
            val end = optionEnd(option, start, hi)
            if (end >= 0) {
                isFound = true
                if (next(end, stringLiteral(option, start, end))) return true
            }
        }
        if (!isFound) type.options.forEach { mismatch("'$it'", start) }
        return false
    }

    private fun enumAtEnd(
        type: PropertyType.UnquotedStringEnum,
        lo: Int,
        hi: Int,
        next: (start: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean {
        val end = trimEnd(lo, hi)
        var isFound = false
        val open = if (end > lo && input[end - 1] == ')') findPartners(lo, end).let { partner(end - 1) } else -1
        if (open >= 0) {
            val start = open
            val animation = if (start == lo || input[start - 1].isWhitespace()) optionsIn(type, start, end) else null
            if (animation != null) {
                isFound = true
                if (next(start, animation)) return true
            }
        }
        for (option in type.options.sortedByDescending { it.length }) {
            val start = optionStart(option, end, lo)
            if (start >= 0) {
                isFound = true
                if (next(start, stringLiteral(option, start, end))) return true
            }
        }
        if (isFound) return false
        var lastWord = end
        while (lastWord > lo && !input[lastWord - 1].isWhitespace()) lastWord--
        type.options.forEach { if (end == lo) missing("'$it'", end) else mismatch("'$it'", lastWord) }
        return false
    }

    /**
     * Where [option] ends if it stands at [start] as words of its own, and -1 if it does not. The words of an
     * option can have any whitespace between them, as words have everywhere else.
     */
    private fun optionEnd(option: String, start: Int, hi: Int): Int {
        var i = start
        var o = 0
        while (o < option.length) {
            if (option[o].isWhitespace()) {
                if (i >= hi || !input[i].isWhitespace()) return -1
                i = skipWhitespace(i, hi)
                while (option[o].isWhitespace()) o++
            } else {
                if (i >= hi || input[i] != option[o]) return -1
                i++
                o++
            }
        }
        return if (i == hi || input[i].isWhitespace()) i else -1
    }

    /** Where [option] starts if it stands before [end] as words of its own, and -1 if it does not. */
    private fun optionStart(option: String, end: Int, lo: Int): Int {
        var i = end
        var o = option.length
        while (o > 0) {
            if (option[o - 1].isWhitespace()) {
                if (i <= lo || !input[i - 1].isWhitespace()) return -1
                i = trimEnd(lo, i)
                while (option[o - 1].isWhitespace()) o--
            } else {
                if (i <= lo || input[i - 1] != option[o - 1]) return -1
                i--
                o--
            }
        }
        return if (i == lo || input[i - 1].isWhitespace()) i else -1
    }

    /** The parenthesized text between [lo] and [hi] if all it holds are options of [type], and null if not. */
    private fun optionsIn(type: PropertyType.UnquotedStringEnum, lo: Int, hi: Int): PropertyExpression? =
        try {
            unquoted(lo, hi, type.options)
        } catch (e: PropertyExpressionParseException) {
            // Whatever this is, it is not this enum; it may still be part of an unquoted string next to it
            null
        }

    /**
     * Reads the text between [lo] and [hi], which is not empty and has no whitespace at its ends, as an unquoted
     * string or as an animation between unquoted strings. With [options], each string has to be one of them, and
     * the result is null if one is not.
     */
    private fun unquoted(lo: Int, hi: Int, options: List<String>?): PropertyExpression? {
        findPartners(lo, hi)
        // Each part of an animation is within the depth limit by itself, but nested in one another they may not be
        return unquotedPart(lo, hi, options, 0)?.also { PropertyExpressionParser(input).checkDepth(it) }
    }

    private fun unquotedPart(lo: Int, hi: Int, options: List<String>?, depth: Int): PropertyExpression? {
        if (input[lo] == '(' && partner(lo) == hi - 1) {
            // Parentheses around all of it only group, as they do in an expression
            if (depth >= PropertyExpressionParser.MAX_DEPTH) fail(PropertyExpressionParser.TOO_DEEP, lo, lo + 1)
            val start = skipWhitespace(lo + 1, hi - 1)
            val end = trimEnd(start, hi - 1)
            if (start < end) return unquotedPart(start, end, options, depth + 1)
            return if (options != null) null else fail("Expected text between the parentheses", lo, hi)
        }
        if ((input[lo] == '"' || input[lo] == '\'') && partner(lo) == hi - 1) {
            // Quotes are the way to write text that would otherwise be taken for something else
            return if (options != null) null else PropertyExpressionParser(input, lo, hi).parse()
        }

        val keyframes = ArrayList<PropertyExpression.Keyframe>()
        val easings = ArrayList<PropertyExpression.Easing?>()
        val groupEnds = HashMap<Int, Int>()
        var valueStart = lo
        var operatorStart = lo
        var i = lo
        while (i < hi) {
            if (input[i] == '.' && i + 1 < hi && input[i + 1] == '.') {
                // The text behind the dots need not be an expression, so it is only looked at for the outline of
                // `..easing..`. Dots without one are a plain range, whatever comes after them.
                val easedEnd = PropertyExpressionParser(input, i, hi, groupEnds = groupEnds).easedRangeEnd()
                val operator = PropertyExpressionParser(input, i, if (easedEnd < 0) i + 2 else easedEnd)
                easings += operator.parseRangeOperator()
                keyframes += keyframe(valueStart, i, i until operator.consumedEnd, options, depth) ?: return null
                operatorStart = i
                i = operator.consumedEnd
                valueStart = i
            } else {
                i = stepOver(i, hi)
            }
        }
        if (keyframes.isEmpty()) return plainText(lo, hi, options)

        keyframes += keyframe(valueStart, hi, operatorStart until valueStart, options, depth) ?: return null
        val segments = easings.indices.map { PropertyExpression.Segment(easings[it], keyframes[it + 1]) }
        val animation = PropertyExpression.Animation(keyframes[0], segments)
        animation.sourceRange = lo until hi
        return animation
    }

    /**
     * One keyframe of an animation between unquoted strings, `text` or `text @ time`, from the text between [lo]
     * and [hi]. [operator] is where the `..` next to it is.
     */
    private fun keyframe(
        lo: Int,
        hi: Int,
        operator: IntRange,
        options: List<String>?,
        depth: Int,
    ): PropertyExpression.Keyframe? {
        // The time is what follows the last '@' that is not inside of something
        var at = -1
        var i = lo
        while (i < hi) {
            if (input[i] == '@') at = i
            i = stepOver(i, hi)
        }
        val start = skipWhitespace(lo, if (at < 0) hi else at)
        val end = trimEnd(start, if (at < 0) hi else at)
        if (start == end) {
            if (options != null) return null
            if (at >= 0) fail("Expected text before '@'", at, at + 1)
            fail("An animation needs a value on both sides of each '..'", operator.first, operator.last + 1)
        }
        val value = unquotedPart(start, end, options, depth + 1) ?: return null
        if (at < 0) return PropertyExpression.Keyframe(value)
        if (isBlank(at + 1, hi)) fail("Expected a time after '@'", at, at + 1)
        return PropertyExpression.Keyframe(value, PropertyExpressionParser(input, at + 1, hi).parseOperand())
    }

    /** The text between [lo] and [hi] as a string, with each run of whitespace in it turned into one space. */
    private fun plainText(lo: Int, hi: Int, options: List<String>?): PropertyExpression? {
        val text = buildString {
            var i = lo
            while (i < hi) {
                if (input[i].isWhitespace()) {
                    append(' ')
                    i = skipWhitespace(i, hi)
                } else {
                    append(input[i++])
                }
            }
        }
        if (options != null) return if (text in options) stringLiteral(text, lo, hi) else null
        failOnQuotedPart(lo, hi)
        return stringLiteral(text, lo, hi)
    }

    /**
     * Refuses unquoted text in which one of the words, or a run of them, is in quotes of its own. Taken as it
     * stands, such text would have the quotes in it, which is hardly what was meant.
     */
    private fun failOnQuotedPart(lo: Int, hi: Int) {
        var i = lo
        while (i < hi) {
            val close = partner(i)
            val isQuoted = (input[i] == '"' || input[i] == '\'') && close in (i + 1) until hi &&
                (i == lo || input[i - 1].isWhitespace()) && (close == hi - 1 || input[close + 1].isWhitespace())
            if (isQuoted) {
                fail("Text in quotes cannot be combined with text outside of them: quote all of it", i, close + 1)
            }
            i = stepOver(i, hi)
        }
    }

    private fun stringLiteral(text: String, start: Int, end: Int): PropertyExpression =
        PropertyExpression.StringLiteral(text).apply { sourceRange = start until end }
}
