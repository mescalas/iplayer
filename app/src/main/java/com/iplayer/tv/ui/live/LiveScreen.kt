package com.iplayer.tv.ui.live

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.CAT_FAVORITES
import com.iplayer.tv.data.CAT_RECENT
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.SubtitleLayer
import com.iplayer.tv.player.VideoSurface
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.player.resizeMode
import com.iplayer.tv.ui.AppNav
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.Chip
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.IconPill
import com.iplayer.tv.ui.components.LiveBadge
import com.iplayer.tv.ui.components.LiveTile
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.ScreenAhead
import com.iplayer.tv.ui.components.ScreenEdge
import com.iplayer.tv.ui.components.SectionTitle
import com.iplayer.tv.ui.components.ShelfAhead
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.delay

/**
 * Télévision: the focused channel large (live preview when it is the one playing), the categories as chips,
 * the channels on air now, and the programme grid of the category. The full-screen guide opens from the grid.
 */
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
    val loaded by vm.loaded.collectAsState()
    val now by vm.nowPrograms.collectAsState()
    val favorites by vm.favoriteKeys.collectAsState()
    val info by vm.info.collectAsState()
    val nowPlaying by container.player.nowPlaying.collectAsState()
    val livePlayer by container.player.playerFlow.collectAsState()
    val channels = loaded?.second
    val listState = rememberLazyListState(cacheWindow = ShelfAhead)
    val tilesState = rememberLazyListState(cacheWindow = ScreenAhead)
    val focusManager = LocalFocusManager.current
    val restoreRequester = remember { FocusRequester() }
    val guideRequester = remember { FocusRequester() }
    var restoreTargetId by remember { mutableStateOf(-1L) }
    var guideOpen by remember { mutableStateOf(false) }
    val toast = { m: String -> shell.toast(m) }

    // Stop the preview when the user leaves the TV tab (but not when opening the full-screen player).
    DisposableEffect(Unit) {
        onDispose {
            if (!nav.inPlayer && container.player.nowPlaying.value?.isLive == true) container.player.stop()
            shell.contentScrolled.value = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 }.collect { shell.contentScrolled.value = it }
    }
    LaunchedEffect(shell.barFocused.value) {
        if (shell.barFocused.value && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }

    // The hero needs a channel before anything is focused: the one playing, else the first of the list.
    LaunchedEffect(channels) {
        val list = channels ?: return@LaunchedEffect
        if (list.isEmpty()) return@LaunchedEffect
        if (info == null || list.none { it.id == vm.lastFocusedId }) {
            val key = container.player.nowPlaying.value?.key
            val ch = list.firstOrNull { it.itemKey == key } ?: list.firstOrNull { it.id == vm.lastFocusedId } ?: list.first()
            vm.focus(ch)
        }
    }

    // Coming back from the full-screen player: focus the channel that is playing (the user may have zapped).
    LaunchedEffect(channels) {
        val list = channels ?: return@LaunchedEffect
        if (!nav.restoreFocus) return@LaunchedEffect
        val key = container.player.nowPlaying.value?.key
        var idx = if (key != null) list.indexOfFirst { it.itemKey == key } else -1
        if (idx < 0) idx = list.indexOfFirst { it.id == vm.lastFocusedId }
        var ok = false
        if (idx >= 0) {
            restoreTargetId = list[idx].id
            listState.scrollToItem(2)
            tilesState.scrollToItem((idx - 1).coerceAtLeast(0))
            delay(60)
            ok = restoreRequester.tryFocus() || run { delay(120); restoreRequester.tryFocus() }
        }
        if (!ok) shell.focusTabs()
        nav.restoreFocus = false
    }

    val p = playlist ?: return
    val selectedName = categories.firstOrNull { it.id == selected }?.name ?: ""

    fun watch(ch: ChannelEntity) {
        val l = channels ?: return
        vm.focus(ch)
        nav.playLive(p, l, l.indexOfFirst { it.id == ch.id }.coerceAtLeast(0), keepAliveOnExit = settings.livePreview)
    }

    fun toggleFavorite(ch: ChannelEntity) {
        vm.toggleFavorite(ch) { fav -> shell.toast(if (fav) "Ajoutée aux favoris" else "Retirée des favoris") }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(shell.contentEntry)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || ev.key != Key.DirectionUp) return@onPreviewKeyEvent false
                if (!focusManager.moveFocus(FocusDirection.Up)) shell.focusTabs()
                true
            },
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item("hero") {
            val ch = info?.channel
            val np = nowPlaying
            // Not while the full-screen player opens: this view would grab the video surface, then release it on
            // leaving, and the player would go on with sound but no picture.
            val showVideo = ch != null && np != null && np.isLive && np.key == ch.itemKey && settings.livePreview && livePlayer != null && !nav.inPlayer
            LiveHero(
                ch = ch,
                schedule = info?.schedule.orEmpty(),
                favorite = ch != null && ch.itemKey in favorites,
                showNumber = settings.showChannelNumbers,
                video = {
                    if (showVideo) {
                        // Cropped to fill the hero (a letterboxed 16:9 picture in a wide banner looked broken).
                        VideoSurface(livePlayer, AspectMode.ZOOM.resizeMode(), Modifier.fillMaxSize())
                        SubtitleLayer(livePlayer, settings.subtitleStyle)
                    }
                },
                showVideo = showVideo,
                onWatch = { ch?.let { watch(it) } },
                onFromStart = { prg ->
                    val c = ch ?: return@LiveHero
                    if (!playCatchup(nav, container.repository.catchupUrl(p, c, prg), p, c, prg)) toast("Pas de replay pour ce programme")
                },
                onGuide = { if (!channels.isNullOrEmpty()) guideOpen = true },
                onFavorite = { ch?.let { toggleFavorite(it) } },
            )
        }
        item("chips") {
            val selectedChip = remember { FocusRequester() }
            val chipsState = rememberLazyListState()
            LazyRow(
                state = chipsState,
                // Entering the row lands on the selected category, not on the first chip (focusing selects).
                modifier = Modifier.fillMaxWidth().focusProperties {
                    enter = {
                        if (chipsState.layoutInfo.visibleItemsInfo.any { it.key == selected }) selectedChip else FocusRequester.Default
                    }
                },
                contentPadding = PaddingValues(horizontal = ScreenEdge, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(categories, key = { it.id }) { cat ->
                    Chip(
                        text = cat.name,
                        selected = cat.id == selected,
                        icon = cat.icon,
                        count = cat.tag,
                        modifier = if (cat.id == selected) Modifier.focusRequester(selectedChip) else Modifier,
                        onClick = {
                            vm.select(cat.id)
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                        onFocused = { vm.onCategoryFocused(cat.id) },
                    )
                }
            }
        }
        item("now") {
            Column {
                SectionTitle(
                    "En ce moment",
                    Modifier.padding(horizontal = ScreenEdge),
                    note = channels?.let { "${it.size} chaîne${if (it.size > 1) "s" else ""} · $selectedName" },
                )
                val list = channels
                when {
                    list == null -> Loading(Modifier.fillMaxWidth().height(150.dp))
                    list.isEmpty() -> EmptyState(
                        when (selected) {
                            CAT_FAVORITES -> Icons.Rounded.Star
                            CAT_RECENT -> Icons.Rounded.History
                            else -> Icons.Rounded.LiveTv
                        },
                        when (selected) {
                            CAT_FAVORITES -> "Aucun favori"
                            CAT_RECENT -> "Aucune chaîne récente"
                            else -> "Aucune chaîne"
                        },
                        if (selected == CAT_FAVORITES) "Maintenez OK sur une chaîne pour l'ajouter aux favoris." else null,
                        Modifier.fillMaxWidth(),
                    )
                    else -> LazyRow(
                        state = tilesState,
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(start = ScreenEdge, end = ScreenEdge, top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(list, key = { _, c -> c.id }) { _, ch ->
                            val prg = ch.epgId?.let { now[it.lowercase()] }
                            val t = System.currentTimeMillis()
                            LiveTile(
                                name = if (settings.showChannelNumbers) "${ch.number}  ${ch.name.cleanTitle()}" else ch.name,
                                logo = ch.logo,
                                program = prg?.title,
                                progress = prg?.let { (t - it.startAt).toFloat() / (it.endAt - it.startAt).coerceAtLeast(1) },
                                timeRange = prg?.let { "${formatClock(it.startAt)} – ${formatClock(it.endAt)}" },
                                category = prg?.categories?.split(',', ';', '/')?.firstOrNull { it.isNotBlank() }?.trim()?.replaceFirstChar { it.uppercase() },
                                modifier = if (ch.id == restoreTargetId) Modifier.focusRequester(restoreRequester) else Modifier,
                                onFocused = { vm.focus(ch) },
                                onClick = { watch(ch) },
                                onLongClick = { toggleFavorite(ch) },
                            )
                        }
                    }
                }
            }
        }
        val guideChannels = channels
        if (!guideChannels.isNullOrEmpty()) item("guide-$selected") {
            Column(Modifier.padding(horizontal = ScreenEdge)) {
                SectionTitle(
                    "Guide des programmes",
                    note = tonightLabel(System.currentTimeMillis()),
                    trailing = {
                        PillButton("Programme complet", onClick = { guideOpen = true }, icon = Icons.Rounded.CalendarMonth)
                    },
                )
                val state = rememberGuideState(guideChannels, info?.channel?.id ?: vm.lastFocusedId, spanMin = 150, visibleRows = 6)
                var gridFocused by remember { mutableStateOf(false) }
                GuideGrid(
                    state = state,
                    showNumber = settings.showChannelNumbers,
                    exitUp = true,
                    focusRequester = guideRequester,
                    onFocusChanged = {
                        gridFocused = it
                        if (it) vm.focus(state.channel)
                    },
                    onPlayLive = { idx -> watch(guideChannels[idx]) },
                    onPlayCatchup = { c, prg ->
                        val ok = playCatchup(nav, container.repository.catchupUrl(p, c, prg), p, c, prg)
                        if (ok) vm.focus(c)
                        ok
                    },
                    toast = toast,
                )
                LaunchedEffect(state.row) { if (gridFocused) vm.focus(state.channel) }
            }
        }
    }

    val guideChannels = channels
    if (guideOpen && !guideChannels.isNullOrEmpty()) {
        LiveGuide(
            channels = guideChannels,
            categoryName = selectedName,
            startChannelId = info?.channel?.id ?: vm.lastFocusedId,
            favorites = favorites,
            showNumber = settings.showChannelNumbers,
            onDismiss = { guideOpen = false },
            onPlayLive = { idx ->
                guideOpen = false
                watch(guideChannels[idx])
            },
            onPlayCatchup = { ch, prg ->
                val ok = playCatchup(nav, container.repository.catchupUrl(p, ch, prg), p, ch, prg)
                if (ok) {
                    guideOpen = false
                    vm.focus(ch)
                }
                ok
            },
        )
    }
}

private fun playCatchup(nav: AppNav, url: String?, p: PlaylistEntity, ch: ChannelEntity, prg: ProgramEntity): Boolean {
    if (url == null) return false
    nav.play(
        PlayRequest.Vod(
            p,
            listOf(
                VodItem(
                    kind = Kind.LIVE, key = "catchup:${ch.itemKey}:${prg.startAt}", title = prg.title,
                    subtitle = "${ch.name.cleanTitle()} · ${formatClock(prg.startAt)}", image = ch.logo,
                    url = url, trackHistory = false, description = prg.description,
                )
            ),
            0,
        )
    )
    return true
}

/** The focused channel, full width: what is on, its progress, and what OK can do with it. */
@Composable
private fun LiveHero(
    ch: ChannelEntity?,
    schedule: List<ProgramEntity>,
    favorite: Boolean,
    showNumber: Boolean,
    video: @Composable () -> Unit,
    showVideo: Boolean,
    onWatch: () -> Unit,
    onFromStart: (ProgramEntity) -> Unit,
    onGuide: () -> Unit,
    onFavorite: () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Focusing a hero button keeps the list at the top (the whole hero visible under the tab bar).
    val showWholeHero = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = Rect(Offset.Zero, size.toSize())
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }
    val now = System.currentTimeMillis()
    val current = schedule.firstOrNull { it.startAt <= now && it.endAt > now }
    val next = schedule.firstOrNull { it.startAt >= (current?.endAt ?: now) }
    Box(Modifier.fillMaxWidth().height(390.dp).onSizeChanged { size = it }.bringIntoViewResponder(showWholeHero)) {
        // Artwork: the live picture when this channel is the one playing, else its logo on a dusk-tinted backdrop.
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF14182C), Color(0xFF0A0A0C)), start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY))))
        if (showVideo) {
            Box(Modifier.fillMaxSize()) { video() }
        } else if (ch != null) {
            ChannelLogo(
                ch.logo, ch.name,
                Modifier.align(Alignment.CenterEnd).padding(end = 90.dp, top = 40.dp).width(300.dp).height(170.dp),
                padding = 0.dp, transparent = true,
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE60A0A0C), 0.45f to Color(0x800A0A0C), 0.8f to Color(0x000A0A0C))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x990A0A0C), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to C.Background)))

        Column(Modifier.align(Alignment.BottomStart).padding(start = ScreenEdge, bottom = 18.dp).width(560.dp)) {
            if (ch == null) {
                Text("Télévision", style = T.Display)
                return@Column
            }
            val name = remember(ch.name) { ch.name.mediaName() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge()
                Spacer(Modifier.width(7.dp))
                Text(
                    listOfNotNull(name.title, if (showNumber) "Chaîne ${ch.number}" else null, name.channelBadge).joinToString(" · "),
                    style = T.Subhead,
                    color = C.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(current?.title ?: name.title, style = T.Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                current?.description?.takeIf { it.isNotBlank() }
                    ?: next?.let { "À suivre · ${formatClock(it.startAt)}  ${it.title}" }
                    ?: if (schedule.isEmpty()) "Aucune information de programme" else "",
                style = T.Body,
                color = C.Text.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    Text(formatClock(current.startAt), style = T.Footnote, color = C.Text2)
                    Spacer(Modifier.width(8.dp))
                    ProgressLine(
                        (now - current.startAt).toFloat() / (current.endAt - current.startAt).coerceAtLeast(1),
                        Modifier.width(154.dp),
                        height = 2.5.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatClock(current.endAt), style = T.Footnote, color = C.Text2)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Regarder", onWatch, icon = Icons.Rounded.PlayArrow, primary = true)
                if (current != null && ch.catchupDays > 0) {
                    PillButton("Depuis le début", { onFromStart(current) }, icon = Icons.Rounded.Replay)
                } else {
                    PillButton("Guide TV", onGuide, icon = Icons.Rounded.CalendarMonth)
                }
                IconPill(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, onFavorite, tint = if (favorite) C.Yellow else null)
            }
        }
    }
}
