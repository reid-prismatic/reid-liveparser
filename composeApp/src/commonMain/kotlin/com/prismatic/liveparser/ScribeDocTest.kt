package com.prismatic.liveparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScribeDocTest() {
    val textFieldState = remember { TextFieldState() }
    val liveParse = textFieldState.rememberLiveParse( LineType.None) { line, context ->
        getLineType(line, context)
    }
//    val scribeDocState = remember { ScribeDocumentState(scope) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(liveParse.lines) { line ->
                    LineDetail(line)
                }
            }
            BasicTextField(
                textFieldState,
                Modifier.fillMaxSize().padding(top=3.dp).drawLineNumbers(textLayoutResult, 30.dp).padding(start = 45.dp),
                onTextLayout = {
                    textLayoutResult = it()
                }
            )
        }
//        Column(Modifier.weight(1f).fillMaxHeight()) {
//            scribeDocState.changeList.takeLast(20).forEach {
//                Text(it.toString())
//            }
//        }
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
        LineType.CodeContinue -> Color(0.898f, 0.537f, 0.69f, 1.0f)
        LineType.CodeEnd -> Color(0.824f, 0.471f, 0.62f, 1.0f)
        LineType.ScriptStart -> Color(0.478f, 0.91f, 0.761f, 1.0f)
        LineType.ScriptContinue -> Color(0.424f, 0.851f, 0.702f, 1.0f)
        LineType.ScriptEnd -> Color(0.31f, 0.655f, 0.533f, 1.0f)
        LineType.SyntaxError -> Color(0.89f, 0.353f, 0.353f, 1.0f)
        LineType.SegmentBreak -> Color(0.353f, 0.353f, 0.353f, 1.0f)
    })) {
        Text(line.line, Modifier.height(16.5.dp).align(Alignment.TopStart), color=Color.Blue, fontSize=13.sp)
        Text(line.context.toString() + " / " + recomposeCount[0], Modifier.height(16.5.dp).padding(end=25.dp).align(Alignment.TopStart).fillMaxWidth(), color=Color.Red, fontSize=13.sp, textAlign = TextAlign.End)
    }
}

@Composable
fun Modifier.drawLineNumbers(layoutResult: TextLayoutResult?, width: Dp): Modifier {
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
                drawText(number, topLeft = Offset(width.toPx() - number.size.width, baseline-number.firstBaseline), color = Color.DarkGray)
            }
        }
    }
}