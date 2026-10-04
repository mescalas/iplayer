package com.iplayer.tv.ui.live

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.CAT_ALL
import com.iplayer.tv.data.CAT_FAVORITES
import com.iplayer.tv.data.CAT_RECENT
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.util.categoryLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatItem(val id: String, val name: String, val icon: ImageVector? = null, val tag: String? = null)

data class ChannelInfo(
    val channel: ChannelEntity,
    val schedule: List<ProgramEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LiveViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val playlist = repo.activePlaylist

    private val _selected = MutableStateFlow(CAT_ALL)
    val selected: StateFlow<String> = _selected

    var lastFocusedId: Long = -1
    private var catJob: Job? = null

    val categories: StateFlow<List<CatItem>> = playlist.flatMapLatest { p ->
        if (p == null) flowOf(emptyList())
        else repo.categories(p.id, Kind.LIVE).map { cats ->
            listOf(
                CatItem(CAT_FAVORITES, "Favoris", Icons.Rounded.Star),
                CatItem(CAT_RECENT, "Récentes", Icons.Rounded.History),
                CatItem(CAT_ALL, "Toutes les chaînes", Icons.Rounded.Apps),
            ) + cats.map { val t = it.name.categoryLabel(Kind.LIVE); CatItem(it.catId, t.name, tag = t.tag) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val channels: StateFlow<List<ChannelEntity>?> = combine(playlist, _selected) { p, cat -> p to cat }
        .flatMapLatest { (p, cat) -> if (p == null) flowOf(emptyList()) else repo.channels(p.id, cat) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val nowPrograms: StateFlow<Map<String, ProgramEntity>> =
        combine(playlist, repo.epgVersion, ticker) { p, _, _ -> p }
            .mapLatest { p -> if (p == null) emptyMap() else repo.currentPrograms(p.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val favoriteKeys: StateFlow<Set<String>> = playlist.flatMapLatest { p ->
        if (p == null) flowOf(emptySet()) else repo.favoriteKeys(p.id, Kind.LIVE).map { it.toHashSet() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _focused = MutableStateFlow<ChannelEntity?>(null)
    val focused: StateFlow<ChannelEntity?> = _focused

    val info: StateFlow<ChannelInfo?> = combine(_focused.debounce(180), repo.epgVersion) { ch, _ -> ch }
        .mapLatest { ch ->
            if (ch == null) null
            else {
                val now = System.currentTimeMillis()
                val from = if (ch.catchupDays > 0) now - minOf(ch.catchupDays, 3) * 86_400_000L else now
                ChannelInfo(ch, repo.schedule(ch, from, now + 18 * 3600_000L))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun focus(ch: ChannelEntity) {
        lastFocusedId = ch.id
        _focused.value = ch
    }

    fun onCategoryFocused(id: String) {
        catJob?.cancel()
        if (id == _selected.value) return
        catJob = viewModelScope.launch {
            delay(320)
            _selected.value = id
        }
    }

    fun select(id: String) {
        catJob?.cancel()
        _selected.value = id
    }

    fun toggleFavorite(ch: ChannelEntity, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.toggleFavorite(ch.playlistId, Kind.LIVE, ch.itemKey)) }
    }
}
