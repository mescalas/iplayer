package com.iplayer.tv.player

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import com.iplayer.tv.data.SubtitleBackground
import com.iplayer.tv.data.SubtitleFont
import com.iplayer.tv.data.SubtitlePosition
import com.iplayer.tv.data.SubtitleStyle
import com.iplayer.tv.ui.theme.Inter

const val SUBTITLE_SAMPLE = "Je crois qu'on devrait rentrer\navant la tombée de la nuit."

/**
 * Subtitles drawn in Compose instead of Media3's SubtitleView (tvOS look: medium-weight text on a
 * rounded translucent plate, sized relative to the video). Bitmap subtitles (PGS, DVB) keep the
 * position chosen by the stream; text cues follow [style].
 *
 * @param avoidBottom / [avoidTop] space taken by on-screen controls, subtitles move out of the way.
 * @param endInset room taken by a side panel, subtitles re-center in what remains.
 * @param sample text shown when no cue is on screen (live preview of the style).
 */
@Composable
fun SubtitleLayer(
    player: Player?,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
    avoidBottom: Dp = 0.dp,
    avoidTop: Dp = 0.dp,
    endInset: Dp = 0.dp,
    sample: String? = null,
) {
    var cues by remember(player) { mutableStateOf(player?.currentCues?.cues.orEmpty()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val inset by animateDpAsState(endInset, tween(200), label = "subInset")

    BoxWithConstraints(modifier.fillMaxSize().padding(end = inset)) {
        val density = LocalDensity.current
        val fontSize = with(density) { (maxHeight * style.size.fraction).toSp() }

        val bitmaps = cues.filter { it.bitmap != null }
        bitmaps.forEach { BitmapCue(it, maxWidth, maxHeight) }

        val texts = remember(cues) { cues.filter { it.bitmap == null && !it.text.isNullOrBlank() } }
        var top = remember(texts, style.position) {
            texts.filter { style.position == SubtitlePosition.TOP || it.isTopAligned() }.map { it.text!!.toAnnotated() }
        }
        var bottom = remember(texts, style.position) {
            texts.filter { style.position != SubtitlePosition.TOP && !it.isTopAligned() }.map { it.text!!.toAnnotated() }
        }
        if (sample != null && top.isEmpty() && bottom.isEmpty() && bitmaps.isEmpty()) {
            if (style.position == SubtitlePosition.TOP) top = listOf(AnnotatedString(sample)) else bottom = listOf(AnnotatedString(sample))
        }

        val edge = maxHeight * style.position.margin
        val bottomPad by animateDpAsState(maxOf(edge, avoidBottom), tween(250), label = "subBottom")
        val topPad by animateDpAsState(maxOf(edge, avoidTop), tween(250), label = "subTop")
        val side = maxWidth * 0.08f
        if (bottom.isNotEmpty()) {
            CueBlock(bottom, style, fontSize, Modifier.align(Alignment.BottomCenter).padding(start = side, end = side, bottom = bottomPad))
        }
        if (top.isNotEmpty()) {
            CueBlock(top, style, fontSize, Modifier.align(Alignment.TopCenter).padding(start = side, end = side, top = topPad))
        }
    }
}

@Composable
private fun CueBlock(lines: List<AnnotatedString>, style: SubtitleStyle, fontSize: TextUnit, modifier: Modifier) {
    val density = LocalDensity.current
    val fontPx = with(density) { fontSize.toPx() }
    val fontDp = with(density) { fontSize.toDp() }
    val plate = Color(style.background.argb)
    val hasPlate = style.background != SubtitleBackground.NONE
    val textStyle = TextStyle(
        fontFamily = when (style.font) {
            SubtitleFont.MODERN -> Inter
            SubtitleFont.SYSTEM -> FontFamily.SansSerif
            SubtitleFont.CLASSIC -> FontFamily.Serif
        },
        fontWeight = if (style.font == SubtitleFont.CLASSIC) FontWeight.Normal else FontWeight.Medium,
        fontSize = fontSize,
        lineHeight = fontSize * 1.24f,
        letterSpacing = fontSize * 0.005f,
        color = Color(style.color.argb),
        textAlign = TextAlign.Center,
        shadow = if (hasPlate) {
            Shadow(Color(0x59000000), Offset(0f, fontPx * 0.03f), fontPx * 0.08f)
        } else {
            Shadow(Color(0xE6000000), Offset(0f, fontPx * 0.05f), fontPx * 0.18f)
        },
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(fontDp * 0.2f)) {
        lines.forEach { line ->
            Text(
                line,
                style = textStyle,
                modifier = if (hasPlate) {
                    Modifier.clip(RoundedCornerShape(fontDp * 0.3f)).background(plate)
                        .padding(horizontal = fontDp * 0.5f, vertical = fontDp * 0.16f)
                } else Modifier,
            )
        }
    }
}

/** Same placement rules as Media3's SubtitlePainter for bitmap cues. */
@Composable
private fun BitmapCue(cue: Cue, width: Dp, height: Dp) {
    val bitmap = cue.bitmap ?: return
    if (bitmap.width <= 0 || bitmap.height <= 0) return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val w = width * (if (cue.size != Cue.DIMEN_UNSET) cue.size else bitmap.width.toFloat() / 1920f)
    val h = if (cue.bitmapHeight != Cue.DIMEN_UNSET) height * cue.bitmapHeight else w * (bitmap.height.toFloat() / bitmap.width)
    val anchorX = width * (if (cue.position != Cue.DIMEN_UNSET) cue.position else 0.5f)
    val anchorY = height * (if (cue.line != Cue.DIMEN_UNSET && cue.lineType == Cue.LINE_TYPE_FRACTION) cue.line else 0.9f)
    val x = anchorX - when (cue.positionAnchor) {
        Cue.ANCHOR_TYPE_END -> w
        Cue.ANCHOR_TYPE_MIDDLE -> w / 2
        Cue.ANCHOR_TYPE_START -> 0.dp
        else -> if (cue.position != Cue.DIMEN_UNSET) 0.dp else w / 2
    }
    val y = anchorY - when (cue.lineAnchor) {
        Cue.ANCHOR_TYPE_END -> h
        Cue.ANCHOR_TYPE_MIDDLE -> h / 2
        Cue.ANCHOR_TYPE_START -> 0.dp
        else -> if (cue.line != Cue.DIMEN_UNSET) 0.dp else h
    }
    Image(image, null, Modifier.offset(x, y).size(w, h), contentScale = ContentScale.FillBounds)
}

/** Cues the stream explicitly places in the upper part of the picture (SSA \an8, CEA-608 top rows…). */
private fun Cue.isTopAligned(): Boolean = when {
    line == Cue.DIMEN_UNSET -> false
    lineType == Cue.LINE_TYPE_FRACTION -> line < 0.4f
    lineType == Cue.LINE_TYPE_NUMBER -> line >= 0f
    else -> false
}

/** Keeps bold / italic / underline from the subtitle file; colours and fonts follow the user's style. */
private fun CharSequence.toAnnotated(): AnnotatedString {
    val raw = toString()
    val start = raw.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = raw.indexOfLast { !it.isWhitespace() } + 1
    val spanned = this as? Spanned ?: return AnnotatedString(raw.substring(start, end))
    return buildAnnotatedString {
        append(raw.substring(start, end))
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val a = (spanned.getSpanStart(span) - start).coerceIn(0, end - start)
            val b = (spanned.getSpanEnd(span) - start).coerceIn(0, end - start)
            if (a >= b) continue
            val spanStyle = when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    else -> null
                }
                is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                else -> null
            } ?: continue
            addStyle(spanStyle, a, b)
        }
    }
}

/** A still "movie frame" with a sample line, to judge the style outside of playback. */
@Composable
fun SubtitlePreview(style: SubtitleStyle, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFF0E1A2B), Color(0xFF29476B), Color(0xFFB8744A), Color(0xFFEBC08A)))
        )
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000), Color(0x80000000)))))
        SubtitleLayer(player = null, style = style, sample = SUBTITLE_SAMPLE)
    }
}
