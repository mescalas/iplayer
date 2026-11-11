package com.iplayer.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.SearchResults
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.ui.AppNav
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.Chip
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.LiveBadge
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.ScreenEdge
import com.iplayer.tv.ui.components.SectionTitle
import com.iplayer.tv.ui.components.TextInputDialog
import com.iplayer.tv.ui.components.WideCard
import com.iplayer.tv.ui.live.dayLabel
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val playlist = repo.activePlaylist
    val query = MutableStateFlow("")
    val results: StateFlow<SearchResults> = combine(query.debounce(220), playlist) { q, p -> q to p }
        .mapLatest { (q, p) -> if (p == null) SearchResults() else repo.search(p.id, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())
}

private enum class Layout(val label: String, val keys: List<String>) {
    LETTERS("abc", "abcdefghijklmnopqrstuvwxyz".map { it.toString() }),
    ACCENTS("ÀÉÎ", "àâäçéèêëîïôöùûüÿœæñ".map { it.toString() }),
    DIGITS("123", "1234567890".map { it.toString() }),
    SYMBOLS("#+=", listOf("-", "'", ":", ".", ",", "&", "!", "?", "(", ")", "/", "+", "@", "#", "%", "*")),
}

private enum class Filter(val label: String) { ALL("Tout"), LIVE("Chaînes"), GUIDE("Guide TV"), MOVIES("Films"), SERIES("Séries") }

