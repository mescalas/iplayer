package com.iplayer.tv.ui.live

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iplayer.tv.data.CAT_FAVORITES
import com.iplayer.tv.data.CAT_RECENT
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.SubtitleLayer
import com.iplayer.tv.player.VideoSurface
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.player.resizeMode
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.InfoPill
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.SideListItem
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.delay

@Composable
fun LiveScreen() {
    val vm = appViewModel { LiveViewModel(it) }
    val container = LocalContainer.current
    val nav = LocalNav.current
    val shell = LocalShell.current
    val settings by container.settings.flow.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selected.collectAsState()
    val channels by vm.channels.collectAsState()
    val now by vm.nowPrograms.collectAsState()
    val favorites by vm.favoriteKeys.collectAsState()
    val info by vm.info.collectAsState()
    val nowPlaying by container.player.nowPlaying.collectAsState()
    val livePlayer by container.player.playerFlow.collectAsState()
    val listState = rememberLazyListState()
    val restoreRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var restoreTargetId by remember { mutableStateOf(-1L) }

    // Stop the preview when the user leaves the TV tab (but not when opening the full-screen player).
    DisposableEffect(Unit) {
        onDispose {
            if (!nav.inPlayer && container.player.nowPlaying.value?.isLive == true) container.player.stop()
        }
    }

    // Coming back from the full-screen player: focus the channel that is playing (the user may have zapped).
    LaunchedEffect(channels) {
        val list = channels ?: return@LaunchedEffect
        if (!nav.restoreFocus) return@LaunchedEffect
        val key = container.player.nowPlaying.value?.key
        var idx = if (key != null) list.indexOfFirst { it.itemKey == key } else -1
        if (idx < 0) idx = list.indexOfFirst { it.id == vm.lastFocusedId }
        if (idx < 0) {
            nav.restoreFocus = false
            shell.focusTabs()
            return@LaunchedEffect
        }
        restoreTargetId = list[idx].id
        val first = listState.firstVisibleItemIndex
        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(5)
        if (idx < first || idx >= first + visible - 1) listState.scrollToItem((idx - 2).coerceAtLeast(0))
        delay(40)
        if (!restoreRequester.tryFocus()) {
            delay(120)
            if (!restoreRequester.tryFocus()) shell.focusTabs()
        }
        nav.restoreFocus = false
    }

    val p = playlist ?: return
    val selectedName = categories.firstOrNull { it.id == selected }?.name ?: ""

    Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 40.dp, top = 6.dp)) {
        // ---- categories
        LazyColumn(
            Modifier.width(214.dp).fillMaxHeight().focusRestorer(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                SideListItem(
                    text = cat.name,
                    selected = cat.id == selected,
                    icon = cat.icon,
                    tag = cat.tag,
                    onClick = {
                        vm.select(cat.id)
                        focusManager.moveFocus(FocusDirection.Right)
                    },
                    onFocused = { vm.onCategoryFocused(cat.id) },
                )
            }
        }
        Spacer(Modifier.width(20.dp))

        // ---- channels
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.padding(start = 6.dp, bottom = 8.dp, top = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text(selectedName, style = T.Title3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                channels?.let { Text("  ${it.size}", style = T.Footnote, color = C.Text3) }
            }
            val list = channels
            when {
                list == null -> Loading(Modifier.fillMaxSize())
                list.isEmpty() -> EmptyState(
                    if (selected == CAT_FAVORITES) Icons.Rounded.Star else if (selected == CAT_RECENT) Icons.Rounded.History else Icons.Rounded.LiveTv,
                    when (selected) {
                        CAT_FAVORITES -> "Aucun favori"
                        CAT_RECENT -> "Aucune chaîne récente"
                        else -> "Aucune chaîne"
                    },
                    if (selected == CAT_FAVORITES) "Maintenez OK sur une chaîne pour l'ajouter aux favoris." else null,
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(list, key = { it.id }) { ch ->
                        val program = ch.epgId?.let { now[it.lowercase()] }
                        ChannelRow(
                            ch = ch,
                            program = program,
                            favorite = ch.itemKey in favorites,
                            playing = nowPlaying?.key == ch.itemKey,
                            showNumber = settings.showChannelNumbers,
                            modifier = if (ch.id == restoreTargetId) Modifier.focusRequester(restoreRequester) else Modifier,
                            onFocused = { vm.focus(ch) },
                            onClick = {
                                val idx = list.indexOf(ch)
                                nav.playLive(p, list, idx, keepAliveOnExit = settings.livePreview)
                            },
                            onLongClick = {
                                vm.toggleFavorite(ch) { fav ->
                                    shell.toast(if (fav) "Ajoutée aux favoris" else "Retirée des favoris")
                                }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))

        // ---- preview & guide
        Column(Modifier.width(372.dp).fillMaxHeight().padding(top = 6.dp)) {
            val focusedCh = info?.channel
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(C.Surface),
                contentAlignment = Alignment.Center,
            ) {
                val np = nowPlaying
                if (np != null && np.isLive && settings.livePreview && livePlayer != null) {
                    VideoSurface(livePlayer, settings.aspectMode.resizeMode(), Modifier.fillMaxSize())
                    SubtitleLayer(livePlayer, settings.subtitleStyle)
                } else if (focusedCh != null) {
                    ChannelLogo(focusedCh.logo, focusedCh.name, Modifier.fillMaxSize(), padding = 48.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (focusedCh != null) {
                GuidePanel(focusedCh, info?.schedule.orEmpty(), onPlayCatchup = { prg ->
                    val url = container.repository.catchupUrl(p, focusedCh, prg) ?: return@GuidePanel
                    nav.play(
                        PlayRequest.Vod(
                            p,
                            listOf(
                                VodItem(
                                    kind = Kind.LIVE, key = "catchup:${focusedCh.itemKey}:${prg.startAt}", title = prg.title,
                                    subtitle = "${focusedCh.name.cleanTitle()} · ${formatClock(prg.startAt)}", image = focusedCh.logo,
                                    url = url, trackHistory = false, description = prg.description,
                                )
                            ),
                            0,
                        )
                    )
                }, onPlayLive = {
                    val l = channels ?: return@GuidePanel
                    nav.playLive(p, l, l.indexOfFirst { it.id == focusedCh.id }.coerceAtLeast(0), settings.livePreview)
                })
            }
        }
    }
}

@Composable
private fun ChannelRow(
    ch: ChannelEntity,
    program: ProgramEntity?,
    favorite: Boolean,
    playing: Boolean,
    showNumber: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (playing) C.Surface else Color.Transparent,
        focusedScale = 1.025f,
        elevation = 10.dp,
        onFocusChange = { if (it) onFocused() },
    ) {
        val content = LocalContentColor.current
        val name = remember(ch.name) { ch.name.mediaName() }
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showNumber) {
                Text(
                    ch.number.toString(),
                    style = T.Footnote,
                    color = content.copy(alpha = 0.45f),
                    maxLines = 1,
                    modifier = Modifier.width(38.dp),
                )
            }
            ChannelLogo(ch.logo, ch.name, Modifier.width(62.dp).height(40.dp), padding = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name.title,
                        style = T.Headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false),
                    )
                    name.channelBadge?.let {
                        Spacer(Modifier.width(7.dp))
                        InfoPill(it, color = content.copy(alpha = 0.5f))
                    }
                    if (favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = C.Yellow)
                    }
                    if (ch.catchupDays > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Replay, null, Modifier.size(14.dp), tint = content.copy(alpha = 0.5f))
                    }
                }
                if (program != null) {
                    val now = System.currentTimeMillis()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            program.title,
                            style = T.Subhead,
                            color = content.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, false),
                        )
                        Spacer(Modifier.width(8.dp))
                        ProgressLine(
                            (now - program.startAt).toFloat() / (program.endAt - program.startAt).coerceAtLeast(1),
                            Modifier.width(44.dp),
                            color = content.copy(alpha = 0.75f),
                            track = content.copy(alpha = 0.18f),
                            height = 3.dp,
                        )
                    }
                }
            }
            if (playing) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp), tint = if (content == C.OnFocus) C.OnFocus else C.Accent)
            }
        }
    }
}

