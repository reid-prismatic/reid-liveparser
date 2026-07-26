package com.prismatic.liveparser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    val scope = rememberCoroutineScope()
    val scribeDocState = remember { ScribeDocumentState(scope) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(scribeDocState.lines) { line ->
                    Box(Modifier.fillMaxWidth().height(16.5.dp).padding(start=45.dp)) {
                        Text(line.body, Modifier.height(16.5.dp).align(Alignment.TopStart), color=Color.Blue, fontSize=13.sp)
                        Text(line.context.toString() + " / " + line.form.toString(), Modifier.height(16.5.dp).padding(end=25.dp).align(Alignment.TopStart).fillMaxWidth(), color=Color.Red, fontSize=13.sp, textAlign = TextAlign.End)
                    }
                }
            }
            BasicTextField(
                scribeDocState.textFieldState,
                Modifier.fillMaxSize().padding(top=3.dp).drawLineNumbers(textLayoutResult, 30.dp).padding(start = 45.dp),
                onTextLayout = {
                    textLayoutResult = it()
                }
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            scribeDocState.changeList.takeLast(20).forEach {
                Text(it.toString())
            }
        }
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