@Composable
fun SearchScreen() {
    val vm = appViewModel { SearchViewModel(it) }
    val nav = LocalNav.current
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val playlist by vm.playlist.collectAsState()
    var systemKeyboard by remember { mutableStateOf(false) }
    var layout by rememberSaveable { mutableStateOf(Layout.LETTERS) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    fun type(k: String) { vm.query.value += k }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item("query") {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        query.ifEmpty { "Films, séries, chaînes, programmes…" },
                        style = if (query.isEmpty()) T.Title2.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp) else T.Display.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.75).sp),
                        color = if (query.isEmpty()) C.Text3 else C.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Caret
                    Box(Modifier.padding(start = 2.dp).width(1.5.dp).height(26.dp).background(C.Text))
                }
            }
        }
        item("keyboard") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenEdge).focusRestorer(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeyIcon(Icons.Rounded.SpaceBar) { type(" ") }
                layout.keys.forEach { k -> Key(k, onLongClick = { systemKeyboard = true }) { type(k) } }
                KeyIcon(Icons.AutoMirrored.Rounded.Backspace, onLongClick = { vm.query.value = "" }) { vm.query.value = vm.query.value.dropLast(1) }
            }
        }
        item("modes") {
            Row(
                Modifier.fillMaxWidth().padding(start = ScreenEdge, end = ScreenEdge, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clip(CircleShape).background(C.Glass).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Layout.entries.forEach { l ->
                        FocusSurface(
                            onClick = { layout = l },
                            modifier = Modifier.height(20.dp),
                            shape = CircleShape,
                            color = if (l == layout) C.GlassStrong else Color.Transparent,
                            contentColor = if (l == layout) C.Text else C.Text2,
                            focusedScale = 1.06f,
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(l.label, style = T.Caption.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (query.isNotEmpty()) {
                    SmallAction("Effacer") { vm.query.value = "" }
                    Spacer(Modifier.width(8.dp))
                }
                SmallAction("Dicter ou clavier", Icons.Rounded.Mic) { systemKeyboard = true }
            }
        }

        val p = playlist
        val q = query.trim()
        if (q.length < 2) {
            item("hint") {
                EmptyState(Icons.Rounded.Search, "Rechercher", "Films, séries, chaînes et programmes du guide TV de votre source. Maintenez OK sur une touche pour dicter.", Modifier.fillMaxWidth().padding(top = 24.dp))
            }
            return@LazyColumn
        }
        if (results.isEmpty || p == null) {
            item("none") {
                EmptyState(Icons.Rounded.Search, "Aucun résultat", "Aucun contenu ne correspond à « $q ».", Modifier.fillMaxWidth().padding(top = 24.dp))
            }
            return@LazyColumn
        }

        item("filters") {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 18.dp).focusRestorer(),
                contentPadding = PaddingValues(horizontal = ScreenEdge, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val counts = mapOf(
                    Filter.ALL to results.channels.size + results.programs.size + results.movies.size + results.series.size,
                    Filter.LIVE to results.channels.size,
                    Filter.GUIDE to results.programs.size,
                    Filter.MOVIES to results.movies.size,
                    Filter.SERIES to results.series.size,
                )
                Filter.entries.filter { (counts[it] ?: 0) > 0 }.forEach { f ->
                    item(f.name) {
                        Chip(f.label, selected = f == filter, count = counts[f].toString(), onClick = { filter = f }, onFocused = { filter = f })
                    }
                }
            }
        }

        val best = bestResult(q, results)
        val showGuide = (filter == Filter.ALL || filter == Filter.GUIDE) && results.programs.isNotEmpty()
        if (filter == Filter.ALL && best != null || showGuide) {
            item("top") {
                Row(Modifier.fillMaxWidth().padding(start = ScreenEdge, end = ScreenEdge, top = 18.dp)) {
                    if (filter == Filter.ALL && best != null) {
                        Column(Modifier.weight(1.6f)) {
                            SectionTitle("Meilleur résultat")
                            BestResultCard(best, p, nav)
                        }
                        if (showGuide) Spacer(Modifier.width(24.dp))
                    }
                    if (showGuide) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle("Dans le guide TV", trailing = { Text("7 jours", style = T.Caption, color = C.Text3) })
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                results.programs.take(if (filter == Filter.GUIDE) 12 else 4).forEach { (ch, prg) ->
                                    GuideResultRow(ch, prg) { nav.playLive(p, listOf(ch), 0) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val vod: List<Any> = when (filter) {
            Filter.ALL -> (results.movies + results.series).sortedBy { rank(q, (it as? MovieEntity)?.name ?: (it as SeriesEntity).name) }
            Filter.MOVIES -> results.movies
            Filter.SERIES -> results.series
            else -> emptyList()
        }
        if (vod.isNotEmpty()) {
            item("vod") {
                Column(Modifier.padding(top = 18.dp)) {
                    SectionTitle(
                        when (filter) { Filter.MOVIES -> "Films"; Filter.SERIES -> "Séries"; else -> "Films et séries" },
                        Modifier.padding(horizontal = ScreenEdge),
                        note = "${vod.size} résultat${if (vod.size > 1) "s" else ""}",
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenEdge, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        itemsIndexed(vod, key = { _, it -> if (it is MovieEntity) "m${it.id}" else "s${(it as SeriesEntity).id}" }) { _, it ->
                            if (it is MovieEntity) {
                                PosterCard(it.name, it.poster, onClick = { nav.movie(it.id) }, subtitle = listOfNotNull("Film", it.year).joinToString(" · "))
                            } else if (it is SeriesEntity) {
                                PosterCard(it.name, it.cover, onClick = { nav.series(it.id) }, subtitle = listOfNotNull("Série", it.year).joinToString(" · "))
                            }
                        }
                    }
                }
            }
        }
        if ((filter == Filter.ALL || filter == Filter.LIVE) && results.channels.isNotEmpty()) {
            item("channels") {
                Column(Modifier.padding(top = 18.dp)) {
                    SectionTitle("Chaînes", Modifier.padding(horizontal = ScreenEdge), note = "${results.channels.size}")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenEdge, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        itemsIndexed(results.channels, key = { _, c -> "c" + c.id }) { i, ch ->
                            WideCard(ch.name, null, ch.logo, logoMode = true, width = 170.dp, onClick = { nav.playLive(p, results.channels, i) })
                        }
                    }
                }
            }
        }
    }

    if (systemKeyboard) {
        TextInputDialog(
            title = "Rechercher",
            initial = query,
            hint = "Utilisez le micro de la télécommande ou le clavier.",
            onDone = { vm.query.value = it; systemKeyboard = false },
            onDismiss = { systemKeyboard = false },
        )
    }
}

/** Lower is better: titles starting with the query first, then containing it as a word. */
private fun rank(q: String, name: String): Int {
    val t = name.cleanTitle().lowercase()
    val k = q.lowercase()
    return when {
        t == k -> 0
        t.startsWith(k) -> 1
        t.contains(" $k") -> 2
        else -> 3
    }
}

private sealed interface Best {
    data class Movie(val m: MovieEntity) : Best
    data class Series(val s: SeriesEntity) : Best
    data class Live(val ch: ChannelEntity, val prg: ProgramEntity) : Best
}

private fun bestResult(q: String, r: SearchResults): Best? {
    val now = System.currentTimeMillis()
    val onAir = r.programs.firstOrNull { (_, p) -> p.startAt <= now && p.endAt > now && rank(q, p.title) <= 1 }
    if (onAir != null) return Best.Live(onAir.first, onAir.second)
    val m = r.movies.minByOrNull { rank(q, it.name) }
    val s = r.series.minByOrNull { rank(q, it.name) }
    return when {
        m == null && s == null -> null
        s == null -> Best.Movie(m!!)
        m == null -> Best.Series(s)
        rank(q, s.name) < rank(q, m.name) -> Best.Series(s)
        else -> Best.Movie(m)
    }
}

@Composable
private fun BestResultCard(best: Best, p: PlaylistEntity, nav: AppNav) {
    val now = System.currentTimeMillis()
    val image: String?
    val title: String
    val meta: String
    when (best) {
        is Best.Movie -> { image = best.m.poster; title = best.m.name.mediaName().title; meta = listOfNotNull("Film", best.m.year).joinToString(" · ") }
        is Best.Series -> { image = best.s.backdrop ?: best.s.cover; title = best.s.name.mediaName().title; meta = listOfNotNull("Série", best.s.year, best.s.genre?.split(',')?.firstOrNull()?.trim()).joinToString(" · ") }
        is Best.Live -> { image = best.ch.logo; title = best.prg.title; meta = best.prg.description?.lineSequence()?.firstOrNull().orEmpty() }
    }
    Box(Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(9.dp)).background(C.Surface)) {
        if (best is Best.Live) {
            ChannelLogo(best.ch.logo, best.ch.name, Modifier.align(Alignment.CenterEnd).padding(end = 30.dp).width(150.dp).height(90.dp), padding = 0.dp, transparent = true)
        } else if (!image.isNullOrBlank()) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE6000000), 0.55f to Color(0x66000000), 1f to Color.Transparent)))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            if (best is Best.Live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${best.ch.name.cleanTitle()} · depuis ${formatClock(best.prg.startAt)} · ${((best.prg.endAt - now) / 60000).coerceAtLeast(0)} min restantes",
                        style = T.Caption, color = C.Text2, maxLines = 1,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(title, style = T.Title1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, style = T.Caption, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (best) {
                    is Best.Movie -> {
                        PillButton("Lire le film", onClick = {
                            nav.play(PlayRequest.Vod(p, listOf(VodItem(Kind.MOVIE, best.m.itemKey, best.m.name, best.m.year, best.m.poster, best.m.url)), 0))
                        }, icon = Icons.Rounded.PlayArrow, primary = true)
                        PillButton("Détails", onClick = { nav.movie(best.m.id) })
                    }
                    is Best.Series -> PillButton("Voir la série", onClick = { nav.series(best.s.id) }, icon = Icons.Rounded.PlayArrow, primary = true)
                    is Best.Live -> PillButton("Regarder en direct", onClick = { nav.playLive(p, listOf(best.ch), 0) }, icon = Icons.Rounded.PlayArrow, primary = true)
                }
            }
        }
    }
}

