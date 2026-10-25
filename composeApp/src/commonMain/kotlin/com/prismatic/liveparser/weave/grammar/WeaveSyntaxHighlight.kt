package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.weave.elements.VoiceoverElement
import com.prismatic.liveparser.weave.properties.parsePropertyLine

data class WeaveSyntaxHighlights( val highlights : List<WeaveHighlight> ) {
    companion object {
        val None = WeaveSyntaxHighlights(emptyList())
    }
}

data class WeaveHighlight( val range: IntRange, val type: Type ) {
    enum class Type {
        Comment, PropertyValue, PropertyName, PropertyCondition, Voiceover
    }
}


fun computeWeaveSyntaxHighlights( lineText: String, lineType: WeaveLineType, element: WeaveElement): WeaveSyntaxHighlights {
    val highlights = mutableListOf<WeaveHighlight>()
    fun addHighlight( range: IntRange, type: WeaveHighlight.Type) {
        if( range.isEmpty() ) return
        highlights += WeaveHighlight(range,type)
    }

    val decomposedLine = lineText.decomposeLine()

    if( decomposedLine.commentIndex > 0 ) {
        addHighlight(decomposedLine.commentIndex .. lineText.lastIndex, WeaveHighlight.Type.Comment)
    }

    when(lineType) {
        WeaveLineType.None -> {}
        WeaveLineType.Blank -> {}
        WeaveLineType.ConcreteEntityStart, WeaveLineType.ConcreteEntityBody -> {
            if( element is VoiceoverElement ) {
                addHighlight( 0..decomposedLine.rawBody.length, WeaveHighlight.Type.Voiceover )
            }
        }
        WeaveLineType.DelayEntityStart -> {}
        WeaveLineType.Property -> {
            val parsedProperty = parsePropertyLine(decomposedLine.rawBody)
            addHighlight(parsedProperty.valueRange, WeaveHighlight.Type.PropertyValue)
            addHighlight(parsedProperty.nameRange, WeaveHighlight.Type.PropertyName)
            addHighlight(parsedProperty.conditionRange, WeaveHighlight.Type.PropertyCondition)
        }
        WeaveLineType.DefinitionEntityStart -> {}
        WeaveLineType.DefinitionEntityBody -> {}
        WeaveLineType.CodeStart -> {}
        WeaveLineType.CodeContinue -> {}
        WeaveLineType.CodeEnd -> {}
        WeaveLineType.ScriptStart -> {}
        WeaveLineType.ScriptContinue -> {}
        WeaveLineType.ScriptEnd -> {}
        WeaveLineType.SyntaxError -> {}
        WeaveLineType.SegmentBreak -> {}
    }
    return WeaveSyntaxHighlights(highlights)
}