package com.iplayer.tv.util

import com.iplayer.tv.data.db.Kind
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/**
 * A raw IPTV name ("|FR| DUNE (2021) [MULTI] 4K HDR", "Lupin - S02E03 - Chapitre 3", "FR: TF1 FHD ᴴᴰ")
 * split into a clean title and the technical details providers glue to it.
 */
data class MediaName(
    val title: String,
    /** Provider prefix such as "FR", "EN" or "NF". */
    val tag: String? = null,
    val year: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** What follows "S01E02" in an episode name, usually the episode title. */
    val episodeTitle: String? = null,
    /** "SD", "HD", "FHD", "4K" or "8K". */
    val quality: String? = null,
    /** "HDR", "HDR10+" or "Dolby Vision". */
    val hdr: String? = null,
    /** "VF", "VOSTFR", "VO" or "MULTI". */
    val language: String? = null,
    val is3d: Boolean = false,
) {
    /** Pills for films, series and episodes: only what changes the viewing (HD and French audio are the norm). */
    val techBadges: List<String>
        get() = listOfNotNull(
            quality?.takeIf { it == "4K" || it == "8K" },
            hdr,
            "3D".takeIf { is3d },
            language?.takeIf { it != "VF" },
        )

    /** [techBadges] plus the season when a series entry only covers one ("Lupin S02"). */
    val badges: List<String>
        get() = techBadges + listOfNotNull(season?.takeIf { episode == null }?.let { seasonLabel(it) })

    /** Pill for live channels: the quality tells apart the variants of a same channel (TF1 HD / TF1 4K). */
    val channelBadge: String? get() = quality ?: hdr?.let { "HDR" }
}

/** A category name split into an optional provider tag ("FR", "EN"…) and the clean name. */
data class TaggedName(val tag: String?, val name: String)

private val cache = object : LinkedHashMap<String, MediaName>(512, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaName>?) = size > 4000
}

/** Parsed form of a film / series / episode / channel name (cached, cheap to call from the UI). */
fun String.mediaName(): MediaName = synchronized(cache) { cache.getOrPut(this) { NameParser.parse(this) } }

/** Same as [mediaName] without touching the UI cache (bulk use during a playlist sync). */
fun String.mediaNameUncached(): MediaName = NameParser.parse(this)

/** Title without provider tag, year, quality, language… */
fun String.cleanTitle(): String = mediaName().title

fun seasonLabel(n: Int): String = if (n == 0) "Épisodes spéciaux" else "Saison $n"

/**
 * Clean episode title, or null when the provider only repeats the show name / episode number
 * ("Breaking Bad - S01E01" → null, "Breaking Bad - S01E01 - Pilot" → "Pilot").
 */
fun episodeTitle(raw: String, seriesName: String): String? {
    val p = raw.mediaName()
    var t = if (p.episode != null) p.episodeTitle.orEmpty() else p.title
    val show = seriesName.cleanTitle()
    t = NameParser.stripLeading(t, show)
    t = NameParser.stripEpisodeNumber(t)
    t = NameParser.trimSeparators(t)
    if (t.none { it.isLetter() }) return null
    if (t.searchKey() == show.searchKey()) return null
    return t
}

/** "S1 · É3 — Pilot" (the episode title is left out when the provider has none). */
fun episodeSubtitle(season: Int, episode: Int, title: String?): String =
    "S$season · É$episode" + (title?.let { " — $it" } ?: "")

private val EPISODE_SUBTITLE = Regex("^S(\\d+) · É(\\d+)(?: — (.*))?$")

/** Cleans an [episodeSubtitle] saved in the history before titles were cleaned up. */
fun cleanEpisodeSubtitle(subtitle: String?, seriesName: String): String? {
    val m = EPISODE_SUBTITLE.find(subtitle ?: return null) ?: return subtitle
    val title = m.groupValues[3].takeIf { it.isNotBlank() }?.let { episodeTitle(it, seriesName) }
    return episodeSubtitle(m.groupValues[1].toInt(), m.groupValues[2].toInt(), title)
}

/** Category name without provider tag, decorations nor words repeating the section ("FILMS", "SÉRIES"…). */
fun String.categoryLabel(kind: Int): TaggedName = NameParser.category(this, kind)

