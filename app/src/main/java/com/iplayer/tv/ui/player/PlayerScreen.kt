package com.iplayer.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C as MediaC
import androidx.media3.common.Player
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.player.VideoSurface
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.player.resizeMode
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.Toast
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.Badge
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatDuration
import com.iplayer.tv.util.formatMinutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Panel { NONE, CHANNELS, OPTIONS }

@Composable
fun PlayerScreen() {
    val vm = appViewModel { PlayerViewModel(it) }
    val nav = LocalNav.current
    val container = LocalContainer.current
    if (vm.request == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val pm = vm.pm
    val currentPlayer by pm.playerFlow.collectAsState()
    val player = currentPlayer ?: pm.player
    val notice by pm.notice.collectAsState()
    val settings by container.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()

    var aspect by remember { mutableStateOf(settings.aspectMode) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var digits by remember { mutableStateOf("") }
    val toast = remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }

    val error by pm.error.collectAsState()
    val reconnecting by pm.reconnecting.collectAsState()
    val liveIndex by vm.liveIndex.collectAsState()
    val vodIndex by vm.vodIndex.collectAsState()
    val nowNext by vm.nowNext.collectAsState()
    val favorite by vm.favorite.collectAsState()
    val finished by vm.finished.collectAsState()

    fun showOverlay() {
        overlay = true
        overlayTick++
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_ENDED) vm.onEnded()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.onExit()
            nav.inPlayer = false
        }
    }
    LaunchedEffect(Unit) {
        vm.start()
        rootFocus.tryFocus()
    }
    LaunchedEffect(finished) { if (finished) nav.back() }
    LaunchedEffect(notice) {
        notice?.let {
            toast.value = it
            pm.consumeNotice()
        }
    }
    LaunchedEffect(overlay, overlayTick, isPlaying, panel, seekTarget) {
        if (overlay && panel == Panel.NONE && seekTarget == null && (vm.isLive || isPlaying)) {
            delay(if (vm.isLive) 4500 else 3500)
            overlay = false
        }
    }
    LaunchedEffect(overlay, panel) {
        while (overlay || panel != Panel.NONE) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != MediaC.TIME_UNSET && it > 0 } ?: 0L
            delay(500)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            vm.saveProgress()
        }
    }
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1400)
            digits.toIntOrNull()?.let { vm.playNumber(it) }
            digits = ""
            showOverlay()
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            panel = Panel.NONE
            delay(50)
            retryFocus.tryFocus()
        } else rootFocus.tryFocus()
    }
    LaunchedEffect(panel) { if (panel == Panel.NONE && error == null) rootFocus.tryFocus() }

    BackHandler {
        when {
            panel != Panel.NONE -> panel = Panel.NONE
            digits.isNotEmpty() -> digits = ""
            seekTarget != null -> { seekJob?.cancel(); seekTarget = null }
            overlay && !(vm.isLive && error != null) -> overlay = false
            else -> nav.back()
        }
    }

    fun commitSeekLater() {
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(700)
            seekTarget?.let { player.seekTo(it) }
            seekTarget = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration.takeIf { it != MediaC.TIME_UNSET && it > 0 } ?: return
        val base = seekTarget ?: player.currentPosition
        seekTarget = (base + deltaMs).coerceIn(0, dur - 1000)
        showOverlay()
        commitSeekLater()
    }

    fun togglePlay() {
        if (seekTarget != null) {
            seekJob?.cancel()
            player.seekTo(seekTarget!!)
            seekTarget = null
            player.play()
        } else if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        showOverlay()
    }

    fun handleKey(code: Int, repeat: Int): Boolean {
        if (code in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 && vm.isLive) {
            if (digits.length < 4) digits += (code - AndroidKeyEvent.KEYCODE_0).toString()
            return true
        }
        when (code) {
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> { togglePlay(); return true }
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); showOverlay(); return true }
            AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); showOverlay(); return true }
            AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_SETTINGS -> { panel = Panel.OPTIONS; return true }
            AndroidKeyEvent.KEYCODE_INFO -> { if (overlay) overlay = false else showOverlay(); return true }
        }
        if (vm.isLive) {
            val up = if (settings.invertZapping) 1 else -1
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_UP -> { vm.zap(up); showOverlay() }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { vm.zap(-up); showOverlay() }
                AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_PAGE_UP -> { vm.zap(1); showOverlay() }
                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN, AndroidKeyEvent.KEYCODE_PAGE_DOWN -> { vm.zap(-1); showOverlay() }
                AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> { vm.lastChannel(); showOverlay() }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ->
                    if (overlay) panel = Panel.CHANNELS else showOverlay()
                AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_GUIDE -> panel = Panel.CHANNELS
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> panel = Panel.OPTIONS
                else -> return false
            }
            return true
        } else {
            val step = when {
                repeat > 12 -> 120_000L
                repeat > 4 -> 30_000L
                else -> 10_000L
            }
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> togglePlay()
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> seekBy(-step)
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> seekBy(step)
                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-30_000)
                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(30_000)
                AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> vm.next()
                AndroidKeyEvent.KEYCODE_DPAD_UP -> showOverlay()
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> panel = Panel.OPTIONS
                else -> return false
            }
            return true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onKeyEvent { ev ->
                if (panel != Panel.NONE || error != null) return@onKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                handleKey(ev.nativeKeyEvent.keyCode, ev.nativeKeyEvent.repeatCount)
            }
            .focusable()
    ) {
        VideoSurface(player, aspect.resizeMode(), Modifier.fillMaxSize())

        if ((playbackState == Player.STATE_BUFFERING || reconnecting) && error == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                if (reconnecting) {
                    Spacer(Modifier.height(12.dp))
                    Text("Reconnexion…", style = T.Callout, color = C.Text2)
                }
            }
        }

        // ---------------- overlays
        AnimatedVisibility(overlay && panel == Panel.NONE && error == null, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
            if (vm.isLive) {
                LiveOverlay(vm.channels.getOrNull(liveIndex), nowNext, favorite, settings.showChannelNumbers)
            } else {
                VodOverlay(vm.currentVod, position, duration, seekTarget, isPlaying, vm.hasNext)
            }
        }
        if (!isPlaying && !vm.isLive && playbackState == Player.STATE_READY && !overlay && error == null) {
            Icon(
                Icons.Rounded.Pause, null,
                Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(36.dp)).background(Color(0x80000000)).padding(14.dp),
                tint = Color.White,
            )
        }

        if (digits.isNotEmpty()) {
            Text(
                digits,
                style = T.LargeTitle.copy(fontSize = 54.sp),
                modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xCC1C1C1E)).padding(horizontal = 26.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(
            panel == Panel.CHANNELS,
            enter = slideInHorizontally(tween(180)) { -it / 3 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(160)) { -it / 3 } + fadeOut(tween(160)),
        ) {
            ChannelsPanel(vm.channels, liveIndex, vm.nowPrograms.collectAsState().value, settings.showChannelNumbers) { idx ->
                vm.playChannelAt(idx)
                panel = Panel.NONE
                showOverlay()
            }
        }
        AnimatedVisibility(
            panel == Panel.OPTIONS,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(180)) { it / 3 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(160)) { it / 3 } + fadeOut(tween(160)),
        ) {
            OptionsPanel(
                player = player,
                isLive = vm.isLive,
                favorite = favorite,
                aspect = aspect,
                hasNext = vm.hasNext,
                onAspect = { aspect = it },
                onFavorite = { vm.toggleFavorite { fav -> toast.value = if (fav) "Ajoutée aux favoris" else "Retirée des favoris" } },
                onNext = { panel = Panel.NONE; vm.next() },
                onClose = { panel = Panel.NONE },
            )
        }

        if (error != null) {
            ErrorCard(
                message = error ?: "",
                isLive = vm.isLive,
                retryFocus = retryFocus,
                onRetry = { pm.retry() },
                onNext = { vm.zap(1) },
                onBack = { nav.back() },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp))
    }
}

