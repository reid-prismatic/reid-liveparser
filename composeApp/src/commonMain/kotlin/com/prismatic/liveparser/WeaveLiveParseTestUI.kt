package com.prismatic.liveparser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismatic.liveparser.parser.LiveParseEntity
import com.prismatic.liveparser.parser.LiveParseError
import com.prismatic.liveparser.parser.LiveParseLine
import com.prismatic.liveparser.weave.elements.TextElement
import com.prismatic.liveparser.weave.elements.VoiceoverElement
import com.prismatic.liveparser.weave.grammar.*
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyDataType
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyExpressionContext
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.evaluate
import com.prismatic.liveparser.weave.properties.toFlatString
import com.prismatic.liveparser.weave.properties.toVec2OrNull
import com.prismatic.liveparser.weave.properties.toWeaveDimensionOrNull
import com.prismatic.liveparser.weave.styles.resolveStyles
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.time.Time
import org.prismatic.opus.time.seconds

@Composable
fun WeaveLiveParseTestUI() {
    var t by remember { mutableStateOf(0.5f) }

    val textFieldState = remember { TextFieldState() }
    val liveParse = textFieldState.rememberWeaveLiveParse()

    val selectedLine by derivedStateOf(structuralEqualityPolicy()) {
        textFieldState.text.countInSubsequence(0,textFieldState.selection.start) {it=='\n'}
    }
    val textFieldScrollState = rememberScrollState()
    val listState = rememberLazyListState()

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
                        .drawErrors(liveParse.errors, textLayoutResult, 45.dp, textFieldScrollState.value)
                        .padding(start = 45.dp)
                        .verticalScroll(textFieldScrollState),
                    onTextLayout = {
                        textLayoutResult = it()
                    }
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                items(liveParse.errors) { error ->
                    Text("${error.errorLine}: ${error.message}", color = Color.Red, style = TextStyle(fontSize = 10.sp, lineHeight = 10.sp))
                }
                items(liveParse.entities, {it.id}) { entity ->
                    val selected = selectedLine in entity.startLine ..entity.endLine

                    val propExprContext = remember(t) {
                        SamplePropertyExpressionContext(
                            duration = 10.seconds,
                            progress = t,
                            elementEntity = entity.entityData,
                            variableProvider = { symbol ->
                                var value : PropertyExpression? = null
                                liveParse.variableElements.forEach { el ->
                                    el.variables[symbol]?.let {value = it}
                                }
                                value
                            }
//                            variables = liveParse.variableElements.flatMap { el ->
//                                el.variables.entries.map { it.key to it.value }
//                            }.toMap()
                        ).withColorConstructors()
                    }

                    EntityDetails(
                        entity, t, propExprContext,
                        Modifier
                            .background(if (selected) Color.Yellow else Color.White)
                            .fillMaxWidth(),
                        styleResolver = { property ->
                            resolveStyles(entity.entityData, property, liveParse.styleElements, propExprContext)
                        }
                    )
                }
            }
        }
        Slider(t, {t=it}, Modifier.fillMaxWidth())
    }
}

@Composable
private fun EntityDetails(
    parseEntity: LiveParseEntity<WeaveElement>,
    t: Float,
    propExprContext: PropertyExpressionContext,
    modifier: Modifier = Modifier,
    styleResolver: (property: WeaveElement.Prop<*>)->ParsedProperty
) {

    val element = parseEntity.entityData

    ProvideTextStyle(TextStyle(fontSize = 10.sp, lineHeight = 11.sp)) {
        Column(modifier) {
            Text("${parseEntity.id}: ${element.elementTypeName}", Modifier.padding(top = 5.dp))
            val body = element.debugString()
            if (body.isNotBlank()) {
                HorizontalDivider()
                Text(body, Modifier.padding(start = 10.dp))
            }
            if( element.styles.isNotEmpty() ) {
                HorizontalDivider()
                element.styles.forEach { style ->
                    Text("{$style}", Modifier.padding(start = 10.dp))
                }
                HorizontalDivider()
            }
            element.properties.forEach { (name,prop) ->
                Row {
                    val lineNumberStr = if(prop.lineNumber>=0) "(${prop.lineNumber + parseEntity.startLine + 1})" else ""
                    Text( "${prop.key}$lineNumberStr:  ", Modifier.padding(start = 10.dp).width(70.dp), overflow = TextOverflow.Ellipsis, maxLines = 1)
                    Text(styleResolver(prop).evaluate(propExprContext).toFlatString(), Modifier.clickable {
                        val newValue = makeEditingTestValue(prop, propExprContext)
                        if(newValue != null) {
                            prop.editValueAsString(newValue)
                        }
                    })
                }
            }
            if (element.acceptsOtherProperties && element.otherProperties.isNotEmpty()) {
                HorizontalDivider()
                element.otherProperties.forEach { prop ->
                    Text(prop.name + ":  ", Modifier.padding(start = 10.dp).width(70.dp), overflow = TextOverflow.Ellipsis, maxLines = 1)
                    Text(prop.value.evaluate(PropertyExpressionContext.Empty).toFlatString())
                }
            }
        }
    }


}

