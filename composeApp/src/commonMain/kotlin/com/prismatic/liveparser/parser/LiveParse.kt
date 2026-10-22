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

data class SourceAddress(
    /** Indicates the compilation unit if there are multiple compilation units */
    val compilationUnit: String? = null,
    /** The 1-based line number, relative to the start of the entity, or relative to the start of the compilation unit if [entityId] is [EntityId.None] */
    val lineNumber: Int,
    /** The ID of the entity that [lineNumber] is relative to, or [EntityId.None] if the line number is relative to the compilation unit*/
    val entityId: EntityId = EntityId.None,
    /** The range of characters in the line, or [IntRange.EMPTY] if there is no specific range */
    val position: IntRange = IntRange.EMPTY
)

@Composable
public fun <LineType:LiveParseLineType, EntityData:Any> TextFieldState.rememberLiveParse(
    initialLineType: LineType,
    computeContext: (line: String, prevLineType: LineType) -> LineType,
    parseEntity: LiveParseEntityScope<LineType,EntityData>.()->EntityData
) : LiveParse<LineType,EntityData> {
    val coroutineScope = rememberCoroutineScope()
    val liveParse = remember { LiveParseImpl(coroutineScope,this, initialLineType, computeContext, parseEntity) }
    return liveParse
}

interface LiveParseLineType {
    val isEntityStart: Boolean
}

public interface LiveParse<LineType:LiveParseLineType, EntityData:Any> {
    val lines: List<LiveParseLine<LineType>>
    val entities: List<LiveParseEntity<EntityData>>
}

public interface LiveParseLine<LineType:LiveParseLineType> {
    val line: String
    val lineType: LineType
    val entityId: EntityId
}

