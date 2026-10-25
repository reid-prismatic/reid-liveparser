package com.prismatic.liveparser.parser

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.prismatic.liveparser.ChangedLinesInfo
import com.prismatic.liveparser.changedLinesInfoFlow
import com.prismatic.liveparser.isNewlineInsertion
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyDataType
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.isOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.collections.forEachIndexed
import kotlin.jvm.JvmInline

@Composable
public fun <LineType:LiveParseLineType, EntityData:Any, SyntaxHighlightData:Any, ErrorData> TextFieldState.rememberLiveParse(
    initialLineType: LineType,
    unknwonErrorData: ErrorData,
    computeContext: (line: String, prevLineType: LineType) -> LineType,
    computeSyntaxHighlight: (line: String, lineType: LineType, entityData: EntityData) -> SyntaxHighlightData,
    parseEntity: LiveParseEntityScope<LineType,EntityData,ErrorData>.()->EntityData
) : LiveParse<LineType,EntityData,SyntaxHighlightData,ErrorData> {
    val coroutineScope = rememberCoroutineScope()
    val liveParse = remember { LiveParseImpl(coroutineScope,this, initialLineType, computeContext, computeSyntaxHighlight, parseEntity) }
    return liveParse
}

interface LiveParseLineType {
    val isEntityStart: Boolean
}

public interface LiveParse<LineType:LiveParseLineType, EntityData:Any, SyntaxHighlightData:Any, ErrorData> {
    val lines: List<LiveParseLine<LineType,SyntaxHighlightData>>
    val entities: List<LiveParseEntity<EntityData>>

    val errors: List<LiveParseError<ErrorData>>
}

