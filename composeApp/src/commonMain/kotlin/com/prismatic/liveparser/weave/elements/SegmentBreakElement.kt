package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyExpressionContext
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.evaluate
import com.prismatic.liveparser.weave.properties.parsePropertyString
import com.prismatic.liveparser.weave.properties.toFlatString
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope
import com.prismatic.liveparser.weave.grammar.isSegmentBreak

class SegmentBreakElement() : TypedWeaveElement<SegmentBreakElement>() {
    override val elementTypeName: String get() = "Break"
    var transition by mutableStateOf<ParsedProperty?>(null)
    override fun WeaveEntityParseScope.recognize(): Boolean = firstLine.isSegmentBreak
    override fun makeInstance(): SegmentBreakElement = SegmentBreakElement()
    override fun WeaveEntityParseScope.parse() {
        val argString = firstLine.trimmedBody.trimStart('-').trimStart()
        transition = runCatching {
            parsePropertyString(
                argString, PropertyType.Alternate(
                    PropertyType.Expression,
                    PropertyType.UnquotedStringLiteral,
                )
            )
        }.getOrNull()
    }
    override fun debugString(): String = "segment(transition=${transition?.evaluate(PropertyExpressionContext.Empty)?.toFlatString()})"
}