public interface LiveParseEntity<EntityData:Any> {
    val entityData: EntityData
    val id: EntityId
    val startLine: Int
    val endLine: Int
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

private data class LiveParseEntityImpl<EntityData:Any>(
    override val entityData: EntityData,
    override val id: EntityId,
    override val startLine: Int,
    override val endLine: Int
) : LiveParseEntity<EntityData> {
    val lineRange: IntRange get() = startLine..endLine
}

private data class LiveParseLineImpl<LineType:LiveParseLineType>(
    override val line: String,
    override val lineType: LineType,
    val prevLineType: LineType,
    override val entityId: EntityId
) : LiveParseLine<LineType>

private inline infix fun <T:Comparable<T>> ClosedRange<T>.intersects(other: ClosedRange<T>) : Boolean =
    this.start <= other.endInclusive && this.endInclusive >= other.start

private inline infix fun <T:Comparable<T>> ClosedRange<T>.notIntersects(other: ClosedRange<T>) : Boolean =
    !(this intersects other)

interface LiveParseEntityScope<LineType:LiveParseLineType,EntityData:Any> {
    val lineType: LineType
    val entityId: EntityId
    val existingEntityData: EntityData?
    val entityLines: List<String>
}

private class LiveParseImpl<LineType:LiveParseLineType, EntityData:Any>(
    coroutineScope: CoroutineScope,
    textFieldState: TextFieldState,
    val initialLineType: LineType,
    val computeContext: (String, LineType) -> LineType,
    val parseEntity: LiveParseEntityScope<LineType,EntityData>.()->EntityData
) : LiveParse<LineType,EntityData> {
    val lineChangesFlow = textFieldState.changedLinesInfoFlow()

    private val _lines = mutableStateListOf<LiveParseLineImpl<LineType>>(LiveParseLineImpl(
        line = "",
        lineType = initialLineType,
        prevLineType = initialLineType,
        entityId = EntityId()
    ))
    override val lines: List<LiveParseLine<LineType>> get() = _lines

    private val _entities = mutableStateListOf<LiveParseEntityImpl<EntityData>>()
    override val entities: List<LiveParseEntity<EntityData>> get() = _entities

    private fun validateEntities() {
        var prev = -1
        var prevId = EntityId.None
        for( entity in _entities ) {
            check(_entities.count { it.id == entity.id } == 1)
            check(entity.startLine > prev) {"Entity ${entity.id} starts at ${entity.startLine} but prev ($prevId) ended at $prev"}
            prev = entity.endLine
            prevId = entity.id
        }
    }

    init {
        coroutineScope.launch {
            lineChangesFlow.collect { rawChange ->

                val change = if(rawChange.isNewlineInsertion() && rawChange.startLine > 0 && _lines[rawChange.startLine-1].line.isEmpty()) {
                    object : ChangedLinesInfo {
                        override val prevLines: List<String> = listOf("")
                        override val newLines: List<String> = listOf("","")
                        override val startLine: Int = rawChange.startLine-1
                        override val prevLineCount: Int = 1
                        override val newLineCount: Int = 2

                    }
                } else { rawChange }

                // Update any lines that were actually modified by this change, including
                // updating their contexts.
                var prevContext = if(change.startLine <= 0) initialLineType else _lines[change.startLine-1].lineType
                change.newLines.forEachIndexed { i, line ->
                    // TODO: Handle inserted blank lines better so new EID isn't allocated needlessly
                    val newContext = computeContext(line, prevContext)
                    val entityId = if( i < change.prevLineCount && _lines[change.startLine+i].lineType == newContext ) {
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
                var precedingContext = if(firstUnchangedLine > 0) _lines[firstUnchangedLine-1].lineType else initialLineType
                while( firstUnchangedLine < _lines.size && _lines[firstUnchangedLine].prevLineType != precedingContext ) {
                    val newContext = computeContext(_lines[firstUnchangedLine].line,precedingContext)
                    val entityId = if( _lines[firstUnchangedLine].lineType == newContext ) _lines[firstUnchangedLine].entityId else EntityId()
                    _lines[firstUnchangedLine] = LiveParseLineImpl(_lines[firstUnchangedLine].line, newContext, precedingContext, entityId)
                    firstUnchangedLine++
                    precedingContext = if(firstUnchangedLine > 0) _lines[firstUnchangedLine-1].lineType else initialLineType
                }

                validateEntities()

                // Update entity start and end lines based on this change
                fun remapLineNumber(lineNumber: Int): Int = when {
                    lineNumber < change.startLine -> lineNumber
                    lineNumber >= change.startLine -> (lineNumber - change.prevLineCount + change.newLineCount).coerceAtLeast(change.startLine)
                    change.newLineCount < change.prevLineCount -> lineNumber.coerceAtMost(change.startLine + change.newLineCount - 1).coerceAtLeast(change.startLine)
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

                var workingEntityStartLine = -1
                var workingEntityId = EntityId.None
                var workingEntityEndLine = -1
                var workingEntityLineType: LineType = initialLineType
                var existingEntity: EntityData? = null

                var nextEntityIndex = 0

                val entityParseScope = object : LiveParseEntityScope<LineType,EntityData> {
                    override val lineType: LineType get() = workingEntityLineType
                    override val entityId: EntityId get() = workingEntityId
                    override val existingEntityData: EntityData? get() = existingEntity
                    override val entityLines: List<String> = object : AbstractList<String>() {
                        override val size: Int
                            get() = workingEntityEndLine - workingEntityStartLine + 1
                        override fun get(index: Int): String = _lines[workingEntityStartLine+index].line
                    }
                }

                fun processEntity() {
                    existingEntity = null
                    if( workingEntityStartLine < 0 ) return                     // Didn't find start of entity yet
                    if( workingEntityEndLine < workingEntityStartLine ) return  // Entity body would have zero length
                    val workingEntityLineRange = workingEntityStartLine..workingEntityEndLine
                    if( nextEntityIndex < _entities.size ) {
                        val nextEntity = _entities[nextEntityIndex]
                        if( nextEntity.id == workingEntityId ) {
                            if (nextEntity.lineRange == workingEntityLineRange && nextEntity.lineRange notIntersects affectedLineRange) {
                                nextEntityIndex++
                            } else {
                                existingEntity = nextEntity.entityData
                                val newEntity = entityParseScope.parseEntity()
                                existingEntity = null
                                _entities[nextEntityIndex] = LiveParseEntityImpl(
                                    newEntity,
                                    workingEntityId,
                                    workingEntityStartLine,
                                    workingEntityEndLine
                                )
                                nextEntityIndex++
                                while(nextEntityIndex < _entities.size && _entities[nextEntityIndex].startLine <= workingEntityEndLine ) {
                                    _entities.removeAt(nextEntityIndex)
                                }
                            }
                        } else if(nextEntity.startLine > workingEntityEndLine) {
                            existingEntity = null
                            val newEntity = entityParseScope.parseEntity()
                            _entities.add(nextEntityIndex, LiveParseEntityImpl(
                                newEntity,
                                workingEntityId,
                                workingEntityStartLine,
                                workingEntityEndLine
                            ))
                            nextEntityIndex++
                        } else {
                            existingEntity = null
                            val newEntity = entityParseScope.parseEntity()
                            _entities[nextEntityIndex] = LiveParseEntityImpl(
                                newEntity,
                                workingEntityId,
                                workingEntityStartLine,
                                workingEntityEndLine
                            )
                            nextEntityIndex++
                        }
                    } else {
                        existingEntity = null
                        val newEntity = entityParseScope.parseEntity()
                        _entities.add(nextEntityIndex, LiveParseEntityImpl(
                            newEntity,
                            workingEntityId,
                            workingEntityStartLine,
                            workingEntityEndLine
                        ))
                        nextEntityIndex++
                    }
                }

                _lines.forEachIndexed { index, line ->
                    if(line.lineType.isEntityStart) {
                        workingEntityEndLine = index-1
                        processEntity()
                        workingEntityStartLine = index
                        workingEntityId = line.entityId
                        workingEntityLineType = line.lineType
                        workingEntityEndLine = -1
                    }
                }
                workingEntityEndLine = _lines.size-1
                processEntity()
                while( nextEntityIndex < _entities.size )
                    _entities.removeAt(nextEntityIndex)
                validateEntities()


            }
        }
    }
}

