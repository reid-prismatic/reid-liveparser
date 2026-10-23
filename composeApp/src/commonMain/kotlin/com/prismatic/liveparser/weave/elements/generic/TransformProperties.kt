package com.prismatic.liveparser.weave.elements.generic

import com.prismatic.liveparser.weave.grammar.PercentProp
import com.prismatic.liveparser.weave.grammar.PropertyManager
import com.prismatic.liveparser.weave.grammar.Vec2Prop
import com.prismatic.liveparser.weave.grammar.multiplyMultipleInstances
import com.prismatic.liveparser.weave.grammar.sumMultipleInstances
import com.prismatic.liveparser.weave.types.percent
import org.prismatic.opus.math.Vec2

interface TransformProperties {
    val offset: Vec2Prop
    val scale: Vec2Prop
    val opacity: PercentProp
}

class TransformPropertiesImpl(pm: PropertyManager) : TransformProperties {
    override val offset by pm.prop(Vec2.ZERO) {
        sumMultipleInstances()
    }
    override val scale by pm.prop(Vec2.ZERO) {
        multiplyMultipleInstances()
    }
    override val opacity by pm.prop(100.percent) {
        multiplyMultipleInstances()
    }
}
