package com.iplayer.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
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
import com.iplayer.tv.ui.components.BackPill
import com.iplayer.tv.ui.components.CastMember
import com.iplayer.tv.ui.components.Chip
import com.iplayer.tv.ui.components.Clock
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.EpisodeCard
import com.iplayer.tv.ui.components.Eyebrow
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.IconPill
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.MetaLine
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.ScreenAhead
import com.iplayer.tv.ui.components.ScreenEdge
import com.iplayer.tv.ui.components.SectionTitle
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.components.focusWhenReady
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.episodeSubtitle
import com.iplayer.tv.util.episodeTitle
import com.iplayer.tv.util.formatMinutes
import com.iplayer.tv.util.mediaName
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

private fun ratingLabel(r: Float) = String.format(Locale.FRANCE, "%.1f", r)

private fun names(s: String?): List<String> =
    s.orEmpty().split(',', ';', '/').map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() != "n/a" }.distinct()

// ===================================================================== Movie

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    private val repo = c.repository
    val movie = MutableStateFlow<MovieEntity?>(null)
    val details = MutableStateFlow<MovieDetails?>(null)
    val similar = MutableStateFlow<List<MovieEntity>>(emptyList())
    /** Other entries of the provider for the same film (VF / VOSTFR / 4K…). */
    val versions = MutableStateFlow<List<MovieEntity>>(emptyList())
    val loaded = MutableStateFlow(false)
    val playlist = repo.activePlaylist
    /** Restore target of the "Vous aimerez aussi" shelf (the card opened before coming back). */
    val restore = FocusRequester()
    var restoreId = -1L

    init {
        viewModelScope.launch {
            val m = repo.movie(id)
            movie.value = m
            loaded.value = true
            if (m == null) return@launch
            launch { similar.value = runCatching { repo.similarMovies(m) }.getOrDefault(emptyList()) }
            launch {
                val title = m.name.mediaName().title
                val found = runCatching { repo.search(m.playlistId, title).movies }.getOrDefault(emptyList())
                    .filter { it.name.mediaName().title.equals(title, ignoreCase = true) }
                versions.value = if (found.size > 1) (listOf(m) + found.filter { it.id != m.id }).take(5) else emptyList()
            }
            details.value = repo.movieDetails(playlist.filterNotNull().first(), m)
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
    val versions by vm.versions.collectAsState()
    val fav by vm.favorite.collectAsState()
    val history by vm.history.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val playFocus = remember { FocusRequester() }

    BackHandler { nav.back() }
    LaunchedEffect(movie) {
        if (movie != null) {
            delay(30)
            if (!(nav.restoreFocus && vm.restore.tryFocus())) playFocus.focusWhenReady()
            nav.restoreFocus = false
        }
    }

    val m = movie
    val p = playlist
    if (m == null || p == null) {
        if (loaded) EmptyState(Icons.Rounded.PlayArrow, "Film introuvable", "Actualisez votre source.", Modifier.fillMaxSize())
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
    val rating = if (m.rating > 0f) m.rating else ratingFrom(d?.rating)
    val genres = names(d?.genre).take(2)
    val director = names(d?.director)
    val cast = names(d?.cast)

    DetailPage(backdrop = d?.backdrop ?: m.poster, backLabel = "Films", heroHeight = 540.dp) {
        heroItem(
            credits = listOfNotNull(
                director.takeIf { it.isNotEmpty() }?.let { "Réalisation" to it.joinToString(", ") },
                cast.takeIf { it.isNotEmpty() }?.let { "Avec" to it.take(5).joinToString(", ") },
                "Source" to listOf(p.name, if (p.isXtream) "Xtream Codes" else "M3U").joinToString(" · "),
            ),
        ) {
            Text(name.title, style = T.Display.copy(fontSize = 60.sp, lineHeight = 62.sp, letterSpacing = (-2.5).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            MetaLine(
                genres + listOfNotNull(d?.releaseDate?.take(4) ?: m.year ?: name.year, d?.durationSecs?.takeIf { it > 0 }?.let { formatMinutes(it * 1000L) }),
                name.techBadges,
                separator = "  ·  ",
            )
            if (rating > 0f) {
                Spacer(Modifier.height(10.dp))
                RatingLine(rating)
            }
            Spacer(Modifier.height(12.dp))
            if (!d?.plot.isNullOrBlank()) {
                Text(d!!.plot!!, style = T.Body, color = C.Text.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    if (resumable) "Reprendre" else "Lire le film",
                    onClick = { play(false) },
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    modifier = Modifier.focusRequester(playFocus),
                )
                if (resumable) PillButton("Depuis le début", onClick = { play(true) }, icon = Icons.Rounded.Replay)
                IconPill(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, onClick = { vm.toggleFavorite() })
            }
            if (resumable && h != null && h.duration > 0) {
                Spacer(Modifier.height(12.dp))
                ResumeLine(h)
            }
            if (versions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version", style = T.Footnote, color = C.Text3)
                    Spacer(Modifier.width(2.dp))
                    versions.forEachIndexed { i, v ->
                        val vn = v.name.mediaName()
                        Chip(
                            vn.badges.joinToString(" · ").ifEmpty { "Version ${i + 1}" },
                            selected = v.id == m.id,
                            onClick = { if (v.id != m.id) nav.movie(v.id) },
                        )
                    }
                }
            }
        }
        if (cast.isNotEmpty() || director.isNotEmpty()) {
            castRow("Distribution et équipe", cast.map { it to null } + director.map { it to "Réalisation" })
        }
        if (similar.isNotEmpty()) {
            shelf("Vous aimerez aussi", "similar") {
                itemsIndexed(similar, key = { _, it -> it.id }) { _, s ->
                    PosterCard(
                        s.name, s.poster, subtitle = s.year,
                        modifier = if (s.id == vm.restoreId) Modifier.focusRequester(vm.restore) else Modifier,
                        onFocused = { vm.restoreId = s.id },
                        onClick = { nav.movie(s.id) },
                    )
                }
            }
        }
        infoGrid(
            listOfNotNull(
                d?.durationSecs?.takeIf { it > 0 }?.let { "Durée" to formatMinutes(it * 1000L) },
                (d?.releaseDate?.takeIf { it.isNotBlank() } ?: m.year)?.let { "Sortie" to it },
                names(d?.genre).takeIf { it.isNotEmpty() }?.let { "Genre" to it.joinToString(", ") },
                if (rating > 0f) "Note" to "${ratingLabel(rating)} / 10" else null,
                name.badges.takeIf { it.isNotEmpty() }?.let { "Format" to it.joinToString(" · ") },
                (d?.extension ?: m.extension)?.let { "Fichier" to it.uppercase() },
                "Source" to p.name,
            )
        )
    }
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
    val playlist = repo.activePlaylist
    var lastEpisodeId: String? = null
    var seasonFromHistory = false

    init { load(id) }

    fun load(id: Long) {
        error.value = null
        viewModelScope.launch {
            val s = repo.series(id) ?: run { error.value = "Série introuvable."; return@launch }
            series.value = s
            launch { similar.value = runCatching { repo.similarSeries(s) }.getOrDefault(emptyList()) }
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
        playFocus.focusWhenReady()
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

    fun itemsFor(): List<VodItem> = allEpisodes.map { ep ->
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

    fun playEpisode(ep: EpisodeInfo, fromStart: Boolean = false) {
        vm.lastEpisodeId = ep.id
        val items = itemsFor()
        val idx = items.indexOfFirst { it.key == ep.id }.coerceAtLeast(0)
        val list = if (fromStart) items.mapIndexed { i, it -> if (i == idx) it.copy(startPosition = 0) else it } else items
        nav.play(PlayRequest.Vod(p, list, idx))
    }

    fun watched(h: HistoryEntity?) = h != null && h.duration > 0 && h.position >= h.duration * 0.94

    // Which episode the main button resumes / starts.
    val resumeTarget: Pair<EpisodeInfo, Boolean>? = run {
        if (allEpisodes.isEmpty()) return@run null
        val lw = last ?: return@run allEpisodes.first() to false
        val idx = allEpisodes.indexOfFirst { it.id == lw.itemKey }
        if (idx < 0) return@run allEpisodes.first() to false
        if (watched(lw)) allEpisodes.getOrNull(idx + 1)?.let { it to false } ?: (allEpisodes[idx] to false)
        else allEpisodes[idx] to (lw.position > 30_000)
    }

    val name = remember(s.name) { s.name.mediaName() }
    val rating = if (s.rating > 0f) s.rating else ratingFrom(d?.rating)
    val cast = names(d?.cast)
    val director = names(d?.director)
    val seasons = d?.seasons.orEmpty()

    DetailPage(backdrop = d?.backdrop ?: s.backdrop ?: s.cover, backLabel = "Séries", heroHeight = 500.dp) {
        heroItem(
            credits = listOfNotNull(
                director.takeIf { it.isNotEmpty() }?.let { "Création" to it.joinToString(", ") },
                cast.takeIf { it.isNotEmpty() }?.let { "Avec" to it.take(5).joinToString(", ") },
                "Source" to listOf(p.name, if (p.isXtream) "Xtream Codes" else "M3U").joinToString(" · "),
            ),
        ) {
            Text(name.title, style = T.Display.copy(fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = (-2.25).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            MetaLine(
                names(d?.genre ?: s.genre).take(2) + listOfNotNull(
                    d?.releaseDate?.take(4) ?: s.year ?: name.year,
                    seasons.size.takeIf { it > 0 }?.let { if (it > 1) "$it saisons" else "1 saison" },
                    if (rating > 0f) "★ ${ratingLabel(rating)}" else null,
                ),
                name.techBadges,
                separator = "  ·  ",
            )
            Spacer(Modifier.height(12.dp))
            val plot = d?.plot ?: s.plot
            if (!plot.isNullOrBlank()) {
                Text(plot, style = T.Body, color = C.Text.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val target = resumeTarget
                PillButton(
                    when {
                        target == null -> if (error != null) "Réessayer" else "Chargement…"
                        target.second -> "Reprendre S${target.first.season} · É${target.first.episode}"
                        else -> "Lire S${target.first.season} · É${target.first.episode}"
                    },
                    onClick = { if (target != null) playEpisode(target.first) else if (error != null) vm.load(id) },
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    modifier = Modifier.focusRequester(playFocus),
                )
                IconPill(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, onClick = { vm.toggleFavorite() })
            }
            val lw = last
            if (resumeTarget?.second == true && lw != null && lw.duration > 0) {
                Spacer(Modifier.height(12.dp))
                ResumeLine(lw)
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error ?: "", style = T.Callout, color = C.LiveText)
            }
        }

        if (seasons.isNotEmpty()) lazy.item("episodes") {
            val episodes = season?.let { d?.episodes?.get(it) }.orEmpty()
            Column {
                Row(Modifier.padding(horizontal = ScreenEdge).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape).background(C.Glass).padding(4.dp).focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        seasons.forEach { se ->
                            val sel = se.number == season
                            FocusSurface(
                                onClick = { vm.season.value = se.number },
                                modifier = Modifier.height(26.dp),
                                shape = CircleShape,
                                color = if (sel) C.Focus else Color.Transparent,
                                contentColor = if (sel) C.OnFocus else C.Text.copy(alpha = 0.7f),
                                focusedScale = 1.07f,
                                contentAlignment = Alignment.Center,
                                onFocusChange = { if (it) vm.season.value = se.number },
                            ) {
                                Text(
                                    se.name.ifBlank { "Saison ${se.number}" },
                                    style = T.Subhead.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val seen = episodes.count { watched(epHistory[it.id]) }
                    Text(
                        "${episodes.size} épisode${if (episodes.size > 1) "s" else ""}" + if (seen > 0) " · $seen vu${if (seen > 1) "s" else ""}" else "",
                        style = T.Subhead.copy(fontWeight = FontWeight.Normal),
                        color = C.Text3,
                    )
                }
                val rowState = rememberLazyListState(cacheWindow = ScreenAhead)
                LaunchedEffect(season) {
                    val idx = episodes.indexOfFirst { it.id == vm.lastEpisodeId }
                    rowState.scrollToItem(idx.coerceAtLeast(0))
                }
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = ScreenEdge, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    // Index in the key: an id repeated by the provider must never crash the row.
                    itemsIndexed(episodes, key = { i, e -> "$i:${e.id}" }) { _, ep ->
                        val eh = epHistory[ep.id]
                        val epTitle = remember(ep.title, s.name) { episodeTitle(ep.title, s.name) }
                        val remaining = eh?.takeIf { it.duration > 0 && !watched(it) && it.position > 30_000 }
                            ?.let { formatMinutes(it.duration - it.position) + " restantes" }
                        EpisodeCard(
                            number = "Épisode ${ep.episode}",
                            title = epTitle ?: "Épisode ${ep.episode}",
                            description = ep.plot,
                            meta = listOfNotNull(ep.durationSecs.takeIf { it > 0 }?.let { formatMinutes(it * 1000L) }, remaining).joinToString(" · "),
                            image = ep.image ?: d?.backdrop ?: s.cover,
                            progress = eh?.takeIf { !watched(it) }?.let { if (it.duration > 0) it.position.toFloat() / it.duration else null },
                            watched = watched(eh),
                            modifier = if (ep.id == vm.lastEpisodeId) Modifier.focusRequester(episodeFocus) else Modifier,
                            onClick = { playEpisode(ep) },
                            onLongClick = { playEpisode(ep, fromStart = true) },
                        )
                    }
                }
            }
        }
        if (cast.isNotEmpty() || director.isNotEmpty()) {
            castRow("Distribution", cast.map { it to null } + director.map { it to "Création" })
        }
        if (similar.isNotEmpty()) {
            shelf("Vous aimerez aussi", "similar") {
                itemsIndexed(similar, key = { _, it -> it.id }) { _, o ->
                    PosterCard(o.name, o.cover, subtitle = listOfNotNull(o.year, o.genre?.split(',')?.firstOrNull()?.trim()).joinToString(" · "), onClick = { nav.series(o.id) })
                }
            }
        }
        infoGrid(
            listOfNotNull(
                seasons.size.takeIf { it > 0 }?.let { "Saisons" to "$it saison${if (it > 1) "s" else ""} · ${allEpisodes.size} épisodes" },
                (d?.releaseDate?.takeIf { it.isNotBlank() } ?: s.year)?.let { "Première diffusion" to it },
                names(d?.genre ?: s.genre).takeIf { it.isNotEmpty() }?.let { "Genre" to it.joinToString(", ") },
                if (rating > 0f) "Note" to "${ratingLabel(rating)} / 10" else null,
                name.badges.takeIf { it.isNotEmpty() }?.let { "Format" to it.joinToString(" · ") },
                "Source" to p.name,
            )
        )
    }
}

private fun episodeUrl(p: PlaylistEntity, ep: EpisodeInfo): String {
    val base = com.iplayer.tv.data.remote.XtreamClient.normalizeServer(p.url)
    return "$base/series/${p.username}/${p.password}/${ep.id}.${ep.extension}"
}

// ===================================================================== shared layout

private class DetailScope(val lazy: LazyListScope, val heroHeight: Dp, val backdrop: String?, val backLabel: String, val onBack: () -> Unit)

/**
 * Detail page of the design: a full-screen hero over the backdrop (back pill, clock, title block on the left,
 * credits card on the right), then shelves that scroll in under it.
 */
@Composable
private fun DetailPage(backdrop: String?, backLabel: String, heroHeight: Dp, content: DetailScope.() -> Unit) {
    val nav = LocalNav.current
    LazyColumn(
        Modifier.fillMaxSize().background(C.Background),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        DetailScope(this, heroHeight, backdrop, backLabel) { nav.back() }.content()
    }
}

private fun DetailScope.heroItem(credits: List<Pair<String, String>>, info: @Composable () -> Unit) {
    val scope = this
    lazy.item("hero") {
        var size by remember { mutableStateOf(IntSize.Zero) }
        // Focusing a hero button keeps the whole hero on screen (the list stays at the top).
        val showWholeHero = remember {
            object : BringIntoViewResponder {
                override fun calculateRectForParent(localRect: Rect): Rect = Rect(Offset.Zero, size.toSize())
                override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
            }
        }
        Box(Modifier.fillMaxWidth().height(scope.heroHeight).onSizeChanged { size = it }.bringIntoViewResponder(showWholeHero)) {
            if (!scope.backdrop.isNullOrBlank()) {
                AsyncImage(model = scope.backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1E2440), Color(0xFF0A0A0C)))))
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF20A0A0C), 0.4f to Color(0xA60A0A0C), 0.75f to Color(0x1A0A0A0C))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x800A0A0C), 0.2f to Color.Transparent, 0.6f to Color.Transparent, 1f to C.Background)))
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenEdge, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                BackPill(scope.backLabel, onClick = scope.onBack)
                Spacer(Modifier.weight(1f))
                Clock()
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = ScreenEdge, end = ScreenEdge, bottom = 36.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.width(450.dp)) { info() }
                Spacer(Modifier.weight(1f))
                if (credits.isNotEmpty()) CreditsCard(credits)
            }
        }
    }
}

