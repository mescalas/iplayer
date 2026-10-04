package com.iplayer.tv.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.CAT_ALL
import com.iplayer.tv.data.CAT_FAVORITES
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.SideListItem
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.live.CatItem
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.tagged
import com.iplayer.tv.ui.components.CategoryPill
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(c: AppContainer, val kind: Int) : ViewModel() {
    private val repo = c.repository
    val playlist = repo.activePlaylist
    private val _selected = MutableStateFlow(CAT_ALL)
    val selected: StateFlow<String> = _selected
    private var catJob: Job? = null
    var lastFocusedKey: String? = null

    val categories: StateFlow<List<CatItem>> = playlist.flatMapLatest { p ->
        if (p == null) flowOf(emptyList())
        else repo.categories(p.id, kind).map { cats ->
            listOf(
                CatItem(CAT_ALL, "Tout", Icons.Rounded.Apps),
                CatItem(CAT_FAVORITES, "Favoris", Icons.Rounded.Star),
            ) + cats.map { val t = it.name.tagged(); CatItem(it.catId, t.name, tag = t.tag) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val movies: Flow<PagingData<MovieEntity>> = combine(playlist, _selected) { p, cat -> p to cat }
        .flatMapLatest { (p, cat) -> if (p == null || kind != Kind.MOVIE) flowOf(PagingData.empty()) else repo.moviesPaged(p.id, cat) }
        .cachedIn(viewModelScope)

    val series: Flow<PagingData<SeriesEntity>> = combine(playlist, _selected) { p, cat -> p to cat }
        .flatMapLatest { (p, cat) -> if (p == null || kind != Kind.SERIES) flowOf(PagingData.empty()) else repo.seriesPaged(p.id, cat) }
        .cachedIn(viewModelScope)

    fun onCategoryFocused(id: String) {
        catJob?.cancel()
        if (id == _selected.value) return
        catJob = viewModelScope.launch {
            delay(350)
            _selected.value = id
        }
    }

    fun select(id: String) {
        catJob?.cancel()
        _selected.value = id
    }

    fun toggleFavorite(pid: Long, key: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.toggleFavorite(pid, kind, key)) }
    }
}

data class GridEntry(val id: Long, val key: String, val title: String, val image: String?, val backdrop: String?, val year: String?, val rating: Float, val meta: String?)

@Composable
fun MoviesScreen() {
    val vm = appViewModel(key = "movies") { CatalogViewModel(it, Kind.MOVIE) }
    val items = vm.movies.collectAsLazyPagingItems()
    val nav = LocalNav.current
    CatalogLayout(
        vm = vm,
        emptyIcon = Icons.Rounded.Movie,
        countLabel = "films",
        itemCount = items.itemCount,
        keyOf = items.itemKey { it.id },
        entryAt = { i -> items[i]?.let { GridEntry(it.id, it.itemKey, it.name, it.poster, it.poster, it.year, it.rating, it.year) } },
        onOpen = { nav.movie(it.id) },
    )
}

@Composable
fun SeriesScreen() {
    val vm = appViewModel(key = "series") { CatalogViewModel(it, Kind.SERIES) }
    val items = vm.series.collectAsLazyPagingItems()
    val nav = LocalNav.current
    CatalogLayout(
        vm = vm,
        emptyIcon = Icons.Rounded.VideoLibrary,
        countLabel = "séries",
        itemCount = items.itemCount,
        keyOf = items.itemKey { it.id },
        entryAt = { i -> items[i]?.let { GridEntry(it.id, it.itemKey, it.name, it.cover, it.backdrop ?: it.cover, it.year, it.rating, listOfNotNull(it.year, it.genre).joinToString(" · ")) } },
        onOpen = { nav.series(it.id) },
    )
}

@Composable
private fun CatalogLayout(
    vm: CatalogViewModel,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    countLabel: String,
    itemCount: Int,
    keyOf: (Int) -> Any,
    entryAt: (Int) -> GridEntry?,
    onOpen: (GridEntry) -> Unit,
) {
    val nav = LocalNav.current
    val shell = LocalShell.current
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selected.collectAsState()
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    val restoreRequester = remember { FocusRequester() }

    LaunchedEffect(itemCount > 0) {
        if (itemCount > 0 && nav.restoreFocus) {
            delay(80)
            if (!restoreRequester.tryFocus()) shell.focusTabs()
            nav.restoreFocus = false
        }
    }

    val p = playlist ?: return
    if (categories.size <= 2 && itemCount == 0) {
        EmptyState(
            emptyIcon,
            if (vm.kind == Kind.SERIES) "Aucune série" else "Aucun film",
            if (vm.kind == Kind.SERIES && !p.isXtream) "Les séries sont disponibles avec un compte Xtream Codes."
            else "Cette playlist ne contient pas de contenu à la demande.",
            Modifier.fillMaxSize(),
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(if (vm.kind == Kind.SERIES) "SÉRIES" else "FILMS", style = T.Display.copy(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 1.sp))
            Spacer(Modifier.width(16.dp))
            val catName = categories.firstOrNull { it.id == selected }?.name.orEmpty()
            Text(
                "${catName.uppercase(java.util.Locale.FRENCH)}   |   $itemCount ${countLabel.uppercase(java.util.Locale.FRENCH)}",
                style = T.Label,
                color = C.Text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                CategoryPill(
                    text = cat.name,
                    tag = cat.tag,
                    selected = cat.id == selected,
                    icon = cat.icon,
                    onClick = {
                        vm.select(cat.id)
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                    onFocused = { vm.onCategoryFocused(cat.id) },
                )
            }
        }
        if (itemCount == 0) {
            EmptyState(
                if (selected == CAT_FAVORITES) Icons.Rounded.Star else emptyIcon,
                if (selected == CAT_FAVORITES) "Aucun favori" else "Catégorie vide",
                if (selected == CAT_FAVORITES) "Maintenez OK sur une affiche pour l'ajouter aux favoris." else null,
                Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(124.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().focusRestorer(),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 18.dp, bottom = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(count = itemCount, key = keyOf) { i ->
                    val e = entryAt(i)
                    if (e == null) {
                        PosterCard(title = "", image = null, onClick = {}, width = null)
                    } else {
                        PosterCard(
                            title = e.title,
                            image = e.image,
                            rating = e.rating,
                            subtitle = e.year,
                            width = null,
                            modifier = if (e.key == vm.lastFocusedKey) Modifier.focusRequester(restoreRequester) else Modifier,
                            onFocused = {
                                vm.lastFocusedKey = e.key
                                shell.backdrop.value = e.backdrop
                            },
                            onClick = { onOpen(e) },
                            onLongClick = {
                                vm.toggleFavorite(p.id, e.key) { fav ->
                                    shell.toast(if (fav) "Ajouté aux favoris" else "Retiré des favoris")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
