package com.prismatic.liveparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.math.Vec3
import org.prismatic.opus.math.Vec4
import org.prismatic.opus.time.FrameRate
import org.prismatic.opus.time.MediaTime
import org.prismatic.opus.time.Time

interface ScribeEntity {
    val type: LineType
    companion object {
        val None = object : ScribeEntity {
            override val type: LineType = LineType.None
        }
    }
}

data class LWElementEntity(val element: LWElement<*>, override val type: LineType) : ScribeEntity

@Immutable
interface MarkdownProperties : ScribeEntity{
    val range: TextRange
    val propRanges: Map<String,TextRange>
    val parseErrors: List<CodeError>
    //    val lineNumber: Int
    val lineRange: IntRange
    val position: Vec2
    val size: Vec2
    val color: Vec4
    val font: String
    val bold: Boolean
    val maxWidth: Float
    val italic: Boolean
    val fontSize: Float
    val scale: Vec2
    val filters: List<String>
    val duration: Time
    val comment: String?
    val voice: String
    val rotateAxis: Vec3
    val rotateAngle: Float
}

@Immutable
private data class MarkdownPropertiesImpl(
    override val type: LineType,
    override val parseErrors: List<CodeError>,
    override val range: TextRange,
    override val propRanges: Map<String,TextRange>,
    override val lineRange: IntRange,
    override val position: Vec2 = Vec2.ZERO,
    override val fontSize: Float = 56f,
    override val size: Vec2 = Vec2.ZERO,
    override val color: Vec4 = Vec4(0.7f,0.7f,0.7f,1f),
    override val font: String = "Default",
    override val bold: Boolean = false,
    override val italic: Boolean = false,
    override val maxWidth: Float = Float.NaN,
    override val scale: Vec2 = Vec2(1f,1f),
    override val filters: List<String> = emptyList(),
    override val duration: Time = MediaTime.Unspecified,
    override val comment: String? = null,
    override val voice: String = "",
    override val rotateAxis: Vec3 = Vec3(0f,0f,1f),
    override val rotateAngle: Float = 0f,
): MarkdownProperties



data class EntityTiming(val delayNext: Boolean, val background: Boolean)

interface OutputFormat {
    val outputName: String
    val resolution: Int
    val ratio: Pair<Int,Int>
    val frameRate: FrameRate
}

val OutputFormat.width: Int get() = if(ratio.first > ratio.second) resolution * ratio.first / ratio.second else resolution
val OutputFormat.height: Int get() = if(ratio.first > ratio.second) resolution else resolution * ratio.second / ratio.first
val OutputFormat.ratioFraction: Float get() = ratio.first.toFloat() / ratio.second.toFloat()

@Immutable
sealed interface MarkdownEntity : ScribeEntity, MarkdownProperties {
    data class Style(val props: MarkdownProperties, val styleName: String): MarkdownEntity, MarkdownProperties by props
    data class Code(val props: MarkdownProperties, val code: String): MarkdownEntity, MarkdownProperties by props
    data class CodeSample(val props: MarkdownProperties, val code: String, val codeLanguage: String): MarkdownEntity, MarkdownProperties by props
    data class Output(val props: MarkdownProperties, override val outputName: String, override val resolution: Int, override val ratio: Pair<Int,Int>, override val frameRate: FrameRate): OutputFormat, MarkdownEntity, MarkdownProperties by props
    data class Text(val props: MarkdownProperties, val text: String): MarkdownEntity, MarkdownProperties by props
    data class Voiceover(val props: MarkdownProperties, val text: String, val timing: EntityTiming, val filename: String): MarkdownEntity, MarkdownProperties by props
    data class Media(val props: MarkdownProperties, val filename: String, val timing: EntityTiming): MarkdownEntity, MarkdownProperties by props
//    data class Include(
//        val props: MarkdownProperties,
//        val filename: String,
//        val timing: EntityTiming,
//        val vars: Map<String, String>,
//        val md: MarkdownParseResult? = null,
//    ): MarkdownEntity, MarkdownProperties by props {
//        val sections = md?.entities?.segmented()
//        val id = "${lineRange.first}:$filename"
//    }
    data class Divider(val props: MarkdownProperties, val transition: String): MarkdownEntity, MarkdownProperties by props
    data class Delay(val props: MarkdownProperties, val delayTime: MediaTime): MarkdownEntity, MarkdownProperties by props
}

