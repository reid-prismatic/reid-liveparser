package com.prismatic.liveparser.weave.grammar

import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.prismatic.liveparser.parser.EntityId
import com.prismatic.liveparser.WeaveDimension
import com.prismatic.liveparser.parser.LiveParseEntityScope
import com.prismatic.liveparser.offsetToStartOfLine
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyDataType
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.parsePropertyString
import com.prismatic.liveparser.weave.ALL_ELEMENT_TYPES
import com.prismatic.liveparser.weave.elements.ErrorElement
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.CompoundPropMember
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.CompoundPropProvider
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.CompoundPropertyDefScope
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.MultiPropMember
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.MultiPropProvider
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.PropMember
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.PropProvider
import com.prismatic.liveparser.weave.grammar.TypedWeaveElement.SinglePropertyDefScope
import com.prismatic.liveparser.weave.properties.PropertyExpressionParseException
import com.prismatic.liveparser.weave.properties.PropertyLineParseResult
import com.prismatic.liveparser.weave.properties.parsePropertyLine
import com.prismatic.liveparser.weave.types.WeavePercent
import com.prismatic.liveparser.weave.util.offset
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.math.Vec3
import org.prismatic.opus.math.Vec4
import org.prismatic.opus.time.FrameRate
import org.prismatic.opus.time.MediaTime
import org.prismatic.opus.time.Millis
import org.prismatic.opus.time.Nanos
import org.prismatic.opus.time.Seconds
import org.prismatic.opus.time.Time
import org.prismatic.opus.time.millis
import org.prismatic.opus.time.nanos
import org.prismatic.opus.time.seconds
import kotlin.collections.iterator
import kotlin.collections.plus
import kotlin.reflect.KProperty

