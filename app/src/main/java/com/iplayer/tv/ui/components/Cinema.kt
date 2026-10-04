package com.iplayer.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import java.util.Locale

/*
 * Building blocks of the "cinema" look shared by every screen: full-bleed artwork under dark
 * scrims, uppercase letter-spaced labels, gold accents, a round play button and text actions.
 */

/** Sharp corners of artwork and list rows. */
val ArtShape = RoundedCornerShape(3.dp)
val RowShape = RoundedCornerShape(4.dp)

/** "IPLAYER" in spaced capitals, as the film-site wordmark. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, color: Color = Color(0xD9FFFFFF)) {
    Text("IPLAYER", style = T.Label.copy(letterSpacing = 4.sp), color = color, modifier = modifier)
}

/**
 * Full-bleed artwork with the dark scrims of the cinema layout. [fallbackImage] (a portrait poster)
 * is blurred when no landscape backdrop exists. [settle] adds a slow zoom-out when it first shows.
 */
@Composable
fun BoxScope.CinemaBackdrop(
    backdrop: String?,
    fallbackImage: String? = null,
    settle: Boolean = true,
    imageAlpha: Float = 1f,
    leftScrim: Float = 1f,
) {
    val zoom = remember { Animatable(if (settle) 1.07f else 1f) }
    if (settle) LaunchedEffect(Unit) { zoom.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
    val image = backdrop?.takeIf { it.isNotBlank() } ?: fallbackImage?.takeIf { it.isNotBlank() }
    if (image != null) {
        val isFallback = backdrop.isNullOrBlank()
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = imageAlpha * if (isFallback) 0.55f else 1f,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                }
                .then(if (isFallback) Modifier.blur(28.dp) else Modifier),
        )
    }
    CinemaScrims(leftScrim)
}

/** The dim + left + bottom gradients that keep text readable over any image. */
@Composable
fun BoxScope.CinemaScrims(leftScrim: Float = 1f) {
    Box(Modifier.matchParentSize().background(Color(0x38000000)))
    Box(
        Modifier.matchParentSize().graphicsLayer { alpha = leftScrim }.background(
            Brush.horizontalGradient(0f to Color(0xD9000000), 0.42f to Color(0x80000000), 0.75f to Color(0x33000000), 1f to Color(0x4D000000))
        )
    )
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(0f to Color(0x80000000), 0.2f to Color.Transparent, 0.45f to Color.Transparent, 0.78f to Color(0xB3000000), 1f to Color(0xF2000000))
        )
    )
}

/** "NOTE 8.0 / 10" with the score in gold. */
@Composable
fun RatingLabel(rating: Float, modifier: Modifier = Modifier, scoreSize: TextUnit = 20.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)) { append("NOTE  ") }
            withStyle(SpanStyle(color = C.Gold, fontWeight = FontWeight.Bold, fontSize = scoreSize)) { append(String.format(Locale.ROOT, "%.1f", rating)) }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp)) { append(" / 10") }
        },
        style = T.Caption,
        color = C.Text,
        modifier = modifier,
    )
}

/** Round outlined play button, with an optional gold progress ring. */
@Composable
fun PlayCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 74.dp,
    progress: Float? = null,
    loading: Boolean = false,
    retry: Boolean = false,
    icon: ImageVector = Icons.Rounded.PlayArrow,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color(0x26FFFFFF),
        focusedScale = 1.14f,
        elevation = 26.dp,
        contentAlignment = Alignment.Center,
        onFocusChange = onFocusChange,
    ) { focused ->
        val ring = if (focused) Color.Transparent else Color(0xD9FFFFFF)
        val arc = if (focused) C.OnFocus else C.Gold
        Canvas(Modifier.fillMaxSize()) {
            val sw = 2.dp.toPx()
            drawCircle(ring, radius = this.size.minDimension / 2 - sw / 2, style = Stroke(sw))
            if (progress != null && progress > 0f) {
                val aw = 3.dp.toPx()
                drawArc(
                    color = arc,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(aw / 2, aw / 2),
                    size = Size(this.size.width - aw, this.size.height - aw),
                    style = Stroke(aw, cap = StrokeCap.Round),
                )
            }
        }
        when {
            loading -> CircularProgressIndicator(color = LocalContentColor.current, strokeWidth = 2.dp, modifier = Modifier.size(size * 0.35f))
            retry -> Icon(Icons.Rounded.Refresh, null, Modifier.size(size * 0.43f))
            icon == Icons.Rounded.PlayArrow -> Icon(icon, null, Modifier.size(size * 0.52f).offset(x = size * 0.03f))
            else -> Icon(icon, null, Modifier.size(size * 0.45f))
        }
    }
}

