package com.iplayer.tv.ui.vod

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.CAT_ALL
import com.iplayer.tv.data.CAT_FAVORITES
import com.iplayer.tv.data.db.CategoryEntity
import com.iplayer.tv.data.db.HistoryEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.data.remote.MovieDetails
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.ActionDialog
import com.iplayer.tv.ui.components.Badge
import com.iplayer.tv.ui.components.DialogAction
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.Eyebrow
import com.iplayer.tv.ui.components.GenreTile
import com.iplayer.tv.ui.components.IconPill
import com.iplayer.tv.ui.components.MetaLine
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.RankedPoster
import com.iplayer.tv.ui.components.ScreenAhead
import com.iplayer.tv.ui.components.ScreenEdge
import com.iplayer.tv.ui.components.SectionTitle
import com.iplayer.tv.ui.components.ShelfAhead
import com.iplayer.tv.ui.components.WideCard
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.categoryLabel
import com.iplayer.tv.util.cleanEpisodeSubtitle
import com.iplayer.tv.util.cleanTitle
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class HeroItem(
    val key: String,
    /** White tag before the eyebrow ("NOUVEAU"). */
    val tag: String?,
    val eyebrow: String,
    val title: String,
    val meta: List<String>,
    /** Pills such as "4K" or "VOSTFR" taken out of the title. */
    val badges: List<String>,
    val description: String?,
    val image: String?,
    val movie: MovieEntity? = null,
    val series: SeriesEntity? = null,
    /** Series hero: the episode in progress. */
    val resume: HistoryEntity? = null,
)

data class BrowseState(
    val playlist: PlaylistEntity? = null,
    val continueWatching: List<HistoryEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val topMovies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val loaded: Boolean = false,
)

private data class Catalog(val movies: List<MovieEntity>, val top: List<MovieEntity>, val series: List<SeriesEntity>)

/** Films and Séries tabs: one hero carousel and a few shelves, the full catalogue a click away. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModel(c: AppContainer, val kind: Int) : ViewModel() {
    private val repo = c.repository
    var lastFocused: String? = null
    var heroIndex = 0

    val state: StateFlow<BrowseState> = repo.activePlaylist.flatMapLatest { p ->
        if (p == null) flowOf(BrowseState(loaded = true))
        else combine(
            repo.continueWatching(p.id),
            if (kind == Kind.MOVIE) combine(repo.latestMovies(p.id), repo.topRatedMovies(p.id)) { m, t -> Catalog(m, t, emptyList()) }
            else repo.latestSeries(p.id).map { Catalog(emptyList(), emptyList(), it) },
            repo.categories(p.id, kind),
        ) { cw, cat, cats ->
            val mine = cw.filter { if (kind == Kind.MOVIE) it.kind == Kind.MOVIE else it.kind == Kind.EPISODE }
            BrowseState(p, mine, cat.movies, cat.top, cat.series, cats, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowseState())

    val favorites: StateFlow<Set<String>> = repo.activePlaylist.flatMapLatest { p ->
        if (p == null) flowOf(emptySet()) else repo.favoriteKeys(p.id, kind).map { it.toHashSet() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val movieInfo = MutableStateFlow<Map<String, MovieDetails>>(emptyMap())
    /** Series of the episode being watched, for the series hero. */
    private val resumeSeries = MutableStateFlow<Pair<HistoryEntity, SeriesEntity>?>(null)

    val hero: StateFlow<List<HeroItem>> = combine(state, movieInfo, resumeSeries) { s, info, resume ->
        val out = ArrayList<HeroItem>()
        if (kind == Kind.MOVIE) {
            s.movies.take(5).forEach { m ->
                val d = info[m.itemKey]
                val name = m.name.mediaName()
                out += HeroItem(
                    key = "m:${m.itemKey}", tag = null, eyebrow = "FILM À LA UNE",
                    title = name.title,
                    meta = listOfNotNull(
                        d?.releaseDate?.take(4) ?: m.year,
                        d?.genre?.split(',', '/')?.firstOrNull()?.trim(),
                        d?.durationSecs?.takeIf { it > 0 }?.let { formatMinutes(it * 1000L) },
                        rating(m.rating),
                    ),
                    badges = name.techBadges,
                    description = d?.plot, image = d?.backdrop ?: m.poster, movie = m,
                )
            }
        } else {
            resume?.let { (h, se) ->
                val name = se.name.mediaName()
                out += HeroItem(
                    key = "r:${se.itemKey}", tag = null, eyebrow = "REPRENDRE LA LECTURE",
                    title = name.title,
                    meta = listOfNotNull(se.year, se.genre?.split(',', '/')?.firstOrNull()?.trim(), rating(se.rating)),
                    badges = name.techBadges, description = se.plot, image = se.backdrop ?: h.image ?: se.cover,
                    series = se, resume = h,
                )
            }
            s.series.filter { !it.backdrop.isNullOrBlank() && it.itemKey != resume?.second?.itemKey }.take(5).forEach { se ->
                val name = se.name.mediaName()
                out += HeroItem(
                    key = "s:${se.itemKey}", tag = "NOUVEAU", eyebrow = se.genre?.split(',', '/')?.firstOrNull()?.trim().orEmpty(),
                    title = name.title,
                    meta = listOfNotNull(se.year, rating(se.rating)),
                    badges = name.techBadges, description = se.plot, image = se.backdrop ?: se.cover, series = se,
                )
            }
        }
        out
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            state.collect { s ->
                val p = s.playlist ?: return@collect
                if (kind == Kind.MOVIE) {
                    // Backdrops & synopses for the few movies shown in the hero.
                    s.movies.take(5).forEach { m ->
                        if (movieInfo.value.containsKey(m.itemKey)) return@forEach
                        repo.movieDetails(p, m)?.let { d -> movieInfo.value = movieInfo.value + (m.itemKey to d) }
                    }
                } else {
                    val h = s.continueWatching.firstOrNull { it.parentKey != null }
                    if (h == null) resumeSeries.value = null
                    else if (resumeSeries.value?.first != h) {
                        resumeSeries.value = repo.seriesByKey(p.id, h.parentKey!!)?.let { h to it }
                    }
                }
            }
        }
    }

    fun removeFromContinueWatching(h: HistoryEntity) {
        viewModelScope.launch { repo.removeFromContinueWatching(h.playlistId, h.kind, h.itemKey) }
    }

    fun toggleFavorite(pid: Long, key: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.toggleFavorite(pid, kind, key)) }
    }

    private fun rating(r: Float) = if (r > 0f) "★ " + String.format(Locale.FRANCE, "%.1f", r) else null
}

