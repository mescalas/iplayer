package com.iplayer.tv.data.remote

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class XtreamAccount(
    val expiresAt: Long,
    val maxConnections: Int,
    val status: String,
    val timezone: String,
)

data class XtreamCategory(val id: String, val name: String)

data class EpisodeInfo(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val extension: String,
    val image: String?,
    val plot: String?,
    val durationSecs: Int,
    val rating: String?,
)

data class SeasonInfo(val number: Int, val name: String, val cover: String?)

data class SeriesDetails(
    val name: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val cover: String?,
    val backdrop: String?,
    val seasons: List<SeasonInfo>,
    val episodes: Map<Int, List<EpisodeInfo>>,
)

data class MovieDetails(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val durationSecs: Int,
    val image: String?,
    val backdrop: String?,
    val extension: String?,
)

class XtreamClient(
    server: String,
    private val username: String,
    private val password: String,
    private val userAgent: String?,
) {
    val base: String = normalizeServer(server)

    private fun api(action: String? = null, extra: String = ""): String {
        val sb = StringBuilder(base).append("/player_api.php?username=").append(enc(username))
            .append("&password=").append(enc(password))
        if (action != null) sb.append("&action=").append(action)
        sb.append(extra)
        return sb.toString()
    }

    fun authenticate(): XtreamAccount {
        val text = Http.get(api(), userAgent).use { it.body!!.string() }
        val root = try { JSONObject(text) } catch (e: Exception) {
            throw IOException("Réponse invalide du serveur Xtream. Vérifiez l'adresse.")
        }
        val user = root.optJSONObject("user_info") ?: throw IOException("Identifiants refusés.")
        val auth = user.opt("auth")?.toString()
        if (auth != "1" && auth != "true") throw IOException("Identifiants refusés par le serveur.")
        val server = root.optJSONObject("server_info")
        return XtreamAccount(
            expiresAt = (user.optString("exp_date").toLongOrNull() ?: 0L) * 1000L,
            maxConnections = user.optString("max_connections").toIntOrNull() ?: 0,
            status = user.optString("status"),
            timezone = server?.optString("timezone").orEmpty(),
        )
    }

    fun categories(action: String): List<XtreamCategory> {
        val text = Http.get(api(action), userAgent).use { it.body!!.string() }
        val arr = try { JSONArray(text) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<XtreamCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += XtreamCategory(o.optString("category_id"), o.optString("category_name").trim())
        }
        return out
    }

    /** Streams a large JSON array of flat objects (live / vod / series lists) without loading it in memory. */
    fun streamList(action: String, onItem: (Map<String, String>) -> Unit) {
        Http.get(api(action), userAgent).use { resp ->
            JsonReader(resp.body!!.charStream().buffered(64 * 1024)).use { r ->
                r.isLenient = true
                if (r.peek() != JsonToken.BEGIN_ARRAY) {
                    r.skipValue(); return
                }
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    val map = HashMap<String, String>(24)
                    r.beginObject()
                    while (r.hasNext()) {
                        val key = r.nextName()
                        when (r.peek()) {
                            JsonToken.STRING, JsonToken.NUMBER -> map[key] = r.nextString()
                            JsonToken.BOOLEAN -> map[key] = if (r.nextBoolean()) "1" else "0"
                            JsonToken.BEGIN_ARRAY -> {
                                // keep the first string of arrays (e.g. backdrop_path)
                                r.beginArray()
                                var first: String? = null
                                while (r.hasNext()) {
                                    if (first == null && r.peek() == JsonToken.STRING) first = r.nextString() else r.skipValue()
                                }
                                r.endArray()
                                if (first != null) map[key] = first
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    onItem(map)
                }
                r.endArray()
            }
        }
    }

    fun seriesInfo(seriesId: Long): SeriesDetails {
        val text = Http.get(api("get_series_info", "&series_id=$seriesId"), userAgent).use { it.body!!.string() }
        val root = JSONObject(text)
        val info = root.optJSONObject("info") ?: JSONObject()
        val seasons = ArrayList<SeasonInfo>()
        root.optJSONArray("seasons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val n = s.optString("season_number").toIntOrNull() ?: continue
                val label = s.optString("name").trim().takeIf { it.isNotEmpty() && it.any { c -> c.isDigit() } } ?: "Saison $n"
                seasons += SeasonInfo(n, label, s.str("cover_big") ?: s.str("cover"))
            }
        }
        val episodes = sortedMapOf<Int, MutableList<EpisodeInfo>>()
        fun addEpisode(o: JSONObject, fallbackSeason: Int) {
            val inf = o.optJSONObject("info")
            val season = o.optString("season").toIntOrNull() ?: fallbackSeason
            val ep = EpisodeInfo(
                id = o.optString("id"),
                season = season,
                episode = o.optString("episode_num").toIntOrNull() ?: 0,
                title = o.optString("title").ifBlank { "Épisode ${o.optString("episode_num")}" },
                extension = o.optString("container_extension").ifBlank { "mp4" },
                image = inf?.str("movie_image") ?: inf?.str("cover_big"),
                plot = inf?.str("plot"),
                durationSecs = inf?.optString("duration_secs")?.toIntOrNull() ?: 0,
                rating = inf?.str("rating"),
            )
            episodes.getOrPut(season) { mutableListOf() } += ep
        }
        when (val eps = root.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k ->
                val arr = eps.optJSONArray(k) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, k.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val item = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until item.length()) item.optJSONObject(j)?.let { addEpisode(it, i + 1) }
                    is JSONObject -> addEpisode(item, 1)
                }
            }
        }
        episodes.values.forEach { list -> list.sortBy { it.episode } }
        // Every season that actually has episodes, even when the provider has not listed it in
        // "seasons" yet (ongoing seasons whose episodes are added week after week).
        val known = seasons.associateBy { it.number }
        val allSeasons = episodes.keys.map { n -> known[n] ?: SeasonInfo(n, "Saison $n", null) }
        return SeriesDetails(
            name = info.optString("name"),
            plot = info.str("plot"),
            cast = info.str("cast"),
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releaseDate") ?: info.str("release_date"),
            rating = info.str("rating"),
            cover = info.str("cover"),
            backdrop = info.firstString("backdrop_path"),
            seasons = allSeasons.sortedBy { it.number },
            episodes = episodes,
        )
    }

    fun vodInfo(vodId: Long): MovieDetails {
        val text = Http.get(api("get_vod_info", "&vod_id=$vodId"), userAgent).use { it.body!!.string() }
        val root = JSONObject(text)
        val info = root.optJSONObject("info") ?: JSONObject()
        val data = root.optJSONObject("movie_data")
        return MovieDetails(
            plot = info.str("plot") ?: info.str("description"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            rating = info.str("rating"),
            durationSecs = info.optString("duration_secs").toIntOrNull() ?: 0,
            image = info.str("movie_image") ?: info.str("cover_big"),
            backdrop = info.firstString("backdrop_path"),
            extension = data?.str("container_extension"),
        )
    }

    fun liveUrl(streamId: Long, ext: String) = "$base/live/${username}/${password}/$streamId.$ext"
    fun movieUrl(streamId: Long, ext: String) = "$base/movie/${username}/${password}/$streamId.$ext"
    fun episodeUrl(id: String, ext: String) = "$base/series/${username}/${password}/$id.$ext"
    fun epgUrl() = "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"

    fun catchupUrl(streamId: Long, startMs: Long, durationMin: Int, timezone: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.ROOT)
        fmt.timeZone = timezone.takeIf { it.isNotBlank() }?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        return "$base/timeshift/${username}/${password}/$durationMin/${fmt.format(Date(startMs))}/$streamId.ts"
    }

    companion object {
        fun normalizeServer(input: String): String {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
            return s.trimEnd('/')
        }

        /** Detects Xtream credentials inside a classic `get.php?username=..&password=..` M3U link. */
        fun fromM3uUrl(url: String): Triple<String, String, String>? {
            val u = url.trim().toHttpUrlOrNull() ?: return null
            if (!u.encodedPath.endsWith("get.php")) return null
            val user = u.queryParameter("username") ?: return null
            val pass = u.queryParameter("password") ?: return null
            val port = if ((u.scheme == "http" && u.port == 80) || (u.scheme == "https" && u.port == 443)) "" else ":${u.port}"
            return Triple("${u.scheme}://${u.host}$port", user, pass)
        }

        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    }
}

private fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key)
    if (v is JSONArray) return null
    return v?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}

private fun JSONObject.firstString(key: String): String? = when (val v = opt(key)) {
    is JSONArray -> (0 until v.length()).asSequence().map { v.optString(it) }.firstOrNull { it.isNotBlank() }
    is String -> v.takeIf { it.isNotBlank() }
    else -> null
}