private sealed interface Info {
    data class Quality(val v: String) : Info
    data class Hdr(val v: String) : Info
    data class Lang(val v: String) : Info
    data object ThreeD : Info
    data object Noise : Info
}

internal object NameParser {
    private val TOKENS = HashMap<String, Info>().apply {
        fun add(info: Info, vararg keys: String) = keys.forEach { put(it, info) }
        add(Info.Quality("SD"), "SD", "480P", "576P", "LQ")
        add(Info.Quality("HD"), "HD", "720P", "HQ")
        add(Info.Quality("FHD"), "FHD", "1080P", "1080I", "FULLHD")
        add(Info.Quality("4K"), "4K", "UHD", "2160P", "3840P", "4KUHD", "UHD4K", "4K+")
        add(Info.Quality("8K"), "8K", "4320P")
        add(Info.Hdr("HDR"), "HDR", "HDR10")
        add(Info.Hdr("HDR10+"), "HDR10+", "HDR10PLUS")
        add(Info.Hdr("Dolby Vision"), "DV", "DOVI")
        add(Info.ThreeD, "3D", "SBS", "HSBS")
        add(Info.Lang("VF"), "VF", "VFF", "VFQ", "VFI", "VF2", "FR", "FRENCH", "TRUEFRENCH")
        add(Info.Lang("VOSTFR"), "VOSTFR", "VOST", "STFR", "SUBFRENCH")
        add(Info.Lang("VO"), "VO", "VOA", "ENG", "ENGLISH")
        add(Info.Lang("MULTI"), "MULTI", "MULTISUB", "MULTILANG")
        add(
            Info.Noise,
            "HEVC", "H264", "H265", "X264", "X265", "AVC", "AV1", "VP9", "10BIT", "8BIT",
            "AAC", "AC3", "EAC3", "DD", "DDP", "DD+", "DTS", "DTSHD", "TRUEHD", "ATMOS", "CH51", "CH71",
            "BLURAY", "BDRIP", "BRRIP", "REMUX", "WEBDL", "WEBRIP", "HDRIP", "DVDRIP", "HDLIGHT", "MHD", "4KLIGHT",
            "HDTV", "PDTV", "TVRIP", "PROPER", "REPACK", "SDR", "HFR", "25FPS", "30FPS", "50FPS", "60FPS",
        )
    }

    /** Short tokens that are also ordinary words: only taken when written in capitals. */
    private val CASE_SENSITIVE = setOf("SD", "HD", "HQ", "LQ", "VO", "VOA", "VF", "VFF", "VFQ", "VFI", "FR", "DV", "DD", "FRENCH", "ENGLISH", "ENG", "PROPER")

    /** Country / language codes used as provider prefixes ("FR |", "[EN]"). */
    private val COUNTRIES = setOf(
        "FR", "FRA", "EN", "ENG", "UK", "GB", "US", "USA", "DE", "GER", "ES", "ESP", "IT", "ITA", "PT", "BR", "NL",
        "BE", "CH", "CA", "QC", "AR", "ARA", "TR", "PL", "RO", "GR", "RU", "AF", "AL", "MA", "DZ", "TN", "LAT",
        "LATAM", "EU", "EXYU", "IN", "PK", "KR", "JP", "CN", "SE", "NO", "DK", "FI", "CZ", "HU", "BG", "HR", "RS",
        "SK", "SI", "MK", "BA", "IR", "KU", "IL", "AFR", "ARAB", "AFRIQUE", "FRANCE", "BELGIQUE", "SUISSE", "QUEBEC",
    )
    private val PLATFORMS = setOf("NF", "NFX", "NETFLIX", "AMZ", "AMAZON", "PRIME", "DSNP", "DISNEY", "APPLE", "ATV", "HBO", "MAX", "CANAL", "OCS", "SKY", "PARAMOUNT")
    /** Prefixes that say nothing useful once the item is already in the right section. */
    private val GENERIC = setOf("VOD", "LIVE", "TOP", "NEW", "ALL", "TV", "VIP", "HOT", "24/7")
    private val COUNTRY_ALIASES = mapOf("FRA" to "FR", "FRANCE" to "FR", "ENG" to "EN", "GER" to "DE", "ESP" to "ES", "ITA" to "IT", "ARA" to "AR", "BELGIQUE" to "BE", "SUISSE" to "CH", "QUEBEC" to "QC")

