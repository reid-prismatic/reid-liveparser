package com.prismatic.liveparser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.reflect.KProperty

fun LiveParseEntityScope<LineType, ScribeEntity>.parseElement(firstLineType: LineType): LWElementEntity {
    val scope = LWEntityParseScope(this)
    val matches = elements.filter { with(it){scope.recognize()} }
    val match = matches.singleOrNull() ?: return  LWElementEntity(ErrorElement(),firstLineType)
    val lwee = (existingEntity as? LWElementEntity) ?: LWElementEntity(match.makeInstance(),firstLineType)
    val inst: LWElement<*> = lwee.element
    val bodyLines = entityLines.takeWhile { !it.startsWith("- ") }
    val propLines = entityLines.drop(bodyLines.size)
    val props = mutableMapOf<String,MutableList<String>>()
    propLines.forEach { line ->
        val key = line.substringBefore(':', "").trim().removePrefix("- ").trim()
        val targetProperty = match.properties[key]
        if( targetProperty != null ) {
            val value = line.substringAfter(':', "").trim()
            if(targetProperty.accumulateInstances) {
                props.getOrPut(key,{mutableListOf()}).add(value)
            } else {
                props.getOrPut(key,{mutableListOf()}).apply {
                    clear()
                    add(value)
                }
            }
        }
    }
//    inst.propertyList.forEach { prop -> prop.update(PropertyExpression.of(prop.defaultValue))}
    for ((k, v) in props) {
        val prop = inst.properties[k] ?: continue
        if( v.size == 1 ) {
            val parsed = runCatching { parsePropertyString(v.first(),prop.type) }.getOrNull() ?: continue
            prop.update(parsed)
        } else if( v.size > 1 ) {
            val parsed = v.asSequence().mapNotNull { runCatching {  parsePropertyString(it,prop.type) }.getOrNull() }.reduce { a, b ->
                when {
                    a !is PropertyExpression -> b
                    b !is PropertyExpression -> a
                    else -> PropertyExpression.Binary(PropertyExpression.BinaryOperator.ADD,a,b)
                }
            } ?: continue
            prop.update(parsed)
        }
    }
    with(inst) {
        scope.parse()
    }
    return lwee
}

class TextElement() : ConcreteElement<TextElement>(
    namespace = "text",
    shorthandRecognizer = { firstLine.isTextEntityStart }
) {
    val fontSize by prop(12.pt)
    val typeface by prop("")
    val weight by prop(FontWeight.Bold)
    val fontStyle by prop(FontStyle.Normal)
    val font by prop(fontSize, typeface, weight, fontStyle)
//    val text by prop("")
    var text : String by mutableStateOf("")
    val position by prop(Vec2.ZERO)
    val duration by prop(MediaTime.Unspecified)
    val color by prop(Color.Black)
    val offset by prop(Vec2.ZERO) {
        accumulateMultipleInstances()
    }

    override fun LWEntityParseScope.parse() {
        text = entityLines.takeWhile { !it.trim().startsWith("- ") }.joinToString(separator = "\n")
    }

    override fun makeInstance(): TextElement = TextElement()
}

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

    override fun makeInstance(): VoiceoverElement = VoiceoverElement()
}

class MediaElement() : ConcreteElement<MediaElement>(
    namespace = "media",
    shorthandRecognizer = { firstLine.isConcreteEntityStart && ":" !in firstLine.body && "." in firstLine.body }
) {
    var filename : String by mutableStateOf("")
    val duration by prop(MediaTime.Unspecified)
    override fun LWEntityParseScope.parse() {
        filename = firstLine.trimmedBody.removeSurrounding("[","]").trim()
    }

    override fun makeInstance(): MediaElement = MediaElement()
}

abstract class ConcreteElement<T: ConcreteElement<T>>(
    val namespace: String,
    val shorthandRecognizer: LWEntityParseScope.() -> Boolean = { false }
) : LWElement<T>() {
    override fun LWEntityParseScope.recognize(): Boolean = isConcreteEntity(namespace) || shorthandRecognizer()
}

class ErrorElement : LWElement<ErrorElement>() {
    override fun LWEntityParseScope.recognize(): Boolean = false
    override fun LWEntityParseScope.parse() {}
    override fun makeInstance(): ErrorElement = ErrorElement()
}

val elements = listOf<LWElement<*>>(
    TextElement(),
    VoiceoverElement(),
    MediaElement()
)

fun LWEntityParseScope.isConcreteEntity(namespace: String) : Boolean =
    firstLine.firstChar == '[' && firstLine.lastChar == ']'
            && (':' in firstLine.trimmedBody || '.' !in firstLine.trimmedBody)
            && firstLine.trimmedBody.removeSurrounding("[","]").substringBefore(':') == namespace

abstract class LWElement<T: LWElement<T>> {

