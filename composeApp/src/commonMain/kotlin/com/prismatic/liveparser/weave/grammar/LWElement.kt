package com.prismatic.liveparser.weave.grammar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.prismatic.liveparser.LWDimension
import com.prismatic.liveparser.LWEntityParseScope
import com.prismatic.liveparser.LiveParseEntityScope
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyDataType
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.parsePropertyString
import com.prismatic.liveparser.weave.ALL_ELEMENT_TYPES
import com.prismatic.liveparser.weave.elements.ErrorElement
import com.prismatic.liveparser.weave.properties.PropertyLineParseResult
import com.prismatic.liveparser.weave.properties.parsePropertyLine
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.math.Vec3
import org.prismatic.opus.math.Vec4
import org.prismatic.opus.time.MediaTime
import org.prismatic.opus.time.Millis
import org.prismatic.opus.time.Nanos
import org.prismatic.opus.time.Seconds
import org.prismatic.opus.time.Time
import org.prismatic.opus.time.millis
import org.prismatic.opus.time.nanos
import org.prismatic.opus.time.seconds
import kotlin.collections.iterator
import kotlin.reflect.KProperty

fun LiveParseEntityScope<LineType, LWElement<*>>.parseElement(firstLineType: LineType): LWElement<*> {
    val scope = LWEntityParseScope(this)
    val matches = ALL_ELEMENT_TYPES.filter { with(it){scope.recognize()} }
    val match = matches.singleOrNull() ?: return ErrorElement()
    val inst: LWElement<*> = existingEntity ?: match.makeInstance()
    val bodyLines = entityLines.takeWhile { !it.startsWith("- ") }
    val propLines = entityLines.drop(bodyLines.size).filter { it.isNotBlank() }
    val props = mutableMapOf<String,MutableList<PropertyLineParseResult>>()
    val otherProps = mutableMapOf<String,MutableList<PropertyLineParseResult>>()
    val styles = mutableListOf<String>()
    propLines.forEach { line ->
        val parseResult = parsePropertyLine(line)
        val key = parseResult.name
        val targetProperty = match.properties[key]
        if( parseResult.style.isNotEmpty() ) {
            styles += parseResult.style
        } else if( targetProperty == null ) {
            if( match.acceptsOtherProperties ) {
                otherProps.getOrPut(key,{mutableListOf()}).add(parseResult)
            } else {
                // TODO: Error, unrecognized property
            }
        } else {
            if(parseResult.condition.isNotBlank()) {
                // TODO: Error, property conditions not allow except in style definitions
            }
            if(targetProperty.multiInstanceAccumulator!=null) {
                props.getOrPut(key,{mutableListOf()}).add(parseResult)
            } else {
                props.getOrPut(key,{mutableListOf()}).apply {
                    clear()
                    add(parseResult)
                }
            }
        }
    }
    if( inst.acceptsOtherProperties ) {
        val otherPropLines = otherProps.flatMap { (key,values) ->
            values.map { parseResult ->
                PropertyLine(
                    name = key,
                    condition = if(parseResult.condition.isBlank()) {
                        null
                    } else {
                        parsePropertyStringHandlingErrors(parseResult.condition, PropertyType.Expression)
                    },
                    value = parsePropertyStringHandlingErrors(
                        s = parseResult.value,
                        type = PropertyType.Expression      // TODO: This should handle situations like compound properties (for fonts)
                    )
                )
            }
        }
        inst.updateOtherProperties(otherPropLines)
    }
    inst.updateStyles(styles)
    for ((k, v) in props) {
        val prop = inst.properties[k] ?: continue
        if( v.size == 1 ) {
            val parsed = parsePropertyStringHandlingErrors(v.first().value, prop.type)
            prop.update(parsed)
        } else if( v.size > 1 ) {
            val accumulator = prop.multiInstanceAccumulator ?: PropertyAccumulator.Sum
            val parsed = v.asSequence().map {
                parsePropertyStringHandlingErrors(it.value, prop.type)
            }.reduce { a, b ->
                when {
                    a !is PropertyExpression -> b
                    b !is PropertyExpression -> a
                    else -> accumulator.makeAccumulator(a,b)
                }
            } ?: continue
            prop.update(parsed)
        }
    }
    (inst.properties.keys - props.keys).forEach {inst.properties[it]?.update(null)}
    with(inst) {
        scope.parse()
    }
    return inst
}

fun interface PropertyAccumulator {
    fun makeAccumulator(a: PropertyExpression, b: PropertyExpression):PropertyExpression
    companion object {
        val Sum = PropertyAccumulator { a, b ->
            PropertyExpression.Binary(PropertyExpression.BinaryOperator.ADD,a,b)
        }
        val Product = PropertyAccumulator { a, b ->
            PropertyExpression.Binary(PropertyExpression.BinaryOperator.MULTIPLY,a,b)
        }
    }
}

