package com.iplayer.tv.data

import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.ProgramEntity
import com.iplayer.tv.util.mediaName
import com.iplayer.tv.util.searchKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SportsEvent(
    val sport: Sport,
    val programme: ProgramEntity,
    val channels: List<ChannelEntity>,
) {
    val key: String get() = "${sport.name}:${programme.startAt}:${programme.title.searchKey()}"
}

/** Conservative detection: never infer a sport from the broadcaster or a generic "Sport" category. */
object SportsCalendar {
    private fun words(pattern: String) = Regex("\\b(?:$pattern)\\b")
    private val excluded = words("magazine|documentaire|documentary|journal|news|highlights|resume|resumes|debrief|talk[ -]?show|best of|portrait|reportage")
    private val shows = words("telefoot|canal football club|canal rugby club|l equipe du soir|l after foot")
    private val replay = words("rediffusion|replay|reprise")
    private val americanFootball = words("american football|football americain|nfl|super bowl")
    private val unsupported = words("tennis de table|table tennis|hockey sur gazon|field hockey")
    private val rules = listOf(
        Sport.F1 to words("f1|formula ?1|formule ?1"),
        Sport.MOTORCYCLING to words("motogp|moto ?[23]|superbike"),
        Sport.MMA to words("mma|ufc|bellator|pfl|arts martiaux mixtes|mixed martial arts"),
        Sport.BASKETBALL to words("basket(?:ball|[ -]ball)?|nba|wnba|euroleague|euroligue"),
        Sport.TENNIS to words("tennis|atp|wta|roland[ -]garros|wimbledon"),
        Sport.RUGBY to words("rugby|top 14|pro d2|six nations|6 nations"),
        Sport.CYCLING to words("cyclisme|cycling|tour de france|giro|vuelta|paris[ -]roubaix"),
        Sport.HANDBALL to words("handball|hand[ -]ball|starligue"),
        Sport.BOXING to words("boxe|boxing"),
        Sport.VOLLEYBALL to words("volley(?:ball|[ -]ball)?"),
        Sport.ICE_HOCKEY to words("hockey sur glace|ice hockey|nhl"),
        Sport.FOOTBALL to words("football|soccer|ligue [12]|premier league|bundesliga|liga|serie a|uefa|fifa"),
    )

    fun sport(title: String, categories: String): Sport? {
        val text = "$title\n$categories".searchKey()
        if (title.isBlank() || excluded.containsMatchIn(text) || shows.containsMatchIn(text) || unsupported.containsMatchIn(text)) return null
        // The explicit category wins over incidental words in the title.
        val category = categories.searchKey()
        val match = rules.firstOrNull { it.second.containsMatchIn(category) }
            ?: rules.firstOrNull { it.second.containsMatchIn(title.searchKey()) }
        return match?.first?.takeUnless { it == Sport.FOOTBALL && americanFootball.containsMatchIn(text) }
    }

    fun isReplay(title: String): Boolean = replay.containsMatchIn(title.searchKey())

    fun events(programmes: List<ProgramEntity>, channels: List<ChannelEntity>, selected: Set<Sport>): List<SportsEvent> {
        val byEpg = channels.filter { !it.epgId.isNullOrBlank() }
            .groupBy { it.playlistId to it.epgId!!.lowercase(Locale.ROOT) }
        val events = programmes.mapNotNull { p ->
            val sport = sport(p.title, p.categories)?.takeIf { it in selected } ?: return@mapNotNull null
            val broadcasters = byEpg[p.playlistId to p.channelKey.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            SportsEvent(sport, p, broadcasters)
        }
        // Only merge identical titles at identical times. Similar team names are not enough.
        return events.groupBy { it.key }.values.map { same ->
            same.first().copy(channels = same.flatMap { it.channels }.distinctBy { it.itemKey }
                .sortedWith(compareBy<ChannelEntity> { if (it.name.mediaName().tag == "FR") 0 else 1 }
                    .thenBy { it.position }))
        }.sortedWith(compareBy<SportsEvent> { it.programme.startAt }.thenBy { it.programme.title })
    }

    /** Calendar arithmetic keeps the 23/25-hour days at French daylight-saving boundaries correct. */
    fun dayBounds(now: Long, offset: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, offset)
        val from = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return from to calendar.timeInMillis
    }

    fun format(time: Long, pattern: String): String = SimpleDateFormat(pattern, Locale.FRANCE).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
    }.format(Date(time))
}