private fun makeEditingTestValue(prop: WeaveElement.Prop<*>, propExprContext: PropertyExpressionContext) : String? {
    if(prop.type == PropertyType.TypedExpression(PropertyDataType.Vec2)) {
        val prevValue = prop.parsedProperty.evaluate(propExprContext).toVec2OrNull()
        if( prevValue != null ) {
            val newValue = Vec2(prevValue.x * 2f, prevValue.y * 3f)
            return "${newValue.x.toStringAsFixed(3)}, ${newValue.y.toStringAsFixed(3)}"
        }
    } else if(prop.type == PropertyType.TypedExpression(PropertyDataType.Dimension)) {
        val prevValue = prop.parsedProperty.evaluate(propExprContext).toWeaveDimensionOrNull()
        if( prevValue is WeaveDimension.Points ) {
            val prevPoints = prevValue.value
            val newPoints = when {
                prevPoints < 8 -> 8
                prevPoints < 10 -> 10
                prevPoints < 12 -> 12
                prevPoints < 14 -> 14
                prevPoints < 16 -> 16
                prevPoints < 18 -> 18
                prevPoints < 20 -> 20
                prevPoints < 24 -> 24
                prevPoints < 28 -> 28
                prevPoints < 32 -> 32
                prevPoints < 36 -> 36
                prevPoints < 40 -> 40
                prevPoints < 48 -> 48
                prevPoints < 54 -> 54
                prevPoints < 60 -> 60
                else -> 8
            }
            return "${newPoints}pt"
        }
    }
    return null
}

private class SamplePropertyExpressionContext(
    override val duration: Time = 10.seconds,
    override val progress: Float = 0.5f,
    val elementEntity: WeaveElement,
    val variableProvider: (String)->PropertyExpression?
) : PropertyExpressionContext {

    override fun resolveVariable(symbol: String): PropertyExpression? = when(symbol) {
//        "foo" -> PropertyExpression.of(123)
//        "bar" -> PropertyExpression.of("Test")
//        "cts" -> PropertyExpression.of(progress)
        "elementType" -> PropertyExpression.of(elementEntity.elementTypeName)
        "textType" -> PropertyExpression.of((elementEntity as? TextElement)?.textType?.ifBlank { "Text" } ?: "")
        "H1" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h1")
        "H2" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h2")
        "H3" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h3")
        "H4" -> PropertyExpression.of((elementEntity as? TextElement)?.textType == "h4")
        "Text" -> PropertyExpression.of(elementEntity is TextElement)
        "Voiceover" -> PropertyExpression.of(elementEntity is VoiceoverElement)
        else -> variableProvider(symbol)
    }

    override fun callFunction(
        symbol: String,
        args: List<PropertyExpression>
    ): PropertyExpression? {
        return null
    }
}

