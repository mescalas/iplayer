package com.iplayer.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (primary) C.Surface3 else C.Surface2,
        focusedScale = 1.06f,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = T.Headline, maxLines = 1)
        }
    }
}

@Composable
fun IconPill(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, tint: Color? = null) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = C.Surface2,
        focusedScale = 1.1f,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Icon(icon, null, Modifier.size(size * 0.45f), tint = if (!focused && tint != null) tint else LocalContentColor.current)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = T.Title3, color = C.Text, modifier = modifier.padding(bottom = 12.dp))
}

@Composable
fun Loading(modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = C.Text, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, style = T.Callout, color = C.Text2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(C.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = C.Text2)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = T.Title3, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = T.Subhead, color = C.Text2, textAlign = TextAlign.Center, modifier = Modifier.width(420.dp))
        }
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = C.Text, track: Color = C.Separator, height: Dp = 4.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height))
                .background(color)
        )
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = C.Surface3, textColor: Color = C.Text) {
    Text(
        text,
        style = T.Caption.copy(fontWeight = FontWeight.SemiBold),
        color = textColor,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Channel logo on a soft tile, with initials as fallback. */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier, padding: Dp = 6.dp) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262A)), contentAlignment = Alignment.Center) {
        Text(
            initials(name),
            style = T.Footnote.copy(fontWeight = FontWeight.Bold),
            color = C.Text2,
            maxLines = 1,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color(0xFF26262A)).padding(padding),
            )
        }
    }
}

private fun initials(name: String): String {
    val words = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** 2:3 poster card used for movies and series. */
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
) {
    Column(if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp),
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.08f,
            elevation = 22.dp,
            onFocusChange = { if (it) onFocused?.invoke() },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    title,
                    style = T.Footnote,
                    color = C.Text2,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (rating > 0f) {
                Badge(
                    "★ " + String.format(java.util.Locale.ROOT, "%.1f", rating),
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    color = Color(0xB3000000),
                )
            }
            if (progress != null && progress > 0f) {
                ProgressLine(
                    progress,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    track = Color(0x66000000),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = T.Footnote, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = T.Caption, color = C.Text3, maxLines = 1)
    }
}

/** 16:9 card used for "continue watching", episodes and channels on the home screen. */
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
) {
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 9f / 16f),
            shape = RoundedCornerShape(12.dp),
            color = C.Surface,
            focusedColor = if (logoMode) Color(0xFF3A3A3E) else C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.07f,
            elevation = 22.dp,
            onFocusChange = { if (it) onFocused?.invoke() },
        ) {
            if (logoMode) {
                ChannelLogo(image, title, Modifier.fillMaxSize(), padding = 22.dp)
            } else {
                Box(Modifier.fillMaxSize().background(C.Surface2))
                if (!image.isNullOrBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (progress != null && progress > 0f) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), track = Color(0x66000000))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = T.Footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = T.Caption, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SideListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) C.Surface2 else Color.Transparent,
        contentColor = if (selected) C.Text else C.Text2,
        focusedScale = 1.03f,
        elevation = 8.dp,
        onFocusChange = { if (it) onFocused?.invoke() },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = T.Callout.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) Text(trailing, style = T.Caption, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 48.dp)
