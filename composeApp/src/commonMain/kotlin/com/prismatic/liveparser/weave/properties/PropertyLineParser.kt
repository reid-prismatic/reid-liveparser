package com.prismatic.liveparser.weave.properties

import com.prismatic.liveparser.StringParser

data class PropertyLineParseResult(
    val success: Boolean = true,
    val errorOffset: Int = -1,
    val errorType: StringParseErrorType? = null,
    val style: String = "",
    val name: String = "",
    val value: String = "",
    val condition: String = ""
)

fun Char.isPropertyNameStartChar() = isLetter() || this == '_'
fun Char.isPropertyNameChar() = isLetterOrDigit() || this == '_' || this == '-'
fun Char.isStyleNameStartChar() = isLetterOrDigit() || this == '_'
fun Char.isStyleNameChar() = isLetterOrDigit() || this == '_'

sealed class StringParseErrorType(val message: String) {
    data class UnexpectedChar(val expected: Char, val actual: Char) : StringParseErrorType("Expected $expected, got $actual")
    data object EndExpected : StringParseErrorType("Unexpected characters")
    data object MismatchedBracket : StringParseErrorType("Mismatched brackets")
    data object UnterminatedQuote : StringParseErrorType("Unterminated quote")
    data object ExpectedPropertyName : StringParseErrorType("Expected property name")
    data object ExpectedStyleName : StringParseErrorType("Expected style reference")
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
    } catch( e: StringParser.ParseFailure ) {
        return PropertyLineParseResult(
            false,
            parser.failureOffset,
            parser.errorType,
            "", "", "", ""
        )
    }
}

private class PropertyLineParser(line: String) : StringParser(line) {

    var propertyName: String = ""
    var styleReference: String = ""
    var condition: String = ""
    var propertyValue: String = ""

    fun parse() {
        skipWhitespace()
        expect('-')
        skipWhitespace()
        if( ch=='{') {
            advance()
            skipWhitespace()
            styleReference = parseStyleName()
            skipWhitespace()
            expect('}')
            skipWhitespace()
            expectEnd()
            return
        }
        propertyName = parsePropertyName()
        if( ch=='(' ) {
            advance()
            condition = parseCondition()
            skipWhitespace()
            expect(')')
            skipWhitespace()
        }
        expect(':')
        skipWhitespace()
        propertyValue = remainingText
    }

}