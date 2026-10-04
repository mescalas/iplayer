package com.iplayer.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.iplayer.tv.ui.components.Badge
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.IconPill
import com.iplayer.tv.ui.components.InfoPills
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.WideCard
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.episodeSubtitle
import com.iplayer.tv.util.episodeTitle
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

private fun ratingText(r: Float): String? = if (r > 0f) "★ " + String.format(Locale.ROOT, "%.1f", r) else null

private fun ratingFrom(s: String?): Float = s?.replace(',', '.')?.toFloatOrNull()?.let { if (it > 10f) it / 10f else it } ?: 0f

// ===================================================================== Movie

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    private val repo = c.repository
    val movie = MutableStateFlow<MovieEntity?>(null)
    val details = MutableStateFlow<MovieDetails?>(null)
    val loaded = MutableStateFlow(false)
    val playlist = repo.activePlaylist

    init {
        viewModelScope.launch {
            val m = repo.movie(id)
            movie.value = m
            loaded.value = true
            if (m != null) details.value = repo.movieDetails(playlist.filterNotNull().first(), m)
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
    val fav by vm.favorite.collectAsState()
    val history by vm.history.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val playFocus = remember { FocusRequester() }

    BackHandler { nav.back() }
    LaunchedEffect(movie) {
        if (movie != null) {
            delay(30)
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

    val name = remember(m.name) { m.name.mediaName() }
    DetailLayout(backdrop = d?.backdrop ?: m.poster, poster = m.poster) {
        Text(name.title, style = T.LargeTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        MetaRow(
            listOfNotNull(
                (d?.releaseDate?.take(4) ?: m.year ?: name.year),
                d?.durationSecs?.takeIf { it > 0 }?.let { formatMinutes(it * 1000L) },
                d?.genre,
            ),
            ratingText(if (m.rating > 0f) m.rating else ratingFrom(d?.rating)),
            name.techBadges,
        )
        Spacer(Modifier.height(16.dp))
        if (!d?.plot.isNullOrBlank()) {
            Text(d!!.plot!!, style = T.Body, color = C.Text2, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
        }
        d?.director?.let { Credit("Réalisation", it) }
        d?.cast?.let { Credit("Avec", it) }
        Spacer(Modifier.height(24.dp))
        if (resumable && h != null && h.duration > 0) {
            Text("Reste ${formatMinutes(h.duration - h.position)}", style = T.Footnote, color = C.Text2)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                if (resumable) "Reprendre" else "Lecture",
                onClick = { play(false) },
                icon = Icons.Rounded.PlayArrow,
                primary = true,
                modifier = Modifier.focusRequester(playFocus),
            )
            if (resumable) PillButton("Depuis le début", onClick = { play(true) }, icon = Icons.Rounded.Replay)
            IconPill(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, onClick = { vm.toggleFavorite() }, tint = if (fav) C.Red else null)
        }
    }
}

// ===================================================================== Series

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    private val repo = c.repository
    val series = MutableStateFlow<SeriesEntity?>(null)
    val details = MutableStateFlow<SeriesDetails?>(null)
    val error = MutableStateFlow<String?>(null)
    val season = MutableStateFlow<Int?>(null)
    val playlist = repo.activePlaylist
    var lastEpisodeId: String? = null
    var seasonFromHistory = false

    init { load(id) }

    fun load(id: Long) {
        error.value = null
        viewModelScope.launch {
            val s = repo.series(id) ?: run { error.value = "Série introuvable."; return@launch }
            series.value = s
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
    val error by vm.error.collectAsState()
    val season by vm.season.collectAsState()
    val fav by vm.favorite.collectAsState()
    val epHistory by vm.episodeHistory.collectAsState()
    val last by vm.lastWatched.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val playFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }

    BackHandler { nav.back() }

    val s = series
    val p = playlist
    val d = details
    LaunchedEffect(d != null, s != null) {
        if (s == null) return@LaunchedEffect
        delay(40)
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
                subtitle = episodeSubtitle(ep.season, ep.episode, episodeTitle(ep.title, s.name)),
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

    val name = remember(s.name) { s.name.mediaName() }
    DetailLayout(backdrop = d?.backdrop ?: s.backdrop ?: s.cover, poster = null, top = 34.dp) {
        Text(name.title, style = T.Title1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        MetaRow(
            listOfNotNull(
                d?.releaseDate?.take(4) ?: s.year ?: name.year,
                d?.seasons?.size?.takeIf { it > 0 }?.let { if (it > 1) "$it saisons" else "1 saison" },
                d?.genre ?: s.genre,
            ),
            ratingText(if (s.rating > 0f) s.rating else ratingFrom(d?.rating)),
            name.techBadges,
        )
        Spacer(Modifier.height(14.dp))
        val plot = d?.plot ?: s.plot
        if (!plot.isNullOrBlank()) {
            Text(plot, style = T.Body, color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val target = resumeTarget
            PillButton(
                when {
                    target == null -> if (error != null) "Réessayer" else "Chargement…"
                    target.second -> "Reprendre S${target.first.season} É${target.first.episode}"
                    else -> "Lecture S${target.first.season} É${target.first.episode}"
                },
                onClick = { if (target != null) playEpisode(target.first) else if (error != null) vm.load(id) },
                icon = Icons.Rounded.PlayArrow,
                primary = true,
                modifier = Modifier.focusRequester(playFocus),
            )
            IconPill(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, onClick = { vm.toggleFavorite() }, tint = if (fav) C.Red else null)
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error ?: "", style = T.Subhead, color = C.Red)
        }
    }

    // Seasons + episodes, anchored at the bottom of the screen
    if (d != null && d.seasons.isNotEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 24.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    itemsIndexed(d.seasons, key = { _, it -> it.number }) { _, se ->
                        val sel = se.number == season
                        FocusSurface(
                            onClick = { vm.season.value = se.number },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (sel) C.Surface3 else Color(0x33FFFFFF),
                            contentColor = if (sel) C.Text else C.Text2,
                            contentAlignment = Alignment.Center,
                            onFocusChange = { if (it) vm.season.value = se.number },
                        ) {
                            Text(se.name, style = T.Callout.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
                val episodes = season?.let { d.episodes[it] }.orEmpty()
                val rowState = rememberLazyListState()
                LaunchedEffect(season) {
                    val idx = episodes.indexOfFirst { it.id == vm.lastEpisodeId }
                    rowState.scrollToItem(idx.coerceAtLeast(0))
                }
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    itemsIndexed(episodes, key = { _, e -> e.id }) { _, ep ->
                        val eh = epHistory[ep.id]
                        val epTitle = remember(ep.title, s.name) { episodeTitle(ep.title, s.name) }
                        WideCard(
                            title = epTitle?.let { "${ep.episode}. $it" } ?: "Épisode ${ep.episode}",
                            cleanup = false,
                            subtitle = ep.durationSecs.takeIf { it > 0 }?.let { formatMinutes(it * 1000L) },
                            image = ep.image ?: d.backdrop ?: s.cover,
                            width = 200.dp,
                            progress = eh?.let { if (it.duration > 0) it.position.toFloat() / it.duration else null },
                            modifier = if (ep.id == vm.lastEpisodeId) Modifier.focusRequester(episodeFocus) else Modifier,
                            onClick = { playEpisode(ep) },
                            onLongClick = { playEpisode(ep, fromStart = true) },
                        )
                    }
                }
            }
        }
    }
}

private fun episodeUrl(p: PlaylistEntity, ep: EpisodeInfo): String {
    val base = com.iplayer.tv.data.remote.XtreamClient.normalizeServer(p.url)
    return "$base/series/${p.username}/${p.password}/${ep.id}.${ep.extension}"
}

// ===================================================================== shared layout

@Composable
private fun DetailLayout(backdrop: String?, poster: String?, top: androidx.compose.ui.unit.Dp = 56.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Background)) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to C.Background, 0.36f to C.Background, 0.7f to Color(0x80000000), 1f to Color(0x10000000))
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.padding(start = 56.dp, top = top).width(540.dp)) { content() }
        if (backdrop.isNullOrBlank() && !poster.isNullOrBlank()) {
            PosterCard(title = "", image = poster, onClick = {}, width = 220.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 96.dp))
        }
    }
}

@Composable
private fun MetaRow(parts: List<String>, rating: String?, badges: List<String> = emptyList()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rating != null) {
            Badge(rating, color = Color(0x33FFFFFF))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            parts.filter { it.isNotBlank() }.joinToString("  ·  "),
            style = T.Callout,
            color = C.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, false),
        )
        if (badges.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            InfoPills(badges, color = C.Text2)
        }
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label  ", style = T.Subhead.copy(fontWeight = FontWeight.SemiBold), color = C.Text2)
        Text(value, style = T.Subhead, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