    private val SPACES = Regex("\\s{2,}")
    private val NOISE_RUNS = Regex("[=#*~_]{2,}")
    private val PHRASES = listOf(
        Regex("(?i)dolby[\\s._-]*vision") to " DOVI ",
        Regex("(?i)(?<![\\p{L}\\d])full[\\s._-]*hd(?![\\p{L}\\d])") to " FHD ",
        Regex("(?i)(?<![\\p{L}\\d])ultra[\\s._-]*hd(?![\\p{L}\\d])") to " UHD ",
        Regex("(?i)(?<![\\p{L}\\d])multi[\\s._-]*(?:audios?|langues?|lang|subs?|vf2?|vff|vfq)(?![\\p{L}\\d])") to " MULTI ",
        Regex("(?i)(?<![\\p{L}\\d])dual[\\s._-]*audio(?![\\p{L}\\d])") to " MULTI ",
        Regex("(?i)(?<![\\p{L}\\d])web[\\s._-]*(dl|rip)(?![\\p{L}\\d])") to " WEB$1 ",
        Regex("(?i)(?<![\\p{L}\\d])blu[\\s._-]*ray(?![\\p{L}\\d])") to " BLURAY ",
        Regex("(?i)(?<![\\p{L}\\d])([hx])[\\s._-]?26([45])(?![\\p{L}\\d])") to " H26$2 ",
        Regex("(?i)(?<![\\p{L}\\d])dts[\\s._-]*hd(?![\\p{L}\\d])") to " DTSHD ",
        Regex("(?i)(?<![\\p{L}\\d])hdr[\\s._-]?10[\\s._-]?(?:\\+|plus)") to " HDR10+ ",
        Regex("(?i)(?<![\\p{L}\\d])true[\\s._-]*french(?![\\p{L}\\d])") to " TRUEFRENCH ",
        Regex("(?<![\\p{L}\\d])5[.,]1(?![\\p{L}\\d])") to " CH51 ",
        Regex("(?<![\\p{L}\\d])7[.,]1(?![\\p{L}\\d])") to " CH71 ",
    )
    private val BRACKETS = Regex("[\\[({]([^\\[\\](){}]*)[\\])}]")
    private val GROUP_SPLIT = Regex("[\\s,/|_]+")
    private val EPISODE = Regex(
        "(?i)(?<![\\p{L}\\d])(?:S(\\d{1,2})\\s?[EÉ](?:P|PISODE)?\\s?(\\d{1,3})|(\\d{1,2})x(\\d{2,4})|" +
            "(?:saison|season)\\s*(\\d{1,2})\\s*[-,.]?\\s*(?:[ÉéEe]pisode|ep\\.?|[EÉ])\\s*(\\d{1,3})|(?:[ÉéEe]pisode|ep\\.?)\\s*(\\d{1,4}))(?![\\p{L}\\d])"
    )
    private val SEASON_END = Regex(
        "(?i)(?<![\\p{L}\\d])(?:S|saison|season)\\s?(\\d{1,2})(?:\\s?(?:-|à|a|to)\\s?(?:S|saison|season)?\\s?(\\d{1,2}))?[\\s\\-–—:|·]*$"
    )
    private val SEASON_ONLY = Regex("(?i)^\\s*(?:S|saison|season)\\s?(\\d{1,2})\\s*$")
    private val EPISODE_NUMBER = Regex("(?i)^(?:(?:[ÉéEe]pisode|ep\\.?|e|#)\\s*\\d{1,3}(?![\\p{L}\\d])|\\d{1,3}\\s*(?:[-–—:.)|]|$))[\\s\\-–—:.)|]*")
    private const val SEPARATORS = " -–—|:·•»«›‹/,~*#=_>"
    private val YEAR = Regex("(19|20)\\d{2}")
    private val maxYear = Calendar.getInstance().get(Calendar.YEAR) + 2

    private class State {
        var tag: String? = null
        var year: String? = null
        var season: Int? = null
        var quality: String? = null
        var hdr: String? = null
        var language: String? = null
        var is3d = false

        fun apply(info: Info) {
            when (info) {
                is Info.Quality -> if (quality == null || rank(info.v) > rank(quality!!)) quality = info.v
                is Info.Hdr -> if (hdr == null || info.v == "Dolby Vision") hdr = info.v
                is Info.Lang -> if (language == null || info.v == "MULTI") language = info.v
                Info.ThreeD -> is3d = true
                Info.Noise -> {}
            }
        }

