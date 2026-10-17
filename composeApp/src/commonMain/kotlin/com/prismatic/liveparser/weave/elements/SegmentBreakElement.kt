package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.ParsedProperty
import com.prismatic.liveparser.PropertyExpressionContext
import com.prismatic.liveparser.PropertyType
import com.prismatic.liveparser.evaluate
import com.prismatic.liveparser.parsePropertyString
import com.prismatic.liveparser.toFlatString
import com.prismatic.liveparser.weave.grammar.LWElement
import com.prismatic.liveparser.weave.grammar.isSegmentBreak

class SegmentBreakElement() : LWElement<SegmentBreakElement>() {
    var transition by mutableStateOf<ParsedProperty?>(null)
    override fun LWEntityParseScope.recognize(): Boolean = firstLine.isSegmentBreak
    override fun makeInstance(): SegmentBreakElement = SegmentBreakElement()
    override fun LWEntityParseScope.parse() {
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