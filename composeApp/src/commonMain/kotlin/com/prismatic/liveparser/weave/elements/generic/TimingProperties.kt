package com.prismatic.liveparser.weave.elements.generic

import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.TimeProp
import org.prismatic.opus.time.MediaTime

interface TimingProperties {
    val duration: TimeProp
}

class TimingPropertiesImpl(pm: PropertyManager) : TimingProperties {
    override val duration by pm.prop(MediaTime.Unspecified)
}