/** "S2 · É4" out of an episode subtitle saved in the history. */
private fun HistoryEntity.episodeCode(): String? =
    if (kind == Kind.EPISODE) cleanEpisodeSubtitle(subtitle, title)?.substringBefore(" — ") else null

private fun HistoryEntity.remaining(): String? =
    if (duration > 0) formatMinutes((duration - position).coerceAtLeast(0)) + " restantes" else null

private fun HistoryEntity.progress(): Float? = if (duration > 0) position.toFloat() / duration else null

@Composable
fun MoviesHome() = BrowseScreen(appViewModel(key = "browse-movies") { BrowseViewModel(it, Kind.MOVIE) })

@Composable
fun SeriesHome() = BrowseScreen(appViewModel(key = "browse-series") { BrowseViewModel(it, Kind.SERIES) })

@Composable
private fun BrowseScreen(vm: BrowseViewModel) {
    val state by vm.state.collectAsState()
    val hero by vm.hero.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val nav = LocalNav.current
    val shell = LocalShell.current
    val restoreRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(cacheWindow = ShelfAhead)
    val focusManager = LocalFocusManager.current
    val playlist = state.playlist
    val isMovies = vm.kind == Kind.MOVIE
    var removing by remember { mutableStateOf<HistoryEntity?>(null) }
    // After a removal: the card to focus instead (null = back to the top of the screen) once the shelf updates.
    var removedKey by remember { mutableStateOf<String?>(null) }
    var refocusKey by remember { mutableStateOf<String?>(null) }
    val refocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.loaded) {
        if (state.loaded && nav.restoreFocus) {
            delay(80)
            if (!restoreRequester.tryFocus()) shell.focusTabs()
            nav.restoreFocus = false
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
    DisposableEffect(Unit) { onDispose { shell.contentScrolled.value = false } }
    LaunchedEffect(removedKey, state.continueWatching) {
        val gone = removedKey ?: return@LaunchedEffect
        if (state.continueWatching.any { "cw:${it.kind}:${it.itemKey}" == gone }) return@LaunchedEffect
        removedKey = null
        delay(80)
        if (refocusKey == null || !refocusRequester.tryFocus()) {
            if (!shell.contentEntry.tryFocus()) shell.focusTabs()
        }
        refocusKey = null
    }

    fun focusMod(key: String): Modifier = if (key == vm.lastFocused) Modifier.focusRequester(restoreRequester) else Modifier
    fun onFocus(key: String) { vm.lastFocused = key }

    if (!state.loaded || playlist == null) return

    val empty = hero.isEmpty() && state.continueWatching.isEmpty() && state.movies.isEmpty() && state.series.isEmpty() && state.categories.isEmpty()
    if (empty) {
        EmptyState(
            if (isMovies) Icons.Rounded.Movie else Icons.Rounded.VideoLibrary,
            if (isMovies) "Aucun film" else "Aucune série",
            if (!isMovies && !playlist.isXtream) "Les séries sont disponibles avec un compte Xtream Codes."
            else "Cette source ne contient pas de contenu à la demande.",
            Modifier.fillMaxSize().padding(top = 92.dp),
        )
        return
    }

    fun playHistory(h: HistoryEntity) = nav.play(
        PlayRequest.Vod(playlist, listOf(VodItem(h.kind, h.itemKey, h.title, h.subtitle, h.image, h.url, h.parentKey)), 0)
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(shell.contentEntry)
            // Scrolled back to the top, the hero sits under the tab bar: "up" finds nothing above it, so go to the bar.
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || ev.key != Key.DirectionUp) return@onPreviewKeyEvent false
                if (!focusManager.moveFocus(FocusDirection.Up)) shell.focusTabs()
                true
            },
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item("hero") {
            if (hero.isNotEmpty()) {
                Hero(
                    items = hero,
                    vm = vm,
                    favorites = favorites,
                    playMod = focusMod("hero"),
                    onFocusHero = { vm.lastFocused = "hero" },
                    onPrimary = { item ->
                        when {
                            item.movie != null -> {
                                val m = item.movie
                                nav.play(PlayRequest.Vod(playlist, listOf(VodItem(Kind.MOVIE, m.itemKey, m.name, m.year, item.image, m.url, description = item.description)), 0))
                            }
                            item.resume != null -> playHistory(item.resume)
                            item.series != null -> nav.series(item.series.id)
                        }
                    },
                    onSecondary = { item ->
                        item.movie?.let { nav.movie(it.id) } ?: item.series?.let { nav.series(it.id) }
                    },
                    onFavorite = { item ->
                        val key = item.movie?.itemKey ?: item.series?.itemKey ?: return@Hero
                        vm.toggleFavorite(playlist.id, key) { fav -> shell.toast(if (fav) "Ajouté à votre liste" else "Retiré de votre liste") }
                    },
                )
            } else {
                Spacer(Modifier.height(110.dp))
            }
        }
        if (state.continueWatching.isNotEmpty()) {
            shelf(if (isMovies) "Reprendre la lecture" else "Continuer à regarder", "cw", note = "Maintenez OK pour retirer") {
                itemsIndexed(state.continueWatching, key = { _, h -> "cw" + h.kind + h.itemKey }) { _, h ->
                    val key = "cw:${h.kind}:${h.itemKey}"
                    WideCard(
                        title = h.title,
                        subtitle = listOfNotNull(h.episodeCode(), h.remaining()).joinToString(" · ").ifEmpty { null },
                        image = h.image,
                        progress = h.progress(),
                        modifier = focusMod(key).then(if (key == refocusKey) Modifier.focusRequester(refocusRequester) else Modifier),
                        onFocused = { onFocus(key) },
                        onClick = { playHistory(h) },
                        onLongClick = { removing = h },
                    )
                }
            }
        }
        if (isMovies && state.topMovies.size >= 5) {
            shelf("Top 10", "top", note = "Les mieux notés", spacing = 6.dp) {
                itemsIndexed(state.topMovies.take(10), key = { _, m -> "t" + m.id }) { i, m ->
                    val key = "t:${m.itemKey}"
                    RankedPoster(
                        rank = i + 1, title = m.name, image = m.poster,
                        modifier = focusMod(key),
                        onFocused = { onFocus(key) },
                        onClick = { nav.movie(m.id) },
                    )
                }
            }
        }
        if (!isMovies) genres(vm, state, ::focusMod, ::onFocus)
        if (isMovies && state.movies.isNotEmpty()) {
            shelf("Nouveautés", "new") {
                itemsIndexed(state.movies, key = { _, m -> "m" + m.id }) { _, m ->
                    val key = "m:${m.itemKey}"
                    PosterCard(
                        title = m.name, image = m.poster, subtitle = m.year,
                        modifier = focusMod(key),
                        onFocused = { onFocus(key) },
                        onClick = { nav.movie(m.id) },
                        onLongClick = { vm.toggleFavorite(playlist.id, m.itemKey) { fav -> shell.toast(if (fav) "Ajouté à votre liste" else "Retiré de votre liste") } },
                    )
                }
            }
        }
        if (!isMovies && state.series.isNotEmpty()) {
            shelf("Nouveautés", "new") {
                itemsIndexed(state.series, key = { _, s -> "s" + s.id }) { _, s ->
                    val key = "s:${s.itemKey}"
                    PosterCard(
                        title = s.name, image = s.cover,
                        subtitle = listOfNotNull(s.year, s.genre?.split(',', '/')?.firstOrNull()?.trim()).joinToString(" · "),
                        modifier = focusMod(key),
                        onFocused = { onFocus(key) },
                        onClick = { nav.series(s.id) },
                        onLongClick = { vm.toggleFavorite(playlist.id, s.itemKey) { fav -> shell.toast(if (fav) "Ajoutée à votre liste" else "Retirée de votre liste") } },
                    )
                }
            }
        }
        if (isMovies) genres(vm, state, ::focusMod, ::onFocus)
    }

    removing?.let { h ->
        ActionDialog(
            title = h.title.cleanTitle(),
            message = "Retirer de « ${if (isMovies) "Reprendre la lecture" else "Continuer à regarder"} » ? La position de lecture sera oubliée.",
            actions = listOf(
                DialogAction("Retirer", destructive = true) {
                    val list = state.continueWatching
                    val i = list.indexOfFirst { it.kind == h.kind && it.itemKey == h.itemKey }
                    val next = list.getOrNull(i + 1) ?: list.getOrNull(i - 1)
                    refocusKey = next?.let { "cw:${it.kind}:${it.itemKey}" }
                    removedKey = "cw:${h.kind}:${h.itemKey}"
                    vm.lastFocused = refocusKey
                    vm.removeFromContinueWatching(h)
                    removing = null
                },
                DialogAction("Annuler") { removing = null },
            ),
            onDismiss = { removing = null },
        )
    }
}

