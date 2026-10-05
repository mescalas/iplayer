package com.iplayer.tv.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.iplayer.tv.ui.components.Badge
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.InfoPill
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.SideListItem
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.delay

/**
 * Live TV: the channel list on the left, a "stage" on the right (preview, what is on, actions).
 * Categories live in a floating sidebar opened with Left; the full TV guide (grid, with catch-up) opens from the stage.
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
    val listState = rememberLazyListState()
    val restoreRequester = remember { FocusRequester() }
    var restoreTargetId by remember { mutableStateOf(-1L) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    // Bumped when the list must take focus again (sidebar closed); 0 = nothing pending.
    var focusListTick by remember { mutableIntStateOf(0) }
    val watchRequester = remember { FocusRequester() }

    // Stop the preview when the user leaves the TV tab (but not when opening the full-screen player).
    DisposableEffect(Unit) {
        onDispose {
            if (!nav.inPlayer && container.player.nowPlaying.value?.isLive == true) container.player.stop()
        }
    }

    suspend fun focusChannel(list: List<ChannelEntity>, idx: Int): Boolean {
        if (idx !in list.indices) return false
        restoreTargetId = list[idx].id
        val first = listState.firstVisibleItemIndex
        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(5)
        if (idx < first || idx >= first + visible - 1) listState.scrollToItem((idx - 2).coerceAtLeast(0))
        delay(40)
        if (restoreRequester.tryFocus()) return true
        delay(120)
        return restoreRequester.tryFocus()
    }

    // Coming back from the full-screen player: focus the channel that is playing (the user may have zapped).
    LaunchedEffect(channels) {
        val list = channels ?: return@LaunchedEffect
        if (!nav.restoreFocus) return@LaunchedEffect
        val key = container.player.nowPlaying.value?.key
        var idx = if (key != null) list.indexOfFirst { it.itemKey == key } else -1
        if (idx < 0) idx = list.indexOfFirst { it.id == vm.lastFocusedId }
        if (idx < 0 || !focusChannel(list, idx)) shell.focusTabs()
        nav.restoreFocus = false
    }

    // Sidebar closed: back to the list, once the chosen category has loaded.
    LaunchedEffect(focusListTick, loaded) {
        if (focusListTick == 0) return@LaunchedEffect
        val l = loaded ?: return@LaunchedEffect
        if (l.first != selected) return@LaunchedEffect
        val list = l.second
        focusListTick = 0
        if (list.isEmpty()) return@LaunchedEffect
        val idx = list.indexOfFirst { it.id == vm.lastFocusedId }.let { if (it < 0) 0 else it }
        focusChannel(list, idx)
    }

    val p = playlist ?: return
    val selectedName = categories.firstOrNull { it.id == selected }?.name ?: ""

    fun closeSidebar(commit: String?) {
        // Back (no commit) keeps the category on screen and drops a preview still pending.
        vm.select(commit ?: selected)
        sidebarOpen = false
        focusListTick++
    }

    BackHandler(enabled = sidebarOpen) { closeSidebar(null) }

    val listAlpha by animateFloatAsState(if (sidebarOpen) 0.25f else 1f, tween(220), label = "list")

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 40.dp, top = 4.dp)) {
            // ---- channels
            Column(
                Modifier
                    .width(336.dp)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = listAlpha }
                    .onPreviewKeyEvent { ev ->
                        when {
                            ev.type != KeyEventType.KeyDown || sidebarOpen -> false
                            ev.key == Key.DirectionLeft -> {
                                sidebarOpen = true
                                true
                            }
                            // The actions sit at the bottom of the stage: geometric search would rather pick the tab bar.
                            ev.key == Key.DirectionRight -> info?.channel != null && watchRequester.tryFocus()
                            else -> false
                        }
                    },
            ) {
                Row(Modifier.padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(22.dp), tint = C.Text3)
                    Spacer(Modifier.width(4.dp))
                    Text(selectedName, style = T.Title2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    channels?.let { Text("  ${it.size}", style = T.Callout, color = C.Text3) }
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
                    ) {
                        PillButton("Catégories", onClick = { sidebarOpen = true }, icon = Icons.Rounded.ChevronLeft)
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 4.dp, end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(list, key = { it.id }) { ch ->
                            val program = ch.epgId?.let { now[it.lowercase()] }
                            ChannelRow(
                                ch = ch,
                                program = program,
                                favorite = ch.itemKey in favorites,
                                playing = nowPlaying?.key == ch.itemKey,
                                held = info?.channel?.id == ch.id,
                                showNumber = settings.showChannelNumbers,
                                modifier = if (ch.id == restoreTargetId) Modifier.focusRequester(restoreRequester) else Modifier,
                                onFocused = { vm.focus(ch) },
                                onClick = {
                                    nav.playLive(p, list, list.indexOf(ch), keepAliveOnExit = settings.livePreview)
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
            Spacer(Modifier.width(36.dp))

            // ---- stage
            val focusedCh = info?.channel
            Stage(
                ch = focusedCh,
                schedule = info?.schedule.orEmpty(),
                favorite = focusedCh != null && focusedCh.itemKey in favorites,
                showNumber = settings.showChannelNumbers,
                watchRequester = watchRequester,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                preview = {
                    val np = nowPlaying
                    // Not while the full-screen player opens: this view would grab the video surface, then
                    // release it on leaving, and the player would go on with sound but no picture.
                    if (np != null && np.isLive && settings.livePreview && livePlayer != null && !nav.inPlayer) {
                        VideoSurface(livePlayer, settings.aspectMode.resizeMode(), Modifier.fillMaxSize())
                        SubtitleLayer(livePlayer, settings.subtitleStyle)
                    } else if (focusedCh != null) {
                        ChannelLogo(focusedCh.logo, focusedCh.name, Modifier.fillMaxSize(), padding = 56.dp, transparent = true)
                    }
                },
                onWatch = {
                    val l = channels ?: return@Stage
                    val ch = focusedCh ?: return@Stage
                    nav.playLive(p, l, l.indexOfFirst { it.id == ch.id }.coerceAtLeast(0), settings.livePreview)
                },
                onGuide = { if (!channels.isNullOrEmpty()) guideOpen = true },
                onFavorite = {
                    val ch = focusedCh ?: return@Stage
                    vm.toggleFavorite(ch) { fav -> shell.toast(if (fav) "Ajoutée aux favoris" else "Retirée des favoris") }
                },
            )
        }

        CategorySidebar(
            visible = sidebarOpen,
            categories = categories,
            selected = selected,
            onFocused = { vm.onCategoryFocused(it) },
            onClose = { closeSidebar(it) },
        )
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
                vm.focus(guideChannels[idx])
                nav.playLive(p, guideChannels, idx, settings.livePreview)
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

@Composable
private fun ChannelRow(
    ch: ChannelEntity,
    program: ProgramEntity?,
    favorite: Boolean,
    playing: Boolean,
    held: Boolean,
    showNumber: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (held) Color(0x24FFFFFF) else Color.Transparent,
        focusedScale = 1.03f,
        elevation = 12.dp,
        onFocusChange = { if (it) onFocused() },
    ) {
        val content = LocalContentColor.current
        val name = remember(ch.name) { ch.name.mediaName() }
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showNumber) {
                Text(
                    ch.number.toString(),
                    style = T.Footnote,
                    color = content.copy(alpha = 0.45f),
                    maxLines = 1,
                    modifier = Modifier.width(32.dp),
                )
            }
            ChannelLogo(ch.logo, ch.name, Modifier.width(58.dp).height(38.dp), padding = 4.dp)
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
                        Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = if (content == C.OnFocus) Color(0xFFB88A00) else C.Yellow)
                    }
                    if (ch.catchupDays > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Replay, null, Modifier.size(14.dp), tint = content.copy(alpha = 0.5f))
                    }
                }
                if (program != null) {
                    val now = System.currentTimeMillis()
                    Spacer(Modifier.height(2.dp))
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
                            Modifier.width(36.dp),
                            color = content.copy(alpha = 0.8f),
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

/** The focused channel, large: preview, what is on now and next, and what OK can do with it. */
@Composable
private fun Stage(
    ch: ChannelEntity?,
    schedule: List<ProgramEntity>,
    favorite: Boolean,
    showNumber: Boolean,
    watchRequester: FocusRequester,
    modifier: Modifier,
    preview: @Composable () -> Unit,
    onWatch: () -> Unit,
    onGuide: () -> Unit,
    onFavorite: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val current = schedule.firstOrNull { it.startAt <= now && it.endAt > now }
    val next = schedule.firstOrNull { it.startAt >= (current?.endAt ?: now) }
    val progress = current?.let { (now - it.startAt).toFloat() / (it.endAt - it.startAt).coerceAtLeast(1) }
    Column(modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .height(244.dp)
                .aspectRatio(16f / 9f)
                .shadow(24.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(C.Surface),
            contentAlignment = Alignment.Center,
        ) {
            preview()
            if (ch != null) {
                Badge("EN DIRECT", Modifier.align(Alignment.TopStart).padding(14.dp), color = C.Red)
            }
            if (progress != null) {
                ProgressLine(
                    progress,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = C.Text,
                    track = Color(0x33FFFFFF),
                    height = 3.dp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (ch == null) {
            Text("Choisissez une chaîne", style = T.Title2, color = C.Text2)
            return
        }
        val name = remember(ch.name) { ch.name.mediaName() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showNumber) "${ch.number}  ·  ${name.title}" else name.title,
                style = T.Footnote.copy(fontWeight = FontWeight.SemiBold),
                color = C.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, false),
            )
            name.channelBadge?.let {
                Spacer(Modifier.width(8.dp))
                InfoPill(it, color = C.Text2)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            current?.title ?: name.title,
            style = T.Title1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        if (current != null && progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${formatClock(current.startAt)} – ${formatClock(current.endAt)}", style = T.Callout, color = C.Text2)
                Spacer(Modifier.width(10.dp))
                ProgressLine(progress, Modifier.width(72.dp), height = 3.dp, track = Color(0x33FFFFFF))
                Spacer(Modifier.width(10.dp))
                Text("Reste ${formatMinutes(current.endAt - now)}", style = T.Callout, color = C.Text2)
                if (next != null) {
                    Text(
                        "   ·   À suivre ${formatClock(next.startAt)}  ${next.title}",
                        style = T.Callout,
                        color = C.Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Text(
                if (schedule.isEmpty()) "Aucune information de programme" else "Programme indisponible",
                style = T.Callout,
                color = C.Text3,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(42.dp)) {
            val desc = current?.description
            if (!desc.isNullOrBlank()) {
                Text(desc, style = T.Subhead, color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Regarder", onWatch, Modifier.focusRequester(watchRequester), icon = Icons.Rounded.PlayArrow, primary = true)
            PillButton(if (ch.catchupDays > 0) "Guide & replay" else "Guide TV", onGuide, icon = Icons.Rounded.DateRange)
            PillButton(if (favorite) "Favori" else "Ajouter aux favoris", onFavorite, icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder)
        }
    }
}

/** Floating category panel, opened with Left from the channel list. Moving through it previews the category. */
@Composable
private fun CategorySidebar(
    visible: Boolean,
    categories: List<CatItem>,
    selected: String,
    onFocused: (String) -> Unit,
    onClose: (String?) -> Unit,
) {
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(160)),
    ) {
        val requester = remember { FocusRequester() }
        val state = rememberLazyListState()
        var focusedId by remember { mutableStateOf(selected) }
        LaunchedEffect(Unit) {
            val idx = categories.indexOfFirst { it.id == selected }.coerceAtLeast(0)
            state.scrollToItem((idx - 3).coerceAtLeast(0))
            delay(30)
            if (!requester.tryFocus()) {
                delay(120)
                requester.tryFocus()
            }
        }
        Column(
            Modifier
                .padding(start = 20.dp, top = 0.dp, bottom = 20.dp)
                .width(312.dp)
                .fillMaxHeight()
                .shadow(40.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF2232326))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) {
                        onClose(focusedId)
                        true
                    } else false
                }
                .padding(horizontal = 14.dp),
        ) {
            Text(
                "CATÉGORIES",
                style = T.Caption.copy(fontWeight = FontWeight.Bold),
                color = C.Text3,
                modifier = Modifier.padding(start = 14.dp, top = 20.dp, bottom = 10.dp),
            )
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(bottom = 24.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(categories, key = { _, it -> it.id }) { _, cat ->
                    SideListItem(
                        text = cat.name,
                        selected = cat.id == selected,
                        icon = cat.icon,
                        tag = cat.tag,
                        onClick = { onClose(cat.id) },
                        onFocused = {
                            focusedId = cat.id
                            onFocused(cat.id)
                        },
                        modifier = if (cat.id == selected) Modifier.focusRequester(requester) else Modifier,
                    )
                }
            }
        }
    }
}