public interface LiveParseLine<LineType:LiveParseLineType, SyntaxHighlightData:Any> {
    val line: String
    val lineType: LineType
    val entityId: EntityId
    val syntaxHighlights: SyntaxHighlightData?
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

private data class LiveParseLineImpl<LineType:LiveParseLineType,SyntaxHighlightData:Any>(
    override val line: String,
    override val lineType: LineType,
    val prevLineType: LineType,
    override val entityId: EntityId,
    override val syntaxHighlights: SyntaxHighlightData?,
) : LiveParseLine<LineType, SyntaxHighlightData>

private inline infix fun <T:Comparable<T>> ClosedRange<T>.intersects(other: ClosedRange<T>) : Boolean =
    this.start <= other.endInclusive && this.endInclusive >= other.start

private inline infix fun <T:Comparable<T>> ClosedRange<T>.notIntersects(other: ClosedRange<T>) : Boolean =
    !(this intersects other)

interface LiveParseEntityScope<LineType:LiveParseLineType,EntityData:Any, ErrorData> {
    val lineType: LineType
    val entityId: EntityId
    val existingEntityData: EntityData?
    val entityLines: List<String>
    fun parseError(
        message: String,
        errorLine: Int = -1, // Line number where the error occurred, relative to the start of the entity (zero is the first line), -1 if unknown
        errorColumn: Int = -1, // Column (character index within line) where the error occurred, -1 if unknown
        sourceLength: Int = -1, // Number of characters in the source text that the error applies to, -1 if unknown
        errorData: ErrorData? = null  // Extra data associated with the error, for uses like localization
    )
}

fun <T> LiveParseEntityScope<*,*,T>.parseError(
    message: String,
    errorLine: Int,
    errorRange: IntRange,
    errorData: T? = null  // Extra data associated with the error, for uses like localization
) = parseError(message, errorLine, errorRange.first, if(errorRange.isEmpty()) 0 else errorRange.last-errorRange.first+1, errorData)



data class LiveParseError<T>(
    val message: String,
    val errorLine: Int,
    val errorColumn: Int,
    val sourceLength: Int,
    val errorData: T?,
    val entityId: EntityId
)

private class LiveParseImpl<LineType:LiveParseLineType, EntityData:Any, SyntaxHighlightData:Any, ErrorData>(
    coroutineScope: CoroutineScope,
    textFieldState: TextFieldState,
    val initialLineType: LineType,
    val computeContext: (String, LineType) -> LineType,
    val computeSyntaxHighlight: (String, LineType, EntityData) -> SyntaxHighlightData,
    val parseEntity: LiveParseEntityScope<LineType, EntityData, ErrorData>.() -> EntityData
) : LiveParse<LineType,EntityData,SyntaxHighlightData,ErrorData> {
    val lineChangesFlow = textFieldState.changedLinesInfoFlow()

    override val lines: List<LiveParseLine<LineType,SyntaxHighlightData>>
        field = mutableStateListOf(
            LiveParseLineImpl(
                line = "",
                lineType = initialLineType,
                prevLineType = initialLineType,
                entityId = EntityId(),
                syntaxHighlights = null
            )
        )

    override val entities: List<LiveParseEntity<EntityData>>
        field = mutableStateListOf<LiveParseEntityImpl<EntityData>>()
    override val errors: List<LiveParseError<ErrorData>>
        field = mutableStateListOf<LiveParseError<ErrorData>>()

    private fun validateEntities() {
        var prev = -1
        var prevId = EntityId.None
        for( entity in entities) {
            check(entities.count { it.id == entity.id } == 1)
            check(entity.startLine > prev) {"Entity ${entity.id} starts at ${entity.startLine} but prev ($prevId) ended at $prev"}
            prev = entity.endLine
            prevId = entity.id
        }
    }

    init {
        coroutineScope.launch {
            lineChangesFlow.collect { rawChange ->

                val change = if(rawChange.isNewlineInsertion() && rawChange.startLine > 0 && lines[rawChange.startLine-1].line.isEmpty()) {
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
                var prevContext = if(change.startLine <= 0) initialLineType else lines[change.startLine-1].lineType
                change.newLines.forEachIndexed { i, line ->
                    // TODO: Handle inserted blank lines better so new EID isn't allocated needlessly
                    val newContext = computeContext(line, prevContext)
                    val entityId = if( i < change.prevLineCount && lines[change.startLine+i].lineType == newContext ) {
                        lines[change.startLine+i].entityId
                    } else {
                        EntityId()
                    }
                    val newLine = LiveParseLineImpl<LineType,SyntaxHighlightData>(line,newContext,prevContext, entityId, null)
                    prevContext = newContext
                    if( i < change.prevLineCount )
                        lines[change.startLine+i] = newLine
                    else
                        lines.add(change.startLine+i, newLine)
                }
                if( change.newLineCount < change.prevLineCount ) {
                    lines.removeRange(change.startLine+change.newLineCount, change.startLine+change.prevLineCount)
                }

                // Update any following lines where their preceding context changed, because their interpretation may
                // be affected by the change to preceding lines (Once the context matches up, we can stop).
                var firstUnchangedLine = change.startLine + change.newLineCount
                var precedingContext = if(firstUnchangedLine > 0) lines[firstUnchangedLine-1].lineType else initialLineType
                while( firstUnchangedLine < lines.size && lines[firstUnchangedLine].prevLineType != precedingContext ) {
                    val newContext = computeContext(lines[firstUnchangedLine].line,precedingContext)
                    val entityId = if( lines[firstUnchangedLine].lineType == newContext ) lines[firstUnchangedLine].entityId else EntityId()
                    lines[firstUnchangedLine] = LiveParseLineImpl(lines[firstUnchangedLine].line, newContext, precedingContext, entityId, null)
                    firstUnchangedLine++
                    precedingContext = if(firstUnchangedLine > 0) lines[firstUnchangedLine-1].lineType else initialLineType
                }

                validateEntities()

                // Update entity start and end lines based on this change
                fun remapLineNumber(lineNumber: Int): Int = when {
                    lineNumber < change.startLine -> lineNumber
                    lineNumber >= change.startLine -> (lineNumber - change.prevLineCount + change.newLineCount).coerceAtLeast(change.startLine)
                    change.newLineCount < change.prevLineCount -> lineNumber.coerceAtMost(change.startLine + change.newLineCount - 1).coerceAtLeast(change.startLine)
                    else -> lineNumber
                }

                entities.forEachIndexed { index, entity ->
                    val newStart = remapLineNumber(entity.startLine)
                    val newEnd = remapLineNumber(entity.endLine)
                    if( newStart != entity.startLine || newEnd != entity.endLine ) {
                        entities[index] = entities[index].copy(startLine = newStart, endLine = newEnd)
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

                val entityParseScope = object : LiveParseEntityScope<LineType,EntityData,ErrorData> {
                    override val lineType: LineType get() = workingEntityLineType
                    override val entityId: EntityId get() = workingEntityId
                    override val existingEntityData: EntityData? get() = existingEntity
                    override val entityLines: List<String> = object : AbstractList<String>() {
                        override val size: Int
                            get() = workingEntityEndLine - workingEntityStartLine + 1
                        override fun get(index: Int): String = lines[workingEntityStartLine+index].line
                    }

                    override fun parseError(
                        message: String,
                        errorLine: Int,
                        errorColumn: Int,
                        sourceLength: Int,
                        errorData: ErrorData?
                    ) {
                        errors.add( LiveParseError(
                            message = message,
                            errorLine = if(errorLine >= 0 ) {
                                workingEntityStartLine + errorLine + 1
                            } else {
                                workingEntityStartLine + 1
                            },
                            errorColumn = if(errorLine >= 0 && errorColumn >= 0) errorColumn else 0,
                            sourceLength = sourceLength,
                            errorData = errorData,
                            entityId = workingEntityId,
                        ) )
                    }
                }

                fun clearParseErrors() {
                    if( workingEntityId != EntityId.None ) {
                        errors.removeAll { it.entityId == workingEntityId }
                    }
                }

                fun processEntity() {
                    existingEntity = null
                    if( workingEntityStartLine < 0 ) return                     // Didn't find start of entity yet
                    if( workingEntityEndLine < workingEntityStartLine ) return  // Entity body would have zero length
                    val workingEntityLineRange = workingEntityStartLine..workingEntityEndLine
                    if( nextEntityIndex < entities.size ) {
                        val nextEntity = entities[nextEntityIndex]
                        if( nextEntity.id == workingEntityId ) {
                            if (nextEntity.lineRange == workingEntityLineRange && nextEntity.lineRange notIntersects affectedLineRange) {
                                nextEntityIndex++
                            } else {
                                existingEntity = nextEntity.entityData
                                clearParseErrors()
                                val newEntity = entityParseScope.parseEntity()
                                existingEntity = null
                                entities[nextEntityIndex] = LiveParseEntityImpl(
                                    newEntity,
                                    workingEntityId,
                                    workingEntityStartLine,
                                    workingEntityEndLine
                                )
                                nextEntityIndex++
                                while(nextEntityIndex < entities.size && entities[nextEntityIndex].startLine <= workingEntityEndLine ) {
                                    entities.removeAt(nextEntityIndex)
                                }
                            }
                        } else if(nextEntity.startLine > workingEntityEndLine) {
                            existingEntity = null
                            clearParseErrors()
                            val newEntity = entityParseScope.parseEntity()
                            entities.add(nextEntityIndex, LiveParseEntityImpl(
                                newEntity,
                                workingEntityId,
                                workingEntityStartLine,
                                workingEntityEndLine
                            ))
                            nextEntityIndex++
                        } else {
                            existingEntity = null
                            clearParseErrors()
                            val newEntity = entityParseScope.parseEntity()
                            entities[nextEntityIndex] = LiveParseEntityImpl(
                                newEntity,
                                workingEntityId,
                                workingEntityStartLine,
                                workingEntityEndLine
                            )
                            nextEntityIndex++
                        }
                    } else {
                        existingEntity = null
                        clearParseErrors()
                        val newEntity = entityParseScope.parseEntity()
                        entities.add(nextEntityIndex, LiveParseEntityImpl(
                            newEntity,
                            workingEntityId,
                            workingEntityStartLine,
                            workingEntityEndLine
                        ))
                        nextEntityIndex++
                    }
                }

                lines.forEachIndexed { index, line ->
                    if(line.lineType.isEntityStart) {
                        workingEntityEndLine = index-1
                        processEntity()
                        workingEntityStartLine = index
                        workingEntityId = line.entityId
                        workingEntityLineType = line.lineType
                        workingEntityEndLine = -1
                    }
                }
                workingEntityEndLine = lines.size-1
                processEntity()
                while( nextEntityIndex < entities.size )
                    entities.removeAt(nextEntityIndex)
                validateEntities()

                errors.removeAll { err -> entities.none { it.id == err.entityId } }

                // TODO: If syntax highlighting affects performance too much, run this code in a separate coroutine, and restart if the range changes
                var syntaxHighlightStartLine = affectedLineRange.first
                while( syntaxHighlightStartLine > 0 && !lines[syntaxHighlightStartLine].lineType.isEntityStart )
                    syntaxHighlightStartLine--
                var lineEntity = EntityId.None
                for( lineIndex in syntaxHighlightStartLine .. affectedLineRange.last ) {
                    val line = lines[lineIndex]
                    if( line.lineType.isEntityStart) {
                        lineEntity = line.entityId
                    }
                    val entity = entities.firstOrNull { it.id == lineEntity }
                    println("SH: $lineIndex e:${entity?.id} l.eid=${line.entityId} eids=${entities.joinToString(",") { it.id.toString() }}")
                    if( entity != null ) {
                        lines[lineIndex] = line.copy(syntaxHighlights = computeSyntaxHighlight(line.line, line.lineType, entity.entityData))
                    }
                }

            }
        }
    }
}

private fun parsedPropertyTypeMatches(property: ParsedProperty, type: PropertyType): Boolean {
    val propExpr = when(property) {
        is ParsedProperty.Alternate, is ParsedProperty.Compound, is ParsedProperty.Error -> return false
        is PropertyExpression -> property
    }
    return when(type) {
        is PropertyType.TypedExpression -> propExpr.isOf(type.expectedType)
        is PropertyType.Alternate -> type.components.any { parsedPropertyTypeMatches(propExpr, it) }
        is PropertyType.Compound -> false
        PropertyType.Dimension -> propExpr.isOf(PropertyDataType.Dimension)
        PropertyType.Expression -> true
        is PropertyType.Optional -> parsedPropertyTypeMatches(propExpr, type.type)
        is PropertyType.UnquotedStringEnum -> propExpr.isOf(PropertyDataType.String)
        PropertyType.UnquotedStringLiteral -> propExpr.isOf(PropertyDataType.String)
    }
}

fun PropertyType.matches(actual: ParsedProperty?) : Boolean{
    if(actual == null) return false
    return parsedPropertyTypeMatches(actual, this)
}