fun LiveParseEntityScope<WeaveLineType, WeaveElement>.parseWeaveElement(liveParseConnection: WeaveLiveParseConnection): WeaveElement {
    val scope = WeaveEntityParseScope(this)
    val matches = ALL_ELEMENT_TYPES.filter { with(it){scope.recognize()} }
    val match = matches.singleOrNull() ?: return ErrorElement()
    val inst: TypedWeaveElement<*> = (existingEntityData as? TypedWeaveElement<*>) ?: match.makeInstance()
    inst.liveParseConnection = liveParseConnection
    inst.entityId = entityId
    val bodyLines = entityLines.takeWhile { !it.startsWith("- ") }
//    val propLines = entityLines.drop(bodyLines.size).filter { it.isNotBlank() }
    val props = mutableMapOf<String,MutableList<PropertyLineParseResult>>()
    val otherProps = mutableMapOf<String,MutableList<PropertyLineParseResult>>()
    val styles = mutableListOf<String>()
    val targets = mutableListOf<String>()
    for( lineIndex in bodyLines.size..entityLines.lastIndex) {
        val line = entityLines[lineIndex]
        if( line.isBlank() ) continue
        val parseResult = parsePropertyLine(line).copy(lineNumber = lineIndex)
        if(parseResult.errorType != null) {
            parseError(parseResult.errorType, lineIndex, parseResult.errorOffset..parseResult.errorOffset)
            continue
        }
        val key = parseResult.name
        val targetProperty = match.properties[key]
        val compoundProperty = match.compoundProperties[key]
        if( parseResult.style.isNotEmpty() ) {
            styles += parseResult.style
        } else if( parseResult.target.isNotBlank() ) {
            targets += parseResult.target
        } else if (parseResult.name.isBlank() ) {
            parseError(WeaveParseError.MalformedProperty, lineIndex, parseResult.nameRange)
        } else if( targetProperty == null && compoundProperty!=null ) {
            val compoundType = PropertyType.Compound(compoundProperty.components.map {
                PropertyType.Optional(it.type)
            })
            val compoundParseResult = parsePropertyStringHandlingErrors(parseResult.value, compoundType)
            if( compoundParseResult is ParsedProperty.Error ) {
                parseError(WeaveParseError.InvalidPropertyValue(compoundParseResult.error), lineIndex, compoundParseResult.range.offset(parseResult.valueRange.first) )
            }
            if(compoundParseResult is ParsedProperty.Compound && compoundParseResult.components.size == compoundProperty.components.size ) {

                val actual = compoundParseResult.components
                val requested = compoundProperty.components

                requested.forEachIndexed { index, prop ->
                    val actualValue = actual[index]?.resolveToPropertyExpression()
                    if (actualValue != null) {
                        val name = prop.key
                        val targetProperty = match.properties[name]
                        val compoundParseResult = parseResult.copy(
                            value = parseResult.value.substring(actualValue.sourceRange),
                            valueRange = (actualValue.sourceRange.first + parseResult.valueRange.first)..(actualValue.sourceRange.last + parseResult.valueRange.first)
                        )
                        if (targetProperty != null) {
                            if (parseResult.condition.isNotBlank()) {
                                parseError(WeaveParseError.PropertyConditionNotInStyle, lineIndex, parseResult.conditionRange)
                            }
                            if (targetProperty.multiInstanceAccumulator != null) {
                                props.getOrPut(name, { mutableListOf() }).add(compoundParseResult)
                            } else {
                                props.getOrPut(name, { mutableListOf() }).apply {
                                    clear()
                                    add(compoundParseResult)
                                }
                            }
                        } else {
                            if (match.acceptsOtherProperties) {
                                otherProps.getOrPut(name, { mutableListOf() }).add(compoundParseResult)
                            } else {
                                parseError(WeaveParseError.UnrecognizedProperty(key), lineIndex, parseResult.nameRange)
                            }
                        }

                    }
                }
            }
        } else if( targetProperty == null ) {
            if( match.acceptsOtherProperties ) {
                otherProps.getOrPut(key,{mutableListOf()}).add(parseResult)
            } else {
                parseError(WeaveParseError.UnrecognizedProperty(key), lineIndex, parseResult.nameRange)
            }
        } else {
            if(parseResult.condition.isNotBlank()) {
                parseError(WeaveParseError.PropertyConditionNotInStyle, lineIndex, parseResult.conditionRange)
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
                        parsePropertyStringHandlingErrors(parseResult.condition, PropertyType.Expression).also { result ->
                            if( result is ParsedProperty.Error ) {
                                parseError(WeaveParseError.InvalidPropertyCondition(result.error), parseResult.lineNumber, result.range.offset(parseResult.conditionRange) )
                            }
                        }
                    },
                    value = parsePropertyStringHandlingErrors(
                        s = parseResult.value,
                        type = PropertyType.Expression      // TODO: This should handle situations like compound properties (for fonts)
                    ).also { result ->
                        if( result is ParsedProperty.Error ) {
                            parseError(WeaveParseError.InvalidPropertyValue(result.error), parseResult.lineNumber, result.range.offset(parseResult.valueRange) )
                        }
                    }
                )
            }
        }
        inst.updateOtherProperties(otherPropLines)
    }
    inst.updateStyles(styles)
    inst.updateTargets(targets)
    for ((propName, propLines) in props) {
        val prop = inst.properties[propName] ?: continue
        if( propLines.size == 1 ) {
            val propLine = propLines.first()
            val parsed = parsePropertyStringHandlingErrors(propLine.value, prop.type)
            if( parsed is ParsedProperty.Error ) {
                parseError(WeaveParseError.InvalidPropertyValue(parsed.error), propLine.lineNumber, parsed.range.offset(propLine.valueRange) )
            }
            prop.update(parsed, propLine.lineNumber, propLine.nameRange, propLine.valueRange)
        } else if( propLines.size > 1 ) {
            val accumulator = prop.multiInstanceAccumulator ?: PropertyAccumulator.Sum
            val parsed = propLines.asSequence().map {
                parsePropertyStringHandlingErrors(it.value, prop.type).also { result ->
                    if( result is ParsedProperty.Error ) {
                        parseError(WeaveParseError.InvalidPropertyValue(result.error), it.lineNumber, result.range.offset(it.valueRange) )
                    }
                }
            }.reduce { a, b ->
                when {
                    a !is PropertyExpression -> b
                    b !is PropertyExpression -> a
                    else -> accumulator.makeAccumulator(a,b)
                }
            } ?: continue
            prop.update(parsed, propLines.last().lineNumber, propLines.last().nameRange, propLines.last().valueRange)
        }
    }
    val lastNonBlankLine = entityLines.indexOfLast { it.isNotBlank() }
    inst.newPropertyInsertionLine = if(lastNonBlankLine < 0) entityLines.size+1 else lastNonBlankLine+1
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

fun parsePropertyStringHandlingErrors(s: String, type: PropertyType) : ParsedProperty{
    return runCatching { parsePropertyString(s,type) }
        .recover {
            ParsedProperty.Error(it.message ?: it.toString(), null, (it as? PropertyExpressionParseException)?.range ?: IntRange.EMPTY)
        }
        .getOrNull() ?: PropertyExpression.of("null")
}