private fun parsePropertyStringHandlingErrors(s: String, type: PropertyType) : ParsedProperty{
    return runCatching { parsePropertyString(s,type) }
        .recover { ParsedProperty.Error(it.message ?: it.toString(), null) }
        .getOrNull() ?: PropertyExpression.of("null")
}

fun LWEntityParseScope.isConcreteEntity(namespace: String) : Boolean =
    firstLine.firstChar == '[' && firstLine.lastChar == ']'
            && (':' in firstLine.trimmedBody || '.' !in firstLine.trimmedBody)
            && firstLine.trimmedBody.trim().removeSurrounding("[","]").substringBefore(':').trim() == namespace

fun LWEntityParseScope.isDefinitionEntity(namespace: String) : Boolean =
    firstLine.firstChar == '{' && firstLine.lastChar == '}'
            && ':' in firstLine.trimmedBody
            && firstLine.trimmedBody.trim().removeSurrounding("{","}").substringBefore(':').trim() == namespace

abstract class LWElement<T: LWElement<T>> {

    abstract val elementTypeName: String

    abstract fun debugString(): String

    abstract fun LWEntityParseScope.recognize() : Boolean
    abstract fun LWEntityParseScope.parse()
    abstract fun makeInstance() : T

    var propertyList : List<Prop<*>> = emptyList()
        private set

    var styles: List<String> by mutableStateOf(emptyList())
        private set
    val properties: Map<String, Prop<*>> by lazy {propertyList.associateBy { it.key }}
    val otherProperties: List<PropertyLine> field = mutableStateListOf()
    var acceptsOtherProperties : Boolean = false
        private set

    fun updateStyles(styles: List<String>) {
        this.styles = styles
    }

    fun updateOtherProperties(otherPropLines: List<PropertyLine>) {
        // TODO: change the following to only set property lines that have changed, to reduce state changes
        otherProperties.clear()
        otherProperties.addAll(otherPropLines)
    }

    interface Prop<T> {
        val key: String
        val displayName: String
        val type: PropertyType
        val multiInstanceAccumulator: PropertyAccumulator?
        val isSpecified: Boolean
        fun update(value: ParsedProperty?)
        val parsedProperty: ParsedProperty
    }

    interface PropMember<T> {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Prop<T>
    }

    interface MultiPropMember {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<PropertyLine>
    }

    interface CompoundPropMember {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Unit
    }

    private inner class MultiPropImpl() : MultiPropMember {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ): List<PropertyLine> {
            check( acceptsOtherProperties )
            return otherProperties
        }
    }

    private inner class PropImpl<T>(
        val defaultValue: T,
        override val key: String,
        override val displayName: String,
        override val type: PropertyType,
        override val multiInstanceAccumulator: PropertyAccumulator?,
//        val parser: (String) -> T?
    ) : Prop<T>, PropMember<T> {
        val defaultPropValue = PropertyExpression.of(defaultValue)
        var propValue : ParsedProperty? by mutableStateOf(null)
        override val parsedProperty: ParsedProperty
            get() = propValue ?: defaultPropValue
//        var propValue: T = defaultValue
        override fun update(value: ParsedProperty?) {
            propValue = value
//            propValue = parser(value) ?: defaultValue
        }

        override val isSpecified: Boolean get() = propValue != null
        override fun getValue(thisRef: Any?, property: KProperty<*>): Prop<T> = this@PropImpl
    }
    interface PropProvider<T> {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): PropMember<T>
    }

    interface MultiPropProvider {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): MultiPropMember
    }


    interface CompoundPropProvider {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): CompoundPropMember
    }

    interface PropertyDefScope {
        fun accumulateMultipleInstances(accumulator: PropertyAccumulator)
    }

    fun prop(defaultValue: Int, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Int> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Float, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Float> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Double, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Double> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Long, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Long> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Time, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Time> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Time), def=def)
    fun prop(defaultValue: Color, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Color> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Color), def=def)
    fun prop(defaultValue: String, def:(PropertyDefScope.()->Unit)?=null): PropProvider<String> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.String), def=def)
    fun prop(defaultValue: Vec2, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Vec2> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec2), def=def)
    fun prop(defaultValue: Pair<Int,Int>, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Pair<Int,Int>> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Ratio), def=def)
    fun prop(defaultValue: Vec3, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Vec3> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec3), def=def)
    fun prop(defaultValue: Vec4, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Vec4> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec4), def=def)
    fun prop(defaultValue: LWDimension, def:(PropertyDefScope.()->Unit)?=null): PropProvider<LWDimension> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Dimension), def=def)
    fun otherProps(): MultiPropProvider {
        check(!acceptsOtherProperties)
        acceptsOtherProperties = true
        return multiPropProvider()
    }
    fun prop(defaultValue: FontWeight, def:(PropertyDefScope.()->Unit)?=null): PropProvider<FontWeight> =
        propProvider(defaultValue, type = PropertyType.Alternate(
            PropertyType.UnquotedStringEnum("Thin","ExtraLight","Light","Normal","Medium","SemiBold","Bold","ExtraBold","Black"),
            PropertyType.TypedExpression(PropertyDataType.Integer)
        ), def=def)
    fun prop(defaultValue: FontStyle, def:(PropertyDefScope.()->Unit)?=null): PropProvider<FontStyle> = propProvider(defaultValue, type = PropertyType.UnquotedStringEnum("Italic","Regular"), def=def)
    fun prop(vararg components: Prop<*>, def:(PropertyDefScope.()->Unit)?=null) : CompoundPropProvider = compoundPropProvider(type = PropertyType.Compound(components.map { it.type }))

    fun compoundPropProvider(
        key: String? = null,
        displayName: String? = null,
        type: PropertyType,
//        parser: (String) -> T?
    ) : CompoundPropProvider {
        return object : CompoundPropProvider {
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): CompoundPropMember {
//                propertyList += liveProp
                return object : CompoundPropMember {
                    override fun getValue(thisRef: Any?, property: KProperty<*>) {

                    }
                }
            }
        }
    }

    fun multiPropProvider() : MultiPropProvider {
        return object : MultiPropProvider {
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): MultiPropMember {
                val liveProp = MultiPropImpl()
                return liveProp
            }
        }
    }

    fun <T> propProvider(
        defaultValue: T,
        key: String? = null,
        displayName: String? = null,
        type: PropertyType,
        def:(PropertyDefScope.()->Unit)?
//        parser: (String) -> T?
    ) : PropProvider<T> {
        return object : PropProvider<T>, PropertyDefScope {
            var multiInstanceAccumulator: PropertyAccumulator? = null
            override fun accumulateMultipleInstances(accumulator: PropertyAccumulator) {
                check(multiInstanceAccumulator==null)
                multiInstanceAccumulator = accumulator
            }
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): PropMember<T> {
                val liveProp = PropImpl(
                    defaultValue = defaultValue,
                    key = key ?: prop.name,
                    displayName = displayName ?: (key ?: prop.name).apply { take(1).uppercase() + drop(1) },
                    type = type,
                    multiInstanceAccumulator = multiInstanceAccumulator
//                    parser = parser
                )
                propertyList += liveProp
                return liveProp
            }
        }.also{def?.invoke(it)}
    }
}

