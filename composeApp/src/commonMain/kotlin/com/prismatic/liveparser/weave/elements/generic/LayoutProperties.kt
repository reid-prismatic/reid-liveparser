package com.prismatic.liveparser.weave.elements.generic

import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.Vec2Prop
import com.prismatic.liveparser.weave.grammar.sumMultipleInstances
import org.prismatic.opus.math.Vec2

interface LayoutProperties {
    val position: Vec2Prop
}

class LayoutPropertiesImpl(pm: PropertyManager) : LayoutProperties {
    override val position by pm.prop(Vec2.ZERO)
}
