package intrangeset

import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Interfaces
// ---------------------------------------------------------------------------

/**
 * A read-only view of a set of integers stored as a collection of [IntRange]s.
 *
 * Implementations must keep [ranges] **normalized**: every range is non-empty,
 * the list is sorted ascending, and no two ranges overlap or touch
 * (`a.last + 1 < b.first` for consecutive ranges `a`, `b`).
 *
 * Everything other than [ranges] is provided as extension functions so that
 * alternative implementations only need to supply the backing list.
 */
interface IntRangeSet {
    /** The normalized ranges that make up this set, in ascending order. */
    val ranges: List<IntRange>
}

/**
 * An [IntRangeSet] whose operations ([plus], [minus], …) return new sets and
 * never modify the receiver.
 *
 * The single abstract member, [copyWith], is the only thing an implementation
 * has to provide beyond [ranges].
 */
interface ImmutableIntRangeSet : IntRangeSet {
    /**
     * Returns a set of the same implementation containing exactly
     * [normalizedRanges]. The caller guarantees the list is already normalized;
     * implementations may store it without copying.
     */
    fun copyWith(normalizedRanges: List<IntRange>): ImmutableIntRangeSet
}

/**
 * An [IntRangeSet] whose operations ([add], [remove], …) modify the receiver
 * in place.
 *
 * The single abstract member, [splice], is the only thing an implementation
 * has to provide beyond [ranges]; all mutation algorithms are built on top of
 * it as extension functions.
 */
interface MutableIntRangeSet : IntRangeSet {
    /**
     * Replaces `ranges[fromIndex until toIndex]` with [replacement].
     * The caller guarantees that the resulting list remains normalized.
     */
    fun splice(fromIndex: Int, toIndex: Int, replacement: List<IntRange>)
}

// ---------------------------------------------------------------------------
// Default implementations
// ---------------------------------------------------------------------------

/** Immutable, array-backed [IntRangeSet]. Create via [intRangeSetOf] or [toImmutableIntRangeSet]. */
class ArrayIntRangeSet private constructor(
    override val ranges: List<IntRange>,
) : ImmutableIntRangeSet {

    override fun copyWith(normalizedRanges: List<IntRange>): ArrayIntRangeSet =
        if (normalizedRanges.isEmpty()) EMPTY else ArrayIntRangeSet(normalizedRanges.toList())

    override fun equals(other: Any?): Boolean = other is IntRangeSet && ranges == other.ranges
    override fun hashCode(): Int = ranges.hashCode()
    override fun toString(): String = ranges.joinToString(prefix = "[", postfix = "]")

    companion object {
        val EMPTY: ArrayIntRangeSet = ArrayIntRangeSet(emptyList())

        /** Builds a set from arbitrary (possibly overlapping, unsorted, empty) ranges. */
        fun of(ranges: Iterable<IntRange>): ArrayIntRangeSet = EMPTY.copyWith(normalizeRanges(ranges))

        /** Wraps a list that is already normalized, without re-checking it. */
        fun ofNormalized(ranges: List<IntRange>): ArrayIntRangeSet = EMPTY.copyWith(ranges)
    }
}

/** Mutable, array-backed [IntRangeSet]. Create via [mutableIntRangeSetOf] or [toMutableIntRangeSet]. */
class MutableArrayIntRangeSet private constructor(
    private val list: ArrayList<IntRange>,
) : MutableIntRangeSet {

    constructor() : this(ArrayList())

    /** A read-only view over the live backing list; it reflects later mutations. */
    override val ranges: List<IntRange> = ReadOnlyList(list)

    override fun splice(fromIndex: Int, toIndex: Int, replacement: List<IntRange>) {
        val removed = toIndex - fromIndex
        val common = min(removed, replacement.size)
        // Overwrite the overlap in place, then shrink or grow as needed.
        for (i in 0 until common) list[fromIndex + i] = replacement[i]
        if (removed > common) {
            list.subList(fromIndex + common, toIndex).clear()
        } else if (replacement.size > common) {
            list.addAll(fromIndex + common, replacement.subList(common, replacement.size))
        }
    }

    override fun equals(other: Any?): Boolean = other is IntRangeSet && ranges == other.ranges
    override fun hashCode(): Int = ranges.hashCode()
    override fun toString(): String = ranges.joinToString(prefix = "[", postfix = "]")

    companion object {
        /** Builds a set from arbitrary (possibly overlapping, unsorted, empty) ranges. */
        fun of(ranges: Iterable<IntRange>): MutableArrayIntRangeSet =
            MutableArrayIntRangeSet(ArrayList(normalizeRanges(ranges)))

        /** Copies a list that is already normalized, without re-checking it. */
        fun ofNormalized(ranges: List<IntRange>): MutableArrayIntRangeSet =
            MutableArrayIntRangeSet(ArrayList(ranges))
    }

    private class ReadOnlyList<T>(private val delegate: List<T>) : List<T> by delegate {
        override fun equals(other: Any?): Boolean = delegate == other
        override fun hashCode(): Int = delegate.hashCode()
        override fun toString(): String = delegate.toString()
    }
}

