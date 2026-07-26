package com.prismatic.liveparser

enum class LineType {
    None,
    Blank,
    ConcreteEntityStart,
    ConcreteEntityBody,
    Property,
    DefinitionEntityStart,
    DefinitionEntityBody,
    CodeStart,
    CodeContinue,
    CodeEnd,
    ScriptStart,
    ScriptContinue,
    ScriptEnd,
    SyntaxError,
    SegmentBreak,
}

fun getLineType( line: String, prevLineType: LineType = LineType.None): LineType {
    return when(prevLineType) {
        LineType.None, LineType.SegmentBreak -> when(val type = line.decomposeLine().contextFreeType) {
                LineType.Blank -> LineType.None
                LineType.Property -> LineType.SyntaxError
                else -> type
            }
        LineType.SyntaxError -> when {
            line.decomposeLine().isBlankLine -> LineType.None
            else -> LineType.SyntaxError
        }
        LineType.Blank, LineType.ScriptEnd, LineType.CodeEnd -> line.decomposeLine().contextFreeType
        LineType.ConcreteEntityStart, LineType.ConcreteEntityBody -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isBlankLine -> LineType.Blank
                decomposed.isPropertyLine -> LineType.Property
                else -> LineType.ConcreteEntityBody
            }
        }
        LineType.Property -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isBlankLine -> LineType.Blank
                decomposed.isPropertyLine -> LineType.Property
                else -> LineType.SyntaxError
            }
        }
        LineType.DefinitionEntityStart, LineType.DefinitionEntityBody -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isBlankLine -> LineType.Blank
                decomposed.isPropertyLine -> LineType.Property
                else -> LineType.DefinitionEntityBody
            }
        }
        LineType.CodeStart, LineType.CodeContinue -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isCodeBlockEnd -> LineType.CodeEnd
                else -> LineType.CodeContinue
            }
        }
        LineType.ScriptStart, LineType.ScriptContinue -> {
            val decomposed = line.decomposeLine()
            when {
                decomposed.isScriptEnd -> LineType.ScriptEnd
                else -> LineType.ScriptContinue
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
val DecomposedLine.isDefinitionEntityStart: Boolean get() = firstChar == '{' && lastChar == '}'
val DecomposedLine.isScriptStart: Boolean get() = firstChar == '{' && lastChar != '}'
val DecomposedLine.isCodeBlockStart: Boolean get() = firstChar == '`' && body.startsWith("```")
val DecomposedLine.isScriptEnd: Boolean get() = firstChar == '}' && trimmedBody.length==1
val DecomposedLine.isCodeBlockEnd: Boolean get() = firstChar == '`' && trimmedBody=="```"
val DecomposedLine.isPropertyLine: Boolean get() = firstChar == '-' && body.startsWith("- ")
val DecomposedLine.isBlankLine: Boolean get() = trimmedBody.isEmpty()
val DecomposedLine.isTextEntityStart: Boolean get() = firstChar.isLetterOrDigit() || firstChar in "._\""
val DecomposedLine.isVoiceoverEntityStart: Boolean get() = firstChar == '>' && trimmedBody.drop(1).firstOrNull { !it.isWhitespace() }?.isLetterOrDigit() ?: false
val DecomposedLine.isSegmentBreak: Boolean get() = firstChar == '-' && trimmedBody.startsWith("----")

val DecomposedLine.contextFreeType get() = when {
    isBlankLine -> LineType.Blank
    isConcreteEntityStart -> LineType.ConcreteEntityStart
    isDefinitionEntityStart -> LineType.DefinitionEntityBody
    isScriptStart -> LineType.ScriptStart
    isScriptEnd -> LineType.ScriptEnd
    isCodeBlockStart -> LineType.CodeStart
    isCodeBlockEnd -> LineType.CodeEnd
    isPropertyLine -> LineType.Property
    isTextEntityStart -> LineType.ConcreteEntityStart
    isVoiceoverEntityStart -> LineType.ConcreteEntityStart
    isSegmentBreak -> LineType.SegmentBreak
    else -> LineType.SyntaxError
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