class WeaveEntityParseScope(private val baseScope: LiveParseEntityScope<WeaveLineType, WeaveElement>) : LiveParseEntityScope<WeaveLineType, WeaveElement> by baseScope {
    val firstLine = baseScope.entityLines.first().decomposeLine()
    private val terminalLines = baseScope.entityLines.asReversed().asSequence().map { it.decomposeLine() }.takeWhile { it.isBlankLine || it.isPropertyLine }.toList().reversed()
    val bodyLines = entityLines.subList(0, entityLines.size - terminalLines.size)
}

fun WeaveEntityParseScope.isConcreteEntity(namespace: String) : Boolean =
    firstLine.firstChar == '[' && firstLine.lastChar == ']'
            && (':' in firstLine.trimmedBody || '.' !in firstLine.trimmedBody)
            && firstLine.trimmedBody.trim().removeSurrounding("[","]").substringBefore(':').trim() == namespace

fun WeaveEntityParseScope.isDefinitionEntity(namespace: String) : Boolean =
    firstLine.firstChar == '{' && firstLine.lastChar == '}'
            && ':' in firstLine.trimmedBody
            && firstLine.trimmedBody.trim().removeSurrounding("{","}").substringBefore(':').trim() == namespace

typealias Vec2Prop = WeaveElement.Prop<Vec2>
typealias TimeProp = WeaveElement.Prop<Time>
typealias IntProp = WeaveElement.Prop<Int>
typealias PercentProp = WeaveElement.Prop<WeavePercent>

interface WeaveElement {
    val elementTypeName: String

    fun debugString(): String

    fun WeaveEntityParseScope.recognize() : Boolean
    fun WeaveEntityParseScope.parse()

    fun makeInstanceGeneric() : WeaveElement

    val styles: List<String>
    val targets: List<String>
    val properties: Map<String, Prop<*>>
    val compoundProperties: Map<String, CompoundProperty>

    val acceptsOtherProperties : Boolean
    val otherProperties: List<PropertyLine>

    interface PropInfo {
        val key: String
        val displayName: String
    }
    interface CompoundProperty : PropInfo {
        val components: List<Prop<*>>
    }

    interface Prop<T> : PropInfo {
        val type: PropertyType
        val multiInstanceAccumulator: PropertyAccumulator?
        val isSpecified: Boolean
        fun update(value: ParsedProperty?, lineNumber: Int = -1, nameRange: IntRange = IntRange.EMPTY, valueRange: IntRange = IntRange.EMPTY)
        val parsedProperty: ParsedProperty
        val lineNumber: Int // Line number relative to start of entity
        fun editValue(newValue: T)
        fun editValueAsString(newValue: String)
        val conditionRange: IntRange
        val nameRange: IntRange
        val valueRange: IntRange
    }

    val propertyList : List<Prop<*>>
    val compoundPropertyList : List<CompoundProperty>

}

interface PropertyHolder {

    fun <T> propProvider(
        defaultValue: T,
        key: String? = null,
        type: PropertyType,
        def:(SinglePropertyDefScope.()->Unit)?
//        parser: (String) -> T?
    ) : PropProvider<T>

    fun compoundPropProvider(
        components: List<WeaveElement.Prop<*>>,
        def:(CompoundPropertyDefScope.()->Unit)?
    ) : CompoundPropProvider

    fun multiPropProvider() : MultiPropProvider

    val acceptsOtherProperties : Boolean
    val propertyList : List<WeaveElement.Prop<*>>
    val compoundPropertyList : List<WeaveElement.CompoundProperty>

    val otherProperties: List<PropertyLine>