// ---------------------------------------------------------------------------
// Factories and conversions
// ---------------------------------------------------------------------------

fun emptyIntRangeSet(): ImmutableIntRangeSet = ArrayIntRangeSet.EMPTY

fun intRangeSetOf(vararg ranges: IntRange): ImmutableIntRangeSet = ArrayIntRangeSet.of(ranges.asIterable())

fun mutableIntRangeSetOf(vararg ranges: IntRange): MutableIntRangeSet = MutableArrayIntRangeSet.of(ranges.asIterable())

/** Builds an immutable set from arbitrary ranges (they are normalized first). */
fun Iterable<IntRange>.toIntRangeSet(): ImmutableIntRangeSet = ArrayIntRangeSet.of(this)

/** Builds a mutable set from arbitrary ranges (they are normalized first). */
fun Iterable<IntRange>.toMutableIntRangeSet(): MutableIntRangeSet = MutableArrayIntRangeSet.of(this)

/** Returns an immutable snapshot of this set. Immutable receivers are returned as-is. */
fun IntRangeSet.toImmutableIntRangeSet(): ImmutableIntRangeSet =
    this as? ImmutableIntRangeSet ?: ArrayIntRangeSet.ofNormalized(ranges)

/** Returns a new mutable copy of this set. */
fun IntRangeSet.toMutableIntRangeSet(): MutableIntRangeSet = MutableArrayIntRangeSet.ofNormalized(ranges)

/** Returns the ranges of this set as a fresh, independent list. */
fun IntRangeSet.toList(): List<IntRange> = ranges.toList()

// ---------------------------------------------------------------------------
// Read-only queries (shared by both variants)
// ---------------------------------------------------------------------------

/** Number of disjoint ranges in the set (not the number of integers). */
val IntRangeSet.size: Int get() = ranges.size

fun IntRangeSet.isEmpty(): Boolean = ranges.isEmpty()
fun IntRangeSet.isNotEmpty(): Boolean = ranges.isNotEmpty()

fun IntRangeSet.forEach(block: (IntRange) -> Unit) {ranges.forEach(block)}

/** Total number of integers covered, as a Long since it can exceed Int range. */
fun IntRangeSet.valueCount(): Long = ranges.sumOf { it.last.toLong() - it.first.toLong() + 1 }

/** True if [value] lies inside one of the ranges. O(log n). */
operator fun IntRangeSet.contains(value: Int): Boolean {
    val i = ranges.firstIndexWithLastAtLeast(value.toLong())
    return i < ranges.size && ranges[i].first <= value
}

/** True if every value of [range] is in the set. An empty range is trivially contained. O(log n). */
operator fun IntRangeSet.contains(range: IntRange): Boolean {
    if (range.isEmpty()) return true
    val i = ranges.firstIndexWithLastAtLeast(range.first.toLong())
    return i < ranges.size && ranges[i].first <= range.first && ranges[i].last >= range.last
}

/** True if every value of [other] is in this set. O(m log n). */
fun IntRangeSet.containsAll(other: IntRangeSet): Boolean =
    other === this || other.ranges.all { it in this }

/** True if [range] shares at least one value with the set. O(log n). */
fun IntRangeSet.intersects(range: IntRange): Boolean {
    if (range.isEmpty()) return false
    val i = ranges.firstIndexWithLastAtLeast(range.first.toLong())
    return i < ranges.size && ranges[i].first <= range.last
}

