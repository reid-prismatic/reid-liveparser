package com.prismatic.liveparser.weave.properties

import com.prismatic.liveparser.StringParser
import com.prismatic.liveparser.weave.grammar.WeaveParseError

data class PropertyLineParseResult(
    val success: Boolean = true,
    val errorOffset: Int = -1,
    val errorType: WeaveParseError? = null,
    val style: String = "",
    val target: String = "",
    val name: String = "",
    val value: String = "",
    val condition: String = "",
    val lineNumber: Int = -1,
    val nameRange: IntRange = IntRange.EMPTY,
    val valueRange: IntRange = IntRange.EMPTY,
    val conditionRange: IntRange = IntRange.EMPTY,
    val targetRange: IntRange = IntRange.EMPTY,
)

fun Char.isPropertyNameStartChar() = isLetter() || this == '_'
fun Char.isPropertyNameChar() = isLetterOrDigit() || this == '_' || this == '-'
fun Char.isStyleNameStartChar() = isLetterOrDigit() || this == '_'
fun Char.isStyleNameChar() = isLetterOrDigit() || this == '_'
fun Char.isTargetNameStartChar() = isLetterOrDigit() || this == '_'
fun Char.isTargetNameChar() = isLetterOrDigit() || this == '_'

fun parsePropertyLine(propertyLine: String): PropertyLineParseResult {
    val parser = PropertyLineParser(propertyLine)
    try {
        parser.parse()
        return PropertyLineParseResult(
            success = true,
            errorOffset = -1,
            parser.errorType,
            parser.styleReference.value,
            parser.targetReference.value,
            parser.propertyName.value,
            parser.propertyValue.value,
            parser.condition.value,
            nameRange = parser.propertyName.range,
            valueRange = parser.propertyValue.range,
            conditionRange = parser.condition.range,
            targetRange = parser.targetReference.range,
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

data class ParsedStringFragment(val value: String, val range: IntRange) {
    companion object {
        val Empty = ParsedStringFragment("", IntRange.EMPTY)
    }
}

private class PropertyLineParser(line: String) : StringParser(line) {

    var propertyName: ParsedStringFragment = ParsedStringFragment.Empty
    var styleReference: ParsedStringFragment = ParsedStringFragment.Empty
    var targetReference: ParsedStringFragment = ParsedStringFragment.Empty
    var condition: ParsedStringFragment = ParsedStringFragment.Empty
    var propertyValue: ParsedStringFragment = ParsedStringFragment.Empty

    fun parse() {
        removeComment()
        skipWhitespace()
        expect('-')
        skipWhitespace()
        if( ch=='{') {
            advance()
            skipWhitespace()
            styleReference = parseStyleName()
            skipWhitespace()
            if(ch==':' && styleReference.value=="target") {
                styleReference = ParsedStringFragment.Empty
                advance()
                skipWhitespace()
                targetReference = parseTargetName()
                skipWhitespace()
            }
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
        propertyValue = extractRemaining()
    }

}