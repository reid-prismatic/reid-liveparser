package com.prismatic.liveparser

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * Observes the state of a text field and provides a flow describing changes to the text in the text field.
 */
public fun TextFieldState.changedLinesInfoFlow(): Flow<ChangedLinesInfo> =
    snapshotFlow { text }
        .withPrevious()
        .map { (prevText, text) ->
            computeChangedLines(prevText ?: "", text ?: "")
        }


public interface ChangedLinesRange {
    val startLine: Int
    val prevLineCount: Int
    val newLineCount: Int
}

public interface ChangedLinesInfo : ChangedLinesRange {
    val prevLines: List<String>
    val newLines: List<String>
}

private data class ChangedLinesInfoImpl(
    override val startLine: Int,
    override val prevLineCount: Int,
    override val newLineCount: Int,
    override val prevLines: List<String>,
    override val newLines: List<String>
) : ChangedLinesInfo {
    override fun toString(): String = "Lines ${startLine}($prevLineCount->$newLineCount):  [${prevLines.joinToString(","){"'$it'"}}] -> [${newLines.joinToString(","){"'$it'"}}]"
}

private fun CharSequence.indexOfNth(n: Int, predicate: (Char) -> Boolean) : Int {
    if( n==0 ) return -1
    var found = 0
    for( i in indices ) {
        if(predicate(this[i])) {
            found++
            if( found == n ) return i
        }
    }
    return -1
}

private fun computeChangedLines(a: CharSequence, b: CharSequence) : ChangedLinesInfo {
    val unchangedStartLength = b.commonPrefixLengthWith(a)
    val unchangedEndLength = b.commonSuffixLengthWith(a, maxLength = a.length - unchangedStartLength)
    val lineBreaksBefore = b.countInSubsequence(0, unchangedStartLength) { it == '\n' }
    val prevContentEndIndex = a.length - unchangedEndLength
    val newContentEndIndex = b.length - unchangedEndLength
    val lineRangeStart = a.indexOfNth(lineBreaksBefore) {it=='\n'} + 1
    val prevLineRangeEnd = a.indexOf('\n', prevContentEndIndex).let { if(it<0) a.length else it}
    val newLineRangeEnd = b.indexOf('\n', newContentEndIndex).let { if(it<0) b.length else it}
    val prevContent = if(prevLineRangeEnd < lineRangeStart) emptyList() else a.subSequence(lineRangeStart, prevLineRangeEnd).lines()
    val newContent = if(newLineRangeEnd < lineRangeStart) emptyList() else b.subSequence(lineRangeStart, newLineRangeEnd).lines()
    val aLines = a.lineSequence().count()
    val bLines = b.lineSequence().count()
    val lineDelta = newContent.size - prevContent.size
    check(aLines + lineDelta == bLines)
    return ChangedLinesInfoImpl(
        startLine = lineBreaksBefore,
        prevLineCount = prevContent.size,
        newLineCount = newContent.size,
        prevLines = prevContent,
        newLines = newContent
    )
}

private fun <T> Flow<T>.withPrevious() = runningFold<T,Pair<T?,T?>>(null to null) {acc, v -> acc.second to v}

