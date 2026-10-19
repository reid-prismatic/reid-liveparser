package com.prismatic.liveparser.weave.properties

data class PropertyLineParseResult(
    val success: Boolean = true,
    val errorOffset: Int = -1,
    val errorType: PropertyParseErrorType? = null,
    val style: String = "",
    val name: String = "",
    val value: String = "",
    val condition: String = ""
)

fun Char.isPropertyNameStartChar() = isLetter() || this == '_'
fun Char.isPropertyNameChar() = isLetterOrDigit() || this == '_' || this == '-'
fun Char.isStyleNameChar() = isLetterOrDigit() || this == '_'

sealed class PropertyParseErrorType(val message: String) {
    data class UnexpectedChar(val expected: Char, val actual: Char) : PropertyParseErrorType("Expected $expected, got $actual")
    data object EndExpected : PropertyParseErrorType("Unexpected characters")
    data object MismatchedBracket : PropertyParseErrorType("Mismatched brackets")
    data object UnterminatedQuote : PropertyParseErrorType("Unterminated quote")
    data object ExpectedPropertyName : PropertyParseErrorType("Expected property name")
    data object ExpectedStyleName : PropertyParseErrorType("Expected style reference")
}

fun parsePropertyLine(propertyLine: String): PropertyLineParseResult {
    val parser = PropertyLineParser(propertyLine)
    try {
        parser.parse()
        return PropertyLineParseResult(
            success = true,
            errorOffset = -1,
            parser.errorType,
            parser.styleReference,
            parser.propertyName,
            parser.propertyValue,
            parser.condition
        )
    } catch( e: PropertyParseFailure ) {
        return PropertyLineParseResult(
            false,
            parser.failureOffset,
            parser.errorType,
            "", "", "", ""
        )
    }
}

private class PropertyParseFailure : Throwable()
private class PropertyLineParser(val line: String) {
    var i = 0
    val length = line.length
    inline fun peek(offset: Int = 0) : Char = if( i + offset < length ) line[i+offset] else '\u0000'
    var ch: Char = peek(0)
    var next : Char = peek(1)
    inline fun advance() {
        i++
        ch = next
        next = peek(1)
    }
    fun skipWhitespace() {
        while( ch.isWhitespace() ) {advance()}
    }
    var failed = false
    var failureOffset = -1
    var errorType: PropertyParseErrorType? = null
    fun error(type: PropertyParseErrorType) : Nothing{
        failed = true
        failureOffset=i
        errorType = type
        throw PropertyParseFailure()
    }
    inline fun expect(char: Char) {
        if( ch != char ) error(PropertyParseErrorType.UnexpectedChar(char,ch))
        advance()
    }
    inline fun expectEnd() {
        if( i < length ) error(PropertyParseErrorType.EndExpected)
    }
    fun parse() {
        skipWhitespace()
        expect('-')
        skipWhitespace()
        if( ch=='{') {
            advance()
            skipWhitespace()
            parseStyleReference()
            skipWhitespace()
            expect('}')
            skipWhitespace()
            expectEnd()
            return
        }
        parsePropertyName()
        if( ch=='(' ) {
            advance()
            parseCondition()
            skipWhitespace()
            expect(')')
            skipWhitespace()
        }
        expect(':')
        skipWhitespace()
        propertyValue = line.substring(i)
    }

    var propertyName: String = ""
    var styleReference: String = ""
    var condition: String = ""
    var propertyValue: String = ""

    inline val atEnd : Boolean get() = i >= length

    fun parsePropertyName() {
        val start = i
        if( !ch.isPropertyNameStartChar() )
            error(PropertyParseErrorType.ExpectedPropertyName)
        advance()
        while( ch.isPropertyNameChar() ) {advance()}
        propertyName = line.substring(start,i)
    }

    fun parseStyleReference() {
        val start = i
        if(!ch.isStyleNameChar())
            error(PropertyParseErrorType.ExpectedStyleName)
        advance()
        while( ch.isStyleNameChar() ) {advance()}
        styleReference = line.substring(start,i)
    }

    fun parseCondition() {
        val start = i
        var quoteChar = '\u0000'
        val brackets = CharArray(line.length)
        var bracketCount = 0
        while(!atEnd && (ch != ')' || quoteChar!='\u0000' || bracketCount>0)) {
            if(quoteChar != '\u0000') {
                if(ch==quoteChar) quoteChar = '\u0000'
            } else {
                when (ch) {
                    '(', '{', '[' -> brackets[bracketCount++] = ch
                    ')', '}', '}' -> {
                        if( bracketCount <= 0 ) break
                        if (brackets[bracketCount - 1] != ch)
                            error(PropertyParseErrorType.MismatchedBracket)
                        bracketCount--
                    }
                    '"', '\'' -> quoteChar = ch
                }
            }
            advance()
        }
        if( quoteChar != '\u0000' ) error(PropertyParseErrorType.UnterminatedQuote)
        if( bracketCount > 0 ) error(PropertyParseErrorType.MismatchedBracket)
        condition = line.substring(start,i)
    }
}