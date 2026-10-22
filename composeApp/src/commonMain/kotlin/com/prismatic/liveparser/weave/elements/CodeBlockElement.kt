package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope
import com.prismatic.liveparser.weave.grammar.isCodeBlockStart

class CodeBlockElement() : TypedWeaveElement<CodeBlockElement>() {

    var codeLanguage: String by mutableStateOf("")
        private set

    var code: String by mutableStateOf("")
        private set

    override fun makeInstance(): CodeBlockElement  = CodeBlockElement()
    override val elementTypeName: String get() = "CodeBlock[$codeLanguage]:${code.replace("\n", "\\n")}"

    override fun debugString(): String = "CodeBlock"

    override fun WeaveEntityParseScope.recognize(): Boolean = firstLine.isCodeBlockStart

    override fun WeaveEntityParseScope.parse() {
        codeLanguage = firstLine.trimmedBody.drop(3).trim()
        code = bodyLines.drop(1).dropLast(1).joinToString("\n")
    }

}