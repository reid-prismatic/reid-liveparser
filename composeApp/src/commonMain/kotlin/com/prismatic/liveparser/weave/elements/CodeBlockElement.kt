package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.elements.generic.LayoutProperties
import com.prismatic.liveparser.weave.elements.generic.LayoutPropertiesImpl
import com.prismatic.liveparser.weave.elements.generic.TimingProperties
import com.prismatic.liveparser.weave.elements.generic.TimingPropertiesImpl
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope
import com.prismatic.liveparser.weave.grammar.isCodeBlockStart

class CodeBlockElement private constructor(pm: PropertyManager) :
    LayoutProperties by LayoutPropertiesImpl(pm),
    TimingProperties by TimingPropertiesImpl(pm),
    TypedWeaveElement<CodeBlockElement>(pm)
{ constructor() : this(PropertyManager())

    var codeLanguage: String by mutableStateOf("")
        private set

    var code: String by mutableStateOf("")
        private set

    override fun makeInstance(): CodeBlockElement  = CodeBlockElement()
    override val elementTypeName: String get() = "CodeBlock"

    override fun debugString(): String = "CodeBlock[$codeLanguage]:${code.replace("\n", "\\n")}"

    override fun WeaveEntityParseScope.recognize(): Boolean = firstLine.isCodeBlockStart

    override fun WeaveEntityParseScope.parse() {
        codeLanguage = firstLine.trimmedBody.drop(3).trim()
        code = bodyLines.drop(1).dropLast(1).joinToString("\n")
    }

}