    fun prop(defaultValue: Int, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Int> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Float, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Float> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Double, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Double> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Long, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Long> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Number), def=def)
    fun prop(defaultValue: Time, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Time> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Time), def=def)
    fun prop(defaultValue: Color, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Color> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Color), def=def)
    fun prop(defaultValue: String, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<String> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.String), def=def)
    fun prop(defaultValue: Vec2, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Vec2> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec2), def=def)
    fun prop(defaultValue: WeavePercent, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<WeavePercent> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Percent), def=def)
    fun prop(defaultValue: Pair<Int,Int>, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Pair<Int,Int>> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Ratio), def=def)
    fun prop(defaultValue: Vec3, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Vec3> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec3), def=def)
    fun prop(defaultValue: Vec4, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<Vec4> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Vec4), def=def)
    fun prop(defaultValue: FrameRate, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<FrameRate> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.FrameRate), def=def)
    fun prop(defaultValue: WeaveDimension, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<WeaveDimension> = propProvider(defaultValue, type = PropertyType.TypedExpression(PropertyDataType.Dimension), def=def)
    fun prop(defaultValue: FontWeight, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<FontWeight> =
        propProvider(defaultValue, type = PropertyType.Alternate(
            PropertyType.UnquotedStringEnum("Thin","ExtraLight","Light","Normal","Medium","SemiBold","Bold","ExtraBold","Black"),
            PropertyType.TypedExpression(PropertyDataType.Integer)
        ), def=def)
    fun prop(defaultValue: FontStyle, def:(SinglePropertyDefScope.()->Unit)?=null): PropProvider<FontStyle> = propProvider(defaultValue, type = PropertyType.UnquotedStringEnum("Italic","Regular"), def=def)
    fun prop(vararg components: WeaveElement.Prop<*>, def:(CompoundPropertyDefScope.()->Unit)?=null) : CompoundPropProvider = compoundPropProvider(components.toList(), def)
    fun otherProps(): MultiPropProvider = multiPropProvider()
}

class PropertyManager : PropertyHolder {

    final override var acceptsOtherProperties : Boolean = false
        private set

    final override var propertyList : List<WeaveElement.Prop<*>> = emptyList()
        private set

    final override var compoundPropertyList : List<WeaveElement.CompoundProperty> = emptyList()
        private set

    final override val otherProperties: List<PropertyLine> field = mutableStateListOf()

    var liveParseConnection: WeaveLiveParseConnection? = null
    var entityId: EntityId = EntityId.None
    var newPropertyInsertionLine: Int = -1

    fun updateOtherProperties(otherPropLines: List<PropertyLine>) {
        // TODO: change the following to only set property lines that have changed, to reduce state changes
        otherProperties.clear()
        otherProperties.addAll(otherPropLines)
    }

    override fun compoundPropProvider(
        components: List<WeaveElement.Prop<*>>,
        def:(CompoundPropertyDefScope.()->Unit)?
    ) : CompoundPropProvider {
        return object : CompoundPropProvider, CompoundPropertyDefScope {
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): CompoundPropMember {
                val compoundProp : WeaveElement.CompoundProperty = CompoundPropertyImpl(components, prop.name, displayName ?: prop.name)
                compoundPropertyList += compoundProp
                return object : CompoundPropMember {
                    override fun getValue(thisRef: Any?, property: KProperty<*>) : WeaveElement.CompoundProperty {
                        return compoundProp
                    }
                }
            }
            override var displayName: String? = null
        }.also{def?.invoke(it)}
    }

    override fun multiPropProvider() : MultiPropProvider {
        check(!acceptsOtherProperties)
        acceptsOtherProperties = true
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

    override fun <T> propProvider(
        defaultValue: T,
        key: String?,
        type: PropertyType,
        def:(SinglePropertyDefScope.()->Unit)?
//        parser: (String) -> T?
    ) : PropProvider<T> {
        return object : PropProvider<T>, SinglePropertyDefScope {
            var multiInstanceAccumulator: PropertyAccumulator? = null
            var acceptUnquotedString = false
            override var displayName: String? = null
            override fun accumulateMultipleInstances(accumulator: PropertyAccumulator) {
                check(multiInstanceAccumulator==null)
                multiInstanceAccumulator = accumulator
            }

            override fun acceptUnquotedString() {
                acceptUnquotedString = true
            }
            override fun provideDelegate(
                thisRef: Any?,
                prop: KProperty<*>
            ): PropMember<T> {
                val liveProp = PropImpl(
                    defaultValue = defaultValue,
                    key = key ?: prop.name,
                    displayName = displayName ?: (key ?: prop.name).apply { take(1).uppercase() + drop(1) },
                    type = when {
                        acceptUnquotedString -> PropertyType.Alternate(PropertyType.UnquotedStringLiteral, type)
                        else -> type
                    },
                    multiInstanceAccumulator = multiInstanceAccumulator
//                    parser = parser
                )
                propertyList += liveProp
                return liveProp
            }
        }.also{def?.invoke(it)}
    }

    private inner class CompoundPropertyImpl(
        override val components: List<WeaveElement.Prop<*>>,
        override val key: String,
        override val displayName: String,
    ) : WeaveElement.CompoundProperty

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
    ) : WeaveElement.Prop<T>, PropMember<T> {
        val defaultPropValue = PropertyExpression.of(defaultValue)
        var propValue : ParsedProperty? by mutableStateOf(null)
        override val parsedProperty: ParsedProperty
            get() = propValue ?: defaultPropValue

        override fun update(value: ParsedProperty?, lineNumber: Int, nameRange: IntRange, valueRange: IntRange) {
            propValue = value
            this.lineNumber = lineNumber
            this.nameRange = nameRange
            this.valueRange = valueRange
        }

        override var lineNumber: Int = -1
            private set
        override var conditionRange: IntRange = IntRange.EMPTY
            private set
        override var nameRange: IntRange = IntRange.EMPTY
            private set
        override var valueRange: IntRange = IntRange.EMPTY
            private set

        override fun editValue(newValue: T) = editValueAsString(PropertyExpression.of(newValue).toFlatString())

        override fun editValueAsString(newValue: String) {
            val propEntity = liveParseConnection?.weaveLiveParse?.entities?.firstOrNull { it.id == entityId } ?: return
            val tfs = liveParseConnection?.textFieldState ?: return
            val lineStart = tfs.text.offsetToStartOfLine(propEntity.startLine + lineNumber)
            if( lineStart < 0 ) return
            if( valueRange.isEmpty() ) {
                val endOfEntity = tfs.text.offsetToStartOfLine(propEntity.startLine + newPropertyInsertionLine)
                val propLineText = "- $key: $newValue"
                tfs.edit {
                    if( endOfEntity < 0 ) {
                        if( !originalText.endsWith('\n') )
                            appendLine()
                        appendLine(propLineText)
                    } else {
                        insert(endOfEntity, "$propLineText\n")
                    }
                }
            } else {
                tfs.edit {
                    replace(lineStart + valueRange.first, lineStart + valueRange.last + 1, newValue)
                }
            }
        }

        override val isSpecified: Boolean get() = propValue != null
        override fun getValue(thisRef: Any?, property: KProperty<*>): WeaveElement.Prop<T> = this@PropImpl
    }
}

