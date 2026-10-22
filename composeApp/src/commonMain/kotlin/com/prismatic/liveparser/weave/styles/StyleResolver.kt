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

    val styleProperties = mutableListOf<ParsedProperty>()
    val processedStyles = mutableSetOf<String>()

    fun processStyle(name: String) {
        for( el in styleElements[name] ?: emptyList() ) {
            if( el.condition?.evaluate(propExprContext)?.toBoolOrNull() == false )
                continue
            if(el.styles.isNotEmpty()) {
                processedStyles += name
                el.styles.forEach {
                    if( it !in processedStyles ) {
                        processedStyles += it
                        processStyle(it)
                    }
                }
            }
            for( styleProp in el.styleProperties ) {
                if( styleProp.name != property.key )
                    continue
                if( styleProp.condition == null || styleProp.condition.evaluate(propExprContext).toBoolOrNull() == true ) {
                    styleProperties.add(styleProp.value)
                }
            }
        }
    }

    processStyle("*")
    element.styles.forEach { processStyle(it) }

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