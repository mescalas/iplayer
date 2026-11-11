package com.iplayer.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import coil3.compose.AsyncImage
import com.iplayer.tv.R
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatClock
import kotlinx.coroutines.delay

/** Horizontal margin of every screen (80 px in the design). */
val ScreenEdge = 40.dp
val ScreenPadding = PaddingValues(horizontal = ScreenEdge)

/**
 * Pill button of the design. Primary is the white "Lire" button; the others are glass. Focus turns any
 * button white and lifts it.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = CircleShape,
        color = if (primary) Color(0xE6F5F5F7) else C.GlassStrong,
        contentColor = if (primary) C.OnFocus else C.Text,
        focusedScale = 1.07f,
        elevation = 16.dp,
        onFocusChange = onFocusChange,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = if (primary) 20.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = T.Label, maxLines = 1)
        }
    }
}

/** Round glass button ("+", "✓", "👍" next to the hero buttons). */
@Composable
fun IconPill(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = C.GlassStrong,
        focusedScale = 1.1f,
        elevation = 14.dp,
        onFocusChange = onFocusChange,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Icon(icon, null, Modifier.size(size * 0.44f), tint = if (!focused && tint != null) tint else LocalContentColor.current)
    }
}

/** Glass chip; the selected one is white like the selected tab. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
    icon: ImageVector? = null,
    onFocused: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = CircleShape,
        color = if (selected) C.Focus else C.Glass,
        contentColor = if (selected) C.OnFocus else C.Text.copy(alpha = 0.8f),
        focusedScale = 1.08f,
        elevation = 12.dp,
        contentAlignment = Alignment.Center,
        onFocusChange = { if (it) onFocused?.invoke() },
    ) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(11.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = T.Subhead.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
            if (count != null) {
                Spacer(Modifier.width(5.dp))
                Text(count, style = T.Subhead, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

/** Shelf title, with an optional quieter note after it ("Cette semaine", "248 chaînes disponibles"). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, note: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = T.Title3, color = C.Text, maxLines = 1, modifier = Modifier.alignByBaseline())
        if (note != null) {
            Spacer(Modifier.width(8.dp))
            Text(note, style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = C.Text3, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Small tracked uppercase label ("RÉALISATION", "COMPTE", "À SUIVRE"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = C.Text3) {
    Text(text.uppercase(), style = T.Micro.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp), color = color, maxLines = 1, modifier = modifier)
}

@Composable
fun Loading(modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = C.Text, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        if (message != null) {
            Spacer(Modifier.height(14.dp))
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
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = C.Text2)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = T.Title3, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = T.Callout, color = C.Text2, textAlign = TextAlign.Center, modifier = Modifier.width(400.dp))
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = C.Text, track: Color = Color(0x4DFFFFFF), height: Dp = 3.dp) {
    // NaN (a 0/0 duration) would make the layout throw.
    val fraction = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(height))
                .background(color)
        )
    }
}

/** Filled tag ("NOUVEAU", channel name on a tile). */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Color(0xA6000000), textColor: Color = C.Text) {
    Text(
        text,
        style = T.Micro,
        color = textColor,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(3.dp)).background(color).padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** Red "EN DIRECT" badge with its white dot. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, text: String = "EN DIRECT") {
    Row(
        modifier.clip(RoundedCornerShape(3.5.dp)).background(C.Red).padding(horizontal = 5.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(3.5.dp))
        Text(text, style = T.Micro.copy(fontSize = T.Micro.fontSize * 0.88f, letterSpacing = 0.5.sp), color = Color.White, maxLines = 1)
    }
}

/** Outlined pill for a detail pulled out of a title ("4K", "HDR", "12+"). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = 0.7f)) {
    Text(
        text,
        style = T.Micro.copy(letterSpacing = 0.sp),
        color = color,
        maxLines = 1,
        modifier = modifier
            .border(0.75.dp, color, RoundedCornerShape(2.5.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun InfoPills(items: List<String>, modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = 0.7f)) {
    if (items.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { InfoPill(it, color = color) }
    }
}

/** "2025  Science-fiction  2 h 28  [12+] [4K]": the meta line under every title. */
@Composable
fun MetaLine(parts: List<String>, badges: List<String> = emptyList(), modifier: Modifier = Modifier, color: Color = C.Text2, separator: String = "  ") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val text = parts.filter { it.isNotBlank() }.joinToString(separator)
        if (text.isNotEmpty()) {
            Text(text, style = T.Subhead, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        }
        if (badges.isNotEmpty()) {
            if (text.isNotEmpty()) Spacer(Modifier.width(7.dp))
            InfoPills(badges, color = color)
        }
    }
}

