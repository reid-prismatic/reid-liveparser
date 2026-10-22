package com.prismatic.liveparser.weave.grammar

import com.prismatic.liveparser.LiveParseLineType

enum class WeaveLineType(override val isEntityStart: Boolean = false) : LiveParseLineType {
    None,
    Blank,
    ConcreteEntityStart(true),
    ConcreteEntityBody,
    DelayEntityStart(true),
    Property,
    DefinitionEntityStart(true),
    DefinitionEntityBody,
    CodeStart(true),
    CodeContinue,
    CodeEnd,
    ScriptStart(true),
    ScriptContinue,
    ScriptEnd,
    SyntaxError,
    SegmentBreak(true),
}

fun getWeaveLineType(line: String, prevLineType: WeaveLineType = WeaveLineType.None): WeaveLineType {
    return when(prevLineType) {
        WeaveLineType.None, WeaveLineType.SegmentBreak -> when(val type = line.decomposeLine().contextFreeLineType) {
                WeaveLineType.Blank -> WeaveLineType.None
                WeaveLineType.Property -> WeaveLineType.SyntaxError
                else -> type
            }
        WeaveLineType.SyntaxError -> when {
            line.decomposeLine().isBlankLine -> WeaveLineType.None
            else -> WeaveLineType.SyntaxError
        }
        WeaveLineType.Blank, WeaveLineType.ScriptEnd, WeaveLineType.CodeEnd -> line.decomposeLine().contextFreeLineType
        WeaveLineType.ConcreteEntityStart, WeaveLineType.ConcreteEntityBody, WeaveLineType.DelayEntityStart -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isBlankLine -> WeaveLineType.Blank
                decomposed.isPropertyLine -> WeaveLineType.Property
                else -> WeaveLineType.ConcreteEntityBody
            }
        }
        WeaveLineType.Property -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isBlankLine -> WeaveLineType.Blank
                decomposed.isPropertyLine -> WeaveLineType.Property
                else -> WeaveLineType.SyntaxError
            }
        }
        WeaveLineType.DefinitionEntityStart, WeaveLineType.DefinitionEntityBody -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isBlankLine -> WeaveLineType.Blank
                decomposed.isPropertyLine -> WeaveLineType.Property
                else -> WeaveLineType.DefinitionEntityBody
            }
        }
        WeaveLineType.CodeStart, WeaveLineType.CodeContinue -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isCodeBlockEnd -> WeaveLineType.CodeEnd
                else -> WeaveLineType.CodeContinue
            }
        }
        WeaveLineType.ScriptStart, WeaveLineType.ScriptContinue -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isScriptEnd -> WeaveLineType.ScriptEnd
                else -> WeaveLineType.ScriptContinue
            }
        }
    }
}

data class DecomposedLine(
    val lineText: String,   // Full text of the original line as entered in the editor
    val commentIndex: Int,  // Character index where the comment starts, or -1 if there is no comment
    val comment: String,    // Any comment text associated with the line
    val rawComment: String, // The raw comment text including the '//' comment characters and trailing whitespace
    val body: String,       // Line body after removing comments and trailing whitespace
    val rawBody: String,    // Line body after removing comments but with whitespace intact
    val trimmedBody: String = body.trimStart(), // Line body after removing comments and leading and trailing whitespace
    val firstChar: Char = if(trimmedBody.isNotEmpty()) trimmedBody[0] else 0.toChar(),      // First non-whitespace, non-comment character, or the nul character.
    val lastChar: Char = if(trimmedBody.isNotEmpty()) trimmedBody.last() else 0.toChar() // Last non-whitespace, non-comment character, or the nul character.
)

val DecomposedLine.isConcreteEntityStart: Boolean get() = firstChar == '[' && lastChar == ']'
val DecomposedLine.isDefinitionEntityStart: Boolean get() = firstChar == '{' && lastChar == '}' && ':' in trimmedBody
val DecomposedLine.isStyleEntityStart: Boolean get() = firstChar == '{' && lastChar == '}' && ':' !in trimmedBody
val DecomposedLine.isScriptStart: Boolean get() = firstChar == '{' && lastChar != '}'
val DecomposedLine.isCodeBlockStart: Boolean get() = firstChar == '`' && body.startsWith("```")
val DecomposedLine.isScriptEnd: Boolean get() = firstChar == '}' && trimmedBody.length==1
val DecomposedLine.isCodeBlockEnd: Boolean get() = firstChar == '`' && trimmedBody=="```"
val DecomposedLine.isPropertyLine: Boolean get() = firstChar == '-' && body.startsWith("- ")
val DecomposedLine.isDelayEntityStart: Boolean get() = firstChar == '+'
val DecomposedLine.isBlankLine: Boolean get() = trimmedBody.isEmpty()
val DecomposedLine.isTextEntityStart: Boolean get() = firstChar.isLetterOrDigit() || firstChar in "._\"" || textEntityPrefixes.keys.any { body.startsWith(it) }
val DecomposedLine.isVoiceoverEntityStart: Boolean get() = firstChar == '>' && trimmedBody.drop(1).firstOrNull { !it.isWhitespace() }?.isLetterOrDigit() ?: false
val DecomposedLine.isSegmentBreak: Boolean get() = firstChar == '-' && trimmedBody.startsWith("----")

val textEntityPrefixes = mapOf<String,String>(
    "# " to "h1",
    "## " to "h2",
    "### " to "h3",
    "#### " to "h4",
    "##### " to "h5",
)

val DecomposedLine.contextFreeLineType get() = when {
    isBlankLine -> WeaveLineType.Blank
    isConcreteEntityStart -> WeaveLineType.ConcreteEntityStart
    isDefinitionEntityStart -> WeaveLineType.DefinitionEntityStart
    isStyleEntityStart -> WeaveLineType.DefinitionEntityStart
    isScriptStart -> WeaveLineType.ScriptStart
    isScriptEnd -> WeaveLineType.ScriptEnd
    isCodeBlockStart -> WeaveLineType.CodeStart
    isCodeBlockEnd -> WeaveLineType.CodeEnd
    isDelayEntityStart -> WeaveLineType.DelayEntityStart
    isPropertyLine -> WeaveLineType.Property
    isTextEntityStart -> WeaveLineType.ConcreteEntityStart
    isVoiceoverEntityStart -> WeaveLineType.ConcreteEntityStart
    isSegmentBreak -> WeaveLineType.SegmentBreak
    else -> WeaveLineType.SyntaxError
}

fun String.decomposeLine() : DecomposedLine {
    val lineText = this
    val commentIndex = lineText.indexOf("//")
    val comment: String
    val rawComment: String
    val body: String
    val rawBody: String
    if( commentIndex >= 0 ) {
        rawBody = lineText.substring(0,commentIndex)
        comment = lineText.substring(commentIndex+2).trim()
        rawComment = rawBody.takeLastWhile { it.isWhitespace() } + lineText.substring(commentIndex)
        body = rawBody.dropLastWhile { it.isWhitespace() }
    } else {
        rawBody = lineText
        comment = ""
        rawComment = lineText.takeLastWhile { it.isWhitespace() }
        body = lineText.dropLastWhile { it.isWhitespace() }
    }
    return DecomposedLine(lineText, commentIndex, comment, rawComment, body, rawBody)
}