package com.prismatic.liveparser

import com.prismatic.liveparser.weave.grammar.WeaveParseError
import com.prismatic.liveparser.weave.properties.ParsedStringFragment
import com.prismatic.liveparser.weave.properties.isPropertyNameChar
import com.prismatic.liveparser.weave.properties.isPropertyNameStartChar
import com.prismatic.liveparser.weave.properties.isStyleNameChar
import com.prismatic.liveparser.weave.properties.isStyleNameStartChar
import com.prismatic.liveparser.weave.properties.isTargetNameChar
import com.prismatic.liveparser.weave.properties.isTargetNameStartChar

abstract class StringParser(val string: String) {
    class ParseFailure : Throwable()
    var i = 0
    val length = string.length
    inline fun peek(offset: Int = 0) : Char = if( i + offset < length ) string[i+offset] else '\u0000'
    var ch: Char = peek(0)
    var next : Char = peek(1)
    inline fun advance() {
        i++
        ch = next
        next = peek(1)
    }
    fun skipWhitespace() {
        while( ch.isWhitespace() ) {advance()}
    }
    var failed = false
    var failureOffset = -1
    var errorType: WeaveParseError? = null
    fun error(type: WeaveParseError) : Nothing{
        failed = true
        failureOffset=i
        errorType = type
        throw ParseFailure()
    }
    inline fun expect(char: Char) {
        if( ch != char ) error(if(ch=='\u0000') WeaveParseError.UnexpectedEnd(char) else WeaveParseError.UnexpectedChar(char,ch))
        advance()
    }
    inline fun expectEnd() {
        if( i < length ) error(WeaveParseError.EndExpected)
    }
    inline fun extract(block:()->Unit) : ParsedStringFragment {
        val start = i
        block()
        return ParsedStringFragment(string.substring(start, i), start..<i)
    }
//    val remainingText: String get() = string.substring(i)
    inline fun extractRemaining() : ParsedStringFragment {
        val start = i
        i = length
        return ParsedStringFragment(string.substring(start, i), start..<i)
    }
    inline val atEnd : Boolean get() = i >= length


    fun parsePropertyName() = extract {
        if( !ch.isPropertyNameStartChar() )
            error(WeaveParseError.ExpectedPropertyName)
        advance()
        while( ch.isPropertyNameChar() ) {advance()}
    }

    fun parseStyleName() = extract{
        if(!ch.isStyleNameStartChar())
            error(WeaveParseError.ExpectedStyleName)
        advance()
        while( ch.isStyleNameChar() ) {advance()}
    }

    fun parseTargetName() = extract{
        if(!ch.isTargetNameStartChar())
            error(WeaveParseError.ExpectedStyleName)
        advance()
        while( ch.isTargetNameChar() ) {advance()}
    }

    fun parseCondition() = extract {
        var quoteChar = '\u0000'
        val brackets = CharArray(string.length)
        var bracketCount = 0
        while(!atEnd && (ch != ')' || quoteChar!='\u0000' || bracketCount>0)) {
            if(quoteChar != '\u0000') {
                if(ch==quoteChar) quoteChar = '\u0000'
            } else {
                when (ch) {
                    '(', '{', '[' -> brackets[bracketCount++] = ch
                    ')', '}', '}' -> {
                        if( bracketCount <= 0 ) break
                        if (brackets[bracketCount - 1] != ch)
                            error(WeaveParseError.MismatchedBracket)
                        bracketCount--
                    }
                    '"', '\'' -> quoteChar = ch
                }
            }
            advance()
        }
        if( quoteChar != '\u0000' ) error(WeaveParseError.UnterminatedQuote)
        if( bracketCount > 0 ) error(WeaveParseError.MismatchedBracket)
    }
}