        private fun rank(q: String) = listOf("SD", "HD", "FHD", "4K", "8K").indexOf(q)
    }

    // ------------------------------------------------------------------ titles

    fun parse(raw: String): MediaName {
        val st = State()
        var s = clean(raw)
        if (s.isEmpty()) return MediaName(raw.trim())
        // Release-style names: "Dune.Part.Two.2024.MULTi.2160p.WEB-DL.H265-GRP"
        var scene = false
        if (' ' !in s && (s.count { it == '.' } >= 2 || s.count { it == '_' } >= 2)) {
            val parts = s.split('.', '_').filter { it.isNotEmpty() }
            if (parts.drop(1).any { classify(it) != null || isYear(it) }) {
                s = parts.joinToString(" ")
                scene = true
            }
        }
        s = phrases(s)
        s = takePrefixes(s, st, allowSpace = false)
        s = BRACKETS.replace(s) { m -> if (consumeGroup(m.groupValues[1], st)) " " else m.value }

        val toks = s.split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (scene) {
            val cut = (1 until toks.size).firstOrNull { classify(toks[it]) != null || isYear(toks[it]) }
            if (cut != null) {
                toks.subList(cut, toks.size).forEach { t ->
                    if (isYear(t)) st.year = st.year ?: t else classify(t)?.let(st::apply)
                }
                while (toks.size > cut) toks.removeAt(toks.lastIndex)
            }
        }
        while (toks.size > 1) {
            val info = if (isSeparator(toks[0])) Info.Noise else classify(toks[0]) ?: break
            st.apply(info)
            toks.removeAt(0)
        }
        var strippedTech = false
        while (toks.size > 1) {
            val t = toks[toks.lastIndex]
            val info = classify(t)
            when {
                isSeparator(t) -> {}
                info != null -> { st.apply(info); strippedTech = true }
                isYear(t) && (strippedTech || isSeparator(toks[toks.lastIndex - 1])) -> st.year = st.year ?: t
                else -> break
            }
            toks.removeAt(toks.lastIndex)
        }
        var text = toks.joinToString(" ").replace(" | ", " - ")

        var episode: Int? = null
        var episodeTitle: String? = null
        val ep = EPISODE.find(text)
        if (ep != null) {
            val g = ep.groupValues
            st.season = (g[1].ifEmpty { g[3].ifEmpty { g[5] } }).toIntOrNull()
            episode = (g[2].ifEmpty { g[4].ifEmpty { g[6].ifEmpty { g[7] } } }).toIntOrNull()
            episodeTitle = prettyCase(trimSeparators(text.substring(ep.range.last + 1))).ifEmpty { null }
            text = text.substring(0, ep.range.first)
        } else {
            SEASON_END.find(text)?.let { m ->
                if (m.range.first > 0) {
                    if (m.groupValues[2].isEmpty()) st.season = m.groupValues[1].toIntOrNull()
                    text = text.substring(0, m.range.first)
                }
            }
        }

        var title = prettyCase(trimSeparators(text))
        if (title.isEmpty()) title = episodeTitle ?: episode?.let { "Épisode $it" } ?: st.tag ?: prettyCase(trimSeparators(clean(raw)))
        if (title.isEmpty()) title = raw.trim()
        return MediaName(
            title = title,
            tag = st.tag,
            year = st.year,
            season = st.season,
            episode = episode,
            episodeTitle = episodeTitle,
            quality = st.quality,
            hdr = st.hdr,
            language = st.language,
            is3d = st.is3d,
        )
    }

