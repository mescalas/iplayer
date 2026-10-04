package com.iplayer.tv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY id")
    suspend fun all(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :pid AND kind = :kind ORDER BY position")
    fun observe(pid: Long, kind: Int): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE playlistId = :pid AND kind = :kind")
    suspend fun delete(pid: Long, kind: Int)

    @Query("DELETE FROM categories WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<CategoryEntity>)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :pid ORDER BY position")
    fun observeAll(pid: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    fun observeCategory(pid: Long, cat: String): Flow<List<ChannelEntity>>

    @Query(
        """SELECT c.* FROM channels c INNER JOIN favorites f
           ON f.playlistId = c.playlistId AND f.kind = 0 AND f.itemKey = c.itemKey
           WHERE c.playlistId = :pid GROUP BY c.itemKey ORDER BY f.addedAt"""
    )
    fun observeFavorites(pid: Long): Flow<List<ChannelEntity>>

    @Query(
        """SELECT c.* FROM channels c INNER JOIN history h
           ON h.playlistId = c.playlistId AND h.kind = 0 AND h.itemKey = c.itemKey
           WHERE c.playlistId = :pid GROUP BY c.itemKey ORDER BY MAX(h.updatedAt) DESC LIMIT 40"""
    )
    fun observeRecent(pid: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND search LIKE '%' || :q || '%' ORDER BY position LIMIT :limit")
    suspend fun search(pid: Long, q: String, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND number = :number ORDER BY position LIMIT 1")
    suspend fun byNumber(pid: Long, number: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND itemKey = :key LIMIT 1")
    suspend fun byKey(pid: Long, key: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun get(id: Long): ChannelEntity?

    @Query("SELECT DISTINCT LOWER(epgId) FROM channels WHERE playlistId = :pid AND epgId IS NOT NULL AND epgId != ''")
    suspend fun epgIds(pid: Long): List<String>

    @Query("SELECT id, name FROM channels WHERE playlistId = :pid AND (epgId IS NULL OR epgId = '')")
    suspend fun withoutEpg(pid: Long): List<IdName>

    @Query("UPDATE channels SET epgId = :epgId WHERE id = :id")
    suspend fun setEpgId(id: Long, epgId: String)

    @Query("SELECT MAX(catchupDays) FROM channels WHERE playlistId = :pid")
    suspend fun maxCatchupDays(pid: Long): Int?

    @Query("DELETE FROM channels WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)

    @Insert
    suspend fun insert(items: List<ChannelEntity>)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

data class IdName(val id: Long, val name: String)

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE playlistId = :pid ORDER BY position")
    fun pageAll(pid: Long): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    fun pageCategory(pid: Long, cat: String): PagingSource<Int, MovieEntity>

    @Query(
        """SELECT m.* FROM movies m INNER JOIN favorites f
           ON f.playlistId = m.playlistId AND f.kind = 1 AND f.itemKey = m.itemKey
           WHERE m.playlistId = :pid GROUP BY m.itemKey ORDER BY f.addedAt DESC"""
    )
    fun pageFavorites(pid: Long): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :pid ORDER BY added DESC, position LIMIT :limit")
    fun observeLatest(pid: Long, limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND rating > 0 ORDER BY rating DESC, added DESC LIMIT :limit")
    fun observeTopRated(pid: Long, limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND search LIKE '%' || :q || '%' ORDER BY added DESC LIMIT :limit")
    suspend fun search(pid: Long, q: String, limit: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun get(id: Long): MovieEntity?

    /** Titles of the same category, the ones with artwork and the best rating first. */
    @Query(
        """SELECT * FROM movies WHERE playlistId = :pid AND categoryId = :cat AND id != :exclude
           ORDER BY (poster IS NULL OR poster = ''), rating DESC, added DESC LIMIT :limit"""
    )
    suspend fun similar(pid: Long, cat: String, exclude: Long, limit: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND itemKey = :key LIMIT 1")
    suspend fun byKey(pid: Long, key: String): MovieEntity?

    @Query("DELETE FROM movies WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)

    @Insert
    suspend fun insert(items: List<MovieEntity>)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE playlistId = :pid ORDER BY position")
    fun pageAll(pid: Long): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    fun pageCategory(pid: Long, cat: String): PagingSource<Int, SeriesEntity>

    @Query(
        """SELECT s.* FROM series s INNER JOIN favorites f
           ON f.playlistId = s.playlistId AND f.kind = 2 AND f.itemKey = s.itemKey
           WHERE s.playlistId = :pid GROUP BY s.itemKey ORDER BY f.addedAt DESC"""
    )
    fun pageFavorites(pid: Long): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE playlistId = :pid ORDER BY added DESC, position LIMIT :limit")
    fun observeLatest(pid: Long, limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND search LIKE '%' || :q || '%' ORDER BY added DESC LIMIT :limit")
    suspend fun search(pid: Long, q: String, limit: Int): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun get(id: Long): SeriesEntity?

    @Query(
        """SELECT * FROM series WHERE playlistId = :pid AND categoryId = :cat AND id != :exclude
           ORDER BY (cover IS NULL OR cover = ''), rating DESC, added DESC LIMIT :limit"""
    )
    suspend fun similar(pid: Long, cat: String, exclude: Long, limit: Int): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND itemKey = :key LIMIT 1")
    suspend fun byKey(pid: Long, key: String): SeriesEntity?

    @Query("DELETE FROM series WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)

    @Insert
    suspend fun insert(items: List<SeriesEntity>)

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs WHERE playlistId = :pid AND startAt <= :now AND endAt > :now")
    suspend fun current(pid: Long, now: Long): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key AND endAt > :from AND startAt < :to ORDER BY startAt")
    suspend fun range(pid: Long, key: String, from: Long, to: Long): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key AND endAt > :now ORDER BY startAt LIMIT 2")
    suspend fun nowNext(pid: Long, key: String, now: Long): List<ProgramEntity>

    @Query("DELETE FROM programs WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)

    @Insert
    fun insertBlocking(items: List<ProgramEntity>)

    @Query("SELECT COUNT(*) FROM programs WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT itemKey FROM favorites WHERE playlistId = :pid AND kind = :kind")
    fun observeKeys(pid: Long, kind: Int): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM favorites WHERE playlistId = :pid AND kind = :kind AND itemKey = :key")
    fun observeIs(pid: Long, kind: Int, key: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM favorites WHERE playlistId = :pid AND kind = :kind AND itemKey = :key")
    suspend fun isFavorite(pid: Long, kind: Int, key: String): Int

    @Upsert
    suspend fun add(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :pid AND kind = :kind AND itemKey = :key")
    suspend fun remove(pid: Long, kind: Int, key: String)

    @Query("DELETE FROM favorites WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(item: HistoryEntity)

    @Query("SELECT * FROM history WHERE playlistId = :pid AND kind = :kind AND itemKey = :key")
    suspend fun get(pid: Long, kind: Int, key: String): HistoryEntity?

    @Query("SELECT * FROM history WHERE playlistId = :pid AND kind = :kind AND itemKey = :key")
    fun observe(pid: Long, kind: Int, key: String): Flow<HistoryEntity?>

    @Query(
        """SELECT * FROM history h WHERE h.playlistId = :pid AND h.kind IN (1, 3)
           AND h.position > 30000 AND (h.duration <= 0 OR h.position < h.duration * 0.94)
           AND (h.kind = 1 OR h.updatedAt = (SELECT MAX(h2.updatedAt) FROM history h2
                WHERE h2.playlistId = h.playlistId AND h2.kind = 3 AND h2.parentKey = h.parentKey))
           ORDER BY h.updatedAt DESC LIMIT 20"""
    )
    fun observeContinue(pid: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE playlistId = :pid AND kind = 3 AND parentKey = :seriesKey ORDER BY updatedAt DESC")
    fun observeEpisodes(pid: Long, seriesKey: String): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE playlistId = :pid AND kind = :kind AND itemKey = :key")
    suspend fun delete(pid: Long, kind: Int, key: String)

    @Query("DELETE FROM history WHERE playlistId = :pid")
    suspend fun deleteAll(pid: Long)
}