/** "Parcourir par genre": the provider's categories as tinted tiles, opening the full catalogue. */
private fun LazyListScope.genres(
    vm: BrowseViewModel,
    state: BrowseState,
    focusMod: (String) -> Modifier,
    onFocus: (String) -> Unit,
) {
    val tiles = buildList {
        add(Triple(CAT_ALL, "Tout le catalogue", null as String?))
        add(Triple(CAT_FAVORITES, "Ma liste", null))
        state.categories.forEach { val t = it.name.categoryLabel(vm.kind); add(Triple(it.catId, t.name, t.tag)) }
    }
    shelf(if (vm.kind == Kind.MOVIE) "Parcourir par catégorie" else "Parcourir par genre", "genres") {
        itemsIndexed(tiles, key = { _, t -> "g" + t.first }) { i, (id, name, tag) ->
            val key = "g:$id"
            val nav = LocalNav.current
            GenreTile(
                name = name,
                index = i,
                tag = tag,
                icon = when (id) {
                    CAT_ALL -> Icons.Rounded.Apps
                    CAT_FAVORITES -> Icons.Rounded.Star
                    else -> null
                },
                modifier = focusMod(key),
                onFocused = { onFocus(key) },
                onClick = { nav.catalog(vm.kind, id) },
            )
        }
    }
}