// ============================================================================ overlays

@Composable
private fun LiveOverlay(ch: ChannelEntity?, nowNext: List<ProgramEntity>, favorite: Boolean, showNumber: Boolean) {
    if (ch == null) return
    val now = System.currentTimeMillis()
    val current = nowNext.firstOrNull { it.startAt <= now && it.endAt > now }
    val next = nowNext.firstOrNull { it.startAt >= (current?.endAt ?: now) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
        Row(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge("EN DIRECT", color = C.Red)
            Spacer(Modifier.width(14.dp))
            Text(formatClock(now), style = T.Title2)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000))))
        )
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 40.dp), verticalAlignment = Alignment.Bottom) {
            ChannelLogo(ch.logo, ch.name, Modifier.width(132.dp).height(84.dp), padding = 10.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showNumber) {
                        Text(ch.number.toString(), style = T.Title2, color = C.Text2)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(ch.name, style = T.Title1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (favorite) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.Star, null, Modifier.size(22.dp), tint = C.Yellow)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (current != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(current.title, style = T.Title3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        Spacer(Modifier.width(14.dp))
                        Text("${formatClock(current.startAt)} – ${formatClock(current.endAt)}", style = T.Callout, color = C.Text2)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(
                            (now - current.startAt).toFloat() / (current.endAt - current.startAt).coerceAtLeast(1),
                            Modifier.width(360.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Reste ${formatMinutes(current.endAt - now)}", style = T.Footnote, color = C.Text2)
                    }
                    if (next != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Ensuite · ${formatClock(next.startAt)}  ${next.title}", style = T.Subhead, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("Aucune information de programme", style = T.Callout, color = C.Text2)
                }
            }
        }
    }
}

@Composable
private fun VodOverlay(item: VodItem?, position: Long, duration: Long, seekTarget: Long?, isPlaying: Boolean, hasNext: Boolean) {
    if (item == null) return
    val shown = seekTarget ?: position
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
        Column(Modifier.padding(start = 48.dp, top = 32.dp, end = 200.dp)) {
            Text(item.title, style = T.Title1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle != null) Text(item.subtitle, style = T.Callout, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatClock(System.currentTimeMillis()), style = T.Title2, modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 48.dp))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isPlaying && seekTarget == null) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                if (seekTarget != null) {
                    val delta = seekTarget - position
                    Text((if (delta >= 0) "+" else "−") + formatDuration(kotlin.math.abs(delta)), style = T.Headline, color = C.Text2)
                }
                Spacer(Modifier.weight(1f))
                if (hasNext) Text("Menu ▸ Épisode suivant", style = T.Footnote, color = C.Text3)
            }
            Spacer(Modifier.height(12.dp))
            ProgressLine(
                if (duration > 0) shown.toFloat() / duration else 0f,
                Modifier.fillMaxWidth(),
                height = if (seekTarget != null) 8.dp else 6.dp,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Text(formatDuration(shown), style = T.Callout, color = C.Text2)
                Spacer(Modifier.weight(1f))
                if (duration > 0) Text("−" + formatDuration(duration - shown), style = T.Callout, color = C.Text2)
            }
        }
    }
}

// ============================================================================ panels

@Composable
private fun ChannelsPanel(
    channels: List<ChannelEntity>,
    current: Int,
    nowPrograms: Map<String, ProgramEntity>,
    showNumbers: Boolean,
    onSelect: (Int) -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(30)
        focus.tryFocus()
    }
    Box(
        Modifier.fillMaxHeight().width(440.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF5000000), Color(0xE6000000), Color(0x00000000))))
    ) {
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 40.dp, top = 28.dp)) {
            Text("Chaînes", style = T.Title2)
            Text("${channels.size} chaînes", style = T.Footnote, color = C.Text3)
            Spacer(Modifier.height(12.dp))
            LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                    val prg = ch.epgId?.let { nowPrograms[it.lowercase()] }
                    FocusSurface(
                        onClick = { onSelect(i) },
                        modifier = Modifier.fillMaxWidth().height(54.dp).then(if (i == current) Modifier.focusRequester(focus) else Modifier),
                        shape = RoundedCornerShape(10.dp),
                        color = if (i == current) Color(0x33FFFFFF) else Color.Transparent,
                        focusedScale = 1.02f,
                        elevation = 6.dp,
                    ) {
                        val content = LocalContentColor.current
                        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showNumbers) Text(ch.number.toString(), style = T.Footnote, color = content.copy(alpha = 0.5f), modifier = Modifier.width(36.dp))
                            ChannelLogo(ch.logo, ch.name, Modifier.width(54.dp).height(34.dp), padding = 3.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, style = T.Callout.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (prg != null) Text(prg.title, style = T.Caption, color = content.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsPanel(
    player: Player,
    isLive: Boolean,
    favorite: Boolean,
    aspect: AspectMode,
    hasNext: Boolean,
    onAspect: (AspectMode) -> Unit,
    onFavorite: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    val audio = remember(version) { Tracks.list(player, MediaC.TRACK_TYPE_AUDIO) }
    val subs = remember(version) { Tracks.list(player, MediaC.TRACK_TYPE_TEXT) }
    val video = remember(version) { Tracks.list(player, MediaC.TRACK_TYPE_VIDEO) }
    val subsOff = remember(version) { Tracks.isDisabled(player, MediaC.TRACK_TYPE_TEXT) || subs.none { it.selected } }
    val videoAuto = remember(version) { !Tracks.hasOverride(player, MediaC.TRACK_TYPE_VIDEO) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(30)
        firstFocus.tryFocus()
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { version++ }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    Box(
        Modifier.fillMaxHeight().width(420.dp)
            .background(Brush.horizontalGradient(listOf(Color(0x00000000), Color(0xE6000000), Color(0xF5000000))))
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(start = 48.dp, end = 28.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Text("Options", style = T.Title2, modifier = Modifier.padding(bottom = 10.dp)) }
            val ff = Modifier.focusRequester(firstFocus)
            if (hasNext) item { OptionRow("Épisode suivant", false, ff, onNext) }
            if (isLive) item { OptionRow(if (favorite) "Retirer des favoris" else "Ajouter aux favoris", favorite, if (!hasNext) ff else Modifier, onFavorite) }

            item { Header("Format d'image") }
            AspectMode.entries.forEachIndexed { i, m ->
                item { OptionRow(m.label, m == aspect, if (i == 0 && !hasNext && !isLive) ff else Modifier) { onAspect(m) } }
            }
            if (audio.isNotEmpty()) {
                item { Header("Audio") }
                audio.forEach { t -> item { OptionRow(t.label, t.selected) { Tracks.select(player, t); version++ } } }
            }
            if (subs.isNotEmpty()) {
                item { Header("Sous-titres") }
                item { OptionRow("Désactivés", subsOff) { Tracks.disable(player, MediaC.TRACK_TYPE_TEXT); version++ } }
                subs.forEach { t -> item { OptionRow(t.label, t.selected && !subsOff) { Tracks.select(player, t); version++ } } }
            }
            if (video.size > 1) {
                item { Header("Qualité vidéo") }
                item { OptionRow("Automatique", videoAuto) { Tracks.auto(player, MediaC.TRACK_TYPE_VIDEO); version++ } }
                video.forEach { t -> item { OptionRow(t.label, t.selected && !videoAuto) { Tracks.select(player, t); version++ } } }
            }
            item {
                Spacer(Modifier.height(12.dp))
                PillButton("Fermer", onClick = onClose)
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text.uppercase(),
        style = T.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
        color = C.Text3,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 12.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        focusedScale = 1.02f,
        elevation = 6.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = T.Callout, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    isLive: Boolean,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(460.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF21C1C1E)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(40.dp), tint = C.Red)
        Spacer(Modifier.height(12.dp))
        Text("Lecture interrompue", style = T.Title3)
        Spacer(Modifier.height(6.dp))
        Text(message, style = T.Subhead, color = C.Text2)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Réessayer", onClick = onRetry, primary = true, modifier = Modifier.focusRequester(retryFocus))
            if (isLive) PillButton("Chaîne suivante", onClick = onNext)
            PillButton("Retour", onClick = onBack)
        }
    }
}
