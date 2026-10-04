package com.iplayer.tv.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.iplayer.tv.data.db.AppDatabase
import com.iplayer.tv.data.db.CategoryEntity
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.FavoriteEntity
import com.iplayer.tv.data.db.HistoryEntity
import com.iplayer.tv.data.db.Kind
import com.iplayer.tv.data.db.MovieEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.PlaylistType
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.data.db.SeriesEntity
import com.iplayer.tv.data.remote.Http
import com.iplayer.tv.data.remote.M3uParser
import com.iplayer.tv.data.remote.MovieDetails
import com.iplayer.tv.data.remote.SeriesDetails
import com.iplayer.tv.data.remote.XmltvParser
import com.iplayer.tv.data.remote.XtreamClient
import com.iplayer.tv.util.searchKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val playlistId: Long, val message: String) : SyncState
    data class Failed(val playlistId: Long, val message: String) : SyncState
}

const val CAT_ALL = "__all"
const val CAT_FAVORITES = "__fav"
const val CAT_RECENT = "__recent"

@OptIn(ExperimentalCoroutinesApi::class)
class Repository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _sync.asStateFlow()

    private val _epgVersion = MutableStateFlow(0)
    /** Increments every time a guide import finishes, so screens can reload programmes. */
    val epgVersion: StateFlow<Int> = _epgVersion.asStateFlow()

    val playlists: StateFlow<List<PlaylistEntity>?> =
        db.playlists().observeAll().stateIn(scope, SharingStarted.Eagerly, null)

    val activePlaylist: StateFlow<PlaylistEntity?> =
        combine(playlists, settings.flow.map { it.activePlaylistId }.distinctUntilChanged()) { list, id ->
            list?.firstOrNull { it.id == id } ?: list?.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val activeId: Flow<Long> = activePlaylist.map { it?.id ?: -1L }.distinctUntilChanged()

    private val clientCache = ConcurrentHashMap<Long, XtreamClient>()

    fun xtream(p: PlaylistEntity): XtreamClient {
        val cached = clientCache[p.id]
        if (cached != null && cached.base == XtreamClient.normalizeServer(p.url)) return cached
        return XtreamClient(p.url, p.username, p.password, userAgent(p)).also { clientCache[p.id] = it }
    }

    fun userAgent(p: PlaylistEntity?): String? =
        p?.userAgent?.takeIf { it.isNotBlank() } ?: settings.value.userAgent.takeIf { it.isNotBlank() }

    // ---------------------------------------------------------------- playlists

    suspend fun addPlaylist(input: PlaylistEntity): Long = withContext(Dispatchers.IO) {
        var p = input
        if (p.type == PlaylistType.M3U) {
            XtreamClient.fromM3uUrl(p.url)?.let { (server, user, pass) ->
                val ok = runCatching { XtreamClient(server, user, pass, userAgent(p)).authenticate() }.isSuccess
                if (ok) p = p.copy(type = PlaylistType.XTREAM, url = server, username = user, password = pass)
            }
        }
        val id = if (p.id == 0L) db.playlists().insert(p) else { db.playlists().update(p); p.id }
        clientCache.remove(id)
        settings.update { it.copy(activePlaylistId = id) }
        id
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.channels().deleteAll(id)
            db.movies().deleteAll(id)
            db.series().deleteAll(id)
            db.categories().deleteAll(id)
            db.programs().deleteAll(id)
            db.favorites().deleteAll(id)
            db.history().deleteAll(id)
            db.playlists().delete(id)
        }
        clientCache.remove(id)
        if (settings.value.activePlaylistId == id) {
            val next = db.playlists().all().firstOrNull()?.id ?: 0
            settings.update { it.copy(activePlaylistId = next) }
        }
    }

    fun setActive(id: Long) = settings.update { it.copy(activePlaylistId = id) }

    fun refreshIfStale() {
        scope.launch {
            val p = activePlaylist.value ?: db.playlists().all().firstOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            if (now - p.lastSync > 24 * 3600_000L) sync(p.id)
            else if (now - p.lastEpgSync > 12 * 3600_000L) syncEpg(p.id)
        }
    }

    fun syncInBackground(id: Long, epgOnly: Boolean = false) {
        scope.launch { if (epgOnly) syncEpg(id) else sync(id) }
    }

    suspend fun sync(id: Long): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val p = db.playlists().get(id) ?: return@withLock false
            try {
                if (p.isXtream) syncXtream(p) else syncM3u(p)
                _sync.value = SyncState.Idle
            } catch (e: Throwable) {
                _sync.value = SyncState.Failed(id, e.message ?: e.javaClass.simpleName)
                return@withLock false
            }
            true
        }.also { ok -> if (ok) scope.launch { syncEpg(id) } }
    }

    private fun progress(id: Long, msg: String) {
        _sync.value = SyncState.Running(id, msg)
    }

    private suspend fun syncXtream(p: PlaylistEntity) {
        val c = xtream(p)
        progress(p.id, "Connexion au serveur…")
        val acct = c.authenticate()

        progress(p.id, "Chargement des chaînes…")
        val liveCats = c.categories("get_live_categories")
        val channels = ArrayList<ChannelEntity>(4096)
        c.streamList("get_live_streams") { m ->
            val sid = m["stream_id"]?.toLongOrNull() ?: return@streamList
            val name = m["name"]?.trim().orEmpty()
            val pos = channels.size
            channels += ChannelEntity(
                playlistId = p.id,
                categoryId = m["category_id"].orEmpty(),
                itemKey = sid.toString(),
                name = name,
                search = name.searchKey(),
                logo = m["stream_icon"].nonBlank(),
                url = c.liveUrl(sid, "ts"),
                streamId = sid,
                epgId = m["epg_channel_id"].nonBlank(),
                number = m["num"]?.toIntOrNull() ?: (pos + 1),
                position = pos,
                catchupDays = if (m["tv_archive"] == "1") m["tv_archive_duration"]?.toIntOrNull() ?: 0 else 0,
            )
        }
        val liveCatEntities = buildCategories(p.id, Kind.LIVE, liveCats.map { it.id to it.name }, channels.map { it.categoryId })
        db.withTransaction {
            db.categories().delete(p.id, Kind.LIVE)
            db.categories().insert(liveCatEntities)
            db.channels().deleteAll(p.id)
            channels.chunked(2000).forEach { db.channels().insert(it) }
        }
        channels.clear()

        progress(p.id, "Chargement des films…")
        val vodCats = c.categories("get_vod_categories")
        val movies = ArrayList<MovieEntity>(8192)
        c.streamList("get_vod_streams") { m ->
            val sid = m["stream_id"]?.toLongOrNull() ?: return@streamList
            val name = m["name"]?.trim().orEmpty()
            val ext = m["container_extension"].nonBlank() ?: "mp4"
            movies += MovieEntity(
                playlistId = p.id,
                categoryId = m["category_id"].orEmpty(),
                itemKey = sid.toString(),
                name = name,
                search = name.searchKey(),
                poster = m["stream_icon"].nonBlank(),
                url = c.movieUrl(sid, ext),
                streamId = sid,
                extension = ext,
                rating = parseRating(m["rating"], m["rating_5based"]),
                year = m["year"].nonBlank() ?: yearFrom(name),
                added = (m["added"]?.toLongOrNull() ?: 0L) * 1000L,
                position = movies.size,
            )
        }
        val vodCatEntities = buildCategories(p.id, Kind.MOVIE, vodCats.map { it.id to it.name }, movies.map { it.categoryId })
        db.withTransaction {
            db.categories().delete(p.id, Kind.MOVIE)
            db.categories().insert(vodCatEntities)
            db.movies().deleteAll(p.id)
            movies.chunked(2000).forEach { db.movies().insert(it) }
        }
        movies.clear()

        progress(p.id, "Chargement des séries…")
        val serCats = c.categories("get_series_categories")
        val series = ArrayList<SeriesEntity>(4096)
        c.streamList("get_series") { m ->
            val sid = m["series_id"]?.toLongOrNull() ?: return@streamList
            val name = m["name"]?.trim().orEmpty()
            series += SeriesEntity(
                playlistId = p.id,
                categoryId = m["category_id"].orEmpty(),
                itemKey = sid.toString(),
                seriesId = sid,
                name = name,
                search = name.searchKey(),
                cover = m["cover"].nonBlank(),
                plot = m["plot"].nonBlank(),
                genre = m["genre"].nonBlank(),
                year = (m["releaseDate"] ?: m["release_date"] ?: m["year"]).nonBlank()?.take(4) ?: yearFrom(name),
                rating = parseRating(m["rating"], m["rating_5based"]),
                backdrop = m["backdrop_path"].nonBlank(),
                added = (m["last_modified"]?.toLongOrNull() ?: 0L) * 1000L,
                position = series.size,
            )
        }
        val serCatEntities = buildCategories(p.id, Kind.SERIES, serCats.map { it.id to it.name }, series.map { it.categoryId })
        db.withTransaction {
            db.categories().delete(p.id, Kind.SERIES)
            db.categories().insert(serCatEntities)
            db.series().deleteAll(p.id)
            series.chunked(2000).forEach { db.series().insert(it) }
        }
        series.clear()

        db.playlists().update(
            p.copy(
                lastSync = System.currentTimeMillis(),
                expiresAt = acct.expiresAt,
                maxConnections = acct.maxConnections,
                serverTimezone = acct.timezone,
            )
        )
    }

    private suspend fun syncM3u(p: PlaylistEntity) {
        progress(p.id, "Téléchargement de la playlist…")
        val channels = ArrayList<ChannelEntity>(4096)
        val movies = ArrayList<MovieEntity>(4096)
        val liveGroups = LinkedHashSet<String>()
        val movieGroups = LinkedHashSet<String>()
        var epgUrl = ""
        Http.get(p.url.trim(), userAgent(p)).use { resp ->
            resp.body!!.charStream().buffered(64 * 1024).use { reader ->
                M3uParser.parse(reader, onHeader = { h ->
                    epgUrl = (h["url-tvg"] ?: h["x-tvg-url"] ?: "").split(',').firstOrNull()?.trim().orEmpty()
                }) { e ->
                    if (channels.size + movies.size == 0 || (channels.size + movies.size) % 5000 == 0) {
                        progress(p.id, "Analyse de la playlist… ${channels.size + movies.size}")
                    }
                    val group = e.group.ifBlank { "Sans catégorie" }
                    if (isVod(e.url)) {
                        movieGroups += group
                        movies += MovieEntity(
                            playlistId = p.id,
                            categoryId = group,
                            itemKey = e.url,
                            name = e.name,
                            search = e.name.searchKey(),
                            poster = e.attrs["tvg-logo"].nonBlank(),
                            url = e.url,
                            year = yearFrom(e.name),
                            position = movies.size,
                        )
                    } else {
                        liveGroups += group
                        val pos = channels.size
                        channels += ChannelEntity(
                            playlistId = p.id,
                            categoryId = group,
                            itemKey = e.url,
                            name = e.name,
                            search = e.name.searchKey(),
                            logo = e.attrs["tvg-logo"].nonBlank(),
                            url = e.url,
                            epgId = e.attrs["tvg-id"].nonBlank(),
                            number = e.attrs["tvg-chno"]?.toIntOrNull() ?: (pos + 1),
                            position = pos,
                            catchupDays = (e.attrs["catchup-days"] ?: e.attrs["tvg-rec"])?.toIntOrNull() ?: 0,
                        )
                    }
                }
            }
        }
        if (channels.isEmpty() && movies.isEmpty()) throw IllegalStateException("La playlist est vide ou invalide.")
        progress(p.id, "Enregistrement…")
        db.withTransaction {
            db.categories().deleteAll(p.id)
            db.categories().insert(liveGroups.mapIndexed { i, g -> CategoryEntity(p.id, Kind.LIVE, g, g, i) })
            db.categories().insert(movieGroups.mapIndexed { i, g -> CategoryEntity(p.id, Kind.MOVIE, g, g, i) })
            db.channels().deleteAll(p.id)
            db.movies().deleteAll(p.id)
            db.series().deleteAll(p.id)
            channels.chunked(2000).forEach { db.channels().insert(it) }
            movies.chunked(2000).forEach { db.movies().insert(it) }
        }
        db.playlists().update(p.copy(lastSync = System.currentTimeMillis(), detectedEpgUrl = epgUrl))
    }

    suspend fun syncEpg(id: Long) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val p = db.playlists().get(id) ?: return@withLock
            val url = p.epgUrl.ifBlank { if (p.isXtream) xtream(p).epgUrl() else p.detectedEpgUrl }
            if (url.isBlank()) return@withLock
            progress(id, "Mise à jour du guide TV…")
            try {
                importEpg(p, url)
                db.playlists().get(id)?.let { db.playlists().update(it.copy(lastEpgSync = System.currentTimeMillis())) }
                _sync.value = SyncState.Idle
                _epgVersion.value++
            } catch (e: Throwable) {
                _sync.value = SyncState.Failed(id, "Guide TV : " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private suspend fun importEpg(p: PlaylistEntity, url: String) {
        val accepted = HashSet<String>(db.channels().epgIds(p.id))
        val missing = db.channels().withoutEpg(p.id).groupBy { normalizeName(it.name) }
        val assignments = ArrayList<Pair<Long, String>>()
        val now = System.currentTimeMillis()
        val pastDays = (db.channels().maxCatchupDays(p.id) ?: 0).coerceIn(1, 7)
        val offset = settings.value.epgOffsetHours * 3600_000L
        val buffer = ArrayList<ProgramEntity>(5000)
        var lastId: String? = null
        var lastAccepted = false
        var count = 0
        db.programs().deleteAll(p.id)
        Http.get(url, userAgent(p)).use { resp ->
            XmltvParser.maybeGunzip(resp.body!!.byteStream()).use { input ->
                XmltvParser.parse(
                    input = input,
                    onChannel = { xmlId, names ->
                        if (missing.isNotEmpty()) {
                            for (n in names) {
                                val list = missing[normalizeName(n)] ?: continue
                                list.forEach { assignments += it.id to xmlId }
                                accepted += xmlId.lowercase()
                                break
                            }
                        }
                    },
                    onChannelsDone = {},
                    accept = { ch ->
                        if (ch != lastId) {
                            lastId = ch
                            lastAccepted = accepted.contains(ch.lowercase())
                        }
                        lastAccepted
                    },
                    offsetMs = offset,
                    minStop = now - pastDays * 86_400_000L,
                    maxStart = now + 4 * 86_400_000L,
                ) { pr ->
                    buffer += ProgramEntity(
                        playlistId = p.id,
                        channelKey = pr.channel.lowercase(),
                        startAt = pr.start,
                        endAt = pr.stop,
                        title = pr.title,
                        description = pr.desc,
                    )
                    if (buffer.size >= 5000) {
                        db.programs().insertBlocking(ArrayList(buffer))
                        count += buffer.size
                        buffer.clear()
                        progress(p.id, "Mise à jour du guide TV… $count")
                    }
                }
            }
        }
        if (buffer.isNotEmpty()) db.programs().insertBlocking(buffer)
        if (assignments.isNotEmpty()) db.withTransaction { assignments.forEach { (cid, xml) -> db.channels().setEpgId(cid, xml) } }
    }

    // ---------------------------------------------------------------- content

    fun categories(pid: Long, kind: Int) = db.categories().observe(pid, kind)

    fun channels(pid: Long, cat: String): Flow<List<ChannelEntity>> = when (cat) {
        CAT_ALL -> db.channels().observeAll(pid)
        CAT_FAVORITES -> db.channels().observeFavorites(pid)
        CAT_RECENT -> db.channels().observeRecent(pid)
        else -> db.channels().observeCategory(pid, cat)
    }

    fun favoriteChannels(pid: Long) = db.channels().observeFavorites(pid)
    fun recentChannels(pid: Long) = db.channels().observeRecent(pid)

    private val pagingConfig = PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 120, enablePlaceholders = true)

    fun moviesPaged(pid: Long, cat: String): Flow<PagingData<MovieEntity>> = Pager(pagingConfig) {
        when (cat) {
            CAT_ALL -> db.movies().pageAll(pid)
            CAT_FAVORITES -> db.movies().pageFavorites(pid)
            else -> db.movies().pageCategory(pid, cat)
        }
    }.flow

    fun seriesPaged(pid: Long, cat: String): Flow<PagingData<SeriesEntity>> = Pager(pagingConfig) {
        when (cat) {
            CAT_ALL -> db.series().pageAll(pid)
            CAT_FAVORITES -> db.series().pageFavorites(pid)
            else -> db.series().pageCategory(pid, cat)
        }
    }.flow

    fun latestMovies(pid: Long) = db.movies().observeLatest(pid, 30)
    fun topRatedMovies(pid: Long) = db.movies().observeTopRated(pid, 30)
    fun latestSeries(pid: Long) = db.series().observeLatest(pid, 30)
    fun continueWatching(pid: Long) = db.history().observeContinue(pid)

    suspend fun movie(id: Long) = db.movies().get(id)
    suspend fun series(id: Long) = db.series().get(id)
    suspend fun similarMovies(m: MovieEntity) = db.movies().similar(m.playlistId, m.categoryId, m.id, 24)
    suspend fun similarSeries(s: SeriesEntity) = db.series().similar(s.playlistId, s.categoryId, s.id, 24)
    suspend fun channel(id: Long) = db.channels().get(id)
    suspend fun channelByNumber(pid: Long, n: Int) = db.channels().byNumber(pid, n)
    suspend fun channelByKey(pid: Long, key: String) = db.channels().byKey(pid, key)
    suspend fun movieByKey(pid: Long, key: String) = db.movies().byKey(pid, key)
    suspend fun seriesByKey(pid: Long, key: String) = db.series().byKey(pid, key)

    suspend fun search(pid: Long, query: String): SearchResults {
        val q = query.trim().searchKey()
        if (q.length < 2) return SearchResults()
        return SearchResults(
            channels = db.channels().search(pid, q, 60),
            movies = db.movies().search(pid, q, 60),
            series = db.series().search(pid, q, 60),
        )
    }

    // ---------------------------------------------------------------- EPG

    suspend fun currentPrograms(pid: Long): Map<String, ProgramEntity> {
        val now = System.currentTimeMillis()
        return db.programs().current(pid, now).associateBy { it.channelKey }
    }

    suspend fun nowNext(ch: ChannelEntity): List<ProgramEntity> {
        val key = ch.epgId?.lowercase() ?: return emptyList()
        return db.programs().nowNext(ch.playlistId, key, System.currentTimeMillis())
    }

    suspend fun schedule(ch: ChannelEntity, from: Long, to: Long): List<ProgramEntity> {
        val key = ch.epgId?.lowercase() ?: return emptyList()
        return db.programs().range(ch.playlistId, key, from, to)
    }

    // ---------------------------------------------------------------- favorites & history

    fun favoriteKeys(pid: Long, kind: Int) = db.favorites().observeKeys(pid, kind)
    fun isFavorite(pid: Long, kind: Int, key: String) = db.favorites().observeIs(pid, kind, key).map { it > 0 }

    suspend fun toggleFavorite(pid: Long, kind: Int, key: String): Boolean = withContext(Dispatchers.IO) {
        if (db.favorites().isFavorite(pid, kind, key) > 0) {
            db.favorites().remove(pid, kind, key); false
        } else {
            db.favorites().add(FavoriteEntity(pid, kind, key, System.currentTimeMillis())); true
        }
    }

    suspend fun saveHistory(item: HistoryEntity) = withContext(Dispatchers.IO) { db.history().upsert(item) }
    suspend fun history(pid: Long, kind: Int, key: String) = db.history().get(pid, kind, key)
    fun observeHistory(pid: Long, kind: Int, key: String) = db.history().observe(pid, kind, key)
    fun episodeHistory(pid: Long, seriesKey: String) = db.history().observeEpisodes(pid, seriesKey)
    suspend fun removeHistory(pid: Long, kind: Int, key: String) = db.history().delete(pid, kind, key)

    // ---------------------------------------------------------------- details

    private val seriesCache = ConcurrentHashMap<String, Pair<Long, SeriesDetails>>()
    private val movieCache = ConcurrentHashMap<String, MovieDetails>()

    /** Cached copy if any (instant display), the fresh one is loaded with [seriesDetails]. */
    fun cachedSeriesDetails(p: PlaylistEntity, s: SeriesEntity): SeriesDetails? = seriesCache["${p.id}:${s.seriesId}"]?.second

    /** Episodes are always re-fetched after a few minutes so newly released episodes show up. */
    suspend fun seriesDetails(p: PlaylistEntity, s: SeriesEntity): SeriesDetails = withContext(Dispatchers.IO) {
        val key = "${p.id}:${s.seriesId}"
        val cached = seriesCache[key]
        if (cached != null && System.currentTimeMillis() - cached.first < 5 * 60_000L) return@withContext cached.second
        xtream(p).seriesInfo(s.seriesId).also { seriesCache[key] = System.currentTimeMillis() to it }
    }

    suspend fun movieDetails(p: PlaylistEntity, m: MovieEntity): MovieDetails? = withContext(Dispatchers.IO) {
        if (!p.isXtream || m.streamId == 0L) return@withContext null
        val key = "${p.id}:${m.streamId}"
        movieCache[key] ?: runCatching { xtream(p).vodInfo(m.streamId) }.getOrNull()?.also { movieCache[key] = it }
    }

    // ---------------------------------------------------------------- urls

    fun liveUrl(p: PlaylistEntity?, ch: ChannelEntity): String {
        if (p != null && p.isXtream && ch.streamId > 0) return xtream(p).liveUrl(ch.streamId, settings.value.liveFormat.ext)
        return ch.url
    }

    fun catchupUrl(p: PlaylistEntity, ch: ChannelEntity, program: ProgramEntity): String? {
        if (!p.isXtream || ch.catchupDays <= 0 || ch.streamId == 0L) return null
        val minutes = ((program.endAt - program.startAt) / 60000L).toInt().coerceAtLeast(1)
        return xtream(p).catchupUrl(ch.streamId, program.startAt, minutes, p.serverTimezone)
    }

    companion object {
        private val VOD_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v", "webm", "mpg", "mpeg")
        private val YEAR = Regex("[(\\[ ](19[0-9]{2}|20[0-9]{2})[)\\] ]?\\s*$")
        private val NON_ALNUM = Regex("[^a-z0-9]+")
        private val QUALITY = Regex("\\b(hd|fhd|uhd|sd|4k|hevc|h265|1080p?|720p?)\\b")

        fun isVod(url: String): Boolean {
            val lower = url.lowercase()
            if ("/movie/" in lower || "/series/" in lower) return true
            val ext = lower.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
            return ext in VOD_EXT
        }

        fun yearFrom(name: String): String? = YEAR.find(name)?.groupValues?.get(1)

        fun normalizeName(name: String): String =
            NON_ALNUM.replace(QUALITY.replace(name.searchKey(), " "), "")

        private fun parseRating(r: String?, r5: String?): Float {
            val v = r?.toFloatOrNull()
            if (v != null && v > 0f) return v.coerceAtMost(10f)
            val v5 = r5?.toFloatOrNull() ?: return 0f
            return (v5 * 2f).coerceAtMost(10f)
        }

        private fun buildCategories(pid: Long, kind: Int, remote: List<Pair<String, String>>, used: List<String>): List<CategoryEntity> {
            val usedSet = used.toHashSet()
            val out = ArrayList<CategoryEntity>()
            val known = HashSet<String>()
            remote.forEach { (id, name) ->
                if (id in usedSet && known.add(id)) out += CategoryEntity(pid, kind, id, name, out.size)
            }
            usedSet.filter { it !in known }.sorted().forEach { id ->
                out += CategoryEntity(pid, kind, id, if (id.isBlank()) "Autres" else "Catégorie $id", out.size)
            }
            return out
        }
    }
}

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
