package com.iplayer.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.db.HistoryEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.data.remote.EpisodeInfo
import com.iplayer.tv.data.remote.MovieDetails
import com.iplayer.tv.data.remote.SeriesDetails
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.ArtFrame
import com.iplayer.tv.ui.components.ArtShape
import com.iplayer.tv.ui.components.CinemaBackdrop
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.PlayCircle
import com.iplayer.tv.ui.components.ShelfHeader
import com.iplayer.tv.ui.components.TabLabel
import com.iplayer.tv.ui.components.TextAction
import com.iplayer.tv.ui.components.Wordmark
import com.iplayer.tv.ui.components.displaySize
import com.iplayer.tv.ui.components.genresOf
import com.iplayer.tv.ui.components.metaLine
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.cleanTitle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

private fun ratingFrom(s: String?): Float = s?.replace(',', '.')?.toFloatOrNull()?.let { if (it > 10f) it / 10f else it } ?: 0f

/** "2 H 6 MIN" / "45 MIN" */
private fun hoursMinutes(ms: Long): String? {
    val min = ms / 60_000
    if (min <= 0) return null
    return if (min >= 60) "${min / 60} H ${min % 60} MIN" else "$min MIN"
}

/** Keeps the first few names of a long cast list. */
private fun shortCast(s: String?): String? =
    s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)?.joinToString(", ")?.ifEmpty { null }

// ===================================================================== Movie

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    private val repo = c.repository
    val movie = MutableStateFlow<MovieEntity?>(null)
    val details = MutableStateFlow<MovieDetails?>(null)
    val similar = MutableStateFlow<List<MovieEntity>>(emptyList())
    val loaded = MutableStateFlow(false)
    val playlist = repo.activePlaylist

    /** The "you may also like" title the viewer opened, focused again on return. */
    var lastSimilarId: Long? = null

    init {
        viewModelScope.launch {
            val m = repo.movie(id)
            movie.value = m
            loaded.value = true
            if (m != null) {
                launch { similar.value = runCatching { repo.similarMovies(m) }.getOrDefault(emptyList()) }
                details.value = repo.movieDetails(playlist.filterNotNull().first(), m)
            }
        }
    }

    val favorite: StateFlow<Boolean> = movie.filterNotNull().flatMapLatest { repo.isFavorite(it.playlistId, Kind.MOVIE, it.itemKey) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val history: StateFlow<HistoryEntity?> = movie.filterNotNull().flatMapLatest { repo.observeHistory(it.playlistId, Kind.MOVIE, it.itemKey) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun toggleFavorite() {
        val m = movie.value ?: return
        viewModelScope.launch { repo.toggleFavorite(m.playlistId, Kind.MOVIE, m.itemKey) }
    }
}

@Composable
fun MovieDetailScreen(id: Long) {
    val vm = appViewModel(key = "movie$id") { MovieDetailViewModel(it, id) }
    val nav = LocalNav.current
    val movie by vm.movie.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val details by vm.details.collectAsState()
    val similar by vm.similar.collectAsState()
    val fav by vm.favorite.collectAsState()
    val history by vm.history.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val playFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }

    BackHandler { nav.back() }
    LaunchedEffect(movie) {
        if (movie != null) {
            delay(30)
            if (vm.lastSimilarId != null && similarFocus.tryFocus()) {
                vm.lastSimilarId = null
                return@LaunchedEffect
            }
            vm.lastSimilarId = null
            playFocus.tryFocus()
        }
    }

    val m = movie
    val p = playlist
    if (m == null || p == null) {
        if (loaded) EmptyState(Icons.Rounded.PlayArrow, "Film introuvable", "Actualisez votre playlist.", Modifier.fillMaxSize())
        else Loading(Modifier.fillMaxSize())
        return
    }
    val d = details
    val h = history
    val resumable = h != null && h.position > 30_000 && (h.duration <= 0 || h.position < h.duration * 0.94)

    fun play(fromStart: Boolean) {
        val url = if (d?.extension != null && p.isXtream && m.streamId > 0) m.url.substringBeforeLast('.') + "." + d.extension else m.url
        nav.play(
            PlayRequest.Vod(
                p,
                listOf(
                    VodItem(
                        kind = Kind.MOVIE, key = m.itemKey, title = m.name, subtitle = m.year, image = d?.backdrop ?: m.poster,
                        url = url, startPosition = if (fromStart) 0 else -1, description = d?.plot,
                    )
                ),
                0,
            )
        )
    }

    val year = d?.releaseDate?.take(4)?.takeIf { it.isNotBlank() } ?: m.year
    val runtime = d?.durationSecs?.takeIf { it > 0 }?.let { hoursMinutes(it * 1000L) }
    val progress = if (resumable && h != null && h.duration > 0) h.position.toFloat() / h.duration else null

    CinemaLayout(
        backdrop = d?.backdrop,
        fallbackImage = m.poster,
        rating = if (m.rating > 0f) m.rating else ratingFrom(d?.rating),
        play = {
            PlayCluster(
                above = runtime,
                below = when {
                    !resumable -> null
                    h != null && h.duration > 0 -> "REPRENDRE · RESTE ${hoursMinutes(h.duration - h.position) ?: "1 MIN"}"
                    else -> "REPRENDRE"
                },
                progress = progress,
                onClick = { play(false) },
                modifier = Modifier.focusRequester(playFocus),
            )
        },
        actions = {
            TextAction(
                if (fav) "DANS MA LISTE" else "AJOUTER À MA LISTE",
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                onClick = { vm.toggleFavorite() },
            )
            if (resumable) TextAction("DEPUIS LE DÉBUT", Icons.Rounded.Replay, onClick = { play(true) }, iconTint = null)
        },
        info = {
            InfoBlock(
                genres = genresOf(d?.genre),
                title = m.name.cleanTitle(),
                meta = listOfNotNull(
                    year?.let { null to it },
                    d?.director?.takeIf { it.isNotBlank() }?.let { "RÉALISATION" to it },
                    shortCast(d?.cast)?.let { "AVEC" to it },
                ),
                plot = d?.plot,
            )
        },
        side = {
            if (similar.isNotEmpty()) {
                val state = rememberLazyListState()
                ShelfHeader("Vous aimerez aussi", Modifier.padding(end = 48.dp), state)
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = state,
                    contentPadding = PaddingValues(end = 48.dp, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(similar, key = { it.id }) { s ->
                        MiniPoster(
                            title = s.name,
                            image = s.poster,
                            onClick = {
                                vm.lastSimilarId = s.id
                                nav.movie(s.id)
                            },
                            modifier = if (s.id == vm.lastSimilarId) Modifier.focusRequester(similarFocus) else Modifier,
                        )
                    }
                }
            }
        },
    )
}

