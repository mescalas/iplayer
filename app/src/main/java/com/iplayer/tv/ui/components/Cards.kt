package com.iplayer.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.mediaName
import java.util.Locale

/** 14 px corners of the design artwork. */
val CardShape = RoundedCornerShape(7.dp)
private val Hairline = Color(0x14FFFFFF)

/**
 * Hairline frame + tvOS-like specular highlight on the focused artwork. One node, no extra layer: the
 * highlight's fade is applied while drawing, so focusing a card never recomposes nor re-layers it.
 */
@Composable
fun BoxScope.CardChrome(focused: Boolean) {
    val sheen = animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "sheen")
    Box(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val brush = Brush.linearGradient(
                    0f to Color(0x33FFFFFF),
                    0.38f to Color(0x0AFFFFFF),
                    0.55f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
                onDrawWithContent {
                    drawContent()
                    val a = sheen.value
                    if (a > 0f) drawRect(brush, alpha = a)
                }
            }
            .border(0.75.dp, Hairline, CardShape)
    )
}

/** Title + meta under an artwork, as in the design: always visible, brighter when the card has focus. */
@Composable
private fun CardCaption(title: String, meta: String?, focused: Boolean, gap: Dp = 8.dp) {
    Column(Modifier.padding(top = gap)) {
        Text(
            title,
            style = T.Subhead,
            color = if (focused) C.Text else C.Text.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(meta, style = T.Caption, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun ratingMeta(subtitle: String?, rating: Float): String? = listOfNotNull(
    subtitle?.takeIf { it.isNotBlank() },
    if (rating > 0f) "★ " + String.format(Locale.FRANCE, "%.1f", rating) else null,
).joinToString(" · ").ifEmpty { null }

@Composable
private fun BoxScope.ArtworkProgress(progress: Float?, padding: Dp = 8.dp) {
    if (progress == null || progress <= 0f) return
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
    )
    ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(padding), height = 3.dp)
}

/** Placeholder artwork: the title on a soft gradient, until (or instead of) the image. */
@Composable
private fun BoxScope.ArtworkFallback(title: String) {
    Box(
        Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF2A2A2E), Color(0xFF1C1C1F)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = T.Subhead.copy(fontWeight = FontWeight.SemiBold),
            color = C.Text2,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** Portrait lockup (248 × 372 px): artwork, then title and meta. */
@Composable
fun PosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 124.dp,
    subtitle: String? = null,
    progress: Float? = null,
    rating: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    showCaption: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val name = remember(title) { title.mediaName() }
    val display = name.title
    Column(if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.08f,
            elevation = 24.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            ArtworkFallback(display)
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (name.badges.isNotEmpty()) {
                Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    name.badges.take(2).forEach { Badge(it) }
                }
            }
            ArtworkProgress(progress)
            CardChrome(f)
        }
        if (showCaption) CardCaption(display, ratingMeta(subtitle, rating), focused)
    }
}

/** Landscape lockup (420 × 236 px) used for "Reprendre la lecture", trailers, chapters… */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 210.dp,
    progress: Float? = null,
    logoMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    badge: String? = null,
    /** False when [title] is already clean (episode titles built by the caller). */
    cleanup: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shown = if (cleanup) remember(title) { title.cleanTitle() } else title
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 236f / 420f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.07f,
            elevation = 24.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            if (logoMode) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2E2E33), Color(0xFF1C1C1F)))))
                ChannelLogo(image, title, Modifier.fillMaxSize(), padding = 22.dp, transparent = true)
            } else {
                ArtworkFallback(shown)
                if (!image.isNullOrBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            ArtworkProgress(progress)
            if (badge != null) Badge(badge, Modifier.align(Alignment.TopStart).padding(7.dp))
            CardChrome(f)
        }
        CardCaption(shown, subtitle, focused, gap = 7.dp)
    }
}

/**
 * Live channel tile (372 × 210 px): channel name and "EN DIRECT" on top, programme progress at the bottom,
 * the programme and its time under it. Without programme artwork the channel logo fills the preview.
 */
