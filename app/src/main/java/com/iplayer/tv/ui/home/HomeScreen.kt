package com.iplayer.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.HistoryEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.player.VodItem
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.SectionTitle
import com.iplayer.tv.ui.components.WideCard
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.formatMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val playlist: PlaylistEntity? = null,
    val continueWatching: List<HistoryEntity> = emptyList(),
    val favorites: List<ChannelEntity> = emptyList(),
    val recent: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    var lastFocused: String? = null

    val state: StateFlow<HomeState> = repo.activePlaylist.flatMapLatest { p ->
        if (p == null) flowOf(HomeState(loaded = true))
        else combine(
            repo.continueWatching(p.id),
            repo.favoriteChannels(p.id),
            repo.recentChannels(p.id),
            repo.latestMovies(p.id),
            repo.latestSeries(p.id),
        ) { cw, fav, rec, mov, ser -> HomeState(p, cw, fav, rec.take(20), mov, ser, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeScreen() {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsState()
    val nav = LocalNav.current
    val shell = LocalShell.current
    val restoreRequester = remember { FocusRequester() }
    var hero by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val playlist = state.playlist

    LaunchedEffect(state.loaded) {
        if (state.loaded && nav.restoreFocus) {
            delay(60)
            if (!restoreRequester.tryFocus()) shell.focusTabs()
            nav.restoreFocus = false
        }
    }

    fun focusMod(key: String): Modifier =
        if (key == vm.lastFocused) Modifier.focusRequester(restoreRequester) else Modifier

    fun onFocus(key: String, title: String, sub: String?, image: String?) {
        vm.lastFocused = key
        hero = title to sub
        shell.backdrop.value = image
    }

    if (!state.loaded || playlist == null) return

    val empty = state.continueWatching.isEmpty() && state.favorites.isEmpty() && state.recent.isEmpty() &&
        state.movies.isEmpty() && state.series.isEmpty()
    if (empty) {
        EmptyState(
            Icons.Rounded.Tv,
            "Tout est prêt",
            "Parcourez la TV en direct, les films et les séries. Vos favoris et vos lectures en cours apparaîtront ici.",
            Modifier.fillMaxSize(),
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 48.dp)) {
            val h = hero
            if (h != null) {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(h.first, style = T.Title1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (h.second != null) Text(h.second!!, style = T.Callout, color = C.Text2, maxLines = 1)
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            if (state.continueWatching.isNotEmpty()) item("cw") {
                Row("Reprendre la lecture") {
                    itemsIndexed(state.continueWatching, key = { _, h -> "cw" + h.kind + h.itemKey }) { _, h ->
                        val key = "cw:${h.kind}:${h.itemKey}"
                        WideCard(
                            title = h.title,
                            subtitle = h.subtitle ?: (if (h.duration > 0) "Reste " + formatMinutes(h.duration - h.position) else null),
                            image = h.image,
                            progress = if (h.duration > 0) h.position.toFloat() / h.duration else null,
                            modifier = focusMod(key),
                            onFocused = { onFocus(key, h.title, h.subtitle, h.image) },
                            onClick = {
                                nav.play(
                                    PlayRequest.Vod(
                                        playlist,
                                        listOf(VodItem(h.kind, h.itemKey, h.title, h.subtitle, h.image, h.url, h.parentKey)),
                                        0,
                                    )
                                )
                            },
                        )
                    }
                }
            }
            if (state.favorites.isNotEmpty()) item("fav") {
                Row("Chaînes favorites") {
                    itemsIndexed(state.favorites, key = { _, c -> "fav" + c.id }) { i, ch ->
                        val key = "fav:${ch.itemKey}"
                        WideCard(
                            title = ch.name, subtitle = null, image = ch.logo, logoMode = true, width = 180.dp,
                            modifier = focusMod(key),
                            onFocused = { onFocus(key, ch.name, "Chaîne favorite", null) },
                            onClick = { nav.playLive(playlist, state.favorites, i) },
                        )
                    }
                }
            }
            if (state.recent.isNotEmpty()) item("recent") {
                Row("Regardées récemment") {
                    itemsIndexed(state.recent, key = { _, c -> "rec" + c.id }) { i, ch ->
                        val key = "rec:${ch.itemKey}"
                        WideCard(
                            title = ch.name, subtitle = null, image = ch.logo, logoMode = true, width = 180.dp,
                            modifier = focusMod(key),
                            onFocused = { onFocus(key, ch.name, "Regardée récemment", null) },
                            onClick = { nav.playLive(playlist, state.recent, i) },
                        )
                    }
                }
            }
            if (state.movies.isNotEmpty()) item("movies") {
                Row("Films ajoutés récemment") {
                    itemsIndexed(state.movies, key = { _, m -> "m" + m.id }) { _, m ->
                        val key = "m:${m.itemKey}"
                        PosterCard(
                            title = m.name, image = m.poster, rating = m.rating, subtitle = m.year,
                            modifier = focusMod(key),
                            onFocused = { onFocus(key, m.name, listOfNotNull(m.year, "Film").joinToString(" · "), m.poster) },
                            onClick = { nav.movie(m.id) },
                        )
                    }
                }
            }
            if (state.series.isNotEmpty()) item("series") {
                Row("Séries ajoutées récemment") {
                    itemsIndexed(state.series, key = { _, s -> "s" + s.id }) { _, s ->
                        val key = "s:${s.itemKey}"
                        PosterCard(
                            title = s.name, image = s.cover, rating = s.rating, subtitle = s.year,
                            modifier = focusMod(key),
                            onFocused = { onFocus(key, s.name, listOfNotNull(s.year, s.genre).joinToString(" · "), s.backdrop ?: s.cover) },
                            onClick = { nav.series(s.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(horizontal = 48.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}
