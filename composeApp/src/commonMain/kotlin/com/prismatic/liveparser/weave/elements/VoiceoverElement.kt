package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.isConcreteEntityStart
import com.prismatic.liveparser.weave.grammar.isDefinitionEntityStart
import com.prismatic.liveparser.weave.grammar.isVoiceoverEntityStart
import org.prismatic.opus.time.MediaTime

class VoiceoverElement() : ConcreteElement<VoiceoverElement>(
    namespace = "voiceover",
    shorthandRecognizer = { firstLine.isVoiceoverEntityStart }
) {
    override val elementTypeName: String get() = "Voiceover"
    var text : String by mutableStateOf("")
    val voice by prop("")
    val volume by prop(0)
    val duration by prop(MediaTime.Unspecified)
    override fun parseBody(bodyLines: List<String>) {
        text = bodyLines.joinToString("\n")
    }

    override fun parseShorthandBody(bodyLines: List<String>) {
        text = bodyLines.joinToString("\n") { it.removePrefix(">").trim() }
    }
    override fun debugString(): String = "voiceover:${text.replace("\n", "\\n")}"

    override fun makeInstance(): VoiceoverElement = VoiceoverElement()
}