/** True if the two sets share at least one value. O(n + m). */
fun IntRangeSet.intersects(other: IntRangeSet): Boolean {
    if (other === this) return isNotEmpty()
    val a = ranges
    val b = other.ranges
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        when {
            a[i].last < b[j].first -> i++
            b[j].last < a[i].first -> j++
            else -> return true
        }
    }
    return false
}

// ---------------------------------------------------------------------------
// Immutable operations
// ---------------------------------------------------------------------------

/** Returns a copy with [range] added (merging with any overlapping or adjacent ranges). */
operator fun IntRangeSet.plus(range: IntRange): ImmutableIntRangeSet {
    if( this !is ImmutableIntRangeSet ) return toImmutableIntRangeSet() + range
    val splice = ranges.spliceForAdd(range) ?: return this
    return copyWith(ranges.applied(splice))
}

/** Returns a copy with [range] removed, splitting any range it falls inside. */
operator fun IntRangeSet.minus(range: IntRange): ImmutableIntRangeSet {
    if( this !is ImmutableIntRangeSet ) return toImmutableIntRangeSet() - range
    val splice = ranges.spliceForRemove(range) ?: return this
    return copyWith(ranges.applied(splice))
}

/** Returns the union of this set and [other]. */
fun IntRangeSet.union(other: IntRangeSet): ImmutableIntRangeSet = when {
    this !is ImmutableIntRangeSet -> toImmutableIntRangeSet() + other
    other.isEmpty() || other === this -> this
    isEmpty() -> copyWith(other.ranges)
    else -> copyWith(normalizeRanges(ranges + other.ranges))
}

/** Returns a copy with every value of [other] removed. O(n + m). */
fun IntRangeSet.subtract(other: IntRangeSet): ImmutableIntRangeSet = when {
    this !is ImmutableIntRangeSet -> toImmutableIntRangeSet() - other
    other.isEmpty() || isEmpty() -> this
    other === this -> copyWith(emptyList())
    else -> copyWith(differenceOf(ranges, other.ranges))
}

operator fun IntRangeSet.plus(other: IntRangeSet): ImmutableIntRangeSet = union(other)
operator fun IntRangeSet.minus(other: IntRangeSet): ImmutableIntRangeSet = subtract(other)

// ---------------------------------------------------------------------------
// Mutable operations
// ---------------------------------------------------------------------------

/** Adds [range] in place, merging as needed. Returns true if the set changed. */
fun MutableIntRangeSet.add(range: IntRange): Boolean {
    val splice = ranges.spliceForAdd(range) ?: return false
    splice(splice.from, splice.to, splice.replacement)
    return true
}

/** Removes [range] in place, splitting as needed. Returns true if the set changed. */
fun MutableIntRangeSet.remove(range: IntRange): Boolean {
    val splice = ranges.spliceForRemove(range) ?: return false
    splice(splice.from, splice.to, splice.replacement)
    return true
}

/** Removes every value from the set. */
fun MutableIntRangeSet.clear() {
    if (ranges.isNotEmpty()) splice(0, ranges.size, emptyList())
}

/** Adds every value of [other]. Returns true if the set changed. */
fun MutableIntRangeSet.addAll(other: IntRangeSet): Boolean {
    if (other === this) return false
    var changed = false
    for (r in other.ranges) changed = add(r) || changed
    return changed
}

/** Removes every value of [other]. Returns true if the set changed. */
fun MutableIntRangeSet.removeAll(other: IntRangeSet): Boolean {
    if (other === this) {
        if (isEmpty()) return false
        clear()
        return true
    }
    var changed = false
    for (r in other.ranges) changed = remove(r) || changed
    return changed
}

operator fun MutableIntRangeSet.plusAssign(range: IntRange) { add(range) }
operator fun MutableIntRangeSet.minusAssign(range: IntRange) { remove(range) }
operator fun MutableIntRangeSet.plusAssign(other: IntRangeSet) { addAll(other) }
operator fun MutableIntRangeSet.minusAssign(other: IntRangeSet) { removeAll(other) }

// ---------------------------------------------------------------------------
// Core algorithms on normalized List<IntRange>
// ---------------------------------------------------------------------------

