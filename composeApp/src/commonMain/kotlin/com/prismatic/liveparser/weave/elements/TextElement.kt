package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.prismatic.liveparser.pt
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.PropertyAccumulator
import com.prismatic.liveparser.weave.grammar.isTextEntityStart
import com.prismatic.liveparser.weave.grammar.textEntityPrefixes
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.time.MediaTime

class TextElement() : ConcreteElement<TextElement>(
    namespace = "text",
    shorthandRecognizer = { firstLine.isTextEntityStart }
) {
    override val elementTypeName: String get() = "Text"

    var text : String by mutableStateOf("")

    val fontSize by prop(12.pt)
    val typeface by prop("")
    val weight by prop(FontWeight.Bold)
    val fontStyle by prop(FontStyle.Normal)
    val font by prop(fontSize, typeface, weight, fontStyle)
    var textType : String by mutableStateOf("")
    val position by prop(Vec2.ZERO)
    val duration by prop(MediaTime.Unspecified)
    val color by prop(Color.Black)
    val offset by prop(Vec2.ZERO) {
        accumulateMultipleInstances(PropertyAccumulator.Sum)
    }

    override fun parseBody(bodyLines: List<String>) {
        text = bodyLines.joinToString("\n")
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
            text = (listOf(firstBodyLine.drop(prefix.length)) + bodyLines.drop(1)).joinToString("\n")
        } else {
            textType = ""
            text = bodyLines.joinToString("\n")
        }
    }

    override fun makeInstance(): TextElement = TextElement()
    override fun debugString(): String = "text[${textType.ifBlank { "normal" }}]:${text.replace("\n", "\\n")}"
}