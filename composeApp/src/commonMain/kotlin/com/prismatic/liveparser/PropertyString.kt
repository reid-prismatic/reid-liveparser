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
     * converts to it unambiguously. It is parsed like an [Expression]: what a variable or a call evaluates to is
     * not known to the parser, so holding the value to its type is left to whoever evaluates the tree. The parser
     * only uses the type where the expression may be left out and stands next to an unquoted string: there, the
     * literals of this kind are what it is told from words of the string by, as [parsePropertyString] describes.
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

    /**
     * One of [components], whichever the text fits. They are tried in the order given and the first that fits is
     * taken, which matters for text that fits several: `Bold` is an option of an enum and also an expression, the
     * name of a variable, so the enum goes first. (Where the alternate may be left out next to an unquoted
     * string, a word is no expression unless it shows itself to be one, and `Bold` is the option either way.)
     *
     * A component that may be left out should not be among them: make the alternate optional instead. As a
     * component of a [Compound], such an alternative also fits by being left out, and that is tried before the
     * alternatives after it.
     *
     * In a [Compound], an alternate with an [UnquotedStringLiteral] in any of its components is the one unquoted
     * string that the compound can have. Each of the components can have its own.
     */
    data class Alternate(val components: List<PropertyType>) : PropertyType {
        constructor(vararg components: PropertyType) : this(components.toList())

        init {
            requireAlternatives(components)
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
    Vec4,
    Ratio
}

private fun requireOptions(options: List<String>) {
    require(options.isNotEmpty()) { "An unquoted string enum needs at least one option" }
    require(options.all { it.isNotEmpty() && it.trim() == it }) {
        "The options of an unquoted string enum cannot be empty or begin or end with whitespace: $options"
    }
}

private fun requireAlternatives(components: List<PropertyType>) {
    require(components.isNotEmpty()) { "An alternate needs at least one component to choose from" }
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
        is PropertyType.Alternate -> {
            requireAlternatives(components)
            components.forEach { it.requireValid() }
        }
        is PropertyType.Optional -> type.requireValid()
        else -> {}
    }
}

private fun PropertyType.countUnquotedStringLiterals(): Int = when (this) {
    PropertyType.UnquotedStringLiteral -> 1
    is PropertyType.Compound -> components.sumOf { it.countUnquotedStringLiterals() }
    // Only one of the alternatives is ever used, so each of them may have an unquoted string of its own
    is PropertyType.Alternate -> components.maxOfOrNull { it.countUnquotedStringLiterals() } ?: 0
    is PropertyType.Optional -> type.countUnquotedStringLiterals()
    else -> 0
}

/** Whether an expression of any kind can stand somewhere in this type, a [PropertyType.Dimension] aside. */
private fun PropertyType.hasExpression(): Boolean = when (this) {
    PropertyType.Expression, is PropertyType.TypedExpression -> true
    is PropertyType.Compound -> components.any { it.hasExpression() }
    is PropertyType.Alternate -> components.any { it.hasExpression() }
    is PropertyType.Optional -> type.hasExpression()
    else -> false
}

/**
 * How an expression among the components of a compound is told from words of the unquoted string that the compound
 * may have.
 */
private enum class Footing {
    /** There is no unquoted string for its words to belong to: whatever reads as an expression is one. */
    ANY,

    /** The component is one of those around an unquoted string, which matters if it may be left out. */
    BESIDE_STRING,

    /**
     * The component is around an unquoted string and may be left out, so its words could as well be words of the
     * string. It is only taken for an expression if it evidently is one: see `isEvident`.
     */
    EVIDENT,

    /**
     * The component may be left out, but its words turned out not to be words of the string. So they are an
     * expression after all if they read as one, unless that is evidently none of its type: see `couldBe`.
     */
    AFTER_ALL,
}

