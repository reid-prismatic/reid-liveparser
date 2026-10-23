package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.weave.elements.generic.AudioProperties
import com.prismatic.liveparser.weave.elements.generic.AudioPropertiesImpl
import com.prismatic.liveparser.weave.elements.generic.TimingProperties
import com.prismatic.liveparser.weave.elements.generic.TimingPropertiesImpl
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.isVoiceoverEntityStart
import org.prismatic.opus.time.MediaTime

class VoiceoverElement private constructor( pm: PropertyManager) :
    TimingProperties by TimingPropertiesImpl(pm),
    AudioProperties by AudioPropertiesImpl(pm),
    ConcreteElement<VoiceoverElement>(
    propertyManager = pm,
    namespace = "voiceover",
    shorthandRecognizer = { firstLine.isVoiceoverEntityStart }
) { constructor() : this(PropertyManager())
    override val elementTypeName: String get() = "Voiceover"
    var text : String by mutableStateOf("")
    val voice by prop("")
    override fun parseBody(bodyLines: List<String>) {
        text = bodyLines.joinToString("\n")
    }

    override fun parseShorthandBody(bodyLines: List<String>) {
        text = bodyLines.joinToString("\n") { it.removePrefix(">").trim() }
    }
    override fun debugString(): String = "voiceover:${text.replace("\n", "\\n")}"

    override fun makeInstance(): VoiceoverElement = VoiceoverElement()
}