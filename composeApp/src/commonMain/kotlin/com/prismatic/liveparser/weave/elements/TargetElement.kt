package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.grammar.DefinitionElement
import com.prismatic.liveparser.weave.grammar.PropertyManager
import org.prismatic.opus.time.fps

class TargetElement private constructor(pm: PropertyManager) : DefinitionElement<TargetElement>(
    propertyManager = pm,
    namespace = "target",
    shorthandRecognizer = {false}
) { constructor() : this(PropertyManager()) 
    override val elementTypeName: String get() = "Target"
    var targetName by mutableStateOf("")
    val ratio by prop(16 to 9)
    val frameRate by prop(30.fps)
    val outputResolution by prop(1080)
    val outputFrameRate by prop(30.fps)
    val outputRatio by prop(16 to 9)
    val output by prop(outputResolution, outputRatio, outputFrameRate)
    override fun parseArguments(arguments: String) {
        targetName = arguments
    }
    override fun makeInstance(): TargetElement = TargetElement()
    override fun debugString(): String = "target:$targetName"
}