//private fun LWEntityParseScope.parseMediaEntity(): ScribeEntity {
//
//}

//abstract class LWVisualElement(definition: Definition<*>) : LWElement(definition) {
//    var position: Vec2 by prop()
//}
//
//class LWTextElement : LWVisualElement(Definition) {
//    var text : String by prop()
//    companion object {
//        val Definition = Definition<LWTextElement>()
//    }
//}
//


sealed class LWPropertyType {
    data object Int : LWPropertyType()
    data object Float : LWPropertyType()
    data object Duration : LWPropertyType()
    data object Vec2 : LWPropertyType()
    data object Vec3 : LWPropertyType()
    data object Vec4 : LWPropertyType()
    data object Color : LWPropertyType()
    data object String : LWPropertyType()
    data object StringList : LWPropertyType()
    data object Boolean : LWPropertyType()
    data object Compound : LWPropertyType()
    data class Enum(val enumValues: List<String>) : LWPropertyType()
}

enum class LWProperty(val key: String, val type: LWPropertyType) {
    Position( "position", LWPropertyType.Vec2),
    Offset( "offset", LWPropertyType.Vec2),
    Size( "size",LWPropertyType.Vec2),
    Color( "color", LWPropertyType.Color),
    Font( "font",LWPropertyType.Compound),
    Typeface( "typeface",LWPropertyType.String),
    Bold( "bold",LWPropertyType.Boolean),
    MaxWidth( "maxWidth",LWPropertyType.Float),
    Italic( "italic",LWPropertyType.Boolean),
    FontSize( "fontsize",LWPropertyType.Float),
    Scale( "scale",LWPropertyType.Float),
    Filters( "filters",LWPropertyType.StringList),
    Duration( "duration",LWPropertyType.Duration),
    Voice( "voice",LWPropertyType.String),
    RotateAxis( "rotateAxis",LWPropertyType.Vec3),
    RotateAngle( "rotateAngle",LWPropertyType.Float);
}

val LWProperties: Map<String, LWProperty> = LWProperty.entries.associateBy(LWProperty::key)
data class LWPropertyInstance<T>(
    val key: String,
    val prop: LWProperty,
    val rawValue: String,
    val value: T
)

private inline fun <reified T> List<LWPropertyInstance<*>>.getProp(prop: LWProperty, default: T, combine: (T,T)->T = {a,b -> b}) : T {
    asReversed().forEach {  }
    var value = default
    forEach { propInst ->
        if( propInst.prop == prop && T::class.isInstance(propInst.value) )
            value = combine( value, propInst.value as T )
    }
    return value
}

//private fun LWEntityParseScope.buildProps() : MarkdownProperties {
//    val errors = mutableListOf<CodeError>()
//    val properties = mutableListOf<LWPropertyInstance<*>>()
//    for ((lineIndex, line) in propertyLines.withIndex()) {
//        val propName = line.trimmedBody.substringBefore(':',"").removePrefix("-").trim()
//        val propValue = line.trimmedBody.substringAfter(':',"").trim()
//        if( propName.isEmpty() ) {
//            errors.add( codeError("Missing property name", line = lineIndex))
//            continue
//        }
//        val prop = LWProperties[propName]
//        if( prop == null ) {
//            errors.add( codeError("Unknown property name: $propName", line = lineIndex))
//            continue
//        }
//        when(prop.type) {
//            LWPropertyType.Boolean -> {
//                when(propValue) {
//                    "true" -> properties.add(LWPropertyInstance(propName, prop, propValue, true))
//                    "false" -> properties.add(LWPropertyInstance(propName, prop, propValue, false))
//                    else -> errors.add( codeError("Invalid property value", line = lineIndex))
//                }
//            }
//            LWPropertyType.Color -> {
//                propValue.toColorVec4OrNull()
//            }
//            LWPropertyType.Compound -> TODO()
//            LWPropertyType.Duration -> TODO()
//            is LWPropertyType.Enum -> TODO()
//            LWPropertyType.Float -> TODO()
//            LWPropertyType.Int -> TODO()
//            LWPropertyType.String -> TODO()
//            LWPropertyType.StringList -> TODO()
//            LWPropertyType.Vec2 -> TODO()
//            LWPropertyType.Vec3 -> TODO()
//            LWPropertyType.Vec4 -> TODO()
//        }
//    }
//    return MarkdownPropertiesImpl(
//        type = context,
//        parseErrors = errors,
//        position = TODO(),
//        fontSize = TODO(),
//        size = TODO(),
//        color = TODO(),
//        font = TODO(),
//        bold = properties.getProp(LWProperty.Bold,false),
//        italic = TODO(),
//        maxWidth = TODO(),
//        scale =  properties.getProp(LWProperty.Scale,Vec2(1f,1f)) {a, b -> a*b},
//        filters = TODO(),
//        duration = TODO(),
//        comment = TODO(),
//        voice = TODO(),
//        rotateAxis = TODO(),
//        rotateAngle = TODO()
//    )
//}

