package com.prismatic.liveparser

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

fun LiveParseEntityScope<LineType, ScribeEntity>.parseElement(firstLineType: LineType): LWElement {
    val scope = LWEntityParseScope(this)
    val matches = elements.filter { with(it){scope.recognize()} }
    val match = matches.singleOrNull() ?: return ErrorElement(ElementPropertyAdapter.DefaultElement)
    match.properties.forEach { property ->
        property
    }
}

class TextElement(adapter: ElementPropertyAdapter) : ConcreteElement(
    adapter = adapter,
    namespace = "TextElement",
    shorthandRecognizer = { firstLine.isTextEntityStart }
) {
    val text: String by prop()
    val position: Vec2 by prop()
    companion object {
        val Default = TextElement(ElementPropertyAdapter.DefaultElement)
    }

    override fun LWEntityParseScope.parse(): LWElement {
        TODO("Not yet implemented")
    }
}

abstract class ConcreteElement(
    adapter: ElementPropertyAdapter,
    val namespace: String,
    val shorthandRecognizer: LWEntityParseScope.() -> Boolean = { false }
) : LWElement(adapter) {
    override fun LWEntityParseScope.recognize(): Boolean = isConcreteEntity(namespace) || shorthandRecognizer()
}

class ErrorElement(
    adapter: ElementPropertyAdapter,
) : LWElement(adapter) {
    override fun LWEntityParseScope.recognize(): Boolean = false
    override fun LWEntityParseScope.parse(): LWElement = this@ErrorElement
}

val elements = listOf<LWElement>(
    TextElement.Default
)

fun LWEntityParseScope.isConcreteEntity(namespace: String) : Boolean =
    firstLine.firstChar == '[' && firstLine.lastChar == ']'
            && (':' in firstLine.trimmedBody || '.' !in firstLine.trimmedBody)
            && firstLine.trimmedBody.removeSurrounding("[","]").substringBefore(':') == namespace

abstract class LWElement(
    private val adapter: ElementPropertyAdapter
) {

    abstract fun LWEntityParseScope.recognize() : Boolean
    abstract fun LWEntityParseScope.parse() : LWElement

    var properties : List<Prop<*>> = emptyList()
        private set

    interface Prop<T> {
        val key: String
        val displayName: String
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
    }
    private inner class PropImpl<T>(
        val defaultValue: T,
        override val key: String,
        override val displayName: String,
        val parser: (String) -> T?
    ) : Prop<T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T =
            adapter.getProperty(key)?.let {parser(it)} ?: defaultValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            adapter.setProperty(key, value.toString())
        }
    }
    interface PropProvider<T> {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): Prop<T>
    }
    inline fun <reified T> prop(
        noinline defaultValue: (()->T)? = null,
        key: String? = null,
        displayName: String? = null,
        noinline parser: ((String) -> T?)? = null
    ) : PropProvider<T> {
        return prop(defaultValue?.invoke() ?: defaultValueForType(), key, displayName, parser ?: parserForType() )
    }
    fun <T> prop(
        defaultValue: T,
        key: String? = null,
        displayName: String? = null,
        parser: (String) -> T?
    ) : PropProvider<T> {
        return object : PropProvider<T> {
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): Prop<T> {
                val liveProp : Prop<T> = PropImpl(
                    defaultValue = defaultValue,
                    key = key ?: prop.name,
                    displayName = displayName ?: (key ?: prop.name).apply { take(1).uppercase() + drop(1) },
                    parser = parser
                )
                properties += liveProp
                return liveProp
            }
        }
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

inline fun <reified T> parserForType() : (String)->T? {
    when(T::class) {
        String::class -> return {it as? T}
        Int::class -> return {it.toIntOrNull() as? T}
        Float::class -> return {it.toFloatOrNull() as? T}
        Double::class -> return {it.toDoubleOrNull() as? T}
        Long::class -> return {it.toLongOrNull() as? T}
        Char::class -> return {it.firstOrNull() as? T}
        Boolean::class -> return {
            when(it.lowercase().trim()) {
                "true" -> true
                "false" -> false
                else -> null
            } as? T
        }
        Vec2::class -> return {it.toVec2OrNull() as? T}
        Vec3::class -> return {it.toVec3OrNull() as? T}
        Vec4::class -> return {it.toVec4OrNull() as? T}
        Time::class -> return {it.toTimeOrNull() as? T}
        Seconds::class -> return {it.toTimeOrNull()?.toSeconds() as? T}
        Millis::class -> return {it.toTimeOrNull()?.toMillis() as? T}
        Nanos::class -> return {it.toTimeOrNull()?.toNanos() as? T}
        else -> throw IllegalArgumentException("Unsupported type")
    }
}