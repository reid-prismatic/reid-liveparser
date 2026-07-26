package com.prismatic.liveparser

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ScriptLine(
    val lineText: String,                   // Full text of the original line as entered in the editor
    val body: String,                       // Line body after removing comments and whitespace
    val rawBody: String,                    // Line body after removing comments but with whitespace intact
    val comment: String,                    // Any comment text associated with the line
    val rawComment: String,                 // The raw comment text including the '//' comment characters and trailing whitespace
    val form: ScriptLineForm,
    val context: ScriptLineContext
) {
    companion object {
        val None = ScriptLine(
            lineText = "",
            body = "",
            rawBody = "",
            comment = "",
            rawComment = "",
            form = ScriptLineForm.Blank,
            context = ScriptLineContext.None
        )
    }
}

enum class ScriptLineForm {
    Blank,
    Property,
    StartCodeBlock,
    EndCodeBlock,
    StartScriptBlock,
    EndScriptBlock,
    SingleLineScriptBlock,
    ConcreteEntityStart,
    EntityBody,
    ShorthandEntityStart,
    DelayEntityStart,
    Error
}

sealed class ScriptLineContext {
    data object None : ScriptLineContext()
    data object EntityBody : ScriptLineContext()
    data object AfterEntityBody : ScriptLineContext()
    data object Properties : ScriptLineContext()
    data object CodeBlock : ScriptLineContext()
    data class ScriptBlock(val depth: Int, val inQuote: Boolean, val inBlockComment: Boolean) : ScriptLineContext()
}

fun Char.isPropertyNameStartChar() = isLetter() || this == '_'
fun Char.isPropertyNameChar() = isLetterOrDigit() || this == '_' || this == '-'

fun Char.isTextBlockStartChar() = isLetterOrDigit() || this == '_' || this == '.' || this == '"'
fun Char.isVoiceoverBlockStart() = this == '>'

private fun DecomposedLine.toScriptLine(
    form: ScriptLineForm,
    context: ScriptLineContext
) : ScriptLine = ScriptLine(lineText, body, rawBody, comment, rawComment, form, context)

private fun DecomposedLine.parseAsConcreteEntityStart() =
    if(body.startsWith('[') && body.endsWith(']') && !body.contains('='))
        toScriptLine(ScriptLineForm.ConcreteEntityStart, ScriptLineContext.EntityBody)
    else null

private fun DecomposedLine.parseAsPropertyLine() =
    if( body.matches(Match_PropertyLine) )
        toScriptLine(ScriptLineForm.Property, ScriptLineContext.Properties)
    else null

private fun DecomposedLine.parseAsCodeBlockStart() =
    if( body.matches(Match_CodeBlockStart) )
        toScriptLine(ScriptLineForm.StartCodeBlock, ScriptLineContext.CodeBlock)
    else null

private fun DecomposedLine.parseAsScriptStart() =
    if( body.startsWith('{') && !body.endsWith('}') )
        toScriptLine(ScriptLineForm.StartScriptBlock, ScriptLineContext.ScriptBlock(0, false, false))
    else null

private fun DecomposedLine.parseAsSingleLineScriptBlock() =
    if( body.startsWith('{') && body.endsWith('}') && body.contains('=') )
        toScriptLine(ScriptLineForm.SingleLineScriptBlock, ScriptLineContext.AfterEntityBody)
    else null

private fun DecomposedLine.parseAsBlankLine(context: ScriptLineContext = ScriptLineContext.None) =
    if( body.isBlank() )
        toScriptLine(ScriptLineForm.Blank, context)
    else null

private fun DecomposedLine.parseAsDefinitionEntityStart() =
    if(body.startsWith('{') && body.endsWith('}') && !body.contains('='))
        toScriptLine(ScriptLineForm.ConcreteEntityStart, ScriptLineContext.EntityBody)
    else null

private fun DecomposedLine.parseAsShorthandTextEntityStart() =
    if(firstChar.isTextBlockStartChar())
        toScriptLine(ScriptLineForm.ShorthandEntityStart, ScriptLineContext.EntityBody)
    else null

