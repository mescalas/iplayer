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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.Toast
import com.iplayer.tv.ui.components.Badge
import com.iplayer.tv.ui.components.ChannelLogo
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

/** Minutes shown across the grid. */
private const val SPAN_MIN = 120
private const val VISIBLE_ROWS = 5

private fun floorHalfHour(t: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    c.set(Calendar.MINUTE, if (c.get(Calendar.MINUTE) >= 30) 30 else 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayLabel(t: Long, now: Long): String {
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

/**
 * Full-screen TV guide: one row per channel of the current category, programs laid out on a time line.
 * Up/Down change channel at the same time, Left/Right walk through programs (the past is catch-up when the
 * channel has it). The grid is a single focus target that moves its own cursor, like a native EPG.
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
    val repo = LocalContainer.current.repository
    val opened = remember { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(opened) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val maxCatchup = remember(channels) { channels.maxOf { it.catchupDays }.coerceIn(0, 7) }
    val minWin = remember { floorHalfHour(opened) - if (maxCatchup > 0) maxCatchup * DAY else HOUR }
    val maxWin = remember { floorHalfHour(opened) + DAY }

    var row by remember { mutableIntStateOf(channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0)) }
    var winStart by remember { mutableLongStateOf(floorHalfHour(opened) - HALF_HOUR) }
    // The cursor is a point in time: the focused program is the one airing then on the focused row.
    var cursor by remember { mutableLongStateOf(opened) }
    val schedules = remember { mutableStateMapOf<Long, List<ProgramEntity>>() }
    val toast = remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    val rowStart = (row - 2).coerceIn(0, (channels.size - VISIBLE_ROWS).coerceAtLeast(0))
    LaunchedEffect(rowStart) {
        val from = (rowStart - 4).coerceAtLeast(0)
        val to = (rowStart + VISIBLE_ROWS + 4).coerceAtMost(channels.size)
        for (i in from until to) {
            val ch = channels[i]
            if (ch.id !in schedules) schedules[ch.id] = repo.schedule(ch, minWin, maxWin + SPAN_MIN * MIN)
        }
    }
    LaunchedEffect(Unit) {
        delay(60)
        if (!focus.tryFocus()) {
            delay(150)
            focus.tryFocus()
        }
    }

    val ch = channels[row]
    val progs = schedules[ch.id]
    val focused = progs?.firstOrNull { it.startAt <= cursor && it.endAt > cursor }

    /** Shows [start, end) in the window and puts the cursor on it. */
    fun moveTo(start: Long, end: Long) {
        var w = winStart
        val winEnd = w + SPAN_MIN * MIN
        if (end <= w || start < w && end <= w + HALF_HOUR) w = floorHalfHour(maxOf(start, end - HOUR))
        else if (start >= winEnd - HALF_HOUR) w = floorHalfHour(start) - HALF_HOUR
        winStart = w.coerceIn(minWin, maxWin)
        cursor = maxOf(start, winStart).coerceIn(minWin, maxWin + SPAN_MIN * MIN - 1)
    }

    fun step(forward: Boolean) {
        val list = progs.orEmpty()
        val target = if (forward) {
            list.firstOrNull { it.startAt >= (focused?.endAt ?: (cursor + 1)) }
        } else {
            list.lastOrNull { it.endAt <= (focused?.startAt ?: cursor) }
        }
        if (target != null) {
            moveTo(target.startAt, target.endAt)
        } else {
            val t = if (forward) cursor + HALF_HOUR else cursor - HALF_HOUR
            moveTo(t, t + HALF_HOUR)
        }
    }

    fun activate() {
        val prg = focused
        when {
            prg == null || (prg.startAt <= now && prg.endAt > now) -> onPlayLive(row)
            prg.endAt <= now -> {
                val available = ch.catchupDays > 0 && prg.startAt >= now - ch.catchupDays * DAY
                if (!available || !onPlayCatchup(ch, prg)) toast.value = "Pas de replay pour ce programme"
            }
            else -> toast.value = "Ce programme commence à ${formatClock(prg.startAt)}"
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF060608))
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionDown -> { if (row < channels.size - 1) row++; true }
                    Key.DirectionUp -> { if (row > 0) row--; true }
                    Key.DirectionRight -> { step(true); true }
                    Key.DirectionLeft -> { step(false); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { activate(); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 26.dp)) {
            // ---- header
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Guide TV", style = T.Title2)
                Spacer(Modifier.width(12.dp))
                Text(categoryName, style = T.Callout, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(dayLabel(maxOf(cursor, winStart), now), style = T.Headline, color = C.Text2)
                Spacer(Modifier.width(16.dp))
                Text(formatClock(now), style = T.Headline, color = C.Text3)
            }
            Spacer(Modifier.height(14.dp))

            // ---- focused program
            Row(Modifier.fillMaxWidth().height(132.dp)) {
                Column(Modifier.weight(1f)) {
                    val name = remember(ch.name) { ch.name.mediaName() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showNumber) "${ch.number}  ·  ${name.title}" else name.title,
                            style = T.Footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = C.Text2,
                            maxLines = 1,
                        )
                        if (ch.itemKey in favorites) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Star, null, Modifier.size(13.dp), tint = C.Yellow)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        focused?.title ?: if (progs == null) "" else "Aucune information de programme",
                        style = T.Title1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (focused != null) {
                        val live = focused.startAt <= now && focused.endAt > now
                        val past = focused.endAt <= now
                        val replay = past && ch.catchupDays > 0 && focused.startAt >= now - ch.catchupDays * DAY
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                live -> Badge("EN DIRECT", color = C.Red)
                                replay -> Badge("REPLAY", color = C.Surface3)
                                past -> Badge("TERMINÉ", color = C.Surface2, textColor = C.Text2)
                                else -> Badge("À VENIR", color = C.Surface2, textColor = C.Text2)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${formatClock(focused.startAt)} – ${formatClock(focused.endAt)}" + when {
                                    live -> "  ·  reste ${formatMinutes(focused.endAt - now)}  ·  OK pour regarder"
                                    replay -> "  ·  OK pour revoir"
                                    past -> ""
                                    else -> "  ·  dans ${formatMinutes(focused.startAt - now)}"
                                },
                                style = T.Callout,
                                color = C.Text2,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (!focused.description.isNullOrBlank()) {
                            Text(focused.description, style = T.Subhead, color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.width(28.dp))
                ChannelLogo(ch.logo, ch.name, Modifier.width(168.dp).height(95.dp), padding = 16.dp)
            }
            Spacer(Modifier.height(10.dp))

            // ---- grid
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val labelW = 172.dp
                val gap = 8.dp
                val lineW = maxWidth - labelW - gap
                val perMin = lineW / SPAN_MIN
                val winEnd = winStart + SPAN_MIN * MIN
                Column {
                    Box(Modifier.padding(start = labelW + gap).height(20.dp).fillMaxWidth()) {
                        for (k in 0 until SPAN_MIN / 30) {
                            Text(
                                formatClock(winStart + k * HALF_HOUR),
                                style = T.Footnote,
                                color = C.Text3,
                                modifier = Modifier.offset(x = perMin * (k * 30)),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    for (i in rowStart until (rowStart + VISIBLE_ROWS).coerceAtMost(channels.size)) {
                        val c = channels[i]
                        val isRow = i == row
                        Row(Modifier.height(50.dp)) {
                            Row(
                                Modifier
                                    .width(labelW)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isRow) Color(0x24FFFFFF) else Color.Transparent)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showNumber) {
                                    Text(c.number.toString(), style = T.Caption, color = C.Text3, maxLines = 1, modifier = Modifier.width(26.dp))
                                }
                                ChannelLogo(c.logo, c.name, Modifier.width(48.dp).height(32.dp), padding = 3.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    remember(c.name) { c.name.mediaName().title },
                                    style = T.Footnote,
                                    color = if (isRow) C.Text else C.Text2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(gap))
                            Box(Modifier.width(lineW).fillMaxHeight().clipToBounds()) {
                                val list = schedules[c.id]
                                if (list != null && list.none { it.endAt > winStart && it.startAt < winEnd }) {
                                    GuideBlock(
                                        title = "Aucune information de programme",
                                        time = null,
                                        focused = isRow,
                                        background = Color(0x0FFFFFFF),
                                        dim = true,
                                        replay = false,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                list?.forEach { prg ->
                                    if (prg.endAt <= winStart || prg.startAt >= winEnd) return@forEach
                                    val a = maxOf(prg.startAt, winStart)
                                    val z = minOf(prg.endAt, winEnd)
                                    val x = perMin * ((a - winStart) / MIN.toFloat())
                                    val w = (perMin * ((z - a) / MIN.toFloat()) - 4.dp).coerceAtLeast(4.dp)
                                    val live = prg.startAt <= now && prg.endAt > now
                                    val past = prg.endAt <= now
                                    val replay = past && c.catchupDays > 0 && prg.startAt >= now - c.catchupDays * DAY
                                    GuideBlock(
                                        title = prg.title,
                                        time = "${formatClock(prg.startAt)} – ${formatClock(prg.endAt)}",
                                        focused = isRow && prg === focused,
                                        background = when {
                                            live -> Color(0x2EFFFFFF)
                                            past -> Color(0x0FFFFFFF)
                                            else -> Color(0x1AFFFFFF)
                                        },
                                        dim = past && !replay,
                                        replay = replay,
                                        modifier = Modifier.offset(x = x).width(w).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                // "now" marker across the rows
                if (now in winStart until winEnd) {
                    val x = labelW + gap + perMin * ((now - winStart) / MIN.toFloat())
                    Box(
                        Modifier
                            .offset(x = x - 1.dp, y = 22.dp)
                            .width(2.dp)
                            .height(56.dp * (VISIBLE_ROWS.coerceAtMost(channels.size)))
                            .background(C.Red)
                    )
                    Box(Modifier.offset(x = x - 4.dp, y = 19.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Red))
                }
            }
        }
        Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

@Composable
private fun GuideBlock(
    title: String,
    time: String?,
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
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) C.Focus else background)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (replay) {
                    Icon(Icons.Rounded.Replay, null, Modifier.size(12.dp), tint = fg.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    title,
                    style = T.Callout.copy(fontWeight = FontWeight.SemiBold),
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (time != null) {
                Text(time, style = T.Caption, color = fg.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
