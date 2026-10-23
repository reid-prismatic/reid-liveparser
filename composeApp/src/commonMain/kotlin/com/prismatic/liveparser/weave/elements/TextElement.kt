package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.prismatic.liveparser.pt
import com.prismatic.liveparser.weave.elements.generic.LayoutProperties
import com.prismatic.liveparser.weave.elements.generic.LayoutPropertiesImpl
import com.prismatic.liveparser.weave.elements.generic.TimingProperties
import com.prismatic.liveparser.weave.elements.generic.TimingPropertiesImpl
import com.prismatic.liveparser.weave.elements.generic.TransformProperties
import com.prismatic.liveparser.weave.elements.generic.TransformPropertiesImpl
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.isTextEntityStart
import com.prismatic.liveparser.weave.grammar.textEntityPrefixes

class TextElement private constructor(pm: PropertyManager) :
    LayoutProperties by LayoutPropertiesImpl(pm),
    TimingProperties by TimingPropertiesImpl(pm),
    TransformProperties by TransformPropertiesImpl(pm),
    ConcreteElement<TextElement>(
        propertyManager = pm,
        namespace = "text",
        shorthandRecognizer = { firstLine.isTextEntityStart }
    ) { constructor() : this(PropertyManager())

    override val elementTypeName: String get() = "Text"

    var text : String by mutableStateOf("")

    val fontSize by prop(12.pt)
    val typeface by prop("") {
        acceptUnquotedString()
    }
    val weight by prop(FontWeight.Bold)
    val fontStyle by prop(FontStyle.Normal)
    val font by prop(fontSize, typeface, weight, fontStyle)
    var textType : String by mutableStateOf("")
    val color by prop(Color.Black)

    override fun parseBody(bodyLines: List<String>) {
        text = bodyLines.joinToString("\n") {
            if( it.startsWith('|'))
                it.drop(1).trimStart()
            else
                it
        }
    }

    override fun parseArguments(arguments: String) {
        textType = arguments.trim()
    }

    override fun parseShorthandBody(bodyLines: List<String>) {
        val firstBodyLine = bodyLines.first()
        val textPrefix = textEntityPrefixes.entries.firstOrNull { firstBodyLine.startsWith(it.key) }
        if( textPrefix != null) {
            val (prefix, type) = textPrefix
            textType = type
            text = (listOf(firstBodyLine.drop(prefix.length)) + bodyLines.drop(1)).joinToString("\n") {
                if( it.startsWith('|'))
                    it.drop(1).trimStart()
                else
                    it
            }
        } else {
            textType = ""
            text = bodyLines.joinToString("\n") {
                if( it.startsWith('|'))
                    it.drop(1).trimStart()
                else
                    it
            }
        }
    }

    override fun makeInstance(): TextElement = TextElement()
    override fun debugString(): String = "text[${textType.ifBlank { "normal" }}]:${text.replace("\n", "\\n")}"
}