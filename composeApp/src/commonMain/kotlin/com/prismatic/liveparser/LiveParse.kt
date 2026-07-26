package com.prismatic.liveparser

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.collections.forEachIndexed

@Composable
public fun <CONTEXT:Any> TextFieldState.rememberLiveParse(initialContext: CONTEXT, computeContext: (String, CONTEXT) -> CONTEXT) : LiveParse<CONTEXT> {
    val coroutineScope = rememberCoroutineScope()
    val liveParse = remember { LiveParseImpl<CONTEXT>(coroutineScope,this, initialContext, computeContext) }
    return liveParse
}

public interface LiveParse<CONTEXT:Any> {
    val lines: List<LiveParseLine<CONTEXT>>
}

public interface LiveParseLine<CONTEXT:Any> {
    val line: String
    val context: CONTEXT
}

private data class LiveParseLineImpl<CONTEXT:Any>(
    override val line: String,
    override val context: CONTEXT,
    val prevContext: CONTEXT
) : LiveParseLine<CONTEXT>

private class LiveParseImpl<CONTEXT:Any>(
    coroutineScope: CoroutineScope,
    textFieldState: TextFieldState,
    val initialContext: CONTEXT,
    val computeContext: (String, CONTEXT) -> CONTEXT
) : LiveParse<CONTEXT> {
    val lineChangesFlow = textFieldState.changedLinesInfoFlow()

    private val _lines = mutableStateListOf<LiveParseLineImpl<CONTEXT>>(LiveParseLineImpl("", initialContext, initialContext))
    override val lines: List<LiveParseLine<CONTEXT>> get() = _lines

    init {
        coroutineScope.launch {
            lineChangesFlow.collect { change ->

                var prevContext = if(change.startLine <= 0) initialContext else _lines[change.startLine-1].context
                change.newLines.forEachIndexed { i, line ->
                    val newContext = computeContext(line, prevContext)
                    val newLine = LiveParseLineImpl(line,newContext,prevContext)
                    prevContext = newContext
                    if( i < change.prevLineCount )
                        _lines[change.startLine+i] = newLine
                    else
                        _lines.add(change.startLine+i, newLine)
                }
                if( change.newLineCount < change.prevLineCount ) {
                    _lines.removeRange(change.startLine+change.newLineCount, change.startLine+change.prevLineCount)
                }

                var firstUnchangedLine = change.startLine + change.newLineCount
                var precedingContext = if(firstUnchangedLine > 0) _lines[firstUnchangedLine-1].context else initialContext
                while( firstUnchangedLine < _lines.size && _lines[firstUnchangedLine].prevContext != precedingContext ) {
                    val newContext = computeContext(_lines[firstUnchangedLine].line,precedingContext)
                    _lines[firstUnchangedLine] = LiveParseLineImpl(_lines[firstUnchangedLine].line, newContext, precedingContext)
                    firstUnchangedLine++
                    precedingContext = if(firstUnchangedLine > 0) _lines[firstUnchangedLine-1].context else initialContext
                }

            }
        }
    }
}