// ===================================================================== Series

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    private val repo = c.repository
    val series = MutableStateFlow<SeriesEntity?>(null)
    val details = MutableStateFlow<SeriesDetails?>(null)
    val similar = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val season = MutableStateFlow<Int?>(null)

    /** True when the "you may also like" tab is shown instead of the episodes. */
    val showSimilar = MutableStateFlow(false)
    val playlist = repo.activePlaylist
    var lastEpisodeId: String? = null
    var lastSimilarId: Long? = null
    var seasonFromHistory = false

    init { load(id) }

    fun load(id: Long) {
        error.value = null
        viewModelScope.launch {
            val s = repo.series(id) ?: run { error.value = "Série introuvable."; return@launch }
            series.value = s
            if (similar.value.isEmpty()) launch { similar.value = runCatching { repo.similarSeries(s) }.getOrDefault(emptyList()) }
            val p = playlist.filterNotNull().first()
            repo.cachedSeriesDetails(p, s)?.let { cached ->
                details.value = cached
                if (season.value == null) season.value = cached.seasons.firstOrNull()?.number
            }
            try {
                val d = repo.seriesDetails(p, s)
                details.value = d
                if (season.value == null) season.value = d.seasons.firstOrNull()?.number
            } catch (e: Exception) {
                error.value = e.message ?: "Impossible de charger les épisodes."
            }
        }
    }

    val favorite: StateFlow<Boolean> = series.filterNotNull().flatMapLatest { repo.isFavorite(it.playlistId, Kind.SERIES, it.itemKey) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val episodeHistory: StateFlow<Map<String, HistoryEntity>> = series.filterNotNull()
        .flatMapLatest { repo.episodeHistory(it.playlistId, it.itemKey) }
        .map { list -> list.associateBy { it.itemKey } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Last watched episode first (in "updatedAt" order). */
    val lastWatched: StateFlow<HistoryEntity?> = series.filterNotNull()
        .flatMapLatest { repo.episodeHistory(it.playlistId, it.itemKey) }
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun toggleFavorite() {
        val s = series.value ?: return
        viewModelScope.launch { repo.toggleFavorite(s.playlistId, Kind.SERIES, s.itemKey) }
    }
}

@Composable
fun SeriesDetailScreen(id: Long) {
    val vm = appViewModel(key = "series$id") { SeriesDetailViewModel(it, id) }
    val nav = LocalNav.current
    val series by vm.series.collectAsState()
    val details by vm.details.collectAsState()
    val similar by vm.similar.collectAsState()
    val error by vm.error.collectAsState()
    val season by vm.season.collectAsState()
    val showSimilar by vm.showSimilar.collectAsState()
    val fav by vm.favorite.collectAsState()
    val epHistory by vm.episodeHistory.collectAsState()
    val last by vm.lastWatched.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val playFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }

    BackHandler { nav.back() }

    val s = series
    val p = playlist
    val d = details
    LaunchedEffect(d != null, s != null) {
        if (s == null) return@LaunchedEffect
        delay(40)
        if (vm.lastSimilarId != null) {
            val ok = similarFocus.tryFocus()
            vm.lastSimilarId = null
            if (ok) return@LaunchedEffect
        }
        if (nav.restoreFocus && vm.lastEpisodeId != null && episodeFocus.tryFocus()) {
            nav.restoreFocus = false
            return@LaunchedEffect
        }
        playFocus.tryFocus()
    }

    if (s == null || p == null) {
        if (error != null) EmptyState(Icons.Rounded.PlayArrow, "Série introuvable", error, Modifier.fillMaxSize())
        else Loading(Modifier.fillMaxSize())
        return
    }

    val allEpisodes: List<EpisodeInfo> = d?.let { dd -> dd.seasons.flatMap { dd.episodes[it.number].orEmpty() } }.orEmpty()

    // Open on the season the viewer is currently watching.
    LaunchedEffect(d, last) {
        val lw = last ?: return@LaunchedEffect
        if (vm.seasonFromHistory) return@LaunchedEffect
        val ep = allEpisodes.firstOrNull { it.id == lw.itemKey } ?: return@LaunchedEffect
        vm.seasonFromHistory = true
        vm.season.value = ep.season
        if (vm.lastEpisodeId == null) vm.lastEpisodeId = ep.id
    }

    fun itemsFor(): List<VodItem> {
        return allEpisodes.map { ep ->
            VodItem(
                kind = Kind.EPISODE,
                key = ep.id,
                title = s.name,
                subtitle = "S${ep.season} · É${ep.episode} — ${ep.title}",
                image = ep.image ?: d?.backdrop ?: s.cover,
                url = episodeUrl(p, ep),
                parentKey = s.itemKey,
                startPosition = -1,
                description = ep.plot,
            )
        }
    }

    fun playEpisode(ep: EpisodeInfo, fromStart: Boolean = false) {
        vm.lastEpisodeId = ep.id
        val items = itemsFor()
        val idx = items.indexOfFirst { it.key == ep.id }.coerceAtLeast(0)
        val list = if (fromStart) items.mapIndexed { i, it -> if (i == idx) it.copy(startPosition = 0) else it } else items
        nav.play(PlayRequest.Vod(p, list, idx))
    }

    // Which episode the main button resumes / starts.
    val resumeTarget: Pair<EpisodeInfo, Boolean>? = run {
        if (allEpisodes.isEmpty()) return@run null
        val lw = last
        if (lw == null) return@run allEpisodes.first() to false
        val idx = allEpisodes.indexOfFirst { it.id == lw.itemKey }
        if (idx < 0) return@run allEpisodes.first() to false
        val finished = lw.duration > 0 && lw.position >= lw.duration * 0.94
        if (finished) allEpisodes.getOrNull(idx + 1)?.let { it to false } ?: (allEpisodes[idx] to false)
        else allEpisodes[idx] to (lw.position > 30_000)
    }
    val target = resumeTarget
    val targetProgress = target?.takeIf { it.second }?.let { epHistory[it.first.id] }
        ?.let { if (it.duration > 0) it.position.toFloat() / it.duration else null }

    val seasonCount = d?.seasons?.size ?: 0
    CinemaLayout(
        backdrop = d?.backdrop ?: s.backdrop,
        fallbackImage = s.cover,
        rating = if (s.rating > 0f) s.rating else ratingFrom(d?.rating),
        play = {
            PlayCluster(
                above = target?.first?.let { "S${it.season} · É${it.episode}" },
                below = when {
                    target == null -> if (error != null) "RÉESSAYER" else "CHARGEMENT…"
                    target.second -> "REPRENDRE"
                    else -> null
                },
                progress = targetProgress,
                loading = target == null && error == null,
                retry = target == null && error != null,
                onClick = { if (target != null) playEpisode(target.first) else if (error != null) vm.load(id) },
                modifier = Modifier.focusRequester(playFocus),
            )
        },
        actions = {
            TextAction(
                if (fav) "DANS MA LISTE" else "AJOUTER À MA LISTE",
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                onClick = { vm.toggleFavorite() },
            )
            if (target != null && target.second) {
                TextAction("ÉPISODE DEPUIS LE DÉBUT", Icons.Rounded.Replay, onClick = { playEpisode(target.first, fromStart = true) }, iconTint = null)
            }
        },
        info = {
            InfoBlock(
                genres = genresOf(d?.genre ?: s.genre),
                title = s.name.cleanTitle(),
                meta = listOfNotNull(
                    (d?.releaseDate?.take(4)?.takeIf { it.isNotBlank() } ?: s.year)?.let { null to it },
                    seasonCount.takeIf { it > 0 }?.let { null to if (it > 1) "$it SAISONS" else "1 SAISON" },
                    d?.director?.takeIf { it.isNotBlank() }?.let { "CRÉATION" to it },
                    shortCast(d?.cast)?.let { "AVEC" to it },
                ),
                plot = d?.plot ?: s.plot,
            )
        },
        side = {
            val seasons = d?.seasons.orEmpty()
            val tabsState = rememberLazyListState()
            if (seasons.isNotEmpty() || similar.isNotEmpty()) {
                LazyRow(
                    state = tabsState,
                    contentPadding = PaddingValues(end = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.focusRestorer().offset(x = (-12).dp),
                ) {
                    items(seasons, key = { "s${it.number}" }) { se ->
                        val pick = {
                            vm.showSimilar.value = false
                            vm.season.value = se.number
                        }
                        TabLabel(
                            if (se.number > 0) "Saison ${se.number}" else se.name,
                            selected = !showSimilar && se.number == season,
                            onClick = pick,
                            onFocused = pick,
                            height = 32.dp,
                        )
                    }
                    if (similar.isNotEmpty()) {
                        item(key = "similar") {
                            TabLabel(
                                "Vous aimerez aussi",
                                selected = showSimilar || seasons.isEmpty(),
                                onClick = { vm.showSimilar.value = true },
                                onFocused = { vm.showSimilar.value = true },
                                height = 32.dp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            when {
                (showSimilar || seasons.isEmpty()) && similar.isNotEmpty() -> {
                    LazyRow(
                        contentPadding = PaddingValues(end = 48.dp, top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        items(similar, key = { it.id }) { o ->
                            MiniPoster(
                                title = o.name,
                                image = o.cover,
                                onClick = {
                                    vm.lastSimilarId = o.id
                                    nav.series(o.id)
                                },
                                modifier = if (o.id == vm.lastSimilarId) Modifier.focusRequester(similarFocus) else Modifier,
                            )
                        }
                    }
                }
                d != null && seasons.isNotEmpty() -> {
                    val episodes = season?.let { d.episodes[it] }.orEmpty()
                    val rowState = rememberLazyListState()
                    LaunchedEffect(season) {
                        val idx = episodes.indexOfFirst { it.id == vm.lastEpisodeId }
                        rowState.scrollToItem(idx.coerceAtLeast(0))
                    }
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(end = 48.dp, top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            val eh = epHistory[ep.id]
                            EpisodeTile(
                                ep = ep,
                                image = ep.image ?: d.backdrop ?: s.cover,
                                progress = eh?.let { if (it.duration > 0) it.position.toFloat() / it.duration else null },
                                modifier = if (ep.id == vm.lastEpisodeId) Modifier.focusRequester(episodeFocus) else Modifier,
                                onClick = { playEpisode(ep) },
                                onLongClick = { playEpisode(ep, fromStart = true) },
                            )
                        }
                    }
                }
                error != null -> Text(error ?: "", style = T.Subhead, color = C.Red, maxLines = 3, modifier = Modifier.padding(end = 48.dp))
            }
        },
    )
}

private fun episodeUrl(p: PlaylistEntity, ep: EpisodeInfo): String {
    val base = com.iplayer.tv.data.remote.XtreamClient.normalizeServer(p.url)
    return "$base/series/${p.username}/${p.password}/${ep.id}.${ep.extension}"
}

// ===================================================================== cinema layout

private val SidePadding = 64.dp

/**
 * Full-bleed "film poster" page: the backdrop fills the screen, rating / play / actions float
 * in the middle, the title block sits bottom-left and a row of artwork bottom-right.
 */
@Composable
private fun CinemaLayout(
    backdrop: String?,
    fallbackImage: String?,
    rating: Float,
    play: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    info: @Composable ColumnScope.() -> Unit,
    side: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(C.Background)) {
        CinemaBackdrop(backdrop, fallbackImage)

        Wordmark(Modifier.align(Alignment.TopCenter).padding(top = 26.dp))

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = SidePadding, end = 48.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { if (rating > 0f) RatingLabel(rating) }
                    play()
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 34.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(0.48f).padding(start = SidePadding, end = 36.dp), content = info)
                Column(Modifier.weight(0.52f), content = side)
            }
        }
    }
}

/** Round, outlined play button with a gold progress ring and a label above / below. */
@Composable
private fun PlayCluster(
    above: String?,
    below: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    retry: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(above ?: "", style = T.Label, color = C.Text, maxLines = 1, modifier = Modifier.height(16.dp))
        Spacer(Modifier.height(12.dp))
        PlayCircle(onClick = onClick, modifier = modifier, progress = progress, loading = loading, retry = retry)
        Spacer(Modifier.height(12.dp))
        Text(below ?: "", style = T.Label, color = C.Text2, maxLines = 1, modifier = Modifier.height(16.dp))
    }
}

/** Genres, big title, credits and synopsis. */
@Composable
private fun InfoBlock(genres: List<String>, title: String, meta: List<Pair<String?, String>>, plot: String?) {
    if (genres.isNotEmpty()) {
        Text(metaLine(genres.map { null to it }, bold = true), style = T.Label, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
    }
    val size = displaySize(title)
    Text(
        title,
        style = T.Display.copy(fontSize = size, lineHeight = size * 1.08f),
        color = C.Text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (meta.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(metaLine(meta), style = T.Subhead, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (!plot.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(plot.trim(), style = T.Footnote.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp), color = C.Text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Small 2:3 artwork with a centered two-line caption, as in a film credits strip. */
@Composable
private fun MiniPoster(title: String, image: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val display = remember(title) { title.cleanTitle() }
    var focused by remember { mutableStateOf(false) }
    val shape = ArtShape
    Column(modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = shape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.12f,
            elevation = 22.dp,
            onFocusChange = { focused = it },
        ) { f ->
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2C2C30), Color(0xFF1C1C1F)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(display, style = T.Caption, color = C.Text3, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
            }
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            ArtFrame(f)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            display,
            style = T.Caption.copy(fontSize = 10.sp, lineHeight = 13.sp),
            color = if (focused) C.Text else C.Text2,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 16:9 episode still with number, title, duration and a gold progress line. */
@Composable
private fun EpisodeTile(
    ep: EpisodeInfo,
    image: String?,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 168.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = ArtShape
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 9f / 16f),
            shape = shape,
            color = C.Surface,
            focusedColor = C.Surface,
            focusedContentColor = C.Text,
            focusedScale = 1.08f,
            elevation = 22.dp,
            onFocusChange = { focused = it },
        ) { f ->
            Box(Modifier.fillMaxSize().background(C.Surface2))
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xB3000000))))
            Text(
                "É${ep.episode}",
                style = T.Label,
                color = C.Text,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = if (progress != null && progress > 0f) 12.dp else 7.dp),
            )
            if (progress != null && progress > 0f) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), color = C.Gold, track = Color(0x40FFFFFF), height = 2.dp)
            }
            ArtFrame(f)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            ep.title.ifBlank { "Épisode ${ep.episode}" },
            style = T.Caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (focused) C.Text else C.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val dur = ep.durationSecs.takeIf { it > 0 }?.let { hoursMinutes(it * 1000L) }
        Text(dur ?: " ", style = T.Caption.copy(fontSize = 10.sp, letterSpacing = 0.8.sp), color = C.Text3, maxLines = 1)
    }
}