/** Small uppercase action ("♡ AJOUTER À MA LISTE"), white pill when focused. */
@Composable
fun TextAction(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = C.Gold,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        focusedScale = 1.06f,
        elevation = 10.dp,
        contentAlignment = Alignment.CenterStart,
        onFocusChange = onFocusChange,
    ) { focused ->
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(17.dp), tint = if (focused || iconTint == null) LocalContentColor.current else iconTint)
                Spacer(Modifier.width(9.dp))
            }
            Text(text.uppercase(Locale.FRENCH), style = T.Label.copy(fontSize = 12.sp), maxLines = 1)
        }
    }
}

/** Uppercase tab: gold underline when selected, white pill when focused. */
@Composable
fun TabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    icon: ImageVector? = null,
    height: Dp = 34.dp,
    onFocused: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(height / 2),
        color = Color.Transparent,
        contentColor = if (selected) C.Text else C.Text2,
        focusedScale = 1.06f,
        elevation = 8.dp,
        contentAlignment = Alignment.Center,
        onFocusChange = { if (it) onFocused?.invoke() },
    ) { focused ->
        Column(Modifier.padding(horizontal = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                }
                if (tag != null) {
                    Text(tag.uppercase(Locale.FRENCH), style = T.Label.copy(fontSize = 10.sp), modifier = Modifier.graphicsLayer { alpha = 0.55f })
                    Spacer(Modifier.width(6.dp))
                }
                Text(text.uppercase(Locale.FRENCH), style = T.Label, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.width(18.dp).height(2.dp)
                    .background(if (selected && !focused) C.Gold else Color.Transparent, RoundedCornerShape(1.dp))
            )
        }
    }
}

/** Shelf title in spaced capitals, with the ‹ › hints of the row below. */
@Composable
fun ShelfHeader(text: String, modifier: Modifier = Modifier, state: LazyListState? = null, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(Locale.FRENCH), style = T.Label, color = C.Text)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing.uppercase(Locale.FRENCH), style = T.Label, color = C.Text3, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (state != null) {
            Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(20.dp), tint = if (state.canScrollBackward) C.Text else C.Text3)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = if (state.canScrollForward) C.Text else C.Text3)
        }
    }
}

/** "2013   |   RÉALISATION : Spike Jonze   |   AVEC : …" with bold labels and dim separators. */
fun metaLine(parts: List<Pair<String?, String>>, bold: Boolean = false): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { i, (label, value) ->
        if (i > 0) withStyle(SpanStyle(color = Color(0x80FFFFFF), fontWeight = FontWeight.Normal)) { append("   |   ") }
        if (label != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontSize = 11.sp)) { append("$label : ") }
            withStyle(SpanStyle(color = Color(0xCCFFFFFF))) { append(value) }
        } else {
            withStyle(SpanStyle(fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)) { append(value) }
        }
    }
}

/** "Drame, Romance / Science-Fiction" → [DRAME, ROMANCE, SCIENCE-FICTION] */
fun genresOf(s: String?): List<String> =
    s.orEmpty().split(',', '/', '|', ';').map { it.trim().uppercase(Locale.FRENCH) }.filter { it.isNotEmpty() }.distinct().take(3)

/** Font size of a [T.Display] title that keeps long names on two lines. */
fun displaySize(title: String): TextUnit = when {
    title.length > 30 -> 32.sp
    title.length > 18 -> 40.sp
    else -> 50.sp
}

/** Hairline frame drawn over artwork; white and thicker when focused. */
@Composable
fun BoxScope.ArtFrame(focused: Boolean, shape: Shape = ArtShape) {
    Box(Modifier.matchParentSize().border(if (focused) 2.dp else 1.dp, if (focused) C.Text else Color(0x1FFFFFFF), shape))
}
