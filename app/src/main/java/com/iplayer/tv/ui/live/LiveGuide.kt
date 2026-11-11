package com.iplayer.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.iplayer.tv.data.Repository
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.Toast
import com.iplayer.tv.ui.components.Badge
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.Clock
import com.iplayer.tv.ui.components.LiveBadge
import com.iplayer.tv.ui.components.ScreenEdge
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.delay
import java.util.Calendar

private const val MIN = 60_000L
private const val HALF_HOUR = 30 * MIN
private const val HOUR = 60 * MIN
private const val DAY = 24 * HOUR

private fun floorHalfHour(t: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    c.set(Calendar.MINUTE, if (c.get(Calendar.MINUTE) >= 30) 30 else 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun dayLabel(t: Long, now: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = t }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val diff = ((a.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 400 + a.get(Calendar.DAY_OF_YEAR) - b.get(Calendar.DAY_OF_YEAR))
    return when (diff) {
        0 -> "Aujourd'hui"
        -1 -> "Hier"
        1 -> "Demain"
        else -> java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE).format(java.util.Date(t))
            .replaceFirstChar { it.uppercase() }
    }
}

/** "Ce soir · Mardi 11 novembre" */
fun tonightLabel(now: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val part = when (h) {
        in 5..11 -> "Ce matin"
        in 12..17 -> "Cet après-midi"
        else -> "Ce soir"
    }
    val date = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE).format(java.util.Date(now)).replaceFirstChar { it.uppercase() }
    return "$part · $date"
}

/**
 * Cursor of a guide grid: the focused row, the time window shown and a point in time (the focused program is
 * the one airing then on the focused row). Schedules are loaded around the visible rows.
 */
@Stable
class GuideState(
    val channels: List<ChannelEntity>,
    startRow: Int,
    opened: Long,
    val spanMin: Int,
    val visibleRows: Int,
) {
    var now by mutableLongStateOf(opened)
    private val maxCatchup = channels.maxOfOrNull { it.catchupDays }?.coerceIn(0, 7) ?: 0
    val minWin = floorHalfHour(opened) - if (maxCatchup > 0) maxCatchup * DAY else HOUR
    val maxWin = floorHalfHour(opened) + DAY
    var row by mutableIntStateOf(startRow.coerceIn(0, (channels.size - 1).coerceAtLeast(0)))
    var winStart by mutableLongStateOf(floorHalfHour(opened) - HALF_HOUR)
    var cursor by mutableLongStateOf(opened)
    val schedules = mutableStateMapOf<Long, List<ProgramEntity>>()

    val rowStart get() = (row - 2).coerceIn(0, (channels.size - visibleRows).coerceAtLeast(0))
    val channel get() = channels[row.coerceIn(0, channels.lastIndex)]
    val programs get() = schedules[channel.id]
    val focused get() = programs?.firstOrNull { it.startAt <= cursor && it.endAt > cursor }

    suspend fun load(repo: Repository) {
        val from = (rowStart - 4).coerceAtLeast(0)
        val to = (rowStart + visibleRows + 4).coerceAtMost(channels.size)
        for (i in from until to) {
            val ch = channels[i]
            if (ch.id !in schedules) schedules[ch.id] = repo.schedule(ch, minWin, maxWin + spanMin * MIN)
        }
    }

    /** Shows [start, end) in the window and puts the cursor on it. */
    private fun moveTo(start: Long, end: Long) {
        var w = winStart
        val winEnd = w + spanMin * MIN
        if (end <= w || start < w && end <= w + HALF_HOUR) w = floorHalfHour(maxOf(start, end - HOUR))
        else if (start >= winEnd - HALF_HOUR) w = floorHalfHour(start) - HALF_HOUR
        winStart = w.coerceIn(minWin, maxWin)
        cursor = maxOf(start, winStart).coerceIn(minWin, maxWin + spanMin * MIN - 1)
    }

    fun step(forward: Boolean) {
        val list = programs.orEmpty()
        val f = focused
        val target = if (forward) list.firstOrNull { it.startAt >= (f?.endAt ?: (cursor + 1)) }
        else list.lastOrNull { it.endAt <= (f?.startAt ?: cursor) }
        if (target != null) moveTo(target.startAt, target.endAt)
        else {
            val t = if (forward) cursor + HALF_HOUR else cursor - HALF_HOUR
            moveTo(t, t + HALF_HOUR)
        }
    }

    fun isReplay(ch: ChannelEntity, prg: ProgramEntity) =
        prg.endAt <= now && ch.catchupDays > 0 && prg.startAt >= now - ch.catchupDays * DAY
}

