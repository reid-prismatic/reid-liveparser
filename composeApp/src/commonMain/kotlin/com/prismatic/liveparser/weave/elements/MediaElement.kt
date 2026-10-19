package com.prismatic.liveparser.weave.elements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.weave.grammar.ConcreteElement
import com.prismatic.liveparser.weave.grammar.isConcreteEntityStart
import org.prismatic.opus.time.MediaTime

class MediaElement() : ConcreteElement<MediaElement>(
    namespace = "media",
    shorthandRecognizer = { firstLine.isConcreteEntityStart && ":" !in firstLine.body && "." in firstLine.body }
) {
    override val elementTypeName: String get() = "Media"
    var filename : String by mutableStateOf("")
    val duration by prop(MediaTime.Unspecified)
    override fun parseArguments(arguments: String) {
        filename = arguments
    }
    override fun parseShorthandBody(bodyLines: List<String>) {
        filename = bodyLines.first().trim().removeSurrounding("[","]").trim()
    }
    override fun debugString(): String = "media:$filename"

    override fun makeInstance(): MediaElement = MediaElement()
}