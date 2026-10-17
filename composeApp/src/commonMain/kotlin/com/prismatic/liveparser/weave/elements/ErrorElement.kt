package com.prismatic.liveparser.weave.elements

import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.weave.grammar.LWElement

class ErrorElement : LWElement<ErrorElement>() {
    override fun LWEntityParseScope.recognize(): Boolean = false
    override fun LWEntityParseScope.parse() {}
    override fun makeInstance(): ErrorElement = ErrorElement()
    override fun debugString(): String = "error"
}