    abstract fun LWEntityParseScope.recognize() : Boolean
    abstract fun LWEntityParseScope.parse()
    abstract fun makeInstance() : T

    var propertyList : List<Prop<*>> = emptyList()
        private set

    val properties: Map<String, Prop<*>> by lazy {propertyList.associateBy { it.key }}

    interface Prop<T> {
        val key: String
        val displayName: String
        val type: PropertyType
        val accumulateInstances: Boolean
        fun update(value: ParsedProperty)
        val parsedProperty: ParsedProperty
    }

    interface PropMember<T> {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Prop<T>
    }

    interface CompoundPropMember {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Unit
    }



    private inner class PropImpl<T>(
        val defaultValue: T,
        override val key: String,
        override val displayName: String,
        override val type: PropertyType,
        override val accumulateInstances: Boolean,
//        val parser: (String) -> T?
    ) : Prop<T>, PropMember<T> {
        var propValue : ParsedProperty by mutableStateOf(PropertyExpression.of(defaultValue))
        override val parsedProperty: ParsedProperty
            get() = propValue
//        var propValue: T = defaultValue
        override fun update(value: ParsedProperty) {
            propValue = value
//            propValue = parser(value) ?: defaultValue
        }
        override fun getValue(thisRef: Any?, property: KProperty<*>): Prop<T> = this@PropImpl
    }
    interface PropProvider<T> {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): PropMember<T>
    }

    interface CompoundPropProvider {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): CompoundPropMember
    }

    interface PropertyDefScope {
        fun accumulateMultipleInstances()
    }

//    inline fun <reified T> prop(
//        noinline defaultValue: (()->T)? = null,
//        key: String? = null,
//        displayName: String? = null,
//        type: PropertyType? = null,
//        noinline parser: ((String) -> T?)? = null
//    ) : PropProvider<T> {
//        return propProvider(defaultValue?.invoke() ?: defaultValueForType(), key, displayName, type ?: inferPropertyType<T>(), parser ?: parserForType() )
//    }

    fun prop(defaultValue: Int, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Int> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Float, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Float> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Double, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Double> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Long, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Long> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Time, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Time> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Time), def=def)
    fun prop(defaultValue: Color, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Color> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Color), def=def)
    fun prop(defaultValue: String, def:(PropertyDefScope.()->Unit)?=null): PropProvider<String> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.String), def=def)
    fun prop(defaultValue: Vec2, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Vec2> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec2), def=def)
    fun prop(defaultValue: Vec3, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Vec3> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec3), def=def)
    fun prop(defaultValue: Vec4, def:(PropertyDefScope.()->Unit)?=null): PropProvider<Vec4> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec4), def=def)
    fun prop(defaultValue: LWDimension, def:(PropertyDefScope.()->Unit)?=null): PropProvider<LWDimension> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Dimension), def=def)
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

    fun <T> propProvider(
        defaultValue: T,
        key: String? = null,
        displayName: String? = null,
        type: PropertyType,
        def:(PropertyDefScope.()->Unit)?
//        parser: (String) -> T?
    ) : PropProvider<T> {
        return object : PropProvider<T>, PropertyDefScope {
            var multiInstance = false
            override fun accumulateMultipleInstances() {multiInstance = true}
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): PropMember<T> {
                val liveProp = PropImpl(
                    defaultValue = defaultValue,
                    key = key ?: prop.name,
                    displayName = displayName ?: (key ?: prop.name).apply { take(1).uppercase() + drop(1) },
                    type = type,
                    accumulateInstances = multiInstance
//                    parser = parser
                )
                propertyList += liveProp
                return liveProp
            }
        }.also{def?.invoke(it)}
    }
}

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

//inline fun <reified T> parserForType() : (String)->T? {
//    when(T::class) {
//        String::class -> return {it as? T}
//        Int::class -> return {it.toIntOrNull() as? T}
//        Float::class -> return {it.toFloatOrNull() as? T}
//        Double::class -> return {it.toDoubleOrNull() as? T}
//        Long::class -> return {it.toLongOrNull() as? T}
//        Char::class -> return {it.firstOrNull() as? T}
//        Boolean::class -> return {
//            when(it.lowercase().trim()) {
//                "true" -> true
//                "false" -> false
//                else -> null
//            } as? T
//        }
//        Vec2::class -> return {it.toVec2OrNull() as? T}
//        Vec3::class -> return {it.toVec3OrNull() as? T}
//        Vec4::class -> return {it.toVec4OrNull() as? T}
//        Time::class -> return {it.toTimeOrNull() as? T}
//        Seconds::class -> return {it.toTimeOrNull()?.toSeconds() as? T}
//        Millis::class -> return {it.toTimeOrNull()?.toMillis() as? T}
//        Nanos::class -> return {it.toTimeOrNull()?.toNanos() as? T}
//        else -> throw IllegalArgumentException("Unsupported type")
//    }
//}

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