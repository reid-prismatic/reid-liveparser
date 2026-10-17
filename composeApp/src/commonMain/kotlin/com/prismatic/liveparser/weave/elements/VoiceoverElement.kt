package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.isVoiceoverEntityStart
import org.prismatic.opus.time.MediaTime

class VoiceoverElement() : ConcreteElement<VoiceoverElement>(
    namespace = "voiceover",
    shorthandRecognizer = { firstLine.isVoiceoverEntityStart }
) {
    var text : String by mutableStateOf("")
    val voice by prop("")
    val volume by prop(0)
    val duration by prop(MediaTime.Unspecified)
    override fun LWEntityParseScope.parse() {
        text = entityLines.takeWhile { !it.trim().startsWith("- ") }.joinToString(separator = "\n")
    }
    override fun debugString(): String = "voiceover:${text.replace("\n", "\\n")}"

    override fun makeInstance(): VoiceoverElement = VoiceoverElement()
}