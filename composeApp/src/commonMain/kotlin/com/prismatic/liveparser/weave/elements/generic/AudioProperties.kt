package com.prismatic.liveparser.weave.elements.generic

import com.prismatic.liveparser.weave.grammar.PercentProp
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.TimeProp
import com.prismatic.liveparser.weave.types.percent
import org.prismatic.opus.time.MediaTime

interface AudioProperties {
    val volume: PercentProp
}

class AudioPropertiesImpl(pm: PropertyManager) : AudioProperties {
    override val volume by pm.prop(100.percent)
}