    /** Removes provider prefixes ("|FR|", "[EN]", "FR -", "4K-FR |", "NF:") and records what they say. */
    private fun takePrefixes(input: String, st: State, allowSpace: Boolean, tags: MutableList<String>? = null): String {
        var s = input
        repeat(4) {
            val (content, rest) = prefix(s, allowSpace) ?: return s
            if (rest.none { it.isLetterOrDigit() }) return s
            // "DUNE | 4K": what follows is only technical, so the "prefix" is the actual title.
            if (tags == null && rest.split(' ').all { it.isEmpty() || isSeparator(it) || isYear(it) || classify(it) != null }) return s
            val parts = content.split('-', '_', ' ').filter { it.isNotEmpty() }
            // In a category, "NETFLIX |" is the subject itself, not a provider prefix.
            if (tags != null && parts.all { it.uppercase(Locale.ROOT) in PLATFORMS }) return s
            parts.forEach { part ->
                val up = part.uppercase(Locale.ROOT)
                val info = classify(part)
                when {
                    tags != null && up !in GENERIC -> tags += COUNTRY_ALIASES[up] ?: up
                    tags == null && info != null && up !in COUNTRIES -> st.apply(info)
                    tags == null && up !in GENERIC -> if (st.tag == null) st.tag = COUNTRY_ALIASES[up] ?: up
                }
            }
            s = rest
        }
        return s
    }

    private fun prefix(s: String, allowSpace: Boolean): Pair<String, String>? {
        if (s.isEmpty()) return null
        fun rest(from: Int) = s.substring(from).trimStart { it in SEPARATORS }
        when (s[0]) {
            '|' -> {
                val end = s.indexOf('|', 1)
                val content = if (end in 2..15) s.substring(1, end).trim() else return null
                return if (content.none { it.isLowerCase() }) content to rest(end + 1) else null
            }
            '[', '(' -> {
                val end = s.indexOf(if (s[0] == '[') ']' else ')')
                val content = if (end in 2..15) s.substring(1, end).trim() else return null
                return if (isKnown(content)) content to rest(end + 1) else null
            }
        }
        val sep = s.indexOfFirst { it in "|»›:-–—•" }
        if (sep in 1..12) {
            val content = s.substring(0, sep).trim()
            if (content.isNotEmpty() && content.none { it.isLowerCase() }) {
                val c = s[sep]
                val generic = c in "|»›•" && content.length in 2..5 && content.all { it.isLetterOrDigit() || it == '+' }
                if (isKnown(content) || generic) return content to rest(sep + 1)
            }
        }
        if (allowSpace) {
            val space = s.indexOf(' ')
            if (space in 2..7) {
                val word = s.substring(0, space)
                if (word in COUNTRIES || word == "VOD") return word to rest(space + 1)
            }
        }
        return null
    }

    private fun isKnown(content: String): Boolean {
        val parts = content.split('-', '_', ' ').filter { it.isNotEmpty() }
        return parts.isNotEmpty() && parts.all { p ->
            val up = p.uppercase(Locale.ROOT)
            p == up && (up in COUNTRIES || up in PLATFORMS || up in GENERIC || classify(p) != null)
        }
    }

    /** A bracketed group made only of technical details ("(2021)", "[MULTI 4K]", "(VF)") is consumed. */
    private fun consumeGroup(content: String, st: State): Boolean {
        if (content.isBlank()) return true
        SEASON_ONLY.find(content)?.let { m ->
            st.season = st.season ?: m.groupValues[1].toIntOrNull()
            return true
        }
        val tokens = content.split(GROUP_SPLIT).filter { it.isNotEmpty() }.flatMap { t ->
            if ('-' in t && classify(t) == null) t.split('-').filter { it.isNotEmpty() } else listOf(t)
        }
        if (tokens.isEmpty()) return true
        val infos = tokens.map { t ->
            when {
                isYear(t) -> null
                t.uppercase(Locale.ROOT) in setOf("FRA", "VFR") -> Info.Lang("VF")
                t == "EN" -> Info.Lang("VO")
                else -> classify(t) ?: return false
            }
        }
        tokens.forEach { if (isYear(it)) st.year = st.year ?: it }
        infos.forEach { if (it != null) st.apply(it) }
        return true
    }

    private fun classify(token: String): Info? {
        val t = token.trim { !it.isLetterOrDigit() && it != '+' }
        if (t.isEmpty()) return null
        val key = t.uppercase(Locale.ROOT).replace("-", "").replace(".", "")
        val info = TOKENS[key] ?: return null
        if (key in CASE_SENSITIVE) {
            val letters = t.filter { it.isLetter() }
            if (letters != letters.uppercase(Locale.ROOT)) return null
        }
        return info
    }

    private fun isYear(t: String): Boolean = YEAR.matches(t) && t.toInt() <= maxYear

    private fun isSeparator(t: String): Boolean = t.isNotEmpty() && t.all { it in SEPARATORS }

