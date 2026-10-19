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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismatic.liveparser.weave.elements.ErrorElement
import com.prismatic.liveparser.weave.elements.StyleElement
import com.prismatic.liveparser.weave.elements.TextElement
import com.prismatic.liveparser.weave.elements.VoiceoverElement
import com.prismatic.liveparser.weave.grammar.*
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyExpressionContext
import com.prismatic.liveparser.weave.properties.evaluate
import com.prismatic.liveparser.weave.properties.toFlatString
import org.prismatic.opus.time.Time
import org.prismatic.opus.time.seconds


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

class LWEntityParseScope(private val baseScope: LiveParseEntityScope<LineType, LWElement<*>>) : LiveParseEntityScope<LineType, LWElement<*>> by baseScope {
    val firstLine = baseScope.entityLines.first().decomposeLine()
    private val terminalLines = baseScope.entityLines.asReversed().asSequence().map { it.decomposeLine() }.takeWhile { it.isBlankLine || it.isPropertyLine }.toList().reversed()
    val propertyLines = terminalLines.filter { it.isPropertyLine }
    val bodyLines = entityLines.subList(0, entityLines.size - terminalLines.size)
}

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
    var t by remember { mutableStateOf(0.5f) }
    val textFieldState = remember { TextFieldState() }
    val selectedLine by derivedStateOf(structuralEqualityPolicy()) {
        textFieldState.text.countInSubsequence(0,textFieldState.selection.start) {it=='\n'}
    }
    val textFieldScrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val liveParse = textFieldState.rememberLiveParse(
        initialContext = LineType.None,
        nullEntity = ErrorElement() as LWElement<*>,
        computeContext = { line, context ->
            getLineType(line, context)
        },
        parseEntity = {
            println("ParseElement:${this.entityId}")
            parseElement(context)
        }
    )
    val styleElements by derivedStateOf {liveParse.entities.map { it.entity }.filterIsInstance<StyleElement>().groupBy { it.styleName }}
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val topLine : Int by remember { derivedStateOf(structuralEqualityPolicy()) {
        textLayoutResult?.getLineForVerticalPosition(textFieldScrollState.value.toFloat()) ?: 0
    }}
    val scrollDiff : Int by remember { derivedStateOf(structuralEqualityPolicy()) {
        val t : Float = textLayoutResult?.getLineTop(listState.firstVisibleItemIndex) ?: 0f
        textFieldScrollState.value - t.toInt()
    }}

    LaunchedEffect(topLine) {
        listState.scrollToItem(topLine)
    }

    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().weight(1f)) {
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
                    val elementEntity = entity.entity
                    val propExprContext = remember(t) { object: PropertyExpressionContext {
                        override val duration: Time get() = 10.seconds
                        override val progress: Float = t

                        override fun resolveVariable(symbol: String): PropertyExpression? = when(symbol) {
                            "foo" -> PropertyExpression.of(123)
                            "bar" -> PropertyExpression.of("Test")
                            "cts" -> PropertyExpression.of(t)
                            "elementType" -> PropertyExpression.of(elementEntity.elementTypeName)
                            "textType" -> PropertyExpression.of((elementEntity as? TextElement)?.textType?.ifBlank { "Text" } ?: "")
                            "H1" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h1")
                            "H2" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h2")
                            "H3" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h3")
                            "H4" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h4")
                            "Text" -> PropertyExpression.of(elementEntity is TextElement)
                            "Voiceover" -> PropertyExpression.of(elementEntity is VoiceoverElement)
                            else -> null
                        }

                        override fun callFunction(
                            symbol: String,
                            args: List<PropertyExpression>
                        ): PropertyExpression? {
                            return null
                        }
                    }.withColorConstructors() }
                    val elementName = elementEntity::class.simpleName
                    val elementBodyString = "  " + elementEntity.debugString() + "\n"
                    val styleString = if(elementEntity.styles.isNotEmpty()) elementEntity.styles.joinToString() { "  {$it}\n" } + " ----\n" else ""
                    val otherPropertiesString = if(elementEntity.acceptsOtherProperties && elementEntity.otherProperties.isNotEmpty()) {
                        "\n  ---\n"+
                        elementEntity.otherProperties.joinToString("\n") {
                            val valueLines = it.value.evaluate(propExprContext).toFlatString().lines()
                            if(valueLines.size == 1)
                                "  ${it.name}: ${valueLines.first()}"
                            else
                                "  ${it.name}:\n" + valueLines.joinToString("\n") {"     $it"}
                        }
                    } else ""
                    val propertyString = elementBodyString + styleString + elementEntity.properties.entries.joinToString("\n") { (k, v) ->
                        val styleProperties: List<ParsedProperty> = (elementEntity.styles + listOf("*"))
                            .flatMap { style -> styleElements[style] ?: emptyList() }
                            .flatMap { styleElement -> styleElement.styleProperties.filter { it.name == k } }
                            .filter { it.condition==null || (it.condition.evaluate(propExprContext) as? PropertyExpression.BooleanLiteral)?.value == true } // TODO: Handle condition here
                            .map { it.value }
                        val accumulator = v.multiInstanceAccumulator
                        val resolvedProperty = if( accumulator != null ) {
                            if(styleProperties.isEmpty()) {
                                v.parsedProperty
                            } else {
                                (styleProperties+listOf(v.parsedProperty)).reduce { a, b ->
                                    when {
                                        a !is PropertyExpression -> b
                                        b !is PropertyExpression -> a
                                        else -> accumulator.makeAccumulator(a,b)
                                    }
                                }
                            }
                        } else {
                            if( v.isSpecified || styleProperties.isEmpty() ) {
                                v.parsedProperty
                            } else {
                                styleProperties.last()
                            }
                        }
                        val valueLines = resolvedProperty.evaluate(propExprContext).toFlatString().lines()
                        if(valueLines.size == 1)
                            "  $k: ${valueLines.first()}"
                        else
                            "  $k:\n" + valueLines.joinToString("\n") {"     $it"}
                    } + otherPropertiesString
                    Text(
                        "${entity.id}: $elementName(\n$propertyString\n)",
                        Modifier.background(if (selected) Color.Yellow else Color.White), style = TextStyle(fontSize = 12.sp, lineHeight = 12.sp)
                    )
                }
            }
        }
        Slider(t, {t=it}, Modifier.fillMaxWidth())
    }
}

/*

Test
- position: (10,3+5) ..accel.. (35, 100) @ 30s
- position: (5,10009) + (5,80)

Blah
More text
And stuff
- typeface: "foo" + foo
- position: (10,30)
 */

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