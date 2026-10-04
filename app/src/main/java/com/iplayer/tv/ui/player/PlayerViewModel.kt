package com.iplayer.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.HistoryEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.player.VodItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val settings = c.settings
    val pm = c.player
    val request: PlayRequest? = c.playback.request
    private val keepAlive = c.playback.keepAliveOnExit
    private val playlist = request?.playlist

    val isLive = request is PlayRequest.Live

    // ---- live
    var channels: List<ChannelEntity> = (request as? PlayRequest.Live)?.channels.orEmpty()
        private set
    private val _liveIndex = MutableStateFlow((request as? PlayRequest.Live)?.index ?: 0)
    val liveIndex: StateFlow<Int> = _liveIndex
    private var previousIndex = -1
    private val _nowNext = MutableStateFlow<List<ProgramEntity>>(emptyList())
    val nowNext: StateFlow<List<ProgramEntity>> = _nowNext
    private val _nowPrograms = MutableStateFlow<Map<String, ProgramEntity>>(emptyMap())
    val nowPrograms: StateFlow<Map<String, ProgramEntity>> = _nowPrograms
    private val _favorite = MutableStateFlow(false)
    val favorite: StateFlow<Boolean> = _favorite
    private var zapJob: Job? = null
    private var epgJob: Job? = null

    // ---- vod
    private val vodItems: List<VodItem> = (request as? PlayRequest.Vod)?.items.orEmpty()
    private val _vodIndex = MutableStateFlow((request as? PlayRequest.Vod)?.index ?: 0)
    val vodIndex: StateFlow<Int> = _vodIndex
    val currentVod: VodItem? get() = vodItems.getOrNull(_vodIndex.value)
    val hasNext: Boolean get() = _vodIndex.value + 1 < vodItems.size

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    private var started = false

    val currentChannel: ChannelEntity? get() = channels.getOrNull(_liveIndex.value)

    fun start() {
        if (started || request == null) return
        started = true
        if (isLive) {
            playChannelNow(_liveIndex.value)
            viewModelScope.launch {
                playlist?.let { _nowPrograms.value = repo.currentPrograms(it.id) }
            }
        } else {
            playVod(_vodIndex.value)
        }
    }

    // ------------------------------------------------------------------ live

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        val n = channels.size
        val idx = ((_liveIndex.value + delta) % n + n) % n
        selectChannel(idx, debounce = true)
    }

    fun playChannelAt(idx: Int) = selectChannel(idx, debounce = false)

    fun lastChannel() {
        if (previousIndex in channels.indices) selectChannel(previousIndex, debounce = false)
    }

    private fun selectChannel(idx: Int, debounce: Boolean) {
        if (idx != _liveIndex.value) previousIndex = _liveIndex.value
        _liveIndex.value = idx
        refreshChannelInfo()
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            if (debounce) delay(320)
            playChannelNow(idx)
        }
    }

    private fun playChannelNow(idx: Int) {
        val ch = channels.getOrNull(idx) ?: return
        val p = playlist ?: return
        if (!pm.isPlaying(ch.itemKey) || pm.error.value != null) {
            pm.play(repo.liveUrl(p, ch), ch.name, ch.itemKey, isLive = true, userAgent = repo.userAgent(p))
        }
        refreshChannelInfo()
        settings.update { it.copy(lastChannelKey = ch.itemKey) }
        viewModelScope.launch {
            repo.saveHistory(
                HistoryEntity(
                    playlistId = p.id, kind = Kind.LIVE, itemKey = ch.itemKey, title = ch.name, image = ch.logo,
                    url = ch.url, updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun refreshChannelInfo() {
        val ch = currentChannel ?: return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            _favorite.value = repo.isFavorite(ch.playlistId, Kind.LIVE, ch.itemKey).first()
            while (true) {
                _nowNext.value = repo.nowNext(ch)
                delay(30_000)
            }
        }
    }

    fun playNumber(number: Int) {
        val inList = channels.indexOfFirst { it.number == number }
        if (inList >= 0) {
            selectChannel(inList, debounce = false)
            return
        }
        val p = playlist ?: return
        viewModelScope.launch {
            val ch = repo.channelByNumber(p.id, number) ?: return@launch
            val list = repo.channels(p.id, ch.categoryId).first()
            channels = list
            selectChannel(list.indexOfFirst { it.id == ch.id }.coerceAtLeast(0), debounce = false)
        }
    }

    fun toggleFavorite(onDone: (Boolean) -> Unit) {
        val ch = currentChannel ?: return
        viewModelScope.launch {
            val fav = repo.toggleFavorite(ch.playlistId, Kind.LIVE, ch.itemKey)
            _favorite.value = fav
            onDone(fav)
        }
    }

    // ------------------------------------------------------------------ vod

    fun playVod(idx: Int) {
        val item = vodItems.getOrNull(idx) ?: return
        val p = playlist ?: return
        _vodIndex.value = idx
        viewModelScope.launch {
            var start = item.startPosition
            if (start < 0) {
                start = 0
                if (item.trackHistory) {
                    val h = repo.history(p.id, item.kind, item.key)
                    if (h != null && h.position > 30_000 && (h.duration <= 0 || h.position < h.duration * 0.94)) start = h.position
                }
            }
            pm.play(item.url, item.title, item.key, isLive = false, userAgent = repo.userAgent(p), startPositionMs = start)
        }
    }

    fun next() {
        if (hasNext) {
            saveProgress()
            playVod(_vodIndex.value + 1)
        }
    }

    fun saveProgress(finishedItem: Boolean = false) {
        if (isLive) return
        val item = currentVod ?: return
        if (!item.trackHistory) return
        val p = playlist ?: return
        val player = pm.playerOrNull ?: return
        if (pm.nowPlaying.value?.key != item.key) return
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val pos = if (finishedItem) dur else player.currentPosition
        if (pos <= 0 && !finishedItem) return
        repo.scope.launch {
            repo.saveHistory(
                HistoryEntity(
                    playlistId = p.id, kind = item.kind, itemKey = item.key, title = item.title, subtitle = item.subtitle,
                    image = item.image, url = item.url, position = pos, duration = dur,
                    updatedAt = System.currentTimeMillis(), parentKey = item.parentKey,
                )
            )
        }
    }

    fun onEnded() {
        if (isLive) return
        saveProgress(finishedItem = true)
        if (hasNext && settings.value.autoNextEpisode) playVod(_vodIndex.value + 1) else _finished.value = true
    }

    fun onExit() {
        if (!isLive) saveProgress()
        if (!(isLive && keepAlive)) pm.stop()
    }
}
