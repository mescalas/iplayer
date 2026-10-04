package com.iplayer.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.HistoryEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.data.remote.MovieDetails
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.LiveTile
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.WideCard
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatClock
import com.iplayer.tv.util.formatMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class HeroKind { RESUME, MOVIE, SERIES }

data class HeroItem(
    val key: String,
    val kind: HeroKind,
    val label: String,
    val title: String,
    val meta: String,
    val description: String?,
    val image: String?,
    val history: HistoryEntity? = null,
    val movie: MovieEntity? = null,
    val series: SeriesEntity? = null,
)

data class HomeState(
    val playlist: PlaylistEntity? = null,
    val continueWatching: List<HistoryEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val topMovies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val loaded: Boolean = false,
)

private data class Lists(val fav: List<ChannelEntity>, val recent: List<ChannelEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    var lastFocused: String? = null
    var heroIndex = 0

    val state: StateFlow<HomeState> = repo.activePlaylist.flatMapLatest { p ->
        if (p == null) flowOf(HomeState(loaded = true))
        else combine(
            repo.continueWatching(p.id),
            combine(repo.favoriteChannels(p.id), repo.recentChannels(p.id)) { f, r -> Lists(f, r) },
            repo.latestMovies(p.id),
            repo.topRatedMovies(p.id),
            repo.latestSeries(p.id),
        ) { cw, ch, mov, top, ser ->
            val channels = (ch.fav + ch.recent).distinctBy { it.itemKey }.take(20)
            HomeState(p, cw, channels, mov, top, ser, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val nowPrograms: StateFlow<Map<String, ProgramEntity>> =
        combine(repo.activePlaylist, repo.epgVersion, ticker) { p, _, _ -> p }
            .mapLatest { p -> if (p == null) emptyMap() else repo.currentPrograms(p.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val movieInfo = MutableStateFlow<Map<String, MovieDetails>>(emptyMap())

    val hero: StateFlow<List<HeroItem>> = combine(state, movieInfo) { s, info ->
        val out = ArrayList<HeroItem>()
        s.continueWatching.firstOrNull()?.let { h ->
            out += HeroItem(
                key = "resume:${h.itemKey}", kind = HeroKind.RESUME, label = "REPRENDRE LA LECTURE",
                title = h.title.cleanTitle(),
                meta = listOfNotNull(h.subtitle, if (h.duration > 0) "Reste " + formatMinutes(h.duration - h.position) else null).joinToString("  ·  "),
                description = null, image = h.image, history = h,
            )
        }
        val series = s.series.filter { !it.backdrop.isNullOrBlank() }.take(4)
        val movies = s.movies.take(4)
        for (i in 0 until maxOf(series.size, movies.size)) {
            series.getOrNull(i)?.let { se ->
                out += HeroItem(
                    key = "s:${se.itemKey}", kind = HeroKind.SERIES, label = "SÉRIE",
                    title = se.name.cleanTitle(),
                    meta = listOfNotNull(se.year, se.genre?.split(',')?.firstOrNull()?.trim(), rating(se.rating)).joinToString("  ·  "),
                    description = se.plot, image = se.backdrop ?: se.cover, series = se,
                )
            }
            movies.getOrNull(i)?.let { m ->
                val d = info[m.itemKey]
                out += HeroItem(
                    key = "m:${m.itemKey}", kind = HeroKind.MOVIE, label = "NOUVEAU FILM",
                    title = m.name.cleanTitle(),
                    meta = listOfNotNull(
                        d?.releaseDate?.take(4) ?: m.year,
                        d?.genre?.split(',')?.firstOrNull()?.trim(),
                        d?.durationSecs?.takeIf { it > 0 }?.let { formatMinutes(it * 1000L) },
                        rating(m.rating),
                    ).joinToString("  ·  "),
                    description = d?.plot, image = d?.backdrop ?: m.poster, movie = m,
                )
            }
        }
        out.take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Fetch backdrops & synopses for the few movies shown in the hero.
        viewModelScope.launch {
            state.collect { s ->
                val p = s.playlist ?: return@collect
                s.movies.take(4).forEach { m ->
                    if (movieInfo.value.containsKey(m.itemKey)) return@forEach
                    repo.movieDetails(p, m)?.let { d -> movieInfo.value = movieInfo.value + (m.itemKey to d) }
                }
            }
        }
    }

    private fun rating(r: Float) = if (r > 0f) "★ " + String.format(Locale.ROOT, "%.1f", r) else null
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}

@Composable
fun HomeScreen() {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsState()
    val hero by vm.hero.collectAsState()
    val now by vm.nowPrograms.collectAsState()
    val nav = LocalNav.current
    val shell = LocalShell.current
    val restoreRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val playlist = state.playlist

    LaunchedEffect(state.loaded) {
        if (state.loaded && nav.restoreFocus) {
            delay(80)
            if (!restoreRequester.tryFocus()) shell.focusTabs()
            nav.restoreFocus = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 }.collect { shell.homeScrolled.value = it }
    }
    LaunchedEffect(shell.barFocused.value) {
        if (shell.barFocused.value && listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0)
    }
    DisposableEffect(Unit) { onDispose { shell.homeScrolled.value = false } }

    fun focusMod(key: String): Modifier = if (key == vm.lastFocused) Modifier.focusRequester(restoreRequester) else Modifier

    fun onFocus(key: String, image: String?) {
        vm.lastFocused = key
        shell.backdrop.value = image
    }

    if (!state.loaded || playlist == null) return

    val empty = hero.isEmpty() && state.channels.isEmpty() && state.movies.isEmpty() && state.series.isEmpty()
    if (empty) {
        EmptyState(
            Icons.Rounded.Tv,
            "Tout est prêt",
            "Parcourez la TV en direct, les films et les séries. Vos favoris et vos lectures en cours apparaîtront ici.",
            Modifier.fillMaxSize().padding(top = 76.dp),
        )
        return
    }

    fun playHistory(h: HistoryEntity) = nav.play(
        PlayRequest.Vod(playlist, listOf(VodItem(h.kind, h.itemKey, h.title, h.subtitle, h.image, h.url, h.parentKey)), 0)
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item("hero") {
            if (hero.isNotEmpty()) {
                Hero(
                    items = hero,
                    vm = vm,
                    playMod = focusMod("hero"),
                    onFocusHero = { vm.lastFocused = "hero"; shell.backdrop.value = null },
                    onPlay = { item ->
                        when (item.kind) {
                            HeroKind.RESUME -> item.history?.let { playHistory(it) }
                            HeroKind.MOVIE -> item.movie?.let { nav.movie(it.id) }
                            HeroKind.SERIES -> item.series?.let { nav.series(it.id) }
                        }
                    },
                )
            } else {
                Column(Modifier.padding(start = 56.dp, top = 96.dp)) {
                    Text(greeting(), style = T.LargeTitle)
                    Text("Que regardons-nous ce soir ?", style = T.Title3, color = C.Text2)
                }
            }
        }
        if (state.continueWatching.isNotEmpty()) shelf("Reprendre la lecture", "cw") {
            itemsIndexed(state.continueWatching, key = { _, h -> "cw" + h.kind + h.itemKey }) { _, h ->
                val key = "cw:${h.kind}:${h.itemKey}"
                WideCard(
                    title = h.title,
                    subtitle = h.subtitle ?: (if (h.duration > 0) "Reste " + formatMinutes(h.duration - h.position) else null),
                    image = h.image,
                    width = 264.dp,
                    progress = if (h.duration > 0) h.position.toFloat() / h.duration else null,
                    modifier = focusMod(key),
                    onFocused = { onFocus(key, h.image) },
                    onClick = { playHistory(h) },
                )
            }
        }
        if (state.channels.isNotEmpty()) shelf("En direct", "live") {
            itemsIndexed(state.channels, key = { _, c -> "live" + c.id }) { i, ch ->
                val key = "live:${ch.itemKey}"
                val prg = ch.epgId?.let { now[it.lowercase()] }
                val t = System.currentTimeMillis()
                LiveTile(
                    name = ch.name,
                    logo = ch.logo,
                    program = prg?.title,
                    progress = prg?.let { (t - it.startAt).toFloat() / (it.endAt - it.startAt).coerceAtLeast(1) },
                    timeRange = prg?.let { "${formatClock(it.startAt)} – ${formatClock(it.endAt)}" },
                    modifier = focusMod(key),
                    onFocused = { onFocus(key, null) },
                    onClick = { nav.playLive(playlist, state.channels, i) },
                )
            }
        }
        if (state.movies.isNotEmpty()) shelf("Nouveaux films", "movies") {
            itemsIndexed(state.movies, key = { _, m -> "m" + m.id }) { _, m ->
                val key = "m:${m.itemKey}"
                PosterCard(
                    title = m.name, image = m.poster, rating = m.rating, subtitle = m.year,
                    modifier = focusMod(key),
                    onFocused = { onFocus(key, m.poster) },
                    onClick = { nav.movie(m.id) },
                )
            }
        }
        if (state.series.isNotEmpty()) shelf("Séries à suivre", "series") {
            itemsIndexed(state.series, key = { _, s -> "s" + s.id }) { _, s ->
                val key = "s:${s.itemKey}"
                PosterCard(
                    title = s.name, image = s.cover, rating = s.rating, subtitle = s.year,
                    modifier = focusMod(key),
                    onFocused = { onFocus(key, s.backdrop ?: s.cover) },
                    onClick = { nav.series(s.id) },
                )
            }
        }
        if (state.topMovies.size >= 5) shelf("Les mieux notés", "top") {
            itemsIndexed(state.topMovies, key = { _, m -> "t" + m.id }) { _, m ->
                val key = "t:${m.itemKey}"
                PosterCard(
                    title = m.name, image = m.poster, rating = m.rating, subtitle = m.year,
                    modifier = focusMod(key),
                    onFocused = { onFocus(key, m.poster) },
                    onClick = { nav.movie(m.id) },
                )
            }
        }
    }
}

private fun LazyListScope.shelf(title: String, key: String, content: LazyListScope.() -> Unit) {
    item(key) {
        Column {
            Text(title, style = T.Title3, modifier = Modifier.padding(start = 56.dp, bottom = 2.dp))
            LazyRow(
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Hero(
    items: List<HeroItem>,
    vm: HomeViewModel,
    playMod: Modifier,
    onFocusHero: () -> Unit,
    onPlay: (HeroItem) -> Unit,
) {
    var index by remember { mutableIntStateOf(vm.heroIndex.coerceIn(0, items.size - 1)) }
    var focused by remember { mutableStateOf(false) }
    var focusedButton by remember { mutableIntStateOf(0) }
    val item = items[index.coerceIn(0, items.size - 1)]
    val nav = LocalNav.current

    fun go(delta: Int) {
        index = (index + delta + items.size) % items.size
        vm.heroIndex = index
    }

    // Auto-advance like the Apple TV top shelf, paused while the hero has focus.
    LaunchedEffect(index, focused, items.size) {
        if (!focused && items.size > 1) {
            delay(8000)
            go(1)
        }
    }

    Box(Modifier.fillMaxWidth().height(400.dp)) {
        Crossfade(targetState = item.image, animationSpec = tween(600), label = "hero") { img ->
            Box(Modifier.fillMaxSize()) {
                if (!img.isNullOrBlank()) {
                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2A44), Color(0xFF101014)))))
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF2000000), 0.35f to Color(0xB3000000), 0.7f to Color(0x1A000000), 1f to Color.Transparent)
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent, 0.62f to Color.Transparent, 1f to C.Background)))

        Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 22.dp).width(560.dp).animateContentSize()) {
            Text(
                item.label,
                style = T.Caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
                color = C.Text2,
            )
            Spacer(Modifier.height(6.dp))
            Text(item.title, style = T.LargeTitle.copy(fontSize = 40.sp, lineHeight = 46.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.meta.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.meta, style = T.Callout, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!item.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(item.description, style = T.Body, color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .onFocusChanged {
                        focused = it.hasFocus
                        if (it.hasFocus) onFocusHero()
                    }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val last = if (item.kind == HeroKind.MOVIE) 1 else 0
                        when {
                            ev.key == Key.DirectionLeft && focusedButton == 0 && items.size > 1 -> { go(-1); true }
                            ev.key == Key.DirectionRight && focusedButton == last && items.size > 1 -> { go(1); true }
                            else -> false
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PillButton(
                    when (item.kind) {
                        HeroKind.RESUME -> "Reprendre"
                        HeroKind.MOVIE -> "Lecture"
                        HeroKind.SERIES -> "Voir les épisodes"
                    },
                    onClick = {
                        if (item.kind == HeroKind.MOVIE) {
                            val m = item.movie
                            val p = vm.state.value.playlist
                            if (m != null && p != null) {
                                nav.play(PlayRequest.Vod(p, listOf(VodItem(Kind.MOVIE, m.itemKey, m.name, m.year, item.image, m.url)), 0))
                            }
                        } else onPlay(item)
                    },
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    modifier = playMod,
                    onFocusChange = { if (it) focusedButton = 0 },
                )
                if (item.kind == HeroKind.MOVIE) {
                    PillButton("Plus d'infos", onClick = { onPlay(item) }, icon = Icons.Rounded.Info, onFocusChange = { if (it) focusedButton = 1 })
                }
            }
        }
        if (items.size > 1) {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(width = if (i == index) 20.dp else 6.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == index) Color.White else Color(0x59FFFFFF))
                    )
                }
            }
        }
    }
}