    fun trimSeparators(s: String): String = SPACES.replace(s.trim { it in SEPARATORS || it.isWhitespace() }, " ")

    fun stripLeading(text: String, prefix: String): String {
        if (prefix.isBlank() || text.length <= prefix.length) return text
        if (!text.searchKey().startsWith(prefix.searchKey())) return text
        val next = text[prefix.length]
        return if (next.isLetterOrDigit()) text else text.substring(prefix.length)
    }

    fun stripEpisodeNumber(text: String): String = EPISODE_NUMBER.replace(trimSeparators(text), "")

    /** Known multi-word details ("Dolby Vision", "WEB-DL", "Multi-Audio") become one token. */
    private fun phrases(input: String): String {
        var s = input
        for ((re, rep) in PHRASES) s = re.replace(s, rep)
        return SPACES.replace(s, " ").trim()
    }

    /**
     * Superscript quality marks ("ᴴᴰ", "⁴ᴷ") become plain tokens; emojis, stars, arrows and
     * "=====" style decorations are dropped.
     */
    private fun clean(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            when {
                isSuperscript(cp) -> {
                    val run = StringBuilder()
                    while (i < raw.length && isSuperscript(raw.codePointAt(i))) {
                        val c = raw.codePointAt(i)
                        run.appendCodePoint(c)
                        i += Character.charCount(c)
                    }
                    sb.append(' ').append(Normalizer.normalize(run, Normalizer.Form.NFKC)).append(' ')
                    continue
                }
                Character.getType(cp) == Character.OTHER_SYMBOL.toInt() -> sb.append(' ')
                cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3 || cp == 0x200B -> {}
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> sb.append(' ')
                else -> sb.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return SPACES.replace(NOISE_RUNS.replace(sb, " "), " ").trim()
    }

    private fun isSuperscript(cp: Int): Boolean =
        cp in 0x1D2C..0x1D6A || cp in 0x1D9B..0x1DBF || cp in 0x2070..0x209F || cp == 0x2C7D ||
            cp == 0xB2 || cp == 0xB3 || cp == 0xB9

    // ------------------------------------------------------------------ categories

    private val REDUNDANT = mapOf(
        Kind.MOVIE to setOf("vod", "films", "film", "movies", "movie"),
        Kind.SERIES to setOf("series", "serie", "shows", "show", "tv shows", "series tv"),
        Kind.LIVE to setOf("chaines", "chaine", "channels", "live"),
    )
    private val LINKING = Regex("(?i)^(?:d'|d’|de |du |des )")

    fun category(raw: String, kind: Int): TaggedName {
        val tags = ArrayList<String>()
        var s = phrases(clean(raw))
        s = takePrefixes(s, State(), allowSpace = true, tags = tags)
        // Suffix tags: "ACTION |FR|", "ACTION [FR]", "ACTION (FR)", "ACTION - FR"
        Regex("\\s*(?:\\|\\s*([A-Z]{2,3})\\s*\\|?|\\[([A-Z]{2,3})]|\\(([A-Z]{2,3})\\)|[-–—]\\s*([A-Z]{2,3}))$").find(s)?.let { m ->
            val code = m.groupValues.drop(1).first { it.isNotEmpty() }
            if (code in COUNTRIES && m.range.first > 1) {
                tags += COUNTRY_ALIASES[code] ?: code
                s = s.substring(0, m.range.first)
            }
        }
        s = trimSeparators(s.replace(Regex("\\s*\\|\\s*"), " · "))
        s = stripRedundant(s, kind)
        val name = prettyCase(s).ifEmpty { prettyCase(trimSeparators(clean(raw))) }.ifEmpty { raw.trim() }
        val tag = tags.firstOrNull { it in COUNTRIES } ?: tags.firstOrNull()
        return TaggedName(tag, name)
    }

    private fun stripRedundant(s: String, kind: Int): String {
        val words = REDUNDANT[kind] ?: return s
        val key = s.searchKey()
        for (w in words.sortedByDescending { it.length }) {
            if (key.startsWith("$w ") || key.startsWith("$w-") || key.startsWith("$w:") || key.startsWith("$w·")) {
                val rest = LINKING.replace(trimSeparators(s.substring(w.length)), "")
                if (rest.count { it.isLetter() } >= 3 && rest.searchKey() != "tv") return rest
            }
            if (key.endsWith(" $w")) {
                val rest = trimSeparators(s.substring(0, s.length - w.length))
                if (rest.count { it.isLetter() } >= 3 && rest.searchKey() != "tv") return rest
            }
        }
        return s
    }

    // ------------------------------------------------------------------ casing

    private val SMALL_WORDS = setOf(
        "de", "du", "des", "la", "le", "les", "et", "à", "a", "au", "aux", "en", "sur", "pour", "par", "un", "une",
        "of", "the", "and", "in", "on", "at", "to", "for", "or", "ou", "vs",
    )
    private val ACRONYMS = setOf(
        "TV", "UK", "US", "USA", "EU", "HBO", "ABC", "AMC", "UFC", "NBA", "MMA", "WWE", "UEFA", "ESPN", "OCS", "RAI",
        "ARD", "ZDF", "ORF", "LCI", "TCM", "AXN", "ITV", "IMAX", "DC", "MCU", "NCIS", "CSI", "FBI", "CIA", "SOS",
        "OSS", "LOL", "VF", "VO", "VOST", "VOSTFR", "UHD", "FHD", "HD", "SD", "HDR", "XXX", "BFM", "RMC", "TMC",
        "AB", "OM", "PSG", "TNT", "RTL", "RTBF", "RTS", "NRJ", "MTV", "TFX", "LCP", "CNN", "BBC", "NFL", "NHL", "MLB",
    )
    private val SPECIAL = mapOf("BEIN" to "beIN", "CNEWS" to "CNews", "CSTAR" to "CStar", "MR" to "Mr", "MRS" to "Mrs", "DR" to "Dr", "ST" to "St")
    private val ROMAN = setOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII")
    private val ELISIONS = setOf("L", "D", "J", "M", "N", "S", "T", "C", "QU")
    /** "EST-CE", "DIS-MOI" -> "Est-ce", "Dis-moi". */
    private val CLITICS = setOf("ce", "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "moi", "toi", "là", "t", "y")
    private const val VOWELS = "AEIOUYÀÂÄÉÈÊËÎÏÔÖÛÙÜŸ"

    /** "LES SIMPSON" -> "Les Simpson", "L'ARME FATALE 2" -> "L'Arme Fatale 2"; mixed-case names are kept. */
    fun prettyCase(input: String): String {
        val s = SPACES.replace(input, " ").trim()
        val letters = s.filter { it.isLetter() }
        if (letters.length < 4 || letters != letters.uppercase(Locale.ROOT)) return s
        val words = s.split(' ')
        return words.mapIndexed { i, w ->
            val first = i == 0 || words[i - 1].lastOrNull()?.let { it in ":-–—·|(" } == true
            caseWord(w, first)
        }.joinToString(" ")
    }

    private fun caseWord(w: String, first: Boolean): String {
        val letters = w.filter { it.isLetter() }
        if (letters.isEmpty()) return w
        if (w.any { it.isDigit() }) {
            // "13EME", "6TER" -> "13eme", "6ter"; "TF1", "M6", "4K" stay as they are.
            return if (w.first().isDigit() && letters.length >= 2) w.lowercase(Locale.FRENCH) else w
        }
        SPECIAL[letters]?.let { return w.replace(letters, it) }
        if (letters in ACRONYMS || letters in ROMAN || '.' in w.trimEnd('.')) return w
        if (letters.length <= 5 && letters.none { it in VOWELS }) return w
        val apo = w.indexOfFirst { it == '\'' || it == '’' }
        if (apo in 1..2 && apo < w.length - 1 && w.substring(0, apo).uppercase(Locale.ROOT) in ELISIONS) {
            val head = w.substring(0, apo + 1).lowercase(Locale.FRENCH)
            return (if (first) head.replaceFirstChar { it.titlecase(Locale.FRENCH) } else head) + caseWord(w.substring(apo + 1), true)
        }
        val lower = w.lowercase(Locale.FRENCH)
        if (!first && lower in SMALL_WORDS) return lower
        return lower.split('-').mapIndexed { i, part ->
            val up = part.uppercase(Locale.ROOT)
            when {
                up in ROMAN || up in ACRONYMS -> up
                i > 0 && part in CLITICS -> part
                else -> part.replaceFirstChar { it.titlecase(Locale.FRENCH) }
            }
        }.joinToString("-")
    }
}
