package com.iplayer.tv.ui.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.CAT_ALL
import com.iplayer.tv.data.Sport
import com.iplayer.tv.data.SportsCalendar
import com.iplayer.tv.data.SportsEvent
import com.iplayer.tv.data.db.PlaylistEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class SportsState(
    val playlist: PlaylistEntity? = null,
    val events: List<SportsEvent> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val dayStart: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SportsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val day = MutableStateFlow(0)
    var lastFocusedKey: String? = null
    val now = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    private val bounds = combine(now, day) { time, offset -> SportsCalendar.dayBounds(time, offset) }
        .distinctUntilChanged()
    private val selection = container.settings.flow.map { it.sports }.distinctUntilChanged()

    val state = repo.activePlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(SportsState(loading = false))
        else combine(repo.channels(playlist.id, CAT_ALL), selection, bounds, repo.epgVersion) { channels, sports, range, _ ->
            Triple(channels, sports, range)
        }.mapLatest { (channels, sports, range) ->
            try {
                val programmes = repo.calendarPrograms(playlist.id, range.first, range.second)
                val events = withContext(Dispatchers.Default) { SportsCalendar.events(programmes, channels, sports) }
                SportsState(playlist, events, loading = false, dayStart = range.first)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SportsState(playlist, loading = false, error = "Impossible de lire le calendrier. Réessaie après avoir actualisé le guide.", dayStart = range.first)
            }
        }.onStart { emit(SportsState(playlist)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SportsState())

    fun toggle(sport: Sport) = container.settings.update {
        it.copy(sports = if (sport in it.sports) it.sports - sport else it.sports + sport)
    }

    fun refresh() {
        repo.activePlaylist.value?.let { repo.syncInBackground(it.id, epgOnly = true) }
    }
}
