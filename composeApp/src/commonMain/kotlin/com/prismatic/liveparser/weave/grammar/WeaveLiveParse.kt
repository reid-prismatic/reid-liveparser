package com.prismatic.liveparser.weave.grammar

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.prismatic.liveparser.LiveParse
import com.prismatic.liveparser.rememberLiveParse
import com.prismatic.liveparser.weave.elements.StyleElement

interface WeaveLiveParse : LiveParse<WeaveLineType, WeaveElement> {
    val styleElements: Map<String, List<StyleElement>>
}

@Composable
public fun TextFieldState.rememberWeaveLiveParse(): WeaveLiveParse {
    val liveParse = rememberLiveParse(
        initialLineType = WeaveLineType.None,
        computeContext = { line, prevLineType ->
            getWeaveLineType(line, prevLineType)
        },
        parseEntity = {
            parseWeaveElement()
        }
    )
    val weaveLiveParse = remember(liveParse) { WeaveLiveParseImpl(liveParse) }
    return weaveLiveParse
}

private class WeaveLiveParseImpl(private val baseLaveParse : LiveParse<WeaveLineType, WeaveElement>) : WeaveLiveParse, LiveParse<WeaveLineType, WeaveElement> by baseLaveParse {
    override val styleElements: Map<String, List<StyleElement>> by derivedStateOf {baseLaveParse.entities.map { it.entityData }.filterIsInstance<StyleElement>().groupBy { it.styleName }}
}