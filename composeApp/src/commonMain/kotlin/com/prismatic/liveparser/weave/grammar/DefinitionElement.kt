package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.LWEntityParseScope

abstract class DefinitionElement<T: DefinitionElement<T>>(
    val namespace: String,
    val shorthandRecognizer: LWEntityParseScope.() -> Boolean = { false }
) : LWElement<T>() {
    override fun LWEntityParseScope.recognize(): Boolean = isDefinitionEntity(namespace) || shorthandRecognizer()
    final override fun LWEntityParseScope.parse() {
        if(firstLine.isDefinitionEntityStart) {
            val args = firstLine.trimmedBody.removeSurrounding("{","}").substringAfter(':',"").trim()
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