abstract class TypedWeaveElement<T: TypedWeaveElement<T>>(private val propertyManager: PropertyManager) : WeaveElement, PropertyHolder by propertyManager {

    var liveParseConnection: WeaveLiveParseConnection?
        get() = propertyManager.liveParseConnection
        set(value) { propertyManager.liveParseConnection = value }

    var entityId: EntityId
        get() = propertyManager.entityId
        set(value) { propertyManager.entityId = value }

    var newPropertyInsertionLine: Int
        get() = propertyManager.newPropertyInsertionLine
        set(value) { propertyManager.newPropertyInsertionLine = value }

    override val properties: Map<String, WeaveElement.Prop<*>> by lazy {propertyList.associateBy { it.key }}
    override val compoundProperties: Map<String, WeaveElement.CompoundProperty> by lazy {compoundPropertyList.associateBy { it.key }}

    abstract fun makeInstance() : T

    override fun makeInstanceGeneric() = makeInstance()

    final override var styles: List<String> by mutableStateOf(emptyList())
        private set
    final override var targets: List<String> by mutableStateOf(emptyList())
        private set

    fun updateStyles(styles: List<String>) {
        this.styles = styles
    }

    fun updateTargets(targets: List<String>) {
        this.targets = targets
    }

    fun updateOtherProperties(otherPropLines: List<PropertyLine>) {
        propertyManager.updateOtherProperties(otherPropLines)
    }


    interface PropMember<T> {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): WeaveElement.Prop<T>
    }

    interface MultiPropMember {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<PropertyLine>
    }

    interface CompoundPropMember {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): WeaveElement.CompoundProperty
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
        var displayName: String?
    }

    interface CompoundPropertyDefScope : PropertyDefScope {

    }

    interface SinglePropertyDefScope : PropertyDefScope {
        fun accumulateMultipleInstances(accumulator: PropertyAccumulator)
        fun acceptUnquotedString()
    }
}

fun SinglePropertyDefScope.sumMultipleInstances() = accumulateMultipleInstances(PropertyAccumulator.Sum)
fun SinglePropertyDefScope.multiplyMultipleInstances() = accumulateMultipleInstances(PropertyAccumulator.Product)

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
        WeaveDimension::class, WeaveDimension.Millimeters::class, WeaveDimension.Points::class ->
            return PropertyType.TypedExpression(PropertyDataType.Dimension)
        else -> throw IllegalArgumentException("Unsupported type")
    }
}

fun ParsedProperty.resolveToPropertyExpression() : PropertyExpression? = when(this) {
    is ParsedProperty.Alternate -> value?.resolveToPropertyExpression()
    is ParsedProperty.Compound -> components.firstNotNullOfOrNull { it?.resolveToPropertyExpression() }
    is ParsedProperty.Error -> default?.resolveToPropertyExpression() ?: PropertyExpression.of(error)
    is PropertyExpression -> this
}