//private fun LWEntityParseScope.buildTiming() : EntityTiming {
//
//}

//private fun LWEntityParseScope.parseConcreteEntity(): ScribeEntity {
//    when {
//        firstLine.isConcreteEntityStart -> {
//            val concreteEntitySpec = firstLine.trimmedBody.removeSurrounding("[","]").trim()
//            val entityTypeDelimiterIndex = concreteEntitySpec.indexOf(':')
//            val extIndex = concreteEntitySpec.indexOf('.')
//            val ext = if(extIndex>=0 && extIndex > entityTypeDelimiterIndex) concreteEntitySpec.drop(extIndex+1).trim().lowercase() else ""
//            val entityType = when {
//                entityTypeDelimiterIndex >= 0 -> // When there's a ':' everything before it is the type.
//                    concreteEntitySpec.take(entityTypeDelimiterIndex).trim()
//                extIndex < 0 -> concreteEntitySpec.trim()   // There's no ':' and no extension, so the whole thing is the type.
//                else -> ""
//            }
//            val entitySpec = when {
//                entityTypeDelimiterIndex >= 0 -> // When there's a ':' everything after it is the spec.
//                    concreteEntitySpec.drop(entityTypeDelimiterIndex + 1).trim()
//                extIndex >= 0 -> concreteEntitySpec.trim() // When there's no colon, but there is an extension, the whole thing is the spec.
//                else -> ""
//            }
//
//            when(entityType) {
//                "text" -> TODO()
//                "voiceover" -> MarkdownEntity.Voiceover(buildProps(),bodyLines.joinToString("\n"), buildTiming(), entitySpec)
//                "video" -> MarkdownEntity.Media(buildProps(),entitySpec, buildTiming())
//                "audio" -> MarkdownEntity.Media(buildProps(),entitySpec, buildTiming())
//                "media" -> MarkdownEntity.Media(buildProps(),entitySpec, buildTiming())
//                "" -> when(ext) {
//                    "png","jpg","jpeg","bmp","mov","gif" -> TODO()
//                    "wav","mp3","m4a","aac","flac" -> TODO()
//                    "txt" -> TODO()
//                    else -> TODO()
//                }
//                else -> TODO()
//            }
//
//        }
//        firstLine.isTextEntityStart -> {
//
//        }
//        firstLine.isVoiceoverEntityStart -> {
//
//        }
//    }
//}

//private fun LWEntityParseScope.parseDefinitionEntity(): ScribeEntity {
//
//}
//
//private fun LWEntityParseScope.parseSegmentBreak(): ScribeEntity {
//
//}
//
//private fun LWEntityParseScope.parseCodeBlockEntity(): ScribeEntity {
//
//}
//
//private fun LWEntityParseScope.parseScriptEntity(): ScribeEntity {
//
//}
//
//private fun LWEntityParseScope.parseDelayEntity(): ScribeEntity {
//
//}
//
//private fun LWEntityParseScope.processSyntaxError(): ScribeEntity {
//
//}

