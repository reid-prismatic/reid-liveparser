package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.weave.elements.VoiceoverElement
import com.prismatic.liveparser.weave.properties.ParsedProperty
import com.prismatic.liveparser.weave.properties.PropertyExpression
import com.prismatic.liveparser.weave.properties.PropertyType
import com.prismatic.liveparser.weave.properties.parsePropertyLine
import com.prismatic.liveparser.weave.properties.walk
import com.prismatic.liveparser.weave.properties.walkSpans
import com.prismatic.liveparser.weave.util.offset
import intrangeset.IntRangeSet
import intrangeset.forEach
import kotlin.collections.plus

data class WeaveSyntaxHighlights( val highlights : List<WeaveHighlight> ) {
    companion object {
        val None = WeaveSyntaxHighlights(emptyList())
    }
}

data class WeaveHighlight( val range: IntRange, val type: Type ) {
    enum class Type {
        Comment, PropertyValue, PropertyName, PropertyCondition, Voiceover, ConcreteEntityNamespace, Operator, SemanticTag, ColorLiteral,
        ColorLiteralOperator, ColorLiteralRed, ColorLiteralGreen, ColorLiteralBlue, ColorLiteralAlpha, Literal, Symbol, Break, BreakArguments
    }
}


fun computeWeaveSyntaxHighlights( lineText: String, lineType: WeaveLineType, element: WeaveElement): WeaveSyntaxHighlights {
    val highlights = mutableListOf<WeaveHighlight>()
    fun addHighlight( range: IntRange, type: WeaveHighlight.Type) {
        if( range.isEmpty() ) return
        highlights += WeaveHighlight(range,type)
    }
    fun addHighlight(spans: IntRangeSet, offset: IntRange, type: WeaveHighlight.Type) {
        spans.forEach { addHighlight(it.offset(offset), type) }
    }

    val decomposedLine = lineText.decomposeLine()

    if( decomposedLine.commentIndex > 0 ) {
        addHighlight(decomposedLine.commentIndex .. lineText.lastIndex, WeaveHighlight.Type.Comment)
    }

    when(lineType) {
        WeaveLineType.None -> {}
        WeaveLineType.Blank -> {}
        WeaveLineType.ConcreteEntityStart -> {
            if( decomposedLine.isConcreteEntityStart && ':' in decomposedLine.rawBody ) {
                addHighlight((lineText.indexOf('[')+1)..<lineText.indexOf(':'), WeaveHighlight.Type.ConcreteEntityNamespace)
            } else if( element is VoiceoverElement ) {
                addHighlight(decomposedLine.rawBody.indices, WeaveHighlight.Type.Voiceover )
            }
        }
        WeaveLineType.ConcreteEntityBody -> {
            if( element is VoiceoverElement ) {
                addHighlight(decomposedLine.rawBody.indices, WeaveHighlight.Type.Voiceover )
            }
        }
        WeaveLineType.DelayEntityStart -> {}
        WeaveLineType.Property -> {
            val propLine = parsePropertyLine(decomposedLine.rawBody)
            val parsedProp = parsePropertyStringHandlingErrors(propLine.value, PropertyType.Expression)
            addHighlight(propLine.valueRange, WeaveHighlight.Type.PropertyValue)
            addHighlight(propLine.nameRange, WeaveHighlight.Type.PropertyName)
            addHighlight(propLine.conditionRange, WeaveHighlight.Type.PropertyCondition)
            if( parsedProp is PropertyExpression ) {
                parsedProp.walkSpans { pp, spans, childSpans ->
                    when(pp) {
                        is PropertyExpression.Binary -> addHighlight(
                            spans, propLine.valueRange,
                            WeaveHighlight.Type.Operator
                        )
                        is PropertyExpression.Unary -> addHighlight(
                            spans, propLine.valueRange,
                            WeaveHighlight.Type.Operator
                        )
                        is PropertyExpression.SemanticTag -> addHighlight(
                            pp.sourceRange.offset(propLine.valueRange),
                            WeaveHighlight.Type.SemanticTag
                        )
                        is PropertyExpression.ColorLiteral -> {
                            val colorRange = pp.sourceRange.offset(propLine.valueRange)
                            if( colorRange.last - colorRange.first == 6 ) {
                                addHighlight(
                                    colorRange.first..colorRange.first,
                                    WeaveHighlight.Type.ColorLiteralOperator
                                )
                                addHighlight(
                                    colorRange.first + 1..colorRange.first + 2,
                                    WeaveHighlight.Type.ColorLiteralRed
                                )
                                addHighlight(
                                    colorRange.first + 3..colorRange.first + 4,
                                    WeaveHighlight.Type.ColorLiteralGreen
                                )
                                addHighlight(
                                    colorRange.first + 5..colorRange.first + 6,
                                    WeaveHighlight.Type.ColorLiteralBlue
                                )
                            } else if( colorRange.last - colorRange.first == 8 ) {
                                addHighlight(
                                    colorRange.first .. colorRange.first,
                                    WeaveHighlight.Type.ColorLiteralOperator
                                )
                                addHighlight(
                                    colorRange.first+1 .. colorRange.first+2,
                                    WeaveHighlight.Type.ColorLiteralAlpha
                                )
                                addHighlight(
                                    colorRange.first+3 .. colorRange.first+4,
                                    WeaveHighlight.Type.ColorLiteralRed
                                )
                                addHighlight(
                                    colorRange.first+5 .. colorRange.first+6,
                                    WeaveHighlight.Type.ColorLiteralGreen
                                )
                                addHighlight(
                                    colorRange.first+7 .. colorRange.first+8,
                                    WeaveHighlight.Type.ColorLiteralBlue
                                )
                            } else {
                                addHighlight(
                                    pp.sourceRange.offset(propLine.valueRange),
                                    WeaveHighlight.Type.ColorLiteral
                                )
                            }
                        }
                        is ParsedProperty.Alternate -> {}
                        is ParsedProperty.Compound -> {}
                        is ParsedProperty.Error -> {}
                        is PropertyExpression.Animation -> {}
                        is PropertyExpression.FunctionCall -> {}
                        is PropertyExpression.BooleanLiteral,
                        is PropertyExpression.DimensionLiteral,
                        is PropertyExpression.FrameRateLiteral,
                        is PropertyExpression.IntegerLiteral,
                        is PropertyExpression.NumberLiteral,
                        is PropertyExpression.PercentageLiteral,
                        is PropertyExpression.RatioLiteral,
                        is PropertyExpression.StringLiteral,
                        is PropertyExpression.TimeLiteral -> {
                            addHighlight(
                                pp.sourceRange.offset(propLine.valueRange),
                                WeaveHighlight.Type.Literal
                            )
                        }
                        is PropertyExpression.Variable -> {
                            addHighlight(
                                pp.sourceRange.offset(propLine.valueRange),
                                WeaveHighlight.Type.Symbol
                            )
                        }
                        is PropertyExpression.Vector -> {}
                    }
                }
            }
        }
        WeaveLineType.DefinitionEntityStart -> {}
        WeaveLineType.DefinitionEntityBody -> {}
        WeaveLineType.CodeStart -> {}
        WeaveLineType.CodeContinue -> {}
        WeaveLineType.CodeEnd -> {}
        WeaveLineType.ScriptStart -> {}
        WeaveLineType.ScriptContinue -> {}
        WeaveLineType.ScriptEnd -> {}
        WeaveLineType.SyntaxError -> {}
        WeaveLineType.SegmentBreak -> {
            val dashCount = decomposedLine.rawBody.takeWhile { it == '-' }.count()
            addHighlight( 0..<dashCount, WeaveHighlight.Type.Break )
            if( dashCount+1 < decomposedLine.rawBody.length) {
                addHighlight( dashCount..<decomposedLine.rawBody.length, WeaveHighlight.Type.BreakArguments )
            }
        }
    }
    return WeaveSyntaxHighlights(highlights)
}