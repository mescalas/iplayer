package com.iplayer.tv.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

object Kind {
    const val LIVE = 0
    const val MOVIE = 1
    const val SERIES = 2
    const val EPISODE = 3
}

object PlaylistType {
    const val M3U = "m3u"
    const val XTREAM = "xtream"
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
    val lastSync: Long = 0,
    val lastEpgSync: Long = 0,
    val expiresAt: Long = 0,
    val maxConnections: Int = 0,
    val serverTimezone: String = "",
    val detectedEpgUrl: String = "",
) {
    val isXtream get() = type == PlaylistType.XTREAM
}

@Entity(
    tableName = "categories",
    primaryKeys = ["playlistId", "kind", "catId"],
)
data class CategoryEntity(
    val playlistId: Long,
    val kind: Int,
    val catId: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["playlistId", "categoryId", "position"]),
        Index(value = ["playlistId", "itemKey"]),
        Index(value = ["playlistId", "number"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: String,
    val itemKey: String,
    val name: String,
    val search: String,
    val logo: String?,
    val url: String,
    val streamId: Long = 0,
    val epgId: String?,
    val number: Int,
    val position: Int,
    val catchupDays: Int = 0,
)

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["playlistId", "categoryId", "position"]),
        Index(value = ["playlistId", "itemKey"]),
        Index(value = ["playlistId", "added"]),
    ],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: String,
    val itemKey: String,
    val name: String,
    val search: String,
    val poster: String?,
    val url: String,
    val streamId: Long = 0,
    val extension: String? = null,
    val rating: Float = 0f,
    val year: String? = null,
    val added: Long = 0,
    val position: Int,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["playlistId", "categoryId", "position"]),
        Index(value = ["playlistId", "itemKey"]),
        Index(value = ["playlistId", "added"]),
    ],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: String,
    val itemKey: String,
    val seriesId: Long,
    val name: String,
    val search: String,
    val cover: String?,
    val plot: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val rating: Float = 0f,
    val backdrop: String? = null,
    val added: Long = 0,
    val position: Int,
)

@Entity(
    tableName = "programs",
    indices = [
        Index(value = ["playlistId", "channelKey", "startAt"]),
        // "What is on now" for every channel: a short range of start times instead of the whole guide.
        Index(value = ["playlistId", "startAt"]),
    ],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelKey: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String?,
    @ColumnInfo(defaultValue = "''") val categories: String = "",
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "kind", "itemKey"])
data class FavoriteEntity(
    val playlistId: Long,
    val kind: Int,
    val itemKey: String,
    val addedAt: Long,
)

@Entity(
    tableName = "history",
    primaryKeys = ["playlistId", "kind", "itemKey"],
    indices = [Index(value = ["playlistId", "kind", "updatedAt"])],
)
data class HistoryEntity(
    val playlistId: Long,
    val kind: Int,
    val itemKey: String,
    val title: String,
    val subtitle: String? = null,
    val image: String?,
    val url: String,
    val position: Long = 0,
    val duration: Long = 0,
    val updatedAt: Long,
    /** For episodes: the parent series id, used to resume a show. */
    val parentKey: String? = null,
)
