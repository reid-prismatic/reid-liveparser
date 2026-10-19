package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.weave.grammar.DefinitionElement

class TargetElement() : DefinitionElement<TargetElement>(
    namespace = "target",
    shorthandRecognizer = {false}
) {
    override val elementTypeName: String get() = "Target"
    var targetName by mutableStateOf("")
    val ratio by prop(16 to 9)
    override fun parseArguments(arguments: String) {
        targetName = arguments
    }
    override fun makeInstance(): TargetElement = TargetElement()
    override fun debugString(): String = "target:$targetName"
}