@Composable
fun LiveTile(
    name: String,
    logo: String?,
    program: String?,
    progress: Float?,
    timeRange: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 186.dp,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    category: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val channel = remember(name) { name.cleanTitle() }
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 210f / 372f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.07f,
            elevation = 24.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF34343A), Color(0xFF1A1A1E)))))
            ChannelLogo(logo, name, Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 24.dp), padding = 0.dp, transparent = true)
            Row(Modifier.fillMaxWidth().padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel,
                    style = T.Caption.copy(fontWeight = FontWeight.Bold),
                    color = C.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, false)
                        .background(Color(0xA6000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                LiveBadge()
            }
            if (progress != null) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(7.dp), height = 2.5.dp)
            }
            CardChrome(f)
        }
        CardCaption(
            program ?: channel,
            listOfNotNull(timeRange, category).joinToString(" · ").ifEmpty { if (program != null) channel else null },
            focused,
            gap = 7.dp,
        )
    }
}

/** Episode card (440 × 248 px thumbnail) with number, title, two lines of synopsis and duration. */
@Composable
fun EpisodeCard(
    number: String,
    title: String,
    description: String?,
    meta: String?,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    progress: Float? = null,
    watched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 248f / 440f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.07f,
            elevation = 24.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            ArtworkFallback(title)
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (watched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(7.dp).size(17.dp).background(Color(0xA6000000), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, Modifier.size(9.dp), tint = C.Text) }
            }
            ArtworkProgress(progress, padding = 7.dp)
            CardChrome(f)
        }
        Spacer(Modifier.height(6.dp))
        Text(number.uppercase(), style = T.Micro.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp), color = C.Text3, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(title, style = T.Label, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(description, style = T.Caption.copy(lineHeight = 13.sp), color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(meta, style = T.Caption.copy(fontWeight = FontWeight.Medium), color = C.Text3, maxLines = 1)
        }
    }
}

/** Round portrait with name and role (cast). Without photos, the initials stand in. */
@Composable
fun CastMember(name: String, role: String?, modifier: Modifier = Modifier, onFocused: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = {},
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            color = C.Surface,
            focusedColor = C.Surface2,
            focusedContentColor = C.Text,
            focusedScale = 1.08f,
            elevation = 18.dp,
            contentAlignment = Alignment.Center,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF1C1C1F)))))
            Text(remember(name) { initials(name) }, style = T.Title2, color = C.Text2)
        }
        Spacer(Modifier.height(7.dp))
        Text(name, style = T.Footnote, color = if (focused) C.Text else C.Text.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!role.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(role, style = T.Caption, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

private val GenrePalette = listOf(
    Color(0xFF1E2A4A) to Color(0xFF151A2A),
    Color(0xFF6B3A12) to Color(0xFF2E1A0C),
    Color(0xFF4A1A2A) to Color(0xFF26101A),
    Color(0xFF14403E) to Color(0xFF0E2422),
    Color(0xFF2E3A1E) to Color(0xFF1A2112),
    Color(0xFF3A1E4A) to Color(0xFF1E1228),
)

/** "Parcourir par genre" tile: a tinted gradient with the genre name at the bottom left. */
@Composable
fun GenreTile(
    name: String,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tag: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    val (top, bottom) = GenrePalette[index.mod(GenrePalette.size)]
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width = 164.dp, height = 75.dp),
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        focusedColor = Color.Transparent,
        focusedContentColor = C.Text,
        focusedScale = 1.07f,
        elevation = 20.dp,
        onFocusChange = { if (it) onFocused?.invoke() },
    ) { f ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset.Infinite)))
        if (icon != null) Icon(icon, null, Modifier.align(Alignment.TopEnd).padding(9.dp).size(14.dp), tint = Color(0x80FFFFFF))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 11.dp, vertical = 9.dp)) {
            if (tag != null) Text(tag, style = T.Micro, color = Color(0x99FFFFFF), maxLines = 1)
            Text(name, style = T.Body.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CardChrome(f)
    }
}

/** "Top 10" lockup: the rank in huge digits, the poster overlapping their right edge. */
@Composable
fun RankedPoster(
    rank: Int,
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val digitsWidth = if (rank >= 10) 128.dp else 72.dp
    Box(modifier.width(digitsWidth + 92.dp).height(159.dp)) {
        Text(
            rank.toString(),
            style = T.Display.copy(fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-6).sp),
            color = Color(0xFF2A2A2E),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.BottomStart).offset(y = 22.dp),
        )
        // Poster without caption: the rank does the talking.
        PosterCard(
            title = title, image = image, onClick = onClick, width = 106.dp, showCaption = false, onFocused = onFocused,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Animated alpha helper for lists that dim when a side panel opens. */
@Composable
fun dimmed(dim: Boolean): Modifier {
    val a by animateFloatAsState(if (dim) 0.3f else 1f, tween(220), label = "dim")
    return Modifier.graphicsLayer { alpha = a }
}