@Composable
private fun GuideResultRow(ch: ChannelEntity, prg: ProgramEntity, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val live = prg.startAt <= now && prg.endAt > now
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(7.dp),
        color = C.Row,
        focusedScale = 1.03f,
    ) { focused ->
        val content = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ch.logo, ch.name, Modifier.width(46.dp).height(30.dp), padding = 3.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(prg.title, style = T.Subhead.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (live) "Maintenant" else dayLabel(prg.startAt, now)) + " · ${formatClock(prg.startAt)} – ${formatClock(prg.endAt)}",
                    style = T.Caption,
                    color = if (live && !focused) C.LiveText else content.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            if (live) Box(Modifier.padding(end = 6.dp).size(5.dp).clip(CircleShape).background(C.Red))
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.height(20.dp), shape = CircleShape, color = Color.Transparent, contentColor = C.Text2, focusedScale = 1.06f, contentAlignment = Alignment.Center) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(10.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = T.Caption)
        }
    }
}

@Composable
private fun Key(label: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(width = 22.dp, height = 22.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        contentColor = C.Text.copy(alpha = 0.75f),
        focusedScale = 1.75f,
        elevation = 10.dp,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Text(label, style = T.Body.copy(fontSize = 16.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal), textAlign = TextAlign.Center)
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(width = 26.dp, height = 22.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        contentColor = C.Text.copy(alpha = 0.75f),
        focusedScale = 1.5f,
        elevation = 10.dp,
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(13.dp))
    }
}