class PropertyLine(val name: String, val value: ParsedProperty, val condition: ParsedProperty?)

interface ElementPropertyAdapter {
    fun setProperty(key: String, value: String)
    fun getProperty(key: String): String?
    companion object {
        val DefaultElement = object : ElementPropertyAdapter {
            override fun setProperty(key: String, value: String) {
                throw IllegalStateException("Cannot set properties of element default.")
            }
            override fun getProperty(key: String): String? = null
        }
    }
}

inline fun <reified T> defaultValueForType() : T {
    when(T::class) {
        String::class -> return "" as T
        Int::class -> return 0 as T
        Float::class -> return 0f as T
        Double::class -> return 0.0 as T
        Long::class -> return 0L as T
        Char::class -> return '\u0000' as T
        Boolean::class -> return true as T
        Vec2::class -> return Vec2.ZERO as T
        Vec3::class -> return Vec3.ZERO as T
        Vec4::class -> return Vec4.ZERO as T
        Time::class, MediaTime::class -> return MediaTime.Unspecified as T
        Seconds::class -> return 0.seconds as T
        Millis::class -> return 0.millis as T
        Nanos::class -> return 0.nanos as T
        else -> throw IllegalArgumentException("Unsupported type")
    }
}

inline fun <reified T> inferPropertyType() : PropertyType {
    when(T::class) {
        String::class -> return PropertyType.TypedExpression(PropertyDataType.String)
        Int::class -> return PropertyType.TypedExpression(PropertyDataType.Integer)
        Float::class -> return PropertyType.TypedExpression(PropertyDataType.Number)
        Double::class -> return PropertyType.TypedExpression(PropertyDataType.Number)
        Long::class -> return PropertyType.TypedExpression(PropertyDataType.Integer)
        Char::class -> return PropertyType.TypedExpression(PropertyDataType.Char)
        Boolean::class -> return PropertyType.TypedExpression(PropertyDataType.Boolean)
        Vec2::class -> return PropertyType.TypedExpression(PropertyDataType.Vec2)
        Vec3::class -> return PropertyType.TypedExpression(PropertyDataType.Vec3)
        Vec4::class -> return PropertyType.TypedExpression(PropertyDataType.Vec4)
        Time::class -> return PropertyType.TypedExpression(PropertyDataType.Time)
        Seconds::class -> return PropertyType.TypedExpression(PropertyDataType.Time)
        Millis::class -> return PropertyType.TypedExpression(PropertyDataType.Time)
        Nanos::class -> return PropertyType.TypedExpression(PropertyDataType.Time)
        LWDimension::class, LWDimension.Millimeters::class, LWDimension.Points::class ->
            return PropertyType.TypedExpression(PropertyDataType.Dimension)
        else -> throw IllegalArgumentException("Unsupported type")
    }
}