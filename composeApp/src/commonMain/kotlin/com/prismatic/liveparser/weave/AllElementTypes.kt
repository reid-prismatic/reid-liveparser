package com.prismatic.liveparser.weave

import com.prismatic.liveparser.weave.elements.MediaElement
import com.prismatic.liveparser.weave.elements.SegmentBreakElement
import com.prismatic.liveparser.weave.elements.StyleElement
import com.prismatic.liveparser.weave.elements.TargetElement
import com.prismatic.liveparser.weave.elements.TextElement
import com.prismatic.liveparser.weave.elements.VoiceoverElement
import com.prismatic.liveparser.weave.grammar.LWElement

val ALL_ELEMENT_TYPES = listOf<LWElement<*>>(
    TextElement(),
    VoiceoverElement(),
    MediaElement(),
    TargetElement(),
    StyleElement(),
    SegmentBreakElement()
)