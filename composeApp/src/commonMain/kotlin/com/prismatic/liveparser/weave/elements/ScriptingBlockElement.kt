package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope
import com.prismatic.liveparser.weave.grammar.WeaveParseError
import com.prismatic.liveparser.weave.grammar.isCodeBlockStart
import com.prismatic.liveparser.weave.grammar.isScriptStart
import com.prismatic.liveparser.weave.grammar.parseError
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyExpressionContext
import com.prismatic.liveparser.weave.properties.PropertyExpressionParseException
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.evaluate
import com.prismatic.liveparser.weave.properties.parsePropertyExpression
import com.prismatic.liveparser.weave.properties.parsePropertyString
import com.prismatic.liveparser.weave.properties.provideVariables
import com.prismatic.liveparser.weave.util.offset
import kotlin.text.clear

class ScriptingBlockElement private constructor(pm: PropertyManager) :
    TypedWeaveElement<ScriptingBlockElement>(pm)
{ constructor() : this(PropertyManager())

    var codeLanguage: String by mutableStateOf("")
        private set

    var code: String by mutableStateOf("")
        private set

    val variables: Map<String, PropertyExpression> field = mutableStateMapOf()

    override fun makeInstance(): ScriptingBlockElement  = ScriptingBlockElement()
    override val elementTypeName: String get() = "ScriptingBlock"

    override fun debugString(): String = if(variables.isNotEmpty()) {
        "ScriptingBlock {\n" + variables.entries.joinToString("\n") { "  ${it.key} = ${it.value.evaluate(PropertyExpressionContext.Empty).toFlatString()}" } + "\n}"
    } else {
        "ScriptingBlock[$codeLanguage]:${code.replace("\n", "\\n")}"
    }

    override fun WeaveEntityParseScope.recognize(): Boolean = firstLine.isScriptStart

    override fun WeaveEntityParseScope.parse() {
        val languageSpec = firstLine.trimmedBody.drop(1).trim()
        val codeLines = bodyLines.drop(1).dropLast(1)
        code = codeLines.joinToString("\n")
        if( languageSpec.isNotEmpty()) {
            codeLanguage = languageSpec
            variables.clear()
        } else { //if( codeLines.all { it.isBlank() || it.isVariableAssignment()  } && !codeLines.all { it.isBlank() }) {
            codeLanguage = ""
            val unused = variables.keys.toMutableSet()
            val localVariables = mutableMapOf<String, PropertyExpression>()
            val exprContext = PropertyExpressionContext.Empty.provideVariables { localVariables[it] }
            for ((index, line) in codeLines.withIndex()) {
                if( line.isBlank() ) continue
                if( '=' !in line ) {
                    parseError(WeaveParseError.NotAssignment, index+1, line.takeWhile { it.isWhitespace() }.count() .. line.length)
                    continue
                }
                val name = line.substringBefore('=',"").trim()
                if( !name.isVariableName() ) {
                    parseError(WeaveParseError.InvalidSymbolName(name), index+1, line.takeWhile { it.isWhitespace() }.count() ..< line.indexOf('='))
                    continue
                }
                val value = parsePropertyStringHandlingErrors(line.substringAfter('='), PropertyType.Expression)
                if( value is PropertyExpression ) {
                    val resolvedValue = value.evaluate(exprContext)
                    variables[name] = resolvedValue
                    localVariables[name] = resolvedValue
                    unused.remove(name)
                } else if( value is ParsedProperty.Error ) {
                    parseError(WeaveParseError.InvalidExpression(value.error), index+1, value.range.offset(line.indexOf('=')))
                }
            }
            unused.forEach { variables.remove(it) }
//        } else {
//            variables.clear()
//            codeLanguage = ""
        }

    }

}

private fun parsePropertyStringHandlingErrors(s: String, type: PropertyType) : ParsedProperty{
    return runCatching { parsePropertyString(s,type) }
        .recover { ParsedProperty.Error(it.message ?: it.toString(), null, (it as? PropertyExpressionParseException)?.range ?: IntRange.EMPTY) }
        .getOrNull() ?: PropertyExpression.of("null")
}

private fun String.isVariableAssignment(): Boolean = substringBefore('=',"").trim().isVariableName()

private fun String.isVariableName(): Boolean = isNotEmpty() && first().isSymbolStartChar && all { it.isSymbolChar }

private val Char.isSymbolStartChar: Boolean get() = isLetter() || this=='_'
private val Char.isSymbolChar: Boolean get() = isLetterOrDigit() || this=='_'