/**
 * Parses the string of a property of the given [type].
 *
 * The result mirrors the type. It is a [PropertyExpression] for most types; for a [PropertyType.Compound] it is a
 * [ParsedProperty.Compound] with an entry for each component, for a [PropertyType.Alternate] a
 * [ParsedProperty.Alternate] that holds the value and says which of the alternatives it is, and for a
 * [PropertyType.Optional] that the string leaves out it is `null`.
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
 * * [Alternate][PropertyType.Alternate]: one of its components. They are tried in the order given and the first
 *   that fits is taken. A mistake in one of them is only reported if none of the others fits either.
 *
 * Unquoted strings can be animated like any other value: `Helvetica .. Avro` gives a
 * [PropertyExpression.Animation] whose keyframes are string literals, and its ranges and keyframes take the same
 * easings and times as in an expression, as in `Helvetica @ 1s ..linear.. Avro`. Dots are only an eased range
 * when all of `..easing..` is there; after plain dots the text goes on, whatever it is.
 *
 * An animation between the options of an enum is written in parentheses, as `(Light .. Bold)`. They can be left
 * off where the enum has a stretch of text to itself: as the whole property, or as an alternative to all of it
 * or to the component with the unquoted string. Every keyframe of such an animation is an option. `(400 .. Bold)`
 * is none, and where that is read as an expression instead, `Bold` is a variable in it.
 *
 * ### Compound properties
 *
 * The components come in the order of the type and are separated by whitespace. Those in front of the unquoted
 * string literal are read from the left and those behind it from the right; the text that remains between them is
 * the string. Without an unquoted string literal everything is read from the left, and no text may remain.
 *
 * * An expression component is the longest expression that can be read where it stands, and it may contain
 *   whitespace: in `10pt @ 1s .. 20pt @ 10s + 500ms Helvetica`, all but the last word is one animation. It ends
 *   before the first word that cannot continue it. Behind the string, it is the longest run of words at the end
 *   of the text that reads as one expression. (The search for that run is given up, and the run not found, in a
 *   text that makes it read the same words many times over: a chain of operators over more than a hundred
 *   characters that leads up to a mistake does.)
 * * Outside of brackets, whitespace keeps two components apart where an expression on its own would join them:
 *   `size (a .. b)` is `size` followed by another component and not a call, and `1 -1` is two values, while
 *   `1 - 1` and `1-1` are subtractions.
 * * A [Dimension][PropertyType.Dimension] component is only there if the text starts with a dimension literal.
 *   That is why `Times Roman` has no dimension in it although `Times` would be a valid expression.
 * * A component that may be left out is there if it can be. It is taken to be left out if it does not fit, or if
 *   the rest of the string does not fit with it there.
 * * A mistake is another matter. Once a component has been recognized, a mistake in it is reported, and so is a
 *   mistake that the rest of the text then has. With a weight behind the name that `Bold` is an option of,
 *   `Helvetica .. Bold` is such a case: `Bold` is the weight, and what is reported is that the animation which
 *   this leaves for the name has no second value. In parentheses, `(Helvetica .. Bold)` is all name. Of several
 *   alternatives, the one that is recognized is the first that fits.
 * * Where the string is in quotes, it begins and ends where they do, and an animation between strings ends with
 *   the time of its last keyframe. Words that are left next to that are a mistake, unless they can be read as an
 *   expression that was left out, as described below.
 *
 * ### Expressions next to an unquoted string
 *
 * Every word reads as an expression, since it can be the name of a variable. In a compound with an unquoted
 * string, that would make `Roman` in `Times Roman` an integer weight that may follow the name. So an expression
 * that may be left out is only taken from the words around an unquoted string if it shows itself to be one:
 *
 * * A call shows it, and so does a literal of the kind that the type expects: after `Helvetica`, an integer can
 *   be `700`, `-1`, `(400 .. 700)` or `lerp(a, b, t)`. Integers and numbers are one kind in this, and for an
 *   [Expression][PropertyType.Expression] every literal is of the kind.
 * * A calculation shows it if there is a literal or a call anywhere in it, of whatever kind: `weight * 1.5`,
 *   `w-100`.
 * * Names alone show nothing: `Helvetica weight` is all name, and so are `Helvetica a + b` and
 *   `Helvetica .. Avro`. Of a list and of an animation every value has to show it, which keeps
 *   `Helvetica, Arial, 700` and `Source Sans 3 .. Avro` together, but `Helvetica 400 .. w` as well. The times
 *   and the easings of an animation show nothing, and what follows an `@` is taken for the time of a keyframe.
 * * Behind the string, only the longest run of words that reads as one expression can be it. If that shows
 *   nothing, no shorter run is tried.
 *
 * What does not read as an expression is text, with one exception: an expression that starts with a literal of
 * its kind is held to being a right one. In front of the string, that goes for any mistake in it. Behind the
 * string, where an expression could start at any word, it only goes for all of the text ending in the midst of
 * one: the mistake in `Helvetica 700 +` is reported, while `Helvetica weight +` and `Studio 54 @ Night` are all
 * name, and so is `Helvetica 700 +` where an `Italic` follows it. (A minus that stands by itself at the end is no
 * such mistake either: as between components, it is taken for the sign of something that would follow.)
 *
 * For whoever writes such a string, this means:
 *
 * * A string that ends in a number loses it to a number that may follow, as one that begins with a number does
 *   to one that may come first: `Source Sans 3` is `Source Sans` with a weight of 3. That also goes for a number
 *   in parentheses, and for a word that ends in a hyphen and a number, which reads as a subtraction:
 *   `Avenir Next-85` is `Avenir` with a weight of `Next - 85`. In quotes the string stays together:
 *   `"Source Sans 3"`.
 * * A variable alone is not taken for the expression, and neither is an animation that has one for a value.
 *   Write a calculation, as in `weight + 0`, or put the string in quotes.
 * * A string in quotes is also something that an expression can be made of. Where the string may be left out,
 *   `"Helvetica" - 100` is all one expression, a subtraction, while `"Helvetica" -100` is a string followed by
 *   a number.
 *
 * Words that show nothing are still read as the expression where they cannot be words of the string:
 *
 * * next to a string in quotes, on the side of it that the expression is on: in `"Helvetica" weight`, the weight
 *   is the variable;
 * * behind the time of the string's last keyframe, as in `Helvetica .. Avro @ 1s weight`;
 * * where the text does not fit unless they are the expression, as when a component that has to be there stands
 *   between them and the string.
 *
 * Where it is the quotes or the time that keep words from being the string's, a literal of another kind than the
 * type expects is not taken for the expression, so that `"Helvetica" "Bold"` is no name with a weight. An
 * expression that has to be there takes what it finds in any case, a single word included.
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

    // The text of the unquoted string that is being read, and the mistake in it if it is one of the two that say
    // where a string ends rather than that it is wrong: words behind it, or words in front of it
    private var textFrom = 0
    private var textTo = 0
    private var wordsBehind: PropertyExpressionParseException? = null
    private var wordsInFront: PropertyExpressionParseException? = null

    // The mistakes of those two kinds that have been made: those of a string that may end before the words at the
    // end of its text, and those of one that may begin after the words at the start of it
    private val withWordsBehind = HashSet<PropertyExpressionParseException>()
    private val withWordsInFront = HashSet<PropertyExpressionParseException>()

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
        const val NO_VALUE = "An animation needs a value on both sides of each '..'"

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

    /**
     * Matches [type] against all of the text between [lo] and [hi]. [isAlternative] tells whether that is as one
     * of several alternatives, which makes a difference to what is said about text that is no expression.
     */
    private fun matchAll(
        type: PropertyType,
        lo: Int,
        hi: Int,
        isAlternative: Boolean = false,
        next: (ParsedProperty?) -> Boolean,
    ): Boolean =
        when (type) {
            is PropertyType.Optional ->
                if (isBlank(lo, hi)) next(null) else matchAll(type.type, lo, hi, isAlternative, next)
            is PropertyType.Compound -> matchComponents(type.components, lo, hi, next)
            is PropertyType.Alternate -> firstFitting(type) { index, alternative, rest ->
                matchAll(alternative, lo, hi, isAlternative = true) { value ->
                    rest.of { next(ParsedProperty.Alternate(index, value)) }
                }
            }
            PropertyType.UnquotedStringLiteral ->
                if (isBlank(lo, hi)) missing("text", hi) else next(unquotedString(lo, hi))
            is PropertyType.UnquotedStringEnum -> {
                // With all of the text to itself, an animation between options needs no parentheses around it
                val options = if (isBlank(lo, hi)) null else optionsIn(type, skipWhitespace(lo, hi), trimEnd(lo, hi))
                when (options) {
                    null -> enumAtStart(type, lo, hi) { end, value ->
                        if (isBlank(end, hi)) next(value) else leftover(end)
                    }
                    else -> next(options)
                }
            }
            PropertyType.Expression, is PropertyType.TypedExpression, PropertyType.Dimension ->
                if (isAlternative) alternativeExpression(type, lo, hi, next) else next(wholeExpression(type, lo, hi))
        }

    /**
     * All of the text between [lo] and [hi] as an expression of [type] that is one of several alternatives. That
     * the text does not start like one, or goes on behind one, is then not a mistake in it but its not fitting:
     * the other alternatives get to say what they make of the text. What is wrong inside of it is a mistake.
     */
    private fun alternativeExpression(
        type: PropertyType,
        lo: Int,
        hi: Int,
        next: (ParsedProperty?) -> Boolean,
    ): Boolean {
        val start = skipWhitespace(lo, hi)
        val parser = PropertyExpressionParser(input, start, hi)
        if (!startsLike(type, parser)) return notAnExpression(type, parser, start)
        val expression = parser.parseLeading()
        val end = skipWhitespace(parser.consumedEnd, hi)
        if (end == hi) return next(expression)
        parser.unreadableNext?.let { throw it }
        return leftover(end)
    }

    /**
     * Tries the alternatives of [type] in their order until [attempt] finds one that fits, and returns whether
     * one did. `rest` is what an attempt wraps around the matching of the text that follows the alternative.
     *
     * A mistake inside an alternative does not keep the others from being tried. It is only reported if none of
     * them fits, and if several had one, the mistake that got furthest into the text is. One that says where an
     * unquoted string ends comes before the others, though: it may be a reason for a component that was left
     * out to be there after all (see `optional`). It then stands for all the mistakes of its kind that the
     * alternatives had, so that their order makes no difference to that. A mistake in the text that follows the
     * alternatives is reported at once, like anywhere else.
     */
    private fun firstFitting(
        type: PropertyType.Alternate,
        attempt: (index: Int, alternative: PropertyType, rest: Rest) -> Boolean,
    ): Boolean {
        val rest = Rest()
        var mistake: PropertyExpressionParseException? = null
        for (index in type.components.indices) {
            try {
                if (attempt(index, type.components[index], rest)) return true
            } catch (e: PropertyExpressionParseException) {
                if (rest.has(e)) throw e
                val kept = mistake
                when {
                    kept == null -> mistake = e
                    isTextEnd(kept) -> {
                        if (e in withWordsBehind) withWordsBehind += kept
                        if (e in withWordsInFront) withWordsInFront += kept
                    }
                    isTextEnd(e) || e.range.first > kept.range.first -> mistake = e
                }
            }
        }
        mistake?.let { throw it }
        return false
    }

    /** Marks where the matching of an alternative ends and the matching of the text that follows it begins. */
    private class Rest {
        private val mistakes = ArrayList<PropertyExpressionParseException>()

        /** Whether [mistake] is one that the text which follows was found to have, rather than the alternative. */
        fun has(mistake: PropertyExpressionParseException): Boolean = mistakes.any { it === mistake }

        inline fun of(match: () -> Boolean): Boolean =
            try {
                match()
            } catch (e: PropertyExpressionParseException) {
                if (!has(e)) mistakes += e
                throw e
            }
    }

    /**
     * The places at which a search has been before without success. To come to one of them again is to fail there
     * again, and with the same mistake if it was a mistake that the search came upon: without that, it would
     * depend on the way to a place whether what is wrong there gets reported.
     */
    private class DeadEnds<P> {
        private val mistakes = HashMap<P, PropertyExpressionParseException?>()

        inline fun unlessKnown(place: P, search: () -> Boolean): Boolean {
            if (mistakes.containsKey(place)) {
                mistakes[place]?.let { throw it }
                return false
            }
            mistakes[place] = null
            return try {
                search()
            } catch (e: PropertyExpressionParseException) {
                mistakes[place] = e
                throw e
            }
        }
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
        val footing = if (middle < 0) Footing.ANY else Footing.BESIDE_STRING
        // With many optional components there are many ways to arrive at the same place
        val deadEnds = DeadEnds<Triple<Int, Int, Int>>()

        fun finish(): Boolean = next(ParsedProperty.Compound(values.toList()))

        fun fromRight(i: Int, start: Int, end: Int): Boolean =
            deadEnds.unlessKnown(Triple(i, start, end)) {
                if (i == middle) {
                    matchAll(components[i], start, end) { value ->
                        values[i] = value
                        finish()
                    }
                } else {
                    matchEnd(components[i], start, end, footing) { valueStart, value ->
                        values[i] = value
                        fromRight(i - 1, start, valueStart)
                    }
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
            return deadEnds.unlessKnown(Triple(i, start, hi)) {
                matchStart(components[i], start, hi, footing) { valueEnd, value ->
                    values[i] = value
                    fromLeft(i + 1, valueEnd)
                }
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
        footing: Footing,
        next: (end: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean = when (type) {
        is PropertyType.Optional ->
            optional(type, footing, isBehind = false, { next(lo, null) }) { presentFooting ->
                matchStart(type.type, lo, hi, presentFooting) { end, value -> end > lo && next(end, value) }
            }
        is PropertyType.Compound -> {
            val values = arrayOfNulls<ParsedProperty>(type.components.size)
            val deadEnds = DeadEnds<Pair<Int, Int>>()
            fun from(i: Int, start: Int): Boolean {
                if (i == values.size) return next(start, ParsedProperty.Compound(values.toList()))
                return deadEnds.unlessKnown(i to start) {
                    matchStart(type.components[i], start, hi, footing) { end, value ->
                        values[i] = value
                        from(i + 1, end)
                    }
                }
            }
            from(0, lo)
        }
        is PropertyType.Alternate -> firstFitting(type) { index, alternative, rest ->
            matchStart(alternative, lo, hi, footing) { end, value ->
                rest.of { next(end, ParsedProperty.Alternate(index, value)) }
            }
        }
        is PropertyType.UnquotedStringEnum -> enumAtStart(type, lo, hi, next)
        PropertyType.UnquotedStringLiteral -> error("An unquoted string is matched last, not from the left")
        PropertyType.Expression, is PropertyType.TypedExpression -> expressionAtStart(type, lo, hi, footing, next)
        // (A dimension is known by what it starts with, and needs no telling from the words of a string)
        PropertyType.Dimension -> expressionAtStart(type, lo, hi, Footing.ANY, next)
    }

    /**
     * Matches an optional component, which is behind the unquoted string or with [isBehind] false in front of it:
     * [present] matches what it is of with the footing it is given, and [absent] goes on without it.
     *
     * The component is there if it can be, and left out if it cannot or if the rest of the text then does not
     * match. Around an unquoted string there is more to it, because the words of an expression may be words of
     * the string instead. So an expression is first only taken for one if it evidently is one. If it is not, the
     * component is left out, which leaves the words to the string. Only if they turn out not to be words of the
     * string are they read as the expression that they could also be:
     *
     * * if the text does not match with the component left out, as when a component that has to be there stands
     *   between the words and the string. (Inside of something else that may be left out, that is for the
     *   something else to find out, which is then left out first.)
     * * if the mistake that the string then has is one that says it ends before the words on this side of it:
     *   behind a string in quotes, as in `"Helvetica" weight`, a word cannot belong to the string. The words of
     *   the component are then an expression unless they evidently are none of the type. If they are not, or
     *   if the text does not match with them being one, the mistake of the string is what gets reported.
     */
    private inline fun optional(
        type: PropertyType.Optional,
        footing: Footing,
        isBehind: Boolean,
        absent: () -> Boolean,
        present: (Footing) -> Boolean,
    ): Boolean {
        if (footing == Footing.ANY || footing == Footing.AFTER_ALL) return present(footing) || absent()
        if (present(Footing.EVIDENT)) return true
        if (!type.type.hasExpression()) return absent()
        val textEnd = try {
            if (absent()) return true
            null
        } catch (e: PropertyExpressionParseException) {
            if (!isTextEnd(e, isBehind)) throw e
            e
        }
        if (textEnd == null) return footing == Footing.BESIDE_STRING && present(Footing.ANY)
        val isExpression = try {
            present(Footing.AFTER_ALL)
        } catch (e: PropertyExpressionParseException) {
            false
        }
        if (!isExpression) throw textEnd
        return true
    }

    /**
     * Whether [mistake] is one of an unquoted string that may end before the words at the end of its text; or
     * with [isBehind] false, that may begin after the words at the start of it.
     */
    private fun isTextEnd(mistake: PropertyExpressionParseException, isBehind: Boolean): Boolean =
        mistake in (if (isBehind) withWordsBehind else withWordsInFront)

    /** Whether [mistake] is one of an unquoted string that says where the string ends or begins. */
    private fun isTextEnd(mistake: PropertyExpressionParseException): Boolean =
        mistake in withWordsBehind || mistake in withWordsInFront

    /**
     * Matches [type], which has no unquoted string in it, at the end of the text between [lo] and [hi]. `next` is
     * given where the match starts, which is after whitespace or at [lo].
     */
    private fun matchEnd(
        type: PropertyType,
        lo: Int,
        hi: Int,
        footing: Footing,
        next: (start: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean = when (type) {
        is PropertyType.Optional ->
            optional(type, footing, isBehind = true, { next(hi, null) }) { presentFooting ->
                matchEnd(type.type, lo, hi, presentFooting) { start, value -> start < hi && next(start, value) }
            }
        is PropertyType.Compound -> {
            val values = arrayOfNulls<ParsedProperty>(type.components.size)
            val deadEnds = DeadEnds<Pair<Int, Int>>()
            fun from(i: Int, end: Int): Boolean {
                if (i < 0) return next(end, ParsedProperty.Compound(values.toList()))
                return deadEnds.unlessKnown(i to end) {
                    matchEnd(type.components[i], lo, end, footing) { start, value ->
                        values[i] = value
                        from(i - 1, start)
                    }
                }
            }
            from(values.lastIndex, hi)
        }
        is PropertyType.Alternate -> firstFitting(type) { index, alternative, rest ->
            matchEnd(alternative, lo, hi, footing) { start, value ->
                rest.of { next(start, ParsedProperty.Alternate(index, value)) }
            }
        }
        is PropertyType.UnquotedStringEnum -> enumAtEnd(type, lo, hi, next)
        PropertyType.UnquotedStringLiteral -> error("An unquoted string is matched last, not from the right")
        PropertyType.Expression, is PropertyType.TypedExpression -> expressionAtEnd(type, lo, hi, footing, next)
        PropertyType.Dimension -> expressionAtEnd(type, lo, hi, Footing.ANY, next)
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

    /** What an expression of [type] is expected to evaluate to, if the type says. */
    private fun expectedTypeOf(type: PropertyType): PropertyDataType? =
        (type as? PropertyType.TypedExpression)?.expectedType

    /**
     * Whether the text of [parser] starts in a way that settles its being an expression of [type]: after any
     * opening parentheses or sign, with a literal of the kind that is expected. Such an expression is held to
     * being a right one, so that a mistake in it gets reported rather than read as text.
     */
    private fun startsEvidently(type: PropertyType, parser: PropertyExpressionParser): Boolean =
        parser.leadingLiteral()?.isOf(expectedTypeOf(type)) ?: false

    /**
     * Whether [expression] is evidently an expression, rather than words of an unquoted string that happen to
     * read as one. A call is, and a literal of the [expected] kind; and so is a calculation that has a literal or
     * a call anywhere in it, of whatever kind: `x * 2s` is evident where an integer is expected and `2s` is not.
     *
     * Names alone do not show it, and neither do the times and the easings of an animation, which an animation
     * between unquoted strings has as well. Of a list and of an animation every value has to show it, since names
     * with commas or dots between them are a common thing for a string to be.
     */
    private fun isEvident(expression: PropertyExpression, expected: PropertyDataType?): Boolean = when (expression) {
        is PropertyExpression.Variable -> false
        is PropertyExpression.FunctionCall -> true
        is PropertyExpression.Unary -> isEvident(expression.operand, expected)
        is PropertyExpression.Binary -> hasLiteralOrCall(expression)
        is PropertyExpression.Vector -> expression.components.all { isEvident(it, expected) }
        is PropertyExpression.Animation -> expression.keyframes.all { isEvident(it.value, expected) }
        else -> expression.isOf(expected)
    }

    /** Whether there is a literal or a call among the things that [expression] takes its value from. */
    private fun hasLiteralOrCall(expression: PropertyExpression): Boolean = when (expression) {
        is PropertyExpression.Variable -> false
        is PropertyExpression.Unary -> hasLiteralOrCall(expression.operand)
        is PropertyExpression.Binary -> hasLiteralOrCall(expression.left) || hasLiteralOrCall(expression.right)
        is PropertyExpression.Vector -> expression.components.any { hasLiteralOrCall(it) }
        is PropertyExpression.Animation -> expression.keyframes.any { hasLiteralOrCall(it.value) }
        else -> true
    }

    /**
     * Whether [expression] could be one of the [expected] type as far as can be told without evaluating it:
     * a literal of another kind is not, and neither is a list or an animation that has one among its values.
     */
    private fun couldBe(expression: PropertyExpression, expected: PropertyDataType?): Boolean = when (expression) {
        is PropertyExpression.Variable, is PropertyExpression.FunctionCall, is PropertyExpression.Binary -> true
        is PropertyExpression.Unary -> couldBe(expression.operand, expected)
        is PropertyExpression.Vector -> expression.components.all { couldBe(it, expected) }
        is PropertyExpression.Animation -> expression.keyframes.all { couldBe(it.value, expected) }
        else -> expression.isOf(expected)
    }

    /**
     * Whether this literal is of the kind that an expression has which is expected to be of [type]. Whether a
     * number is written as an integer is not held against it: `700.0` is as much a number as `700`.
     */
    private fun PropertyExpression.isOf(type: PropertyDataType?): Boolean = when (type) {
        null -> true
        PropertyDataType.Integer, PropertyDataType.Number ->
            this is PropertyExpression.IntegerLiteral || this is PropertyExpression.NumberLiteral
        PropertyDataType.Time -> this is PropertyExpression.TimeLiteral
        PropertyDataType.Dimension -> this is PropertyExpression.DimensionLiteral
        PropertyDataType.Color -> this is PropertyExpression.ColorLiteral
        PropertyDataType.String -> this is PropertyExpression.StringLiteral
        PropertyDataType.Percent -> this is PropertyExpression.PercentageLiteral
        PropertyDataType.SemanticTag -> this is PropertyExpression.SemanticTag
        PropertyDataType.Boolean -> this is PropertyExpression.BooleanLiteral
        PropertyDataType.Char -> this is PropertyExpression.StringLiteral && (value.length == 1 || value.isEmpty())
        PropertyDataType.Vec2 -> this is PropertyExpression.Vector && this.components.size==2 && this.isLiteralSubexpression
        PropertyDataType.Vec3 -> this is PropertyExpression.Vector && this.components.size==3 && this.isLiteralSubexpression
        PropertyDataType.Vec4 -> this is PropertyExpression.Vector && this.components.size==4 && this.isLiteralSubexpression
        PropertyDataType.Ratio -> this is PropertyExpression.RatioLiteral
    }

    /** Notes that the text at [start] does not start an expression of [type]. */
    private fun notAnExpression(type: PropertyType, parser: PropertyExpressionParser, start: Int): Boolean {
        val unreadable = parser.leadingError()
        if (unreadable != null) return mismatch(unreadable)
        return mismatch(expectationOf(type), start)
    }

    private fun expectationOf(type: PropertyType): String =
        if (type == PropertyType.Dimension) A_DIMENSION else "a value"

    /**
     * The longest expression at the start of the text is the component; the word after it has to be another one.
     *
     * With [Footing.EVIDENT], it is only the component if it is evidently an expression. Whether it is one is
     * then not held against the text: the other ways of reading it will say what is wrong with it, if anything
     * is. With [Footing.AFTER_ALL], it is not the component if it evidently is none of the type.
     */
    private fun expressionAtStart(
        type: PropertyType,
        lo: Int,
        hi: Int,
        footing: Footing,
        next: (end: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean {
        val start = skipWhitespace(lo, hi)
        val parser = PropertyExpressionParser(input, start, hi, isComponent = true)
        val isEvidentOnly = footing == Footing.EVIDENT
        if (!startsLike(type, parser)) return !isEvidentOnly && notAnExpression(type, parser, start)
        // Once it is this component, a mistake in it is a mistake rather than a reason to look elsewhere
        val isSettled = !isEvidentOnly || startsEvidently(type, parser)
        val expression = try {
            parser.parseLeading()
        } catch (e: PropertyExpressionParseException) {
            if (isSettled) throw e
            return false
        }
        val end = parser.consumedEnd
        if (end < hi && !input[end].isWhitespace()) {
            if (!isSettled) return false
            throw parser.unreadableNext ?: PropertyExpressionParseException(
                "Expected whitespace before ${found(end, hi)}",
                end until wordEnd(end, hi),
            )
        }
        val expected = expectedTypeOf(type)
        if (isEvidentOnly && !isEvident(expression, expected)) return false
        if (footing == Footing.AFTER_ALL && !couldBe(expression, expected)) return false
        return next(end, expression)
    }

    /**
     * The component is the longest run of words at the end of the text that reads as one expression of [type].
     * So the words are tried as its start from the left, and the first from which an expression runs exactly to
     * the end of the text is the one; if the rest of the text then does not match, the search goes on.
     *
     * Two things keep this from reading the text over and over. A word inside an expression that ended early
     * gets no try of its own, since what starts there ends where that expression did; and neither does a word
     * in front of text that cannot be read, once an attempt has come to nothing there. And the search is given
     * up when it has read a multiple of the text, which only happens to text that is made to keep it busy.
     *
     * With [Footing.EVIDENT], that longest run is only the component if it is evidently an expression, and the
     * search does not go on to shorter ones: what they would leave over is the front of an expression, not a
     * string. Nothing is held against the text if there is no such run, as other ways of reading it follow. Only
     * where all of the text ends in the midst of a run that starts like an expression of the type is that
     * reported. With [Footing.AFTER_ALL], a run is not the component if it evidently is none of the type. In
     * neither case is it the component if it follows an `@`, which makes it the time of the keyframe in front
     * of it.
     */
    private fun expressionAtEnd(
        type: PropertyType,
        lo: Int,
        hi: Int,
        footing: Footing,
        next: (start: Int, value: ParsedProperty?) -> Boolean,
    ): Boolean {
        val end = trimEnd(lo, hi)
        val isCutOff = end < trimEnd(0, input.length)
        val isEvidentOnly = footing == Footing.EVIDENT
        val expected = expectedTypeOf(type)
        var firstError: PropertyExpressionParseException? = null
        var isFound = false
        val groupEnds = HashMap<Int, Int>()
        // The latest attempt that could not get to the end of the text, and the place that nothing which starts
        // inside of it gets beyond either: where its expression ended, or where the text cannot be read
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
                        val runsOut = e.range.first >= end
                        // Where all of the text ends in the midst of what starts like an expression of the type,
                        // that is a mistake in an expression rather than a sign of text
                        val isCutShort = runsOut && !isCutOff && !isTime(lo, candidate) && startsEvidently(type, parser)
                        if (isEvidentOnly && isCutShort) throw e
                        // Running out of text where it was cut off for the components behind is not a mistake in it
                        if (firstError == null && !(isCutOff && runsOut)) firstError = e
                        null
                    }
                    if (expression != null && parser.consumedEnd < end) {
                        early = parser
                        earlyEnd = parser.consumedEnd
                    } else if (expression == null && parser.unreadableFrom >= 0) {
                        early = parser
                        earlyEnd = parser.unreadableFrom
                    } else if (expression != null && isEvidentOnly) {
                        val isShown = !isTime(lo, candidate) && isEvident(expression, expected)
                        return isShown && next(candidate, expression)
                    } else if (expression != null && footing == Footing.AFTER_ALL) {
                        if (!isTime(lo, candidate) && couldBe(expression, expected)) {
                            isFound = true
                            if (next(candidate, expression)) return true
                        }
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
        if (isFound || isEvidentOnly) return false
        firstError?.let { return mismatch(it) }
        if (firstWord == end) return missing(expectationOf(type), end)
        // The last word is where the expression should have been at the latest
        return notAnExpression(type, PropertyExpressionParser(input, lastWord, end, isComponent = true), lastWord)
    }

    /** Whether the word at [word] follows an `@` in the text from [lo] on, which makes it the time of a keyframe. */
    private fun isTime(lo: Int, word: Int): Boolean {
        val before = trimEnd(lo, word)
        return before > lo && input[before - 1] == '@'
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

    /**
     * What the text between [lo] and [hi] is if it is made of options of [type] and nothing else: one of them, or
     * an animation between them, in parentheses or not. Null if it is anything else.
     */
    private fun optionsIn(type: PropertyType.UnquotedStringEnum, lo: Int, hi: Int): PropertyExpression? =
        try {
            unquoted(lo, hi, type.options)
        } catch (e: PropertyExpressionParseException) {
            // Whatever this is, it is not this enum; it may still be part of an unquoted string next to it
            null
        }

    /**
     * The unquoted string that the text between [lo] and [hi] is, which is not blank. If the mistake in it is
     * that it has words behind its end, or in front of its start, that is put on record: the words may be an
     * expression that was left out in their favor, which `optional` then finds out.
     */
    private fun unquotedString(lo: Int, hi: Int): PropertyExpression? {
        val start = skipWhitespace(lo, hi)
        try {
            return unquoted(start, trimEnd(start, hi), null)
        } catch (e: PropertyExpressionParseException) {
            if (e === wordsBehind) withWordsBehind += e
            if (e === wordsInFront) withWordsInFront += e
            throw e
        }
    }

    /**
     * Reads the text between [lo] and [hi], which is not empty and has no whitespace at its ends, as an unquoted
     * string or as an animation between unquoted strings. With [options], each string has to be one of them, and
     * the result is null if one is not.
     */
    private fun unquoted(lo: Int, hi: Int, options: List<String>?): PropertyExpression? {
        findPartners(lo, hi)
        textFrom = lo
        textTo = hi
        // Each part of an animation is within the depth limit by itself, but nested in one another they may not be
        return unquotedPart(lo, hi, options, 0, true)?.also { PropertyExpressionParser(input).checkDepth(it) }
    }

    /**
     * The part of an unquoted string between [lo] and [hi], which is nested [depth] deep in it. [isOutermost] says
     * that it is not inside of parentheses, where what is wrong with it would say nothing about the whole string.
     */
    private fun unquotedPart(
        lo: Int,
        hi: Int,
        options: List<String>?,
        depth: Int,
        isOutermost: Boolean,
    ): PropertyExpression? {
        if (input[lo] == '(' && partner(lo) == hi - 1) {
            // Parentheses around all of it only group, as they do in an expression
            if (depth >= PropertyExpressionParser.MAX_DEPTH) fail(PropertyExpressionParser.TOO_DEEP, lo, lo + 1)
            val start = skipWhitespace(lo + 1, hi - 1)
            val end = trimEnd(start, hi - 1)
            if (start < end) return unquotedPart(start, end, options, depth + 1, false)
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
                val range = i until operator.consumedEnd
                keyframes += keyframe(valueStart, i, range, options, depth, isOutermost) ?: return null
                operatorStart = i
                i = operator.consumedEnd
                valueStart = i
            } else {
                i = stepOver(i, hi)
            }
        }
        if (keyframes.isEmpty()) return plainText(lo, hi, options, isOutermost)

        val range = operatorStart until valueStart
        keyframes += keyframe(valueStart, hi, range, options, depth, isOutermost) ?: return null
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
        isOutermost: Boolean,
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
            fail(NO_VALUE + takenElsewhere(lo, hi), operator.first, operator.last + 1)
        }
        val value = unquotedPart(start, end, options, depth + 1, isOutermost) ?: return null
        if (at < 0) return PropertyExpression.Keyframe(value)
        if (isBlank(at + 1, hi)) fail("Expected a time after '@'", at, at + 1)
        return PropertyExpression.Keyframe(value, time(at + 1, hi))
    }

    /**
     * What there is to add about a keyframe without a value between [lo] and [hi], if that is at an end of the
     * string and the text goes on there: the next word was taken for another component, and may well have been
     * meant for the animation.
     */
    private fun takenElsewhere(lo: Int, hi: Int): String {
        val word = when {
            hi == textTo && !isBlank(textTo, input.length) -> found(textTo, input.length)
            lo == textFrom && !isBlank(0, textFrom) -> wordBefore(textFrom)
            else -> return ""
        }
        return ", and $word is not read as part of this text"
    }

    /** What an error message says is in front of [position]: the word that ends there. */
    private fun wordBefore(position: Int): String {
        val end = trimEnd(0, position)
        var start = end
        while (start > 0 && !input[start - 1].isWhitespace()) start--
        val word = input.substring(start, end)
        return if (word.length <= MAX_SHOWN_WORD) "'$word'" else "'...${word.takeLast(MAX_SHOWN_WORD)}'"
    }

    /** The time of a keyframe, which the text between [lo] and [hi] has to be all of. */
    private fun time(lo: Int, hi: Int): PropertyExpression =
        try {
            PropertyExpressionParser(input, lo, hi).parseOperand()
        } catch (e: PropertyExpressionParseException) {
            // At the end of the string, words behind a time may mean that the string ends where the time does
            if (hi == textTo && hasWordsBehindTime(lo, hi)) wordsBehind = e
            throw e
        }

    /** Whether the text between [lo] and [hi] starts with all that the time of a keyframe can be, and goes on. */
    private fun hasWordsBehindTime(lo: Int, hi: Int): Boolean {
        val parser = PropertyExpressionParser(input, lo, hi)
        return try {
            parser.parseLeadingOperand()
            parser.consumedEnd < hi
        } catch (e: PropertyExpressionParseException) {
            false
        }
    }

    /** The text between [lo] and [hi] as a string, with each run of whitespace in it turned into one space. */
    private fun plainText(lo: Int, hi: Int, options: List<String>?, isOutermost: Boolean): PropertyExpression? {
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
        failOnQuotedPart(lo, hi, isOutermost)
        return stringLiteral(text, lo, hi)
    }

    /**
     * Refuses unquoted text in which one of the words, or a run of them, is in quotes of its own. Taken as it
     * stands, such text would have the quotes in it, which is hardly what was meant.
     */
    private fun failOnQuotedPart(lo: Int, hi: Int, isOutermost: Boolean) {
        var i = lo
        while (i < hi) {
            val close = partner(i)
            val isQuoted = (input[i] == '"' || input[i] == '\'') && close in (i + 1) until hi &&
                (i == lo || input[i - 1].isWhitespace()) && (close == hi - 1 || input[close + 1].isWhitespace())
            if (isQuoted) {
                val mistake = PropertyExpressionParseException(
                    "Text in quotes cannot be combined with text outside of them: quote all of it",
                    i..close,
                )
                // It may be that the string is what is in the quotes, and ends and begins where they do
                if (isOutermost && close < hi - 1) wordsBehind = mistake
                if (isOutermost && i > lo) wordsInFront = mistake
                throw mistake
            }
            i = stepOver(i, hi)
        }
    }

    private fun stringLiteral(text: String, start: Int, end: Int): PropertyExpression =
        PropertyExpression.StringLiteral(text).apply { sourceRange = start until end }
}