//private fun DecomposedLine.parseAsShorthandVoiceoverEntityStart() =
//    if(firstChar.isVoiceoverBlockStart() && secondChar.isLetterOrDigit())
//        toScriptLine(ScriptLineForm.ShorthandEntityStart, ScriptLineContext.EntityBody)
//    else null

private fun DecomposedLine.parseAsDelayEntityStart() =
    if(firstChar == '+')
        toScriptLine(ScriptLineForm.DelayEntityStart, ScriptLineContext.EntityBody)
    else null

//private fun DecomposedLine.parseAsShorthandEntityStart() =
//    parseAsShorthandTextEntityStart()
//        ?: parseAsShorthandVoiceoverEntityStart()

private fun DecomposedLine.parseAsEntityStart() =
    parseAsConcreteEntityStart()
        ?: parseAsDefinitionEntityStart()
        ?: parseAsCodeBlockStart()
        ?: parseAsSingleLineScriptBlock()
        ?: parseAsScriptStart()
//        ?: parseAsShorthandEntityStart()
        ?: parseAsDelayEntityStart()

private fun DecomposedLine.isDefinitionEntityStart() = body.startsWith('{') && body.endsWith('}') && !body.contains('=')
private fun DecomposedLine.isCodeLine() = body.startsWith('{') && body.endsWith('}') && body.contains('=')
private fun DecomposedLine.isCodeStart() = body.startsWith('{') && !body.contains('}')
private fun DecomposedLine.isTextBlockStart() = body.startsWith('{') && !body.contains('}')

private val Match_PropertyLine = Regex("^\\s*-\\s+[A-Za-z_][A-Za-z0-9_-]*\\s*:")
private val Match_CodeBlockStart = Regex("^```\\s\\(*[A-Za-z0-9_-]*\\)")

fun ScriptLine(lineText: String, previous: ScriptLine) : ScriptLine {
    when( previous.context ) {
        ScriptLineContext.None -> {
            val decomposed = lineText.decomposeLine()
            decomposed.parseAsBlankLine()?.let { return it }
            decomposed.parseAsPropertyLine()?.let { return it }
            decomposed.parseAsEntityStart()?.let { return it }
        }
        ScriptLineContext.AfterEntityBody -> {
            val decomposed = lineText.decomposeLine()
            decomposed.parseAsBlankLine(ScriptLineContext.AfterEntityBody)?.let { return it }
            decomposed.parseAsPropertyLine()?.let { return it }
            decomposed.parseAsEntityStart()?.let { return it }
        }
        ScriptLineContext.CodeBlock -> {
            if(lineText.startsWith("```")) {
                val decomposed = lineText.decomposeLine()
                return decomposed.toScriptLine(ScriptLineForm.EndCodeBlock, ScriptLineContext.AfterEntityBody)
            } else {
                return ScriptLine(lineText, lineText, lineText, "", "", ScriptLineForm.EntityBody, ScriptLineContext.EntityBody)
            }
        }
        ScriptLineContext.EntityBody -> {
            val decomposed = lineText.decomposeLine()
            decomposed.parseAsBlankLine(ScriptLineContext.AfterEntityBody)?.let { return it }
            decomposed.parseAsPropertyLine()?.let { return it }
            return decomposed.toScriptLine(ScriptLineForm.EntityBody, ScriptLineContext.EntityBody)
        }
        ScriptLineContext.Properties -> {
            val decomposed = lineText.decomposeLine()
            decomposed.parseAsBlankLine(ScriptLineContext.Properties)?.let { return it }
            decomposed.parseAsPropertyLine()?.let { return it }
        }
        is ScriptLineContext.ScriptBlock -> {
            var depth = previous.context.depth
            var inQuote = previous.context.inQuote
            var inBlockComment = previous.context.inBlockComment

            if( depth == 0 && !inQuote && !inBlockComment &&  lineText.startsWith("}") ) {
                val decomposed = lineText.decomposeLine()
                if( decomposed.body.removePrefix("}").isNotEmpty()) {
                    return decomposed.toScriptLine(ScriptLineForm.Error, previous.context)
                }
                return decomposed.toScriptLine(ScriptLineForm.EndScriptBlock, ScriptLineContext.AfterEntityBody)
            }

            var prevCh = 0.toChar()
            for( ch in lineText ) {
                if( inBlockComment ) {
                    if( ch == '/' && prevCh == '*' ) {
                        inBlockComment = false
                    }
                } else if( inQuote ) {
                    if( ch == '"' ) {
                        inQuote = false
                    }
                } else if( ch == '"' ) {
                    inQuote = true
                } else if( ch == '*' && prevCh == '/' ) {
                    inBlockComment = true
                } else if( ch == '{' ) {
                    depth++
                } else if( ch == '}' ) {
                    depth--
                } else if( ch == '/' && prevCh == '/' ) {
                    break
                }
                prevCh = ch
            }
            return if( depth < 0 ) {
                lineText.decomposeLine().toScriptLine(ScriptLineForm.Error, previous.context)
            } else {
                ScriptLine(lineText, lineText, lineText, "", "", ScriptLineForm.EntityBody, ScriptLineContext.ScriptBlock(depth, inQuote, inBlockComment))
            }
        }
    }
    return lineText.decomposeLine().toScriptLine(ScriptLineForm.Error, previous.context)
}

