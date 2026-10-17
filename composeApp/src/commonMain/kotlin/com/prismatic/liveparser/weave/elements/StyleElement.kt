package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.weave.grammar.DefinitionElement
import com.prismatic.liveparser.weave.grammar.isStyleEntityStart

class StyleElement() : DefinitionElement<StyleElement>(
    namespace = "style",
    shorthandRecognizer = { firstLine.isStyleEntityStart },
) {
    var styleName by mutableStateOf("")
    override fun LWEntityParseScope.parse() {
        styleName = firstLine.trimmedBody.trim().removeSurrounding("{", "}").trim().substringAfter(':').trim()
    }
    override fun makeInstance(): StyleElement = StyleElement()
    override fun debugString(): String = "style:$styleName"
}