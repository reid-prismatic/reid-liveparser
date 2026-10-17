package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.LWEntityParseScope

abstract class ConcreteElement<T: ConcreteElement<T>>(
    val namespace: String,
    val shorthandRecognizer: LWEntityParseScope.() -> Boolean = { false }
) : LWElement<T>() {
    override fun LWEntityParseScope.recognize(): Boolean = isConcreteEntity(namespace) || shorthandRecognizer()
}