class ScribeDocumentState(coroutineScope: CoroutineScope) {

    val textFieldState: TextFieldState = TextFieldState()

    private val _lines = mutableStateListOf<ScriptLine>(ScriptLine.None)
    val lines: List<ScriptLine> get() = _lines

    private val _entities = mutableStateListOf<ScribeEntity>()
    val entities: List<ScribeEntity> get() = _entities

    val changeList = mutableStateListOf<ChangedLinesInfo>()
    val lineChangesFlow = textFieldState.changedLinesInfoFlow()

    init {
        coroutineScope.launch {
            lineChangesFlow.collect { change ->
                changeList += change
//                println("change: @${change.startLine} (${change.prevLineCount} -> ${change.newLineCount}) size=${_lines.size}")

                // TODO: Process the lines one at a time.
                //       For each new line, parse in context of the previous line.
                //       When a line overwrites another line, check if the block start/end flags mismatch for it,
                //       and if they do, invalidate subsequent lines until they match again

                var prevLine = if(change.startLine <= 0) ScriptLine.None else _lines[change.startLine-1]
                change.newLines.forEachIndexed { i, line ->
                    val newLine = ScriptLine(line,prevLine)
                    prevLine = newLine
                    if( i < change.prevLineCount )
                        _lines[change.startLine+i] = newLine
                    else
                        _lines.add(change.startLine+i, newLine)
                }
                if( change.newLineCount < change.prevLineCount ) {
                    _lines.removeRange(change.startLine+change.newLineCount, change.startLine+change.prevLineCount)
                }
//                val lastChangedLine = change.startLine + change.newLineCount - 1
//                val expectedContext = if( lastChangedLine >= 0 ) _lines[lastChangedLine].context else ScriptLineContext.None
//                _lines[change.startLine + change.newLineCount - 1]

                // TODO: Check context for changed lines

//                if( change.prevLineCount == change.newLineCount ) {
//                    for( i in 0 ..< change.newLineCount ) {
//                        _lines[change.startLine+i] = ScriptLine(change.newLines[i])
//                    }
//                } else if( change.prevLineCount == 0 ) {
//                     _lines.addAll(change.startLine, change.newLines.map { ScriptLine(it) })
//                } else {
//                    _lines.removeRange(change.startLine, change.startLine + change.prevLineCount)
//                    _lines.addAll(change.startLine, change.newLines.map { ScriptLine(it) })
//                }
            }
        }
    }
}

class ScribeEntity {

}

class EntityProperties {

}

enum class EntityPropertyType {

}