@Composable
private fun CreditsCard(credits: List<Pair<String, String>>) {
    Column(
        Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xA60A0A0C))
            .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(11.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        credits.forEach { (label, value) ->
            Column {
                Eyebrow(label)
                Spacer(Modifier.height(3.dp))
                Text(value, style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = C.Text.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RatingLine(rating: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Star, null, Modifier.size(11.dp), tint = C.Text)
        Spacer(Modifier.width(4.dp))
        Text(ratingLabel(rating), style = T.Subhead.copy(fontWeight = FontWeight.Bold), color = C.Text)
        Spacer(Modifier.width(4.dp))
        Text("Note", style = T.Footnote.copy(fontWeight = FontWeight.Normal), color = C.Text2)
    }
}

@Composable
private fun ResumeLine(h: HistoryEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressLine(h.position.toFloat() / h.duration, Modifier.width(87.dp), height = 2.5.dp)
        Spacer(Modifier.width(8.dp))
        Text(formatMinutes((h.duration - h.position).coerceAtLeast(0)) + " restantes", style = T.Caption, color = C.Text2)
    }
}

private fun DetailScope.shelf(title: String, key: String, content: LazyListScope.() -> Unit) {
    lazy.item(key) {
        Column {
            SectionTitle(title, Modifier.padding(horizontal = ScreenEdge))
            LazyRow(
                state = rememberLazyListState(cacheWindow = ScreenAhead),
                contentPadding = PaddingValues(horizontal = ScreenEdge, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.focusRestorer(),
                content = content,
            )
        }
    }
}

private fun DetailScope.castRow(title: String, people: List<Pair<String, String?>>) {
    shelf(title, "cast") {
        itemsIndexed(people, key = { i, it -> "$i:${it.first}" }) { _, (n, role) -> CastMember(n, role) }
    }
}

/** "Informations": label / value pairs in four columns, under a hairline. */
private fun DetailScope.infoGrid(entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) return
    lazy.item("info") {
        Column(Modifier.padding(horizontal = ScreenEdge)) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.Separator))
            Spacer(Modifier.height(16.dp))
            SectionTitle("Informations")
            entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(label, style = T.Caption.copy(fontWeight = FontWeight.SemiBold), color = C.Text)
                            Spacer(Modifier.height(3.dp))
                            Text(value, style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = C.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
