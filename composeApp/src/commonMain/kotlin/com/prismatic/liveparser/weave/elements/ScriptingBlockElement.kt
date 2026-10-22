package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope
import com.prismatic.liveparser.weave.grammar.isCodeBlockStart
import com.prismatic.liveparser.weave.grammar.isScriptStart
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.parsePropertyExpression
import com.prismatic.liveparser.weave.properties.parsePropertyString
import kotlin.text.clear

class ScriptingBlockElement() : TypedWeaveElement<ScriptingBlockElement>() {

    var codeLanguage: String by mutableStateOf("")
        private set

    var code: String by mutableStateOf("")
        private set

    val variables: Map<String, PropertyExpression> field = mutableStateMapOf()

    override fun makeInstance(): ScriptingBlockElement  = ScriptingBlockElement()
    override val elementTypeName: String get() = "ScriptingBlock"

    override fun debugString(): String = "ScriptingBlock[$codeLanguage]:${code.replace("\n", "\\n")}"

    override fun WeaveEntityParseScope.recognize(): Boolean = firstLine.isScriptStart

    override fun WeaveEntityParseScope.parse() {
        val languageSpec = firstLine.trimmedBody.drop(3).trim()
        val codeLines = bodyLines.drop(1).dropLast(1)
        code = codeLines.joinToString("\n")
        if( languageSpec.isNotEmpty()) {
            codeLanguage = languageSpec
            variables.clear()
        } else if( codeLines.all { it.isBlank() || it.isVariableAssignment() }) {
            codeLanguage = "vars"
            variables.clear()
            for (line in codeLines) {
                val name = line.substringBefore('=',"").trim()
                if( !name.isVariableName() )
                    continue
                val value = parsePropertyStringHandlingErrors(line.substringAfter('=').trim(), PropertyType.Expression)
                if( value is PropertyExpression ) {
                    variables[name] = value
                } else {
                    variables.remove(name)
                }
            }
        } else {
            variables.clear()
            codeLanguage = "?"
        }

    }

}

private fun parsePropertyStringHandlingErrors(s: String, type: PropertyType) : ParsedProperty{
    return runCatching { parsePropertyString(s,type) }
        .recover { ParsedProperty.Error(it.message ?: it.toString(), null) }
        .getOrNull() ?: PropertyExpression.of("null")
}

private fun String.isVariableAssignment(): Boolean = substringBefore('=',"").trim().isVariableName()

private fun String.isVariableName(): Boolean = isNotEmpty() && first().isSymbolStartChar && all { it.isSymbolChar }

private val Char.isSymbolStartChar: Boolean get() = isLetter() || this=='_'
private val Char.isSymbolChar: Boolean get() = isLetterOrDigit() || this=='_'