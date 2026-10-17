package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.LWEntityParseScope

abstract class ConcreteElement<T: ConcreteElement<T>>(
    val namespace: String,
    val shorthandRecognizer: LWEntityParseScope.() -> Boolean = { false }
) : LWElement<T>() {
    override fun LWEntityParseScope.recognize(): Boolean = isConcreteEntity(namespace) || shorthandRecognizer()
    final override fun LWEntityParseScope.parse() {
        if(firstLine.isConcreteEntityStart) {
            val args = firstLine.trimmedBody.removeSurrounding("[","]").substringAfter(':',"").trim()
            parseArguments(args)
            parseBody(bodyLines.drop(1))
        } else {
            parseShorthandBody(bodyLines)
        }
    }
    open fun parseArguments(arguments: String) {}
    open fun parseBody(bodyLines: List<String>) {}
    open fun parseShorthandBody(bodyLines: List<String>) {}
}