class LWEntityParseScope(private val baseScope: LiveParseEntityScope<LineType,ScribeEntity>) : LiveParseEntityScope<LineType, ScribeEntity> by baseScope {
    val firstLine = baseScope.entityLines.first().decomposeLine()
    private val terminalLines = baseScope.entityLines.asReversed().asSequence().map { it.decomposeLine() }.takeWhile { it.isBlankLine || it.isPropertyLine }.toList().reversed()
    val propertyLines = terminalLines.filter { it.isPropertyLine }
    val bodyLines = entityLines.subList(1, entityLines.size - terminalLines.size)
}

//private fun LiveParseEntityScope<LineType, ScribeEntity>.parseEntity(firstLineType: LineType): ScribeEntity {
//    return when (firstLineType) {
//        LineType.None, LineType.Blank -> ScribeEntity.None
//        LineType.ConcreteEntityStart -> LWEntityParseScope(this).parseConcreteEntity()
//        LineType.DelayEntityStart -> LWEntityParseScope(this).parseDelayEntity()
//        LineType.DefinitionEntityStart -> LWEntityParseScope(this).parseDefinitionEntity()
//        LineType.SegmentBreak -> LWEntityParseScope(this).parseSegmentBreak()
//        LineType.CodeStart -> LWEntityParseScope(this).parseCodeBlockEntity()
//        LineType.ScriptStart -> LWEntityParseScope(this).parseScriptEntity()
//        LineType.SyntaxError -> LWEntityParseScope(this).processSyntaxError()
//        LineType.ConcreteEntityBody,
//        LineType.Property,
//        LineType.ScriptContinue,
//        LineType.DefinitionEntityBody,
//        LineType.ScriptEnd,
//        LineType.CodeContinue,
//        LineType.CodeEnd -> throw IllegalStateException("Not a valid entity start line: $firstLineType")
//    }
//}
//
//private fun LiveParseEntityScope<LineType, ScribeEntity>.parseEntityB(firstLineType: LineType): ScribeEntity {
//
//}

data class CodeError(
    val message: String,
    val entityId: EntityId,
    val line: Int,
    val char: Int,
    val data: Any?
)

fun LWEntityParseScope.codeError(
    message: String,
    entityId: EntityId = this.entityId,
    line: Int = 0,
    char: Int = 0,
    data: Any? = null
): CodeError = CodeError(message, entityId, line, char, data)


