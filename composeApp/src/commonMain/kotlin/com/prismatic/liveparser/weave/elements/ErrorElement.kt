package com.prismatic.liveparser.weave.elements

import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement
import com.prismatic.liveparser.weave.grammar.WeaveEntityParseScope

class ErrorElement private constructor(pm: PropertyManager) :
    TypedWeaveElement<ErrorElement>(propertyManager = pm)
{ constructor() : this(PropertyManager())
    override val elementTypeName: String get() = "Error"
    override fun WeaveEntityParseScope.recognize(): Boolean = false
    override fun WeaveEntityParseScope.parse() {}
    override fun makeInstance(): ErrorElement = ErrorElement()
    override fun debugString(): String = "error"
}