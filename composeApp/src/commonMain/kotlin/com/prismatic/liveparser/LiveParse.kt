package com.prismatic.liveparser

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.collections.forEachIndexed
import kotlin.jvm.JvmInline

@Composable
public fun <CONTEXT:LiveParseLineContext, ENTITY:Any> TextFieldState.rememberLiveParse(
    initialContext: CONTEXT,
    nullEntity: ENTITY,
    computeContext: (String, CONTEXT) -> CONTEXT
) : LiveParse<CONTEXT> {
    val coroutineScope = rememberCoroutineScope()
    val liveParse = remember { LiveParseImpl<CONTEXT, ENTITY>(coroutineScope,this, initialContext, computeContext) }
    return liveParse
}

interface LiveParseLineContext {
    val isBlockStart: Boolean
}

public interface LiveParse<CONTEXT:LiveParseLineContext> {
    val lines: List<LiveParseLine<CONTEXT>>
}

public interface LiveParseLine<CONTEXT:LiveParseLineContext> {
    val line: String
    val context: CONTEXT
}

public interface LiveParseEntity<ENTITY:Any> {
    val entity: ENTITY
}

@JvmInline
public value class EntityId(private val rawId: Long) {
    public constructor() : this(rawId = nextId++)
    public companion object {
        private var nextId: Long = 1
        public val None : EntityId = EntityId(0)
    }
    override fun toString(): String = "<E$rawId>"
}

private data class LiveParseEntityImpl<ENTITY:Any>(
    override val entity: ENTITY,
    val id: EntityId,
    val startLine: Int,
    val endLine: Int,
    val lineRange: IntRange = startLine..endLine
) : LiveParseEntity<ENTITY>

private data class LiveParseLineImpl<CONTEXT:LiveParseLineContext>(
    override val line: String,
    override val context: CONTEXT,
    val prevContext: CONTEXT,
    val entityId: EntityId
) : LiveParseLine<CONTEXT>

private inline infix fun <T:Comparable<T>> ClosedRange<T>.intersects(other: ClosedRange<T>) : Boolean =
    this.start <= other.endInclusive && other.start >= this.endInclusive

private class LiveParseImpl<CONTEXT:LiveParseLineContext, ENTITY:Any>(
    coroutineScope: CoroutineScope,
    textFieldState: TextFieldState,
    val initialContext: CONTEXT,
    val computeContext: (String, CONTEXT) -> CONTEXT,
) : LiveParse<CONTEXT> {
    val lineChangesFlow = textFieldState.changedLinesInfoFlow()

    private val _lines = mutableStateListOf<LiveParseLineImpl<CONTEXT>>(LiveParseLineImpl(
        line = "",
        context = initialContext,
        prevContext = initialContext,
        entityId = EntityId()
    ))
    override val lines: List<LiveParseLine<CONTEXT>> get() = _lines

    private val _entities = mutableStateListOf<LiveParseEntityImpl<ENTITY>>()

    init {
        coroutineScope.launch {
            lineChangesFlow.collect { change ->

                // Update any lines that were actually modified by this change, including
                // updating their contexts.
                var prevContext = if(change.startLine <= 0) initialContext else _lines[change.startLine-1].context
                change.newLines.forEachIndexed { i, line ->
                    val newContext = computeContext(line, prevContext)
                    val entityId = if( i < change.prevLineCount && _lines[change.startLine+i].context == newContext ) {
                        _lines[change.startLine+i].entityId
                    } else {
                        EntityId()
                    }
                    val newLine = LiveParseLineImpl(line,newContext,prevContext, entityId)
                    prevContext = newContext
                    if( i < change.prevLineCount )
                        _lines[change.startLine+i] = newLine
                    else
                        _lines.add(change.startLine+i, newLine)
                }
                if( change.newLineCount < change.prevLineCount ) {
                    _lines.removeRange(change.startLine+change.newLineCount, change.startLine+change.prevLineCount)
                }

                // Update any following lines where their preceding context changed, because their interpretation may
                // be affected by the change to preceding lines (Once the context matches up, we can stop).
                var firstUnchangedLine = change.startLine + change.newLineCount
                var precedingContext = if(firstUnchangedLine > 0) _lines[firstUnchangedLine-1].context else initialContext
                while( firstUnchangedLine < _lines.size && _lines[firstUnchangedLine].prevContext != precedingContext ) {
                    val newContext = computeContext(_lines[firstUnchangedLine].line,precedingContext)
                    val entityId = if( _lines[firstUnchangedLine].context == newContext ) _lines[firstUnchangedLine].entityId else EntityId()
                    _lines[firstUnchangedLine] = LiveParseLineImpl(_lines[firstUnchangedLine].line, newContext, precedingContext, entityId)
                    firstUnchangedLine++
                    precedingContext = if(firstUnchangedLine > 0) _lines[firstUnchangedLine-1].context else initialContext
                }

                // Update entity start and end lines based on this change
                fun remapLineNumber(lineNumber: Int): Int = when {
                    lineNumber < change.startLine -> lineNumber
                    lineNumber >= change.startLine + change.prevLineCount -> lineNumber - change.prevLineCount + change.newLineCount
                    change.newLineCount < change.prevLineCount -> lineNumber.coerceIn(change.startLine, change.startLine + change.newLineCount - 1)
                    else -> lineNumber
                }

                _entities.forEachIndexed { index, entity ->
                    val newStart = remapLineNumber(entity.startLine)
                    val newEnd = remapLineNumber(entity.endLine)
                    if( newStart != entity.startLine || newEnd != entity.endLine ) {
                        _entities[index] = _entities[index].copy(startLine = newStart, endLine = newEnd)
                    }
                }

                // "Affected lines" are Lines that were changed directly or had their context change as a result of a
                // modification to a preceding line.
                val firstAffectedLine = change.startLine
                val lastAffectedLine = (firstUnchangedLine-1).coerceAtLeast(firstAffectedLine)
                val affectedLineRange = firstAffectedLine..lastAffectedLine

                // Re-parse any entities that were based on affected lines

                var entityIndex = when {
                    _entities.isEmpty() || firstAffectedLine < _entities.first().startLine -> 0
                    _entities.last().endLine < firstAffectedLine -> _entities.lastIndex
                    else -> _entities.indexOfFirst { it.lineRange intersects affectedLineRange }
                }

                var lastEntityLine = if(_entities.isEmpty() || entityIndex==0) 0 else _entities[entityIndex-1].endLine+1

                // TODO: Check for added entities
                // TODO: Process updated entities


//                _entities.forEachIndexed { index, entity ->
////                    while( lastEntityLine < _lines.size && !_lines[lastEntityLine].context.isBlockStart )
////                        lastEntityLine++
////                    if( lastEntityLine < entity.startLine ) {
////                        // There's another entity that we haven't parsed yet; parse it.
////                        val entityStartLine = lastEntityLine
////                        var entityEndLine = lastEntityLine+1
////                        while( entityEndLine < _lines.size && !_lines[entityEndLine].context.isBlockStart )
////                            entityEndLine++
////                        entityEndLine--
////                        // TODO: Parse entityStartLine..<entityEndLine
////                    }
//                    if( entity.startLine <= lastAffectedLine && entity.endLine >= firstAffectedLine) {
//                        // TODO
//                    }
//                }

            }
        }
    }
}