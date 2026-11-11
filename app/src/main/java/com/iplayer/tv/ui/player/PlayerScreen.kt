package com.iplayer.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C as MediaC
import androidx.media3.common.Player
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.SubtitleStyle
import com.iplayer.tv.data.cycle
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.player.SUBTITLE_SAMPLE
import com.iplayer.tv.player.SubtitleLayer
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
import com.iplayer.tv.ui.components.InfoPill
import com.iplayer.tv.ui.components.InfoPills
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatDuration
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.cleanEpisodeSubtitle
import com.iplayer.tv.util.mediaName
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
    val controlsFocus = remember { FocusRequester() }
    var controlsFocused by remember { mutableStateOf(false) }
    var panelTab by remember { mutableStateOf(PanelTab.TRACKS) }
    val retryFocus = remember { FocusRequester() }

    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }

    val error by pm.error.collectAsState()
    val reconnecting by pm.reconnecting.collectAsState()
    val reconnectMessage by pm.reconnectMessage.collectAsState()
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
    LaunchedEffect(overlay, overlayTick, isPlaying, panel, seekTarget, controlsFocused) {
        if (overlay && panel == Panel.NONE && seekTarget == null && !controlsFocused && (vm.isLive || isPlaying)) {
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
            controlsFocused -> { rootFocus.tryFocus(); showOverlay() }
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
        seekTarget = (base + deltaMs).coerceIn(0, (dur - 1000).coerceAtLeast(0))
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
            AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_SETTINGS -> { panelTab = PanelTab.TRACKS; panel = Panel.OPTIONS; return true }
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
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { panelTab = PanelTab.TRACKS; panel = Panel.OPTIONS }
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
                // Down: to the round controls of the overlay.
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> if (overlay && seekTarget == null) {
                    if (!controlsFocus.tryFocus()) { panelTab = PanelTab.TRACKS; panel = Panel.OPTIONS }
                } else showOverlay()
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
                if (panel != Panel.NONE || error != null || controlsFocused) return@onKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                handleKey(ev.nativeKeyEvent.keyCode, ev.nativeKeyEvent.repeatCount)
            }
            .focusable()
    ) {
        VideoSurface(player, aspect.resizeMode(), Modifier.fillMaxSize())
        val controlsShown = overlay && panel == Panel.NONE && error == null
        SubtitleLayer(
            player = player,
            style = settings.subtitleStyle,
            avoidBottom = if (!controlsShown) 0.dp else if (vm.isLive) 170.dp else 120.dp,
            avoidTop = if (!controlsShown) 0.dp else 50.dp,
            sample = if (panel == Panel.OPTIONS && panelTab == PanelTab.TRACKS) SUBTITLE_SAMPLE else null,
        )

        if ((playbackState == Player.STATE_BUFFERING || reconnecting) && error == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                if (reconnecting) {
                    Spacer(Modifier.height(12.dp))
                    Text(reconnectMessage ?: "Reconnexion…", style = T.Callout, color = C.Text2)
                }
            }
        }

        // ---------------- overlays
        val source = vm.request?.playlist?.name
        AnimatedVisibility(overlay && panel == Panel.NONE && error == null, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
            if (vm.isLive) {
                LiveOverlay(vm.channels.getOrNull(liveIndex), nowNext, favorite, settings.showChannelNumbers, player, source)
            } else {
                VodOverlay(
                    item = vm.currentVod,
                    player = player,
                    source = source,
                    position = position,
                    duration = duration,
                    seekTarget = seekTarget,
                    controls = vodControls(
                        hasNext = vm.hasNext,
                        onTracks = { panelTab = PanelTab.TRACKS; panel = Panel.OPTIONS },
                        onAspect = {
                            aspect = aspect.cycle()
                            toast.value = "Format d'image : ${aspect.label}"
                            showOverlay()
                        },
                        onNext = { rootFocus.tryFocus(); vm.next() },
                        onInfo = { panelTab = PanelTab.INFO; panel = Panel.OPTIONS },
                    ),
                    controlsRequester = controlsFocus,
                    onControlsFocus = {
                        controlsFocused = it
                        if (it) showOverlay()
                    },
                    onControlsUp = {
                        rootFocus.tryFocus()
                        showOverlay()
                    },
                )
            }
        }
        if (!isPlaying && !vm.isLive && playbackState == Player.STATE_READY && !overlay && error == null) {
            Icon(
                Icons.Rounded.Pause, null,
                Modifier.align(Alignment.Center).size(60.dp).clip(CircleShape).background(Color(0x990A0A0C)).padding(15.dp),
                tint = Color.White,
            )
        }

        if (digits.isNotEmpty()) {
            Text(
                digits,
                style = T.Display.copy(fontSize = 40.sp),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 22.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xE61C1C1F)).padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(
            panel == Panel.CHANNELS,
            enter = slideInHorizontally(tween(180)) { -it / 4 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(160)) { -it / 4 } + fadeOut(tween(160)),
        ) {
            ChannelsPanel(vm.channels, liveIndex, vm.nowPrograms.collectAsState().value, settings.showChannelNumbers) { idx ->
                vm.playChannelAt(idx)
                panel = Panel.NONE
                showOverlay()
            }
        }
        AnimatedVisibility(
            panel == Panel.OPTIONS,
            enter = slideInVertically(tween(200)) { -it / 6 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { -it / 6 } + fadeOut(tween(160)),
        ) {
            val vod = vm.currentVod
            val ch = vm.channels.getOrNull(liveIndex)
            val now = System.currentTimeMillis()
            val prg = nowNext.firstOrNull { it.startAt <= now && it.endAt > now }
            OptionsPanel(
                player = player,
                initialTab = panelTab,
                isLive = vm.isLive,
                favorite = favorite,
                aspect = aspect,
                hasNext = vm.hasNext,
                subtitleStyle = settings.subtitleStyle,
                infoTitle = if (vm.isLive) prg?.title ?: ch?.name?.mediaName()?.title.orEmpty() else vod?.title?.mediaName()?.title.orEmpty(),
                infoMeta = if (vm.isLive) listOfNotNull(ch?.name?.mediaName()?.title, prg?.let { "${formatClock(it.startAt)} – ${formatClock(it.endAt)}" }).joinToString(" · ")
                else vod?.let { if (it.kind == Kind.EPISODE) cleanEpisodeSubtitle(it.subtitle, it.title) else it.subtitle },
                infoDescription = if (vm.isLive) prg?.description else vod?.description,
                onAspect = { aspect = it },
                onSubtitleStyle = { st -> container.settings.update { it.copy(subtitleStyle = st) } },
                onFavorite = { vm.toggleFavorite { fav -> toast.value = if (fav) "Ajoutée aux favoris" else "Retirée des favoris" } },
                onNext = { panel = Panel.NONE; vm.next() },
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
        modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF21C1C1F)).padding(24.dp),
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
