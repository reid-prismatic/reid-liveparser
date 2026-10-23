package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.StringParser
import com.prismatic.liveparser.weave.grammar.DefinitionElement
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.WeaveParseError
import com.prismatic.liveparser.weave.grammar.isStyleEntityStart
import com.prismatic.liveparser.weave.properties.ParsedStringFragment
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.parsePropertyExpression

class StyleElement private constructor(pm: PropertyManager) : DefinitionElement<StyleElement>(
    propertyManager = pm,
    namespace = "style",
    shorthandRecognizer = { firstLine.isStyleEntityStart },
) { constructor() : this(PropertyManager())
    override val elementTypeName: String get() = "Style"
    val styleDescription by prop("")
    val styleProperties by otherProps()
    var condition by mutableStateOf<PropertyExpression?>(null)
        private set

    var styleName by mutableStateOf("")
    override fun parseArguments(arguments: String) {
        val parseResult = parseStyleArgs(arguments)
        styleName = parseResult.styleName
        condition = if(parseResult.condition.isNotBlank()) {
            parsePropertyExpression(parseResult.condition)
        } else null
    }

    override fun parseShorthandBody(bodyLines: List<String>) {
        parseArguments(bodyLines.first().trim().removeSurrounding("{", "}").trim())
    }

    override fun makeInstance(): StyleElement = StyleElement()
    override fun debugString(): String = "style:$styleName"
}

private data class StyleArgsParseResult(
    val success: Boolean = true,
    val errorOffset: Int = -1,
    val errorType: WeaveParseError? = null,
    val wildcard: Boolean = false,
    val styleName: String = "",
    val condition: String = ""
)

private fun parseStyleArgs(args: String) : StyleArgsParseResult {
    val parser = StyleArgParser(args)
    try {
        parser.parse()
        return StyleArgsParseResult(
            success = true,
            wildcard = parser.wildcard,
            styleName = parser.styleName.value,
            condition = parser.condition.value
        )
    } catch ( e: StringParser.ParseFailure ) {
        return StyleArgsParseResult(
            success = false,
            errorOffset = parser.failureOffset,
            errorType = parser.errorType
        )
    }
}

private class StyleArgParser(args: String) : StringParser(args) {

    var wildcard: Boolean = false
    var styleName: ParsedStringFragment = ParsedStringFragment.Empty
    var condition: ParsedStringFragment = ParsedStringFragment.Empty

    fun parse() {
        skipWhitespace()
        if(ch=='*') {
            wildcard = true
            styleName = extract { advance() }
        } else {
            styleName = parseStyleName()
        }
        skipWhitespace()
        if(ch=='(') {
            advance()
            condition = parseCondition()
            skipWhitespace()
            expect(')')
            skipWhitespace()
        }

        expectEnd()
    }

}