package com.iplayer.tv.ui.player

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C as MediaC
import androidx.media3.common.Player
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.SubtitleStyle
import com.iplayer.tv.data.cycle
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.Clock
import com.iplayer.tv.ui.components.Eyebrow
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.InfoPills
import com.iplayer.tv.ui.components.LiveBadge
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.leftRight
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanEpisodeSubtitle
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatDuration
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.delay

private val Edge = 22.5.dp
private val GlassPanel = Color(0xE61C1C1F)

/** "📶 Ma source · 1080p · 18 Mbit/s": top-left pill of the player. */
@Composable
private fun SourcePill(player: Player, source: String?, modifier: Modifier = Modifier) {
    val f = (player as? androidx.media3.exoplayer.ExoPlayer)?.videoFormat
    val parts = listOfNotNull(
        source,
        f?.height?.takeIf { it > 0 }?.let { "${it}p" },
        f?.bitrate?.takeIf { it > 0 }?.let { "${(it / 1_000_000f).let { b -> if (b >= 10) b.toInt().toString() else String.format(java.util.Locale.FRANCE, "%.1f", b) }} Mbit/s" },
    )
    if (parts.isEmpty()) return
    Row(
        modifier.clip(CircleShape).background(C.Glass).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(9.dp), tint = C.Text2)
        Spacer(Modifier.width(5.dp))
        Text(parts.joinToString(" · "), style = T.Caption, color = C.Text, maxLines = 1)
    }
}

@Composable
private fun TopScrim() = Box(Modifier.fillMaxWidth().height(90.dp).background(Brush.verticalGradient(listOf(Color(0x990A0A0C), Color.Transparent))))

// ============================================================================ VOD

data class VodControl(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Controls of the design: source pill and clock on top; at the bottom the title, round controls on the right,
 * the scrubber (thumb + time bubble while seeking) and the times ("Fin à 23:57  −1:15:12").
 */
@Composable
fun VodOverlay(
    item: VodItem?,
    player: Player,
    source: String?,
    position: Long,
    duration: Long,
    seekTarget: Long?,
    controls: List<VodControl>,
    controlsRequester: FocusRequester,
    onControlsFocus: (Boolean) -> Unit,
    onControlsUp: () -> Unit,
) {
    if (item == null) return
    val shown = seekTarget ?: position
    Box(Modifier.fillMaxSize()) {
        TopScrim()
        SourcePill(player, source, Modifier.padding(start = Edge, top = 14.dp))
        Clock(Modifier.align(Alignment.TopEnd).padding(end = Edge, top = 16.dp), color = C.Text)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD90A0A0C)))))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Edge, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    val name = remember(item.title) { item.title.mediaName() }
                    val sub = if (item.kind == Kind.EPISODE) cleanEpisodeSubtitle(item.subtitle, item.title) else item.subtitle
                    if (!sub.isNullOrBlank()) {
                        Text(sub, style = T.Footnote.copy(fontWeight = FontWeight.Normal), color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name.title, style = T.Title2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        if (name.techBadges.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            InfoPills(name.techBadges, color = C.Text2)
                        }
                    }
                }
                Row(
                    Modifier
                        .onFocusChanged { onControlsFocus(it.hasFocus) }
                        .controlsUp(onControlsUp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    controls.forEachIndexed { i, c ->
                        RoundControl(c, if (i == 0) Modifier.focusRequester(controlsRequester) else Modifier)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Scrubber(shown, duration, seeking = seekTarget != null)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(shown), style = T.Footnote, color = C.Text)
                Spacer(Modifier.weight(1f))
                if (duration > 0) {
                    Text("Fin à " + formatClock(System.currentTimeMillis() + (duration - shown)), style = T.Footnote.copy(fontWeight = FontWeight.Normal), color = C.Text2)
                    Spacer(Modifier.width(9.dp))
                    Text("−" + formatDuration(duration - shown), style = T.Footnote.copy(fontWeight = FontWeight.SemiBold), color = C.Text)
                }
            }
        }
    }
}

/** Up on the round controls goes back to the scrubber. */
private fun Modifier.controlsUp(onUp: () -> Unit): Modifier = onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) { onUp(); true } else false
}

@Composable
private fun RoundControl(c: VodControl, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = c.onClick,
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = C.GlassStrong,
        focusedScale = 1.12f,
        elevation = 14.dp,
        contentAlignment = Alignment.Center,
    ) { Icon(c.icon, c.label, Modifier.size(14.dp)) }
}

@Composable
private fun Scrubber(position: Long, duration: Long, seeking: Boolean) {
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(if (seeking) 30.dp else 8.dp).onSizeChanged { width = it.width }, contentAlignment = Alignment.BottomStart) {
        ProgressLine(fraction, Modifier.fillMaxWidth().padding(bottom = 2.75.dp), height = 2.5.dp, track = Color(0x4DFFFFFF))
        val x = with(density) { (width * fraction).toDp() }
        Box(Modifier.offset(x = x - 4.dp).size(8.dp).clip(CircleShape).background(Color.White))
        if (seeking) {
            Text(
                formatDuration(position),
                style = T.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = C.Text,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (x - 22.dp).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC1C1C1F))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

// ============================================================================ live banner

/** Bottom glass banner of the live player: logo tile, what is on and its progress, what comes next. */
@Composable
fun LiveOverlay(ch: ChannelEntity?, nowNext: List<ProgramEntity>, favorite: Boolean, showNumber: Boolean, player: Player, source: String?) {
    if (ch == null) return
    val now = System.currentTimeMillis()
    val current = nowNext.firstOrNull { it.startAt <= now && it.endAt > now }
    val next = nowNext.filter { it.startAt >= (current?.endAt ?: now) }.take(2)
    val name = remember(ch.name) { ch.name.mediaName() }
    Box(Modifier.fillMaxSize()) {
        TopScrim()
        SourcePill(player, source, Modifier.padding(start = Edge, top = 14.dp))
        Clock(Modifier.align(Alignment.TopEnd).padding(end = Edge, top = 16.dp), color = C.Text)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x990A0A0C)))))
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Edge, vertical = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(GlassPanel)
                .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(11.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ChannelLogo(ch.logo, ch.name, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)), padding = 5.dp)
                if (showNumber) {
                    Spacer(Modifier.height(5.dp))
                    Text(ch.number.toString(), style = T.Caption, color = C.Text2)
                }
            }
            Divider()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOfNotNull(
                            current?.let { "${formatClock(it.startAt)} – ${formatClock(it.endAt)}" },
                            current?.categories?.split(',', ';', '/')?.firstOrNull { it.isNotBlank() }?.trim()?.replaceFirstChar { it.uppercase() },
                            name.title,
                            name.channelBadge,
                        ).joinToString(" · "),
                        style = T.Caption,
                        color = C.Text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (favorite) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Rounded.Star, null, Modifier.size(10.dp), tint = C.Yellow)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(current?.title ?: name.title, style = T.Title2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val desc = current?.description?.lineSequence()?.firstOrNull { it.isNotBlank() }
                    ?: if (current == null) "Aucune information de programme" else null
                if (desc != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(desc, style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(
                            (now - current.startAt).toFloat() / (current.endAt - current.startAt).coerceAtLeast(1),
                            Modifier.weight(1f),
                            height = 2.5.dp,
                            track = Color(0x33FFFFFF),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(formatMinutes(current.endAt - now) + " restantes", style = T.Caption, color = C.Text2)
                    }
                }
            }
            Divider()
            Column(Modifier.width(190.dp).padding(start = 12.dp, end = 6.dp)) {
                Eyebrow("À suivre")
                Spacer(Modifier.height(5.dp))
                if (next.isEmpty()) Text("—", style = T.Subhead, color = C.Text3)
                next.forEach { p ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(formatClock(p.startAt), style = T.Subhead.copy(fontWeight = FontWeight.SemiBold), color = C.Text2, modifier = Modifier.width(38.dp))
                        Text(p.title, style = T.Subhead.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ExpandLess, null, Modifier.size(9.dp), tint = C.Text3)
                    Text(" Zapper", style = T.Caption, color = C.Text3)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.ViewList, null, Modifier.size(9.dp), tint = C.Text3)
                    Text(" ◀ Chaînes", style = T.Caption, color = C.Text3)
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.width(0.5.dp).height(66.dp).background(Color(0x1FFFFFFF)))

// ============================================================================ channel list

/** Left glass panel listing the channels; the focused one is previewed on the right. */
@Composable
fun ChannelsPanel(
    channels: List<ChannelEntity>,
    current: Int,
    nowPrograms: Map<String, ProgramEntity>,
    showNumbers: Boolean,
    onSelect: (Int) -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    var focused by remember { mutableIntStateOf(current) }
    var focusedNext by remember { mutableStateOf<List<ProgramEntity>>(emptyList()) }
    val repo = LocalContainer.current.repository
    LaunchedEffect(Unit) {
        delay(30)
        focus.tryFocus()
    }
    LaunchedEffect(focused) {
        delay(200)
        focusedNext = channels.getOrNull(focused)?.let { runCatching { repo.nowNext(it) }.getOrDefault(emptyList()) }.orEmpty()
    }
    val now = System.currentTimeMillis()
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xB30A0A0C), Color(0x660A0A0C)))))
    Row(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 24.dp)) {
        Column(
            Modifier
                .width(410.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassPanel)
                .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                .padding(top = 14.dp, start = 9.dp, end = 9.dp),
        ) {
            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chaînes", style = T.Title3.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.weight(1f))
                Text(channels.size.toString(), style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = C.Text3)
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                    val prg = ch.epgId?.let { nowPrograms[it.lowercase()] }
                    val playing = i == current
                    FocusSurface(
                        onClick = { onSelect(i) },
                        modifier = Modifier.fillMaxWidth().height(44.dp).then(if (playing) Modifier.focusRequester(focus) else Modifier),
                        shape = RoundedCornerShape(8.dp),
                        color = if (playing) C.Glass else Color.Transparent,
                        focusedScale = 1.02f,
                        elevation = 10.dp,
                        onFocusChange = { if (it) focused = i },
                    ) {
                        val content = LocalContentColor.current
                        val name = remember(ch.name) { ch.name.mediaName() }
                        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showNumbers) {
                                Text(ch.number.toString(), style = T.Footnote.copy(fontWeight = FontWeight.Normal), color = content.copy(alpha = 0.5f), maxLines = 1, modifier = Modifier.width(30.dp))
                            }
                            ChannelLogo(ch.logo, ch.name, Modifier.width(48.dp).height(30.dp), padding = 3.dp)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(prg?.title ?: name.title, style = T.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                    if (playing) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "● EN COURS",
                                            style = T.Micro,
                                            color = C.OnFocus,
                                            maxLines = 1,
                                            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(C.Text).padding(horizontal = 4.dp, vertical = 1.5.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        listOfNotNull(name.title.takeIf { prg != null }, prg?.let { "${formatClock(it.startAt)} – ${formatClock(it.endAt)}" }).joinToString(" · ").ifEmpty { name.channelBadge ?: "" },
                                        style = T.Caption,
                                        color = content.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (prg != null) {
                                        Spacer(Modifier.width(7.dp))
                                        ProgressLine(
                                            (now - prg.startAt).toFloat() / (prg.endAt - prg.startAt).coerceAtLeast(1),
                                            Modifier.weight(1f),
                                            color = content.copy(alpha = 0.85f),
                                            track = content.copy(alpha = 0.18f),
                                            height = 2.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(30.dp))
        // ---- preview of the focused channel
        val ch = channels.getOrNull(focused)
        if (ch != null) {
            val prg = focusedNext.firstOrNull { it.startAt <= now && it.endAt > now } ?: ch.epgId?.let { nowPrograms[it.lowercase()] }
            val nxt = focusedNext.firstOrNull { it.startAt >= (prg?.endAt ?: now) }
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 70.dp, end = 15.dp), verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.width(320.dp).height(180.dp).clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(listOf(Color(0xFF34343A), Color(0xFF1A1A1E)))),
                ) {
                    ChannelLogo(ch.logo, ch.name, Modifier.fillMaxSize().padding(40.dp), padding = 0.dp, transparent = true)
                    Text(
                        "CHAÎNE · ${ch.name.cleanTitle().uppercase()}",
                        style = T.Micro,
                        color = C.Text,
                        maxLines = 1,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xA6000000)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(prg?.title ?: ch.name.cleanTitle(), style = T.Title2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (prg != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            prg.categories.split(',', ';', '/').firstOrNull { it.isNotBlank() }?.trim()?.replaceFirstChar { it.uppercase() },
                            "${formatClock(prg.startAt)} – ${formatClock(prg.endAt)}",
                            formatMinutes(prg.endAt - now) + " restantes",
                        ).joinToString(" · "),
                        style = T.Footnote.copy(fontWeight = FontWeight.Normal),
                        color = C.Text2,
                        maxLines = 1,
                    )
                    if (!prg.description.isNullOrBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(prg.description, style = T.Callout, color = C.Text.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(320.dp))
                    }
                }
                if (nxt != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("À suivre")
                        Spacer(Modifier.width(6.dp))
                        Text("${formatClock(nxt.startAt)}  ${nxt.title}", style = T.Footnote, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ============================================================================ audio & subtitles

enum class PanelTab(val label: String) { INFO("Infos"), TRACKS("Audio et sous-titres") }

/**
 * Top glass panel of the design: tabs, then three columns (audio language, subtitles, picture & sound).
 * Every change applies at once behind the panel.
 */
@Composable
fun OptionsPanel(
    player: Player,
    initialTab: PanelTab,
    isLive: Boolean,
    favorite: Boolean,
    aspect: AspectMode,
    hasNext: Boolean,
    subtitleStyle: SubtitleStyle,
    infoTitle: String,
    infoMeta: String?,
    infoDescription: String?,
    onAspect: (AspectMode) -> Unit,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    onFavorite: () -> Unit,
    onNext: () -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var version by remember { mutableIntStateOf(0) }
    val audio = remember(version) { Tracks.list(player, MediaC.TRACK_TYPE_AUDIO) }
    val subs = remember(version) { Tracks.list(player, MediaC.TRACK_TYPE_TEXT) }
    val video = remember(version) { Tracks.list(player, MediaC.TRACK_TYPE_VIDEO) }
    val subsOff = remember(version) { Tracks.isDisabled(player, MediaC.TRACK_TYPE_TEXT) || subs.none { it.selected } }
    val videoAuto = remember(version) { !Tracks.hasOverride(player, MediaC.TRACK_TYPE_VIDEO) }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(30)
        tabFocus.tryFocus()
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { version++ }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x990A0A0C), Color(0x330A0A0C)))))
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .padding(horizontal = Edge, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassPanel)
                .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.clip(CircleShape).background(C.Glass).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PanelTab.entries.forEach { t ->
                    val sel = t == tab
                    FocusSurface(
                        onClick = { tab = t },
                        modifier = Modifier.height(26.dp).then(if (sel) Modifier.focusRequester(tabFocus) else Modifier),
                        shape = CircleShape,
                        color = if (sel) C.Focus else Color.Transparent,
                        contentColor = if (sel) C.OnFocus else C.Text.copy(alpha = 0.75f),
                        focusedScale = 1.06f,
                        contentAlignment = Alignment.Center,
                        onFocusChange = { if (it) tab = t },
                    ) {
                        Text(t.label, style = T.Subhead.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium), modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tab == PanelTab.INFO) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(infoTitle, style = T.Title2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!infoMeta.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(infoMeta, style = T.Footnote.copy(fontWeight = FontWeight.Normal), color = C.Text2, maxLines = 1)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(infoDescription?.takeIf { it.isNotBlank() } ?: "Pas de résumé disponible.", style = T.Callout, color = C.Text.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (hasNext) PanelRow("Épisode suivant", icon = Icons.Rounded.SkipNext, onClick = onNext)
                        if (isLive) PanelRow(if (favorite) "Retirer des favoris" else "Ajouter aux favoris", icon = Icons.Rounded.Star, onClick = onFavorite)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    PanelColumn("Langue audio") {
                        if (audio.isEmpty()) item { EmptyRow("Piste unique") }
                        itemsIndexed(audio) { _, t -> PanelRow(t.label, selected = t.selected) { Tracks.select(player, t); version++ } }
                    }
                    PanelColumn("Sous-titres") {
                        item { PanelRow("Désactivés", selected = subsOff) { Tracks.disable(player, MediaC.TRACK_TYPE_TEXT); version++ } }
                        itemsIndexed(subs) { _, t -> PanelRow(t.label, selected = t.selected && !subsOff) { Tracks.select(player, t); version++ } }
                    }
                    PanelColumn("Image et son") {
                        item {
                            StepRow("Format d'image", aspect.label, Icons.Rounded.AspectRatio) { onAspect(aspect.cycle(it)) }
                        }
                        if (video.size > 1) item {
                            val options = listOf<TrackOption?>(null) + video
                            val currentIdx = if (videoAuto) 0 else options.indexOfFirst { it?.selected == true }.coerceAtLeast(0)
                            StepRow("Qualité vidéo", options[currentIdx]?.label ?: "Automatique", Icons.Rounded.GraphicEq) { d ->
                                val target = options[(currentIdx + d).mod(options.size)]
                                if (target == null) Tracks.auto(player, MediaC.TRACK_TYPE_VIDEO) else Tracks.select(player, target)
                                version++
                            }
                        }
                        item { StepRow("Taille des sous-titres", subtitleStyle.size.label, Icons.Rounded.Subtitles) { onSubtitleStyle(subtitleStyle.copy(size = subtitleStyle.size.cycle(it))) } }
                        item { StepRow("Couleur", subtitleStyle.color.label) { onSubtitleStyle(subtitleStyle.copy(color = subtitleStyle.color.cycle(it))) } }
                        item { StepRow("Fond", subtitleStyle.background.label) { onSubtitleStyle(subtitleStyle.copy(background = subtitleStyle.background.cycle(it))) } }
                        item { StepRow("Police", subtitleStyle.font.label) { onSubtitleStyle(subtitleStyle.copy(font = subtitleStyle.font.cycle(it))) } }
                        item { StepRow("Position", subtitleStyle.position.label) { onSubtitleStyle(subtitleStyle.copy(position = subtitleStyle.position.cycle(it))) } }
                        if (subtitleStyle != SubtitleStyle()) item { PanelRow("Style des sous-titres par défaut") { onSubtitleStyle(SubtitleStyle()) } }
                        if (hasNext) item { PanelRow("Épisode suivant", icon = Icons.Rounded.SkipNext, onClick = onNext) }
                        if (isLive) item { PanelRow(if (favorite) "Retirer des favoris" else "Ajouter aux favoris", icon = Icons.Rounded.Star, onClick = onFavorite) }
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.KeyboardReturn, null, Modifier.size(9.dp), tint = C.Text3)
                Spacer(Modifier.width(4.dp))
                Text("Retour pour fermer", style = T.Caption, color = C.Text3)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PanelColumn(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
        Eyebrow(title, Modifier.padding(start = 10.dp, bottom = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 6.dp), content = content)
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, style = T.Callout, color = C.Text3, modifier = Modifier.padding(horizontal = 25.dp, vertical = 8.dp))
}

/** Track row: check mark on the selected one, main label and the rest of it ("Dolby Digital 5.1") under it. */
@Composable
private fun PanelRow(label: String, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val main = label.substringBefore(" · ")
    val rest = label.substringAfter(" · ", "").ifBlank { null }
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (rest != null) 40.dp else 32.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        focusedScale = 1.03f,
        elevation = 12.dp,
    ) {
        val content = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(15.dp), contentAlignment = Alignment.CenterStart) {
                if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(10.dp))
                else if (icon != null) Icon(icon, null, Modifier.size(10.dp), tint = content.copy(alpha = 0.6f))
            }
            Column {
                Text(main, style = T.Headline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (rest != null) Text(rest, style = T.Caption, color = content.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A value changed with ◀ ▶ (or OK for the next value). */
@Composable
private fun StepRow(label: String, value: String, icon: ImageVector? = null, onStep: (Int) -> Unit) {
    FocusSurface(
        onClick = { onStep(1) },
        modifier = Modifier.fillMaxWidth().height(40.dp).leftRight(onStep),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        focusedScale = 1.03f,
        elevation = 12.dp,
    ) { focused ->
        val content = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(15.dp), contentAlignment = Alignment.CenterStart) {
                if (icon != null) Icon(icon, null, Modifier.size(10.dp), tint = content.copy(alpha = 0.6f))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = T.Headline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((if (focused) "◀  " else "") + value + (if (focused) "  ▶" else ""), style = T.Caption, color = content.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

/** Round controls offered by the VOD overlay. */
fun vodControls(hasNext: Boolean, onTracks: () -> Unit, onAspect: () -> Unit, onNext: () -> Unit, onInfo: () -> Unit): List<VodControl> = buildList {
    add(VodControl(Icons.Rounded.Subtitles, "Sous-titres", onTracks))
    add(VodControl(Icons.Rounded.GraphicEq, "Audio", onTracks))
    add(VodControl(Icons.Rounded.AspectRatio, "Format d'image", onAspect))
    if (hasNext) add(VodControl(Icons.Rounded.SkipNext, "Épisode suivant", onNext))
    add(VodControl(Icons.AutoMirrored.Rounded.FormatListBulleted, "Infos", onInfo))
}