private fun LazyListScope.shelf(
    title: String,
    key: String,
    note: String? = null,
    spacing: androidx.compose.ui.unit.Dp = 16.dp,
    content: LazyListScope.() -> Unit,
) {
    item(key) {
        var rowFocused by remember { mutableStateOf(false) }
        Column {
            // The note of "Reprendre la lecture" (long press) only shows while the shelf has focus.
            val shownNote = if (key == "cw") note.takeIf { rowFocused } else note
            SectionTitle(title, Modifier.padding(horizontal = ScreenEdge), note = shownNote)
            LazyRow(
                state = rememberLazyListState(cacheWindow = ScreenAhead),
                modifier = Modifier.onFocusChanged { rowFocused = it.hasFocus }.focusRestorer(),
                contentPadding = PaddingValues(start = ScreenEdge, end = ScreenEdge, top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}

@Composable
private fun Hero(
    items: List<HeroItem>,
    vm: BrowseViewModel,
    favorites: Set<String>,
    playMod: Modifier,
    onFocusHero: () -> Unit,
    onPrimary: (HeroItem) -> Unit,
    onSecondary: (HeroItem) -> Unit,
    onFavorite: (HeroItem) -> Unit,
) {
    var index by remember { mutableIntStateOf(vm.heroIndex.coerceIn(0, items.size - 1)) }
    var focused by remember { mutableStateOf(false) }
    var focusedButton by remember { mutableIntStateOf(0) }
    val item = items[index.coerceIn(0, items.size - 1)]
    val primary = remember { FocusRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val buttons = 3

    fun go(delta: Int) {
        index = (index + delta + items.size) % items.size
        vm.heroIndex = index
    }

    // Focusing a hero button asks the list to show the whole hero, which keeps the list scrolled to the top
    // (the TV default would scroll the button up to a third of the screen and push the title under the tab bar).
    val showWholeHero = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = Rect(Offset.Zero, size.toSize())
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }

    // Auto-advance like the Apple TV top shelf, paused while the hero has focus.
    LaunchedEffect(index, focused, items.size) {
        if (!focused && items.size > 1) {
            delay(8000)
            go(1)
        }
    }

    Box(Modifier.fillMaxWidth().height(480.dp).onSizeChanged { size = it }.bringIntoViewResponder(showWholeHero)) {
        Crossfade(targetState = item.image, animationSpec = tween(600), label = "hero") { img ->
            Box(Modifier.fillMaxSize()) {
                if (!img.isNullOrBlank()) {
                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1E2440), Color(0xFF0A0A0C)))))
                }
            }
        }
        // Scrims of the design: left to right, then a fade into the page at the bottom.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE60A0A0C), 0.45f to Color(0x800A0A0C), 0.8f to Color(0x000A0A0C))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x990A0A0C), 0.22f to Color.Transparent, 0.62f to Color.Transparent, 1f to C.Background)))

        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = ScreenEdge, end = ScreenEdge, bottom = 36.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Fixed slots (one meta line, three description lines) so the buttons never move when the carousel
            // changes item; only a two-line title grows upwards. No animateContentSize: it clips the focused
            // button's scale and shadow.
            Column(Modifier.width(420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.tag != null) {
                        Badge(item.tag, color = C.Text, textColor = C.OnFocus)
                        if (item.eyebrow.isNotBlank()) Spacer(Modifier.width(6.dp))
                    }
                    if (item.tag == null) Eyebrow(item.eyebrow, color = C.Text2)
                    else Text(item.eyebrow, style = T.Footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = C.Text2, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                Text(item.title, style = T.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                MetaLine(item.meta, item.badges, Modifier.height(16.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    item.description.orEmpty(),
                    style = T.Body,
                    color = C.Text.copy(alpha = 0.85f),
                    minLines = 3,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier
                        .onFocusChanged {
                            focused = it.hasFocus
                            if (it.hasFocus) onFocusHero()
                        }
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                ev.key == Key.DirectionLeft && focusedButton == 0 && items.size > 1 -> { go(-1); true }
                                ev.key == Key.DirectionRight && focusedButton == buttons - 1 && items.size > 1 -> { go(1); true }
                                else -> false
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val resume = item.resume
                    PillButton(
                        when {
                            item.movie != null -> "Lire le film"
                            resume != null -> resume.episodeCode()?.let { "Reprendre $it" } ?: "Reprendre"
                            else -> "Voir la série"
                        },
                        onClick = { onPrimary(item) },
                        icon = Icons.Rounded.PlayArrow,
                        primary = true,
                        modifier = playMod.focusRequester(primary),
                        onFocusChange = { if (it) focusedButton = 0 },
                    )
                    PillButton(
                        if (item.movie != null) "Détails" else "Épisodes",
                        onClick = { onSecondary(item) },
                        icon = if (item.movie != null) Icons.Rounded.Info else Icons.AutoMirrored.Rounded.FormatListBulleted,
                        onFocusChange = { if (it) focusedButton = 1 },
                    )
                    val key = item.movie?.itemKey ?: item.series?.itemKey
                    val inList = key != null && key in favorites
                    IconPill(
                        if (inList) Icons.Rounded.Check else Icons.Rounded.Add,
                        onClick = { onFavorite(item) },
                        onFocusChange = { if (it) focusedButton = 2 },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val resume = item.resume
            if (resume != null) {
                ResumeCard(resume)
            } else if (items.size > 1) {
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(width = if (i == index) 14.dp else 4.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == index) C.Text else Color(0x4DFFFFFF))
                        )
                    }
                }
            }
        }
    }
}

/** Glass card at the bottom right of the series hero: the episode in progress. */
@Composable
private fun ResumeCard(h: HistoryEntity) {
    Row(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(C.Panel)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(4.dp)).background(C.Surface2)) {
            if (!h.image.isNullOrBlank()) {
                AsyncImage(model = h.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            h.progress()?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(3.dp), height = 2.dp) }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow("En cours")
            Spacer(Modifier.height(3.dp))
            Text(
                cleanEpisodeSubtitle(h.subtitle, h.title) ?: h.title.cleanTitle(),
                style = T.Subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            h.remaining()?.let { Text(it, style = T.Caption, color = C.Text2, maxLines = 1) }
        }
    }
}