/** Channel logo on a soft tile, with initials as fallback. */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier, padding: Dp = 5.dp, transparent: Boolean = false) {
    var loaded by remember(url) { mutableStateOf(false) }
    val bg = if (transparent) Color.Transparent else Color(0xFF26262A)
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(bg), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Text(
                remember(name) { initials(name.cleanTitle()) },
                style = (if (transparent) T.Title2 else T.Caption).copy(fontWeight = FontWeight.ExtraBold),
                color = C.Text,
                maxLines = 1,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

private val NOT_ALNUM = Regex("[^\\p{L}\\p{N} ]")

fun initials(name: String): String {
    val words = name.replace(NOT_ALNUM, " ").split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** Row of a side list (categories). */
@Composable
fun SideListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
    onFocused: (() -> Unit)? = null,
    tag: String? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(38.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) C.GlassStrong else Color.Transparent,
        contentColor = if (selected) C.Text else C.Text2,
        focusedScale = 1.03f,
        elevation = 8.dp,
        onFocusChange = { if (it) onFocused?.invoke() },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(9.dp))
            }
            if (tag != null) {
                Text(tag, style = T.Micro, color = LocalContentColor.current.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = T.Headline.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) Text(trailing, style = T.Caption, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}

/** Settings row of the design: icon, label, value, chevron; white when focused. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    chevron: Boolean = true,
    valueColor: Color? = null,
    onFocused: (() -> Unit)? = null,
    onLeftRight: ((Int) -> Unit)? = null,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 46.dp else 40.dp)
            .then(if (onLeftRight != null) Modifier.leftRight(onLeftRight) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = C.Row,
        focusedScale = 1.02f,
        elevation = 12.dp,
        onFocusChange = { if (it) onFocused?.invoke() },
    ) { focused ->
        val content = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = if (focused) content else C.Text2)
                Spacer(Modifier.width(11.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = T.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = T.Caption, color = content.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing(focused)
            }
            if (value != null) {
                Spacer(Modifier.width(10.dp))
                if (onLeftRight != null && focused) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(14.dp), tint = content.copy(alpha = 0.5f))
                Text(
                    value,
                    style = T.Callout,
                    color = if (focused) content.copy(alpha = 0.65f) else valueColor ?: C.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
            if (chevron || (onLeftRight != null && focused)) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(13.dp), tint = if (focused) content.copy(alpha = 0.5f) else C.Text3)
            }
        }
    }
}

/** ◀ ▶ step a value (OK keeps its own action). */
fun Modifier.leftRight(onStep: (Int) -> Unit): Modifier = onPreviewKeyEvent { ev ->
    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (ev.key) {
        Key.DirectionLeft -> { onStep(-1); true }
        Key.DirectionRight -> { onStep(1); true }
        else -> false
    }
}

/** iOS-like switch of the design (green when on). */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = 26.dp, height = 15.dp).clip(CircleShape).background(if (on) C.Green else Color(0x40FFFFFF)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(2.dp).size(11.dp).clip(CircleShape).background(Color.White))
    }
}

/** The "lucarne" logotype (arch + name). Height in dp; width follows. */
@Composable
fun LogoLockup(modifier: Modifier = Modifier, height: Dp = 18.dp) {
    Image(painterResource(R.drawable.logo_lockup), "Lucarne", modifier.height(height).width(height * 146f / 36f))
}

/** The arch symbol alone. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, height: Dp = 50.dp) {
    Image(painterResource(R.drawable.logo_mark), null, modifier.height(height).width(height * 0.77f))
}

/** Wall clock, refreshed on the minute. */
@Composable
fun Clock(modifier: Modifier = Modifier, color: Color = C.Text2) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    Text(formatClock(now), style = T.Headline, color = color, modifier = modifier)
}

/** "‹ Films" back pill at the top left of full-screen pages. */
@Composable
fun BackPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = CircleShape,
        color = C.Glass,
        focusedScale = 1.06f,
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(start = 7.dp, end = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, style = T.Subhead, maxLines = 1)
        }
    }
}

/** Large rounded tile with an icon, used as the illustration of a page (settings, sources). */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 75.dp, light: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(
                if (light) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color(0xFFE5E5EA)))
                else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF1C1C1F)))
            )
            .border(0.75.dp, Color(0x1FFFFFFF), RoundedCornerShape(size * 0.27f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.42f), tint = if (light) C.OnFocus else C.Text)
    }
}
