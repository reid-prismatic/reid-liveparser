package com.prismatic.liveparser.weave.styles

import com.prismatic.liveparser.weave.elements.StyleElement
import com.prismatic.liveparser.weave.grammar.WeaveElement
import com.prismatic.liveparser.weave.properties.*

fun resolveStyles(
    element: WeaveElement,
    property: WeaveElement.Prop<*>,
    styleElements: Map<String, List<StyleElement>>,
    propExprContext: PropertyExpressionContext
) : ParsedProperty {
    val styleProperties: List<ParsedProperty> = (element.styles + listOf("*"))
        .flatMap { style -> styleElements[style] ?: emptyList() }
        .filter { el -> el.condition?.evaluate(propExprContext)?.toBoolOrNull() != false  }
        .flatMap { styleElement -> styleElement.styleProperties.filter { it.name == property.key } }
        .filter { it.condition==null || (it.condition.evaluate(propExprContext) as? PropertyExpression.BooleanLiteral)?.value == true } // TODO: Handle condition here
        .map { it.value }
    val accumulator = property.multiInstanceAccumulator
    return if( accumulator != null ) {
        if(styleProperties.isEmpty()) {
            property.parsedProperty
        } else {
            (styleProperties+listOf(property.parsedProperty)).reduce { a, b ->
                when {
                    a !is PropertyExpression -> b
                    b !is PropertyExpression -> a
                    else -> accumulator.makeAccumulator(a,b)
                }
            }
        }
    } else {
        if( property.isSpecified || styleProperties.isEmpty() ) {
            property.parsedProperty
        } else {
            styleProperties.last()
        }
    }
}