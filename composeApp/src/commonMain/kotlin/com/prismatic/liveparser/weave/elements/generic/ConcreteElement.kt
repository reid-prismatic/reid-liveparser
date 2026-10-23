package com.prismatic.liveparser.weave.grammar

abstract class ConcreteElement<T: ConcreteElement<T>>(
    propertyManager: PropertyManager,
    val namespace: String,
    val shorthandRecognizer: WeaveEntityParseScope.() -> Boolean = { false }
) : TypedWeaveElement<T>(propertyManager) {
    override fun WeaveEntityParseScope.recognize(): Boolean = isConcreteEntity(namespace) || shorthandRecognizer()
    final override fun WeaveEntityParseScope.parse() {
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