@Composable
fun rememberGuideState(channels: List<ChannelEntity>, startChannelId: Long, spanMin: Int, visibleRows: Int): GuideState {
    val repo = LocalContainer.current.repository
    val state = remember(channels) {
        GuideState(channels, channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0), System.currentTimeMillis(), spanMin, visibleRows)
    }
    LaunchedEffect(state) {
        while (true) {
            delay(30_000)
            state.now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(state, state.rowStart) { state.load(repo) }
    return state
}

/** What OK does on the focused program: watch live, catch-up, or explain why not. */
private fun activate(
    s: GuideState,
    onPlayLive: (Int) -> Unit,
    onPlayCatchup: (ChannelEntity, ProgramEntity) -> Boolean,
    toast: (String) -> Unit,
) {
    val prg = s.focused
    val ch = s.channel
    when {
        prg == null || (prg.startAt <= s.now && prg.endAt > s.now) -> onPlayLive(s.row)
        prg.endAt <= s.now -> if (!s.isReplay(ch, prg) || !onPlayCatchup(ch, prg)) toast("Pas de replay pour ce programme")
        else -> toast("Ce programme commence à ${formatClock(prg.startAt)}")
    }
}

/**
 * Programme grid of the design: channel cells on the left, programmes on a time line, a red "now" marker.
 * The grid is a single focus target that moves its own cursor, like a native EPG: Up/Down change channel at the
 * same time, Left/Right walk through programmes (the past is catch-up when the channel has it).
 * [exitUp]: Up on the first row leaves the grid (inline guide) instead of being swallowed.
 */
@Composable
fun GuideGrid(
    state: GuideState,
    showNumber: Boolean,
    onPlayLive: (Int) -> Unit,
    onPlayCatchup: (ChannelEntity, ProgramEntity) -> Boolean,
    toast: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    exitUp: Boolean = false,
    rowHeight: Dp = 44.dp,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var hasFocus by remember { mutableStateOf(false) }
    val channels = state.channels
    if (channels.isEmpty()) return
    if (state.row > channels.lastIndex) state.row = channels.lastIndex
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                hasFocus = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionDown -> if (state.row < channels.size - 1) { state.row++; true } else false
                    Key.DirectionUp -> if (state.row > 0) { state.row--; true } else !exitUp
                    Key.DirectionRight -> { state.step(true); true }
                    Key.DirectionLeft -> { state.step(false); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { activate(state, onPlayLive, onPlayCatchup, toast); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val labelW = 110.dp
        val gap = 4.dp
        val lineW = maxWidth - labelW - gap
        val perMin = lineW / state.spanMin
        val winStart = state.winStart
        val winEnd = winStart + state.spanMin * MIN
        val now = state.now
        val focused = state.focused
        val rows = (state.rowStart until (state.rowStart + state.visibleRows).coerceAtMost(channels.size)).toList()
        Column {
            // ---- time axis
            Box(Modifier.padding(start = labelW + gap).height(18.dp).fillMaxWidth()) {
                for (k in 0 until state.spanMin / 30) {
                    Text(
                        formatClock(winStart + k * HALF_HOUR),
                        style = T.Footnote,
                        color = C.Text2,
                        modifier = Modifier.offset(x = perMin * (k * 30)),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            for (i in rows) {
                val c = channels[i]
                val isRow = hasFocus && i == state.row
                Row(Modifier.height(rowHeight)) {
                    Row(
                        Modifier
                            .width(labelW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isRow) C.GlassStrong else C.Row)
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showNumber) {
                            Text(c.number.toString(), style = T.Caption, color = C.Text3, maxLines = 1, modifier = Modifier.width(20.dp))
                        }
                        Text(
                            remember(c.name) { c.name.mediaName().title },
                            style = T.Subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = C.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(gap))
                    Box(Modifier.width(lineW).fillMaxHeight().clipToBounds()) {
                        val list = state.schedules[c.id]
                        if (list != null && list.none { it.endAt > winStart && it.startAt < winEnd }) {
                            GuideBlock("Aucune information de programme", null, isRow, C.Row, dim = true, replay = false, modifier = Modifier.fillMaxSize())
                        }
                        list?.forEach { prg ->
                            if (prg.endAt <= winStart || prg.startAt >= winEnd) return@forEach
                            val a = maxOf(prg.startAt, winStart)
                            val z = minOf(prg.endAt, winEnd)
                            val x = perMin * ((a - winStart) / MIN.toFloat())
                            val w = (perMin * ((z - a) / MIN.toFloat()) - gap).coerceAtLeast(3.dp)
                            val live = prg.startAt <= now && prg.endAt > now
                            val past = prg.endAt <= now
                            val replay = state.isReplay(c, prg)
                            GuideBlock(
                                title = prg.title,
                                subtitle = prg.categories.split(',', ';', '/').firstOrNull { it.isNotBlank() }?.trim()?.replaceFirstChar { it.uppercase() }
                                    ?: "${formatClock(prg.startAt)} – ${formatClock(prg.endAt)}",
                                focused = isRow && prg === focused,
                                background = when {
                                    live -> Color(0x24FFFFFF)
                                    past -> Color(0x0AFFFFFF)
                                    else -> Color(0x14FFFFFF)
                                },
                                dim = past && !replay,
                                replay = replay,
                                modifier = Modifier.offset(x = x).width(w).fillMaxHeight(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(gap))
            }
        }
        // "now" marker across the rows, with the time in a red tag above
        if (now in winStart until winEnd) {
            val x = labelW + gap + perMin * ((now - winStart) / MIN.toFloat())
            Box(
                Modifier
                    .offset(x = x - 0.5.dp, y = 18.dp)
                    .width(1.dp)
                    .height((rowHeight + gap) * rows.size + 4.dp)
                    .background(C.Red.copy(alpha = 0.8f))
            )
            Text(
                formatClock(now),
                style = T.Micro,
                color = Color.White,
                modifier = Modifier
                    .offset(x = x - 15.dp, y = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(C.Red)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Full-screen TV guide: the focused programme on top, the grid of the current category below.
 */
@Composable
fun LiveGuide(
    channels: List<ChannelEntity>,
    categoryName: String,
    startChannelId: Long,
    favorites: Set<String>,
    showNumber: Boolean,
    onDismiss: () -> Unit,
    onPlayLive: (Int) -> Unit,
    onPlayCatchup: (ChannelEntity, ProgramEntity) -> Boolean,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        GuideContent(channels, categoryName, startChannelId, favorites, showNumber, onPlayLive, onPlayCatchup)
    }
}

@Composable
private fun GuideContent(
    channels: List<ChannelEntity>,
    categoryName: String,
    startChannelId: Long,
    favorites: Set<String>,
    showNumber: Boolean,
    onPlayLive: (Int) -> Unit,
    onPlayCatchup: (ChannelEntity, ProgramEntity) -> Boolean,
) {
    val state = rememberGuideState(channels, startChannelId, spanMin = 120, visibleRows = 6)
    val toast = remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        if (!focus.tryFocus()) {
            delay(150)
            focus.tryFocus()
        }
    }
    val ch = state.channel
    val focused = state.focused
    val now = state.now

    Box(Modifier.fillMaxSize().background(C.Background)) {
        Column(Modifier.fillMaxSize().padding(start = ScreenEdge, end = ScreenEdge, top = 24.dp)) {
            // ---- header
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Guide des programmes", style = T.Title3)
                Spacer(Modifier.width(8.dp))
                Text(categoryName, style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(dayLabel(maxOf(state.cursor, state.winStart), now), style = T.Headline, color = C.Text2)
                Spacer(Modifier.width(14.dp))
                Clock()
            }
            Spacer(Modifier.height(16.dp))

            // ---- focused programme
            Row(Modifier.fillMaxWidth().height(118.dp)) {
                Column(Modifier.weight(1f)) {
                    val name = remember(ch.name) { ch.name.mediaName() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (focused != null && focused.startAt <= now && focused.endAt > now) {
                            LiveBadge()
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            if (showNumber) "${name.title} · Chaîne ${ch.number}" else name.title,
                            style = T.Subhead,
                            color = C.Text2,
                            maxLines = 1,
                        )
                        if (ch.itemKey in favorites) {
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Rounded.Star, null, Modifier.size(11.dp), tint = C.Yellow)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        focused?.title ?: if (state.programs == null) "" else "Aucune information de programme",
                        style = T.Title1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (focused != null) {
                        val live = focused.startAt <= now && focused.endAt > now
                        val past = focused.endAt <= now
                        val replay = state.isReplay(ch, focused)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                live -> Unit
                                replay -> Badge("REPLAY", color = C.GlassStrong)
                                past -> Badge("TERMINÉ", color = C.Glass, textColor = C.Text2)
                                else -> Badge("À VENIR", color = C.Glass, textColor = C.Text2)
                            }
                            if (!live) Spacer(Modifier.width(8.dp))
                            Text(
                                "${formatClock(focused.startAt)} – ${formatClock(focused.endAt)}" + when {
                                    live -> " · reste ${formatMinutes(focused.endAt - now)} · OK pour regarder"
                                    replay -> " · OK pour revoir"
                                    past -> ""
                                    else -> " · dans ${formatMinutes(focused.startAt - now)}"
                                },
                                style = T.Callout,
                                color = C.Text2,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (!focused.description.isNullOrBlank()) {
                            Text(focused.description, style = T.Callout, color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.width(24.dp))
                ChannelLogo(ch.logo, ch.name, Modifier.width(150.dp).height(84.dp), padding = 14.dp)
            }
            Spacer(Modifier.height(12.dp))
            GuideGrid(
                state = state,
                showNumber = showNumber,
                onPlayLive = onPlayLive,
                onPlayCatchup = onPlayCatchup,
                toast = { toast.value = it },
                focusRequester = focus,
            )
        }
        Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

@Composable
private fun GuideBlock(
    title: String,
    subtitle: String?,
    focused: Boolean,
    background: Color,
    dim: Boolean,
    replay: Boolean,
    modifier: Modifier,
) {
    val fg = when {
        focused -> C.OnFocus
        dim -> C.Text3
        else -> C.Text
    }
    Box(
        modifier
            .graphicsLayer {
                val s = if (focused) 1.03f else 1f
                scaleX = s
                scaleY = s
                shadowElevation = if (focused) 12.dp.toPx() else 0f
                shape = RoundedCornerShape(4.dp)
            }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) C.Focus else background)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (replay) {
                    Icon(Icons.Rounded.Replay, null, Modifier.size(10.dp), tint = fg.copy(alpha = 0.6f))
                    Spacer(Modifier.width(3.dp))
                }
                Text(title, style = T.Subhead.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (subtitle != null) {
                Text(subtitle, style = T.Caption, color = fg.copy(alpha = if (focused) 0.6f else 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