@Composable
private fun GuidePanel(
    ch: ChannelEntity,
    schedule: List<ProgramEntity>,
    onPlayCatchup: (ProgramEntity) -> Unit,
    onPlayLive: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val currentIdx = schedule.indexOfFirst { it.startAt <= now && it.endAt > now }
    val current = schedule.getOrNull(currentIdx)
    Text(ch.name.cleanTitle(), style = T.Title3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    if (current == null) {
        Text(
            if (schedule.isEmpty()) "Aucune information de programme" else "Programme indisponible",
            style = T.Subhead,
            color = C.Text3,
        )
        return
    }
    Text(current.title, style = T.Headline, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${formatClock(current.startAt)} – ${formatClock(current.endAt)}", style = T.Footnote, color = C.Text2)
        Spacer(Modifier.width(10.dp))
        ProgressLine(
            (now - current.startAt).toFloat() / (current.endAt - current.startAt).coerceAtLeast(1),
            Modifier.width(70.dp),
            height = 3.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text("Reste ${formatMinutes(current.endAt - now)}", style = T.Footnote, color = C.Text3)
    }
    if (!current.description.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(current.description, style = T.Subhead, color = C.Text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(12.dp))
    val catchup = ch.catchupDays > 0
    val entries = if (catchup) schedule else schedule.drop(currentIdx + 1)
    if (entries.isEmpty()) return
    Text(if (catchup) "Guide & replay" else "À suivre", style = T.Footnote.copy(fontWeight = FontWeight.SemiBold), color = C.Text3)
    Spacer(Modifier.height(6.dp))
    if (!catchup) {
        entries.take(6).forEach { prg ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(formatClock(prg.startAt), style = T.Footnote, color = C.Text3, modifier = Modifier.width(48.dp))
                Text(prg.title, style = T.Subhead, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0))
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().focusRestorer(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(entries, key = { it.id }) { prg ->
                val isPast = prg.endAt <= now
                val isNow = prg.startAt <= now && prg.endAt > now
                val isFuture = prg.startAt > now
                FocusSurface(
                    onClick = { if (isPast) onPlayCatchup(prg) else if (isNow) onPlayLive() },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isNow) C.Surface else Color.Transparent,
                    contentColor = if (isFuture) C.Text3 else C.Text2,
                    focusedScale = 1.02f,
                    elevation = 6.dp,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatClock(prg.startAt), style = T.Footnote, modifier = Modifier.width(46.dp))
                        Text(prg.title, style = T.Subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (isPast) Icon(Icons.Rounded.Replay, null, Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