/**
 * Normalizes arbitrary ranges: drops empty ones, sorts, and merges any that
 * overlap or touch. Safe for inputs that reach Int.MIN_VALUE / Int.MAX_VALUE.
 */
fun normalizeRanges(ranges: Iterable<IntRange>): List<IntRange> {
    val sorted = ranges.filterNot { it.isEmpty() }.sortedBy { it.first }
    if (sorted.size <= 1) return sorted
    val out = ArrayList<IntRange>(sorted.size)
    var curFirst = sorted[0].first
    var curLast = sorted[0].last
    for (i in 1 until sorted.size) {
        val r = sorted[i]
        if (r.first.toLong() <= curLast.toLong() + 1) {
            curLast = max(curLast, r.last)
        } else {
            out += curFirst..curLast
            curFirst = r.first
            curLast = r.last
        }
    }
    out += curFirst..curLast
    return out
}

/** A pending edit: replace `[from, to)` with [replacement]. */
class RangeSplice(val from: Int, val to: Int, val replacement: List<IntRange>)

/**
 * Index of the first range whose `last >= value`, or `size` if none.
 * Because the list is normalized, `last` is strictly increasing, so a binary
 * search is valid. [value] is a Long so callers can safely pass `x ± 1`.
 */
fun List<IntRange>.firstIndexWithLastAtLeast(value: Long): Int {
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].last.toLong() < value) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * Computes the edit that adds [range] to this normalized list, or null if the
 * list already contains it. O(log n + k) where k is the number of ranges merged.
 */
fun List<IntRange>.spliceForAdd(range: IntRange): RangeSplice? {
    if (range.isEmpty()) return null
    val lo = range.first.toLong()
    val hi = range.last.toLong()
    // First range that overlaps or is adjacent on the left.
    val from = firstIndexWithLastAtLeast(lo - 1)
    // One past the last range that overlaps or is adjacent on the right.
    var to = from
    while (to < size && this[to].first.toLong() <= hi + 1) to++

    var newFirst = range.first
    var newLast = range.last
    if (from < to) {
        newFirst = min(newFirst, this[from].first)
        newLast = max(newLast, this[to - 1].last)
        if (to - from == 1 && this[from].first == newFirst && this[from].last == newLast) return null
    }
    return RangeSplice(from, to, listOf(newFirst..newLast))
}

/**
 * Computes the edit that removes [range] from this normalized list, or null if
 * nothing would change. O(log n + k). A range strictly inside an existing one
 * splits it into two.
 */
fun List<IntRange>.spliceForRemove(range: IntRange): RangeSplice? {
    if (range.isEmpty()) return null
    val from = firstIndexWithLastAtLeast(range.first.toLong())
    var to = from
    while (to < size && this[to].first <= range.last) to++
    if (from == to) return null

    val replacement = ArrayList<IntRange>(2)
    val first = this[from]
    if (first.first < range.first) replacement += first.first..(range.first - 1)
    val last = this[to - 1]
    if (last.last > range.last) replacement += (range.last + 1)..last.last
    return RangeSplice(from, to, replacement)
}

/** Returns a new list with [splice] applied to this one. */
fun List<IntRange>.applied(splice: RangeSplice): List<IntRange> {
    val out = ArrayList<IntRange>(size - (splice.to - splice.from) + splice.replacement.size)
    for (i in 0 until splice.from) out += this[i]
    out += splice.replacement
    for (i in splice.to until size) out += this[i]
    return out
}

/** `a − b` for two normalized lists, in O(n + m). */
fun differenceOf(a: List<IntRange>, b: List<IntRange>): List<IntRange> {
    if (b.isEmpty()) return a
    val out = ArrayList<IntRange>(a.size + 1)
    var j = 0
    for (r in a) {
        val end = r.last
        var cur = r.first.toLong()
        while (j < b.size && b[j].last < cur) j++
        var k = j
        while (k < b.size && b[k].first <= end) {
            if (b[k].first > cur) out += cur.toInt()..(b[k].first - 1)
            cur = b[k].last.toLong() + 1
            k++
        }
        if (cur <= end) out += cur.toInt()..end
        // The last subtracted range may extend into the next `a` range; keep it.
        if (k > j) j = k - 1
    }
    return out
}