@Composable
fun ScribeDocTest() {
    val textFieldState = remember { TextFieldState() }
    val selectedLine by derivedStateOf(structuralEqualityPolicy()) {
        textFieldState.text.countInSubsequence(0,textFieldState.selection.start) {it=='\n'}
    }
    val textFieldScrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val liveParse = textFieldState.rememberLiveParse(
        initialContext = LineType.None,
        nullEntity = ScribeEntity.None,
        computeContext = { line, context ->
            getLineType(line, context)
        },
        parseEntity = {
            println("ParseElement:${this.entityId}")
            parseElement(context)
//            parseEntity(context)
        }
    )
//    val scribeDocState = remember { ScribeDocumentState(scope) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val topLine : Int by remember { derivedStateOf(structuralEqualityPolicy()) {
        textLayoutResult?.getLineForVerticalPosition(textFieldScrollState.value.toFloat()) ?: 0
    }}
    val scrollDiff : Int by remember { derivedStateOf(structuralEqualityPolicy()) {
        val t : Float = textLayoutResult?.getLineTop(listState.firstVisibleItemIndex) ?: 0f
//        textFieldScrollState.value - listState.firstVisibleItemScrollOffset
        textFieldScrollState.value - t.toInt()
    }}

    LaunchedEffect(topLine) {
        listState.scrollToItem(topLine)
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(3f).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize().offset { IntOffset(0, -scrollDiff) }, state = listState) {
                items(liveParse.lines, {it.entityId}) { line ->
                    LineDetail(line)
                }
            }
            BasicTextField(
                textFieldState,
                Modifier
                    .fillMaxSize()
                    .padding(top = 3.dp)
                    .drawLineNumbers(textLayoutResult, 30.dp, textFieldScrollState.value)
                    .padding(start = 45.dp)
                    .verticalScroll(textFieldScrollState),
                onTextLayout = {
                    textLayoutResult = it()
                }
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
            items(liveParse.entities, {it.id}) { entity ->
                val selected = selectedLine in entity.startLine ..entity.endLine
                val elementEntity = entity.entity as? LWElementEntity
                if(elementEntity != null) {
                    Text(
                        "${entity.id}: ${elementEntity.element::class.simpleName}(${elementEntity.element.properties.map { (k,v) -> "$k: ${v.parsedProperty}" }})",
                        Modifier.background(if (selected) Color.Yellow else Color.White)
                    )

                } else {
                    Text(
                        "${entity.id}: ${entity.entity.type}",
                        Modifier.background(if (selected) Color.Yellow else Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun LineDetail(line: LiveParseLine<LineType>) {
    val recomposeCount = remember { intArrayOf(0) }
    SideEffect {
        recomposeCount[0]++
    }
    Box(Modifier.fillMaxWidth().height(16.5.dp).padding(start=45.dp).background(when(line.context) {
        LineType.None -> Color(0.961f, 0.961f, 0.961f, 1.0f)
        LineType.Blank -> Color(0.878f, 0.878f, 0.878f, 1.0f)
        LineType.ConcreteEntityStart -> Color(0.6f, 0.8f, 1f)
        LineType.ConcreteEntityBody -> Color(0.8f, 0.9f, 1f)
        LineType.Property -> Color(0.455f, 0.537f, 0.961f, 1.0f)
        LineType.DefinitionEntityStart -> Color(1.0f, 0.8f, 0.6f)
        LineType.DefinitionEntityBody -> Color(1.0f, 0.9f, 0.8f)
        LineType.CodeStart -> Color(1.0f, 0.6f, 0.773f, 1.0f)
        LineType.DelayEntityStart -> Color(0.91f, 0.376f, 0.78f, 1.0f)
        LineType.CodeContinue -> Color(0.898f, 0.537f, 0.69f, 1.0f)
        LineType.CodeEnd -> Color(0.824f, 0.471f, 0.62f, 1.0f)
        LineType.ScriptStart -> Color(0.478f, 0.91f, 0.761f, 1.0f)
        LineType.ScriptContinue -> Color(0.424f, 0.851f, 0.702f, 1.0f)
        LineType.ScriptEnd -> Color(0.31f, 0.655f, 0.533f, 1.0f)
        LineType.SyntaxError -> Color(0.89f, 0.353f, 0.353f, 1.0f)
        LineType.SegmentBreak -> Color(0.353f, 0.353f, 0.353f, 1.0f)
    })) {
        Text(line.line, Modifier.height(16.5.dp).align(Alignment.TopStart), color=Color.Blue, fontSize=13.sp)
        val eidStr = if(line.context.isBlockStart) "${line.entityId} / " else ""
        val contextStr = if(line.context == LineType.Blank) "" else "${line.context}"
        Text("$contextStr $eidStr" + recomposeCount[0], Modifier.height(16.5.dp).padding(end=25.dp).align(Alignment.TopStart).fillMaxWidth(), color=Color.Red, fontSize=13.sp, textAlign = TextAlign.End)
    }
}

@Composable
fun Modifier.drawLineNumbers(layoutResult: TextLayoutResult?, width: Dp, scrollOffset: Int): Modifier {
    val measurer = rememberTextMeasurer()
    return this then Modifier.drawBehind {
        if(layoutResult == null || layoutResult.layoutInput.text.isEmpty()) return@drawBehind
        var lineNumber = 0
        for (i in 0 ..< layoutResult.lineCount) {
            val lineStart = layoutResult.getLineStart(i)
            val isLineStart = lineStart<=0 || layoutResult.layoutInput.text[lineStart-1] == '\n'
            if(isLineStart) {
                lineNumber++
                val baseline = layoutResult.getLineBaseline(i)
                val number = measurer.measure(lineNumber.toString())
                drawText(number, topLeft = Offset(width.toPx() - number.size.width, baseline-number.firstBaseline - scrollOffset), color = Color.DarkGray)
            }
        }
    }
}