package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.elements.generic.LayoutPropertiesImpl
import com.prismatic.liveparser.weave.elements.generic.TimingPropertiesImpl
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope
import com.prismatic.liveparser.weave.grammar.isCodeBlockStart
import com.prismatic.liveparser.weave.grammar.isDelayEntityStart
import com.prismatic.liveparser.weave.grammar.parsePropertyStringHandlingErrors
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyDataType
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyExpressionContext
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.evaluate
import com.prismatic.liveparser.weave.properties.parsePropertyString
import kotlin.time.Duration.Companion.seconds

class DelayElement private constructor(pm: PropertyManager) :
    TypedWeaveElement<DelayElement>(pm)
{ constructor() : this(PropertyManager())

    var delay by mutableStateOf<PropertyExpression?>(null)
        private set

    var parseError by mutableStateOf<String?>(null)         // TODO: Better error handling
        private set

    override fun makeInstance(): DelayElement  = DelayElement()
    override val elementTypeName: String get() = "Delay"

    override fun debugString(): String = "Delay(${delay?.evaluate(PropertyExpressionContext.Empty)?.toFlatString() ?: ""})${parseError?:""}"

    override fun WeaveEntityParseScope.recognize(): Boolean = firstLine.isDelayEntityStart

    override fun WeaveEntityParseScope.parse() {
        if( bodyLines.drop(1).any { it.isNotEmpty() }) {
            // TODO: Better error handling
            parseError = "Delay element should not have body"
            delay = null
            return
        }
        val delaySpec = bodyLines.first().drop(1).trim()
        if( delaySpec.isBlank() ) {
            parseError = null
            delay = null
            return
        }
        val parseResult = parsePropertyStringHandlingErrors(delaySpec, PropertyType.TypedExpression(PropertyDataType.Time))
        when(parseResult) {
            is ParsedProperty.Alternate -> throw IllegalArgumentException()
            is ParsedProperty.Compound -> throw IllegalStateException()
            is ParsedProperty.Error -> {
                // TODO: Better error handling
                parseError = parseResult.error
                delay = null
            }
            is PropertyExpression -> {
                parseError = null
                delay = parseResult
            }
        }
    }

}