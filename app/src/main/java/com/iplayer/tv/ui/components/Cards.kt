package com.iplayer.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.Locale

private val CardShape = ArtShape

/** tvOS-like specular highlight that appears on the focused artwork. */
@Composable
fun BoxScope.Sheen(focused: Boolean) {
    val alpha by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "sheen")
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.linearGradient(
                    0f to Color(0x38FFFFFF),
                    0.38f to Color(0x0AFFFFFF),
                    0.55f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
    )
}

/** Title block under an artwork, centered like the credits of a film strip when [centered]. */
@Composable
private fun CardCaption(title: String, meta: String?, focused: Boolean, alwaysVisible: Boolean, lift: Dp, centered: Boolean = false) {
    val alpha by animateFloatAsState(if (focused || alwaysVisible) 1f else 0f, tween(180), label = "caption")
    val shift by animateDpAsState(if (focused) lift else 0.dp, tween(180), label = "shift")
    Column(
        Modifier.fillMaxWidth().offset(y = shift).graphicsLayer { this.alpha = alpha }.padding(top = 10.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            title,
            style = T.Caption.copy(fontWeight = FontWeight.SemiBold, lineHeight = 14.sp),
            color = if (focused) C.Text else C.Text2,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = if (centered) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!meta.isNullOrBlank()) {
            Text(
                meta.uppercase(Locale.FRENCH),
                style = T.Label.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                color = C.Text3,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun ratingMeta(subtitle: String?, rating: Float): String? = listOfNotNull(
    subtitle?.takeIf { it.isNotBlank() },
    if (rating > 0f) "★ " + String.format(Locale.ROOT, "%.1f", rating) else null,
).joinToString("  |  ").ifEmpty { null }

/** Apple TV style portrait lockup: clean artwork, lift + sheen on focus, title revealed under it. */
@Composable
fun PosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 132.dp,
    subtitle: String? = null,
    progress: Float? = null,
    rating: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    alwaysShowTitle: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val display = remember(title) { title.cleanTitle() }
    Column(if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.1f,
            elevation = 28.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2C2C30), Color(0xFF1C1C1F)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    display,
                    style = T.Callout.copy(fontWeight = FontWeight.SemiBold),
                    color = C.Text2,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (progress != null && progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                )
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), color = C.Gold, height = 3.dp, track = Color(0x55FFFFFF))
            }
            ArtFrame(f)
        }
        CardCaption(display, ratingMeta(subtitle, rating), focused, alwaysShowTitle, lift = 8.dp, centered = true)
    }
}

/** 16:9 lockup used for "continue watching", episodes, etc. */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    progress: Float? = null,
    logoMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    badge: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 9f / 16f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.08f,
            elevation = 28.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            if (logoMode) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF34343A), Color(0xFF1E1E22)))))
                ChannelLogo(image, title, Modifier.fillMaxSize(), padding = 24.dp, transparent = true)
            } else {
                Box(Modifier.fillMaxSize().background(C.Surface2))
                if (!image.isNullOrBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (progress != null && progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                )
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), color = C.Gold, height = 3.dp, track = Color(0x55FFFFFF))
            }
            if (badge != null) {
                Badge(badge.uppercase(Locale.FRENCH), Modifier.align(Alignment.TopStart).padding(8.dp), color = Color(0xB3000000))
            }
            ArtFrame(f)
        }
        CardCaption(title.cleanTitle(), subtitle, focused, alwaysVisible = true, lift = 8.dp)
    }
}

/** Live channel tile: logo on a soft gradient with the programme on air and its progress. */
@Composable
fun LiveTile(
    name: String,
    logo: String?,
    program: String?,
    progress: Float?,
    timeRange: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    onFocused: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.width(width).height(width * 9f / 16f),
            shape = CardShape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.08f,
            elevation = 28.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused?.invoke()
            },
        ) { f ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3A40), Color(0xFF1C1C20)))))
            ChannelLogo(
                logo, name,
                Modifier.fillMaxWidth().height(width * 9f / 16f * 0.68f).align(Alignment.TopCenter),
                padding = 20.dp,
                transparent = true,
            )
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(C.Red, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(4.dp))
                Text("EN DIRECT", style = T.Label.copy(fontSize = 9.sp), color = C.Text2)
            }
            if (progress != null) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), color = C.Gold, height = 3.dp, track = Color(0x40FFFFFF))
            }
            ArtFrame(f)
        }
        CardCaption(program ?: name.cleanTitle(), listOfNotNull(name.cleanTitle().takeIf { program != null }, timeRange).joinToString("  ·  ").ifEmpty { null }, focused, true, 8.dp)
    }
}

/** Category selector, drawn as an uppercase tab with a gold underline. */
@Composable
fun CategoryPill(
    text: String,
    tag: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onFocused: (() -> Unit)? = null,
) {
    TabLabel(text = text, selected = selected, onClick = onClick, modifier = modifier, tag = tag, icon = icon, height = 38.dp, onFocused = onFocused)
}