@Composable
fun LineDetail(line: LiveParseLine<WeaveLineType>) {
    val recomposeCount = remember { intArrayOf(0) }
    SideEffect {
        recomposeCount[0]++
    }
    Box(Modifier.fillMaxWidth().height(16.5.dp).padding(start=45.dp).background(when(line.lineType) {
        WeaveLineType.None -> Color(0.961f, 0.961f, 0.961f, 1.0f)
        WeaveLineType.Blank -> Color(0.878f, 0.878f, 0.878f, 1.0f)
        WeaveLineType.ConcreteEntityStart -> Color(0.6f, 0.8f, 1f)
        WeaveLineType.ConcreteEntityBody -> Color(0.8f, 0.9f, 1f)
        WeaveLineType.Property -> Color(0.455f, 0.537f, 0.961f, 1.0f)
        WeaveLineType.DefinitionEntityStart -> Color(1.0f, 0.8f, 0.6f)
        WeaveLineType.DefinitionEntityBody -> Color(1.0f, 0.9f, 0.8f)
        WeaveLineType.CodeStart -> Color(1.0f, 0.6f, 0.773f, 1.0f)
        WeaveLineType.DelayEntityStart -> Color(0.91f, 0.376f, 0.78f, 1.0f)
        WeaveLineType.CodeContinue -> Color(0.898f, 0.537f, 0.69f, 1.0f)
        WeaveLineType.CodeEnd -> Color(0.824f, 0.471f, 0.62f, 1.0f)
        WeaveLineType.ScriptStart -> Color(0.478f, 0.91f, 0.761f, 1.0f)
        WeaveLineType.ScriptContinue -> Color(0.424f, 0.851f, 0.702f, 1.0f)
        WeaveLineType.ScriptEnd -> Color(0.31f, 0.655f, 0.533f, 1.0f)
        WeaveLineType.SyntaxError -> Color(0.89f, 0.353f, 0.353f, 1.0f)
        WeaveLineType.SegmentBreak -> Color(0.353f, 0.353f, 0.353f, 1.0f)
    })) {
        Text(line.line, Modifier.height(16.5.dp).align(Alignment.TopStart), color=Color.Blue, fontSize=13.sp)
        val eidStr = if(line.lineType.isEntityStart) "${line.entityId} / " else ""
        val contextStr = if(line.lineType == WeaveLineType.Blank) "" else "${line.lineType}"
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

@Composable
fun Modifier.drawErrors(errors: List<LiveParseError>, layoutResult: TextLayoutResult?, leftOffset: Dp, scrollOffset: Int): Modifier {
    val measurer = rememberTextMeasurer()
    return this then Modifier.drawBehind {
        if(layoutResult == null || layoutResult.layoutInput.text.isEmpty()) return@drawBehind
        var lineNumber = 0
        val leftOffsetPx = leftOffset.toPx()
        for (i in 0 ..< layoutResult.lineCount) {
            val lineStart = layoutResult.getLineStart(i)
            val isLineStart = lineStart<=0 || layoutResult.layoutInput.text[lineStart-1] == '\n'
            if(isLineStart) {
                lineNumber++
                errors.forEach {
                    if( it.errorLine == lineNumber ) {
                        val baseline = layoutResult.getLineBaseline(i)
                        val y = baseline - scrollOffset + 2.dp.toPx()
                        val thickness = 1.5.dp.toPx()
                        val left = layoutResult.multiParagraph.getLineLeft(i) + leftOffsetPx
                        val right = layoutResult.multiParagraph.getLineRight(i) + leftOffsetPx
                        if( it.errorColumn >= 0 ) {
                            val underlineLeft = layoutResult.multiParagraph.getHorizontalPosition((lineStart+it.errorColumn).coerceAtMost(layoutResult.layoutInput.text.length-1), true)
                            val colRight = layoutResult.multiParagraph.getHorizontalPosition((lineStart+it.errorColumn+it.sourceLength).coerceAtMost(layoutResult.layoutInput.text.length-1), true)
                            val underlineRight = colRight.coerceAtLeast(underlineLeft+5.dp.toPx())
                            drawLine(
                                color = Color.Red,
                                start = Offset(left + underlineLeft, y),
                                end = Offset(left + underlineRight, y),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),2.dp.toPx())),
                                strokeWidth = thickness
                            )
                            val center = left + (underlineLeft + underlineLeft)/2
                            drawPath(Path().apply {
                                moveTo(center, y)
                                lineTo(center - 5.dp.toPx(), y + 5.dp.toPx())
                                lineTo(center + 5.dp.toPx(), y + 5.dp.toPx())
                                close()
                            }, Color.Red)
                        } else {
                            drawLine(
                                color = Color.Red,
                                start = Offset(left, y),
                                end = Offset(right, y),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),2.dp.toPx())),
                                strokeWidth = thickness
                            )
                        }
                    }
                }
            }
        }
    }
}