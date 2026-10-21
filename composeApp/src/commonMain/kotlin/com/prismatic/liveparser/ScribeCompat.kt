package com.prismatic.liveparser

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import com.prismatic.liveparser.weave.elements.TextElement
import com.prismatic.liveparser.weave.grammar.LWElement
import com.prismatic.liveparser.weave.grammar.LineType
import org.prismatic.opus.math.Vec2
import org.prismatic.opus.math.Vec3
import org.prismatic.opus.math.Vec4
import org.prismatic.opus.time.FrameRate
import org.prismatic.opus.time.MediaTime
import org.prismatic.opus.time.Time

fun LWElement<*>.toMarkdownEntity(): MarkdownEntity = when(this) {
    is TextElement -> MarkdownEntity.Text(MarkdownPropertiesImpl(
        parseErrors = emptyList(),
        range = TextRange(0,0),
        propRanges = properties.mapValues { TextRange(0,0) },
        lineRange = 0..0
    ),text)
    else -> TODO()
}

@Immutable
interface MarkdownProperties {
    val range: TextRange
    val propRanges: Map<String,TextRange>
    val parseErrors: List<CodeError>
    //    val lineNumber: Int
    val lineRange: IntRange
    val position: Vec2
    val size: Vec2
    val color: Vec4
    val font: String
    val bold: Boolean
    val maxWidth: Float
    val italic: Boolean
    val fontSize: Float
    val scale: Vec2
    val filters: List<String>
    val duration: Time
    val comment: String?
    val voice: String
    val rotateAxis: Vec3
    val rotateAngle: Float
}

@Immutable
private data class MarkdownPropertiesImpl(
    override val parseErrors: List<CodeError>,
    override val range: TextRange,
    override val propRanges: Map<String,TextRange>,
    override val lineRange: IntRange,
    override val position: Vec2 = Vec2.ZERO,
    override val fontSize: Float = 56f,
    override val size: Vec2 = Vec2.ZERO,
    override val color: Vec4 = Vec4(0.7f,0.7f,0.7f,1f),
    override val font: String = "Default",
    override val bold: Boolean = false,
    override val italic: Boolean = false,
    override val maxWidth: Float = Float.NaN,
    override val scale: Vec2 = Vec2(1f,1f),
    override val filters: List<String> = emptyList(),
    override val duration: Time = MediaTime.Unspecified,
    override val comment: String? = null,
    override val voice: String = "",
    override val rotateAxis: Vec3 = Vec3(0f,0f,1f),
    override val rotateAngle: Float = 0f,
): MarkdownProperties

@Immutable
sealed interface MarkdownEntity : MarkdownProperties {
    data class Style(val props: MarkdownProperties, val styleName: String): MarkdownEntity, MarkdownProperties by props
    data class Code(val props: MarkdownProperties, val code: String): MarkdownEntity, MarkdownProperties by props
    data class CodeSample(val props: MarkdownProperties, val code: String, val codeLanguage: String): MarkdownEntity, MarkdownProperties by props
    data class Output(val props: MarkdownProperties, override val outputName: String, override val resolution: Int, override val ratio: Pair<Int,Int>, override val frameRate: FrameRate): OutputFormat, MarkdownEntity, MarkdownProperties by props
    data class Text(val props: MarkdownProperties, val text: String): MarkdownEntity, MarkdownProperties by props
    data class Voiceover(val props: MarkdownProperties, val text: String, val timing: EntityTiming, val filename: String): MarkdownEntity, MarkdownProperties by props
    data class Media(val props: MarkdownProperties, val filename: String, val timing: EntityTiming): MarkdownEntity, MarkdownProperties by props
    data class Divider(val props: MarkdownProperties, val transition: String): MarkdownEntity, MarkdownProperties by props
    data class Delay(val props: MarkdownProperties, val delayTime: MediaTime): MarkdownEntity, MarkdownProperties by props
}

data class EntityTiming(val delayNext: Boolean, val background: Boolean)

interface OutputFormat {
    val outputName: String
    val resolution: Int
    val ratio: Pair<Int,Int>
    val frameRate: FrameRate
}

val OutputFormat.width: Int get() = if(ratio.first > ratio.second) resolution * ratio.first / ratio.second else resolution
val OutputFormat.height: Int get() = if(ratio.first > ratio.second) resolution else resolution * ratio.second / ratio.first
val OutputFormat.ratioFraction: Float get() = ratio.first.toFloat() / ratio.second.toFloat()
