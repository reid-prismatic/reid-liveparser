package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.elements.generic.*
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.isConcreteEntityStart

class MediaElement private constructor(pm: PropertyManager) :
    LayoutProperties by LayoutPropertiesImpl(pm),
    TimingProperties by TimingPropertiesImpl(pm),
    TransformProperties by TransformPropertiesImpl(pm),
    AudioProperties by AudioPropertiesImpl(pm),
    ConcreteElement<MediaElement>(
        propertyManager = pm,
        namespace = "media",
        shorthandRecognizer = { firstLine.isConcreteEntityStart && ":" !in firstLine.body && "." in firstLine.body }
    ) { constructor() : this(PropertyManager())

    override val elementTypeName: String get() = "Media"
    var filename : String by mutableStateOf("")
    override fun parseArguments(arguments: String) {
        filename = arguments
    }
    override fun parseShorthandBody(bodyLines: List<String>) {
        filename = bodyLines.first().trim().removeSurrounding("[","]").trim()
    }
    override fun debugString(): String = "media:$filename"

    override fun makeInstance(): MediaElement = MediaElement()
}