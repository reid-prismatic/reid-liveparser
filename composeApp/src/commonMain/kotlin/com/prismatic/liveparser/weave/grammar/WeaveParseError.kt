package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.parser.LiveParseEntityScope

sealed class WeaveParseError(val message: String) {
    data object PropertyConditionNotInStyle : WeaveParseError("Property conditions not allowed except in style definitions")
    data class UnrecognizedProperty( val propertyName: String) : WeaveParseError("Property '$propertyName' is not valid for this kindo of element")
    data object MalformedProperty : WeaveParseError("Property line syntax in incorrect")
    data class UnexpectedChar(val expected: Char, val actual: Char) : WeaveParseError("Expected '$expected', got '$actual'")
    data class UnexpectedEnd(val expected: Char) : WeaveParseError("Expected '$expected'")
    data object EndExpected : WeaveParseError("Unexpected characters")
    data object MismatchedBracket : WeaveParseError("Mismatched brackets")
    data object UnterminatedQuote : WeaveParseError("Unterminated quote")
    data object ExpectedPropertyName : WeaveParseError("Expected property name")
    data object ExpectedStyleName : WeaveParseError("Expected style reference")
    data class InvalidPropertyValue(val reason: String) : WeaveParseError(reason)
    data class InvalidPropertyCondition(val reason: String) : WeaveParseError(reason)
    data class InvalidExpression(val reason: String) : WeaveParseError(reason)
    data class InvalidSymbolName(val symbolName: String) : WeaveParseError("Invalid symbol name '$symbolName'")
    data object NotAssignment : WeaveParseError("Only assignment expressions are allowed")
}

fun LiveParseEntityScope<WeaveLineType, WeaveElement>.parseError(
    error: WeaveParseError,
    errorLine: Int = -1,
    errorRange: IntRange = IntRange.EMPTY,
) = parseError(
    message = error.message,
    errorLine = errorLine,
    errorColumn = errorRange.first,
    sourceLength = if(errorRange.isEmpty()) 0 else errorRange.last-errorRange.first+1,
    errorData = error
)
