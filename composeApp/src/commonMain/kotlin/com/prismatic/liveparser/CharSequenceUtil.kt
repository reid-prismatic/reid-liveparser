package com.prismatic.liveparser

import kotlin.math.min


/**
 * Returns the length of the longest string `prefix` such that this char sequence and [other] char sequence both start with this prefix,
 * taking care not to split surrogate pairs.
 * If this and [other] have no common prefix, returns zero.

 * @param ignoreCase `true` to ignore character case when matching a character. By default `false`.
 * @sample samples.text.Strings.commonPrefixWith
 */
public fun CharSequence.commonPrefixLengthWith(other: CharSequence, ignoreCase: Boolean = false): Int {
    val shortestLength = minOf(this.length, other.length)

    var i = 0
    while (i < shortestLength && this[i].equals(other[i], ignoreCase = ignoreCase)) {
        i++
    }
    if (this.hasSurrogatePairAt(i - 1) || other.hasSurrogatePairAt(i - 1)) {
        i--
    }
    return i
}

/**
 * Returns the length of the longest string `suffix` such that this char sequence and [other] char sequence both end with this suffix,
 * taking care not to split surrogate pairs.
 * If this and [other] have no common suffix, returns zero.

 * @param ignoreCase `true` to ignore character case when matching a character. By default `false`.
 * @sample samples.text.Strings.commonSuffixWith
 */
public fun CharSequence.commonSuffixLengthWith(other: CharSequence, ignoreCase: Boolean = false, maxLength: Int = Int.MAX_VALUE): Int {
    val thisLength = this.length
    val otherLength = other.length
    val shortestLength = minOf(minOf(thisLength, otherLength),maxLength)

    var i = 0
    while (i < shortestLength && this[thisLength - i - 1].equals(other[otherLength - i - 1], ignoreCase = ignoreCase)) {
        i++
    }
    if (this.hasSurrogatePairAt(thisLength - i - 1) || other.hasSurrogatePairAt(otherLength - i - 1)) {
        i--
    }
    return i
}

/**
 * Returns the number of characters matching the given [predicate] in the subsequence of this
 * character sequence starting at the specified [startIndex] and ending right before the specified [endIndex].
 *
 * @param predicate predicate to determine if a character is counted.
 * @param startIndex the start index (inclusive).
 * @param endIndex the end index (exclusive).
 */
public inline fun CharSequence.countInSubsequence(startIndex: Int = 0, endIndex: Int = length, predicate: (Char) -> Boolean): Int {
    var count = 0
    if( endIndex <= startIndex) return 0
    for( i in startIndex.coerceIn(0,length-1) ..< endIndex.coerceIn(0, length)) {
        if(predicate(this[i])) ++count
    }
    return count
}
