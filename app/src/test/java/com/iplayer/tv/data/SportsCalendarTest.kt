package com.iplayer.tv.data

import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.ProgramEntity
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SportsCalendarTest {
    @Test fun `recognizes requested sports from categories or competitions`() {
        assertEquals(Sport.FOOTBALL, SportsCalendar.sport("Paris - Marseille", "Sports\nFootball"))
        assertEquals(Sport.BASKETBALL, SportsCalendar.sport("NBA : Boston - Miami", "Sport"))
        assertEquals(Sport.TENNIS, SportsCalendar.sport("Roland-Garros : finale", ""))
        assertEquals(Sport.F1, SportsCalendar.sport("Formule 1 : Grand Prix", "Sport automobile"))
        assertEquals(Sport.MMA, SportsCalendar.sport("UFC 310", ""))
        assertEquals(Sport.RUGBY, SportsCalendar.sport("Top 14 : finale", ""))
        assertEquals(Sport.CYCLING, SportsCalendar.sport("Tour de France", ""))
        assertEquals(Sport.HANDBALL, SportsCalendar.sport("Starligue", ""))
    }

    @Test fun `does not guess a sport for ambiguous events or magazines`() {
        assertNull(SportsCalendar.sport("France - Espagne", "Sport"))
        assertNull(SportsCalendar.sport("Football : le magazine", "Football"))
        assertNull(SportsCalendar.sport("Résumé NBA", "Basketball"))
        assertNull(SportsCalendar.sport("Téléfoot", "Football"))
        assertNull(SportsCalendar.sport("NFL : football américain", "Football"))
        assertNull(SportsCalendar.sport("F150 : découverte", ""))
        assertNull(SportsCalendar.sport("Finale de tennis de table", "Tennis de table"))
        assertNull(SportsCalendar.sport("Finale", "Hockey sur gazon"))
        assertNull(SportsCalendar.sport("", "Football"))
        assertEquals(Sport.RUGBY, SportsCalendar.sport("Ligue des champions", "Rugby"))
    }

    @Test fun `groups broadcasts only for same event and time and prioritizes French channels`() {
        val channels = listOf(channel("uk", "UK | Sports HD"), channel("fr", "FR | Sport FHD"), channel("fr4k", "FR | Sport 4K", epg = "fr"))
        val programmes = listOf(programme("uk"), programme("fr"), programme("fr").copy(id = 9), programme("fr").copy(startAt = 3000))
        val events = SportsCalendar.events(programmes, channels, setOf(Sport.FOOTBALL))
        assertEquals(2, events.size)
        assertEquals(listOf("fr", "fr4k", "uk"), events.first().channels.map { it.itemKey })
        assertTrue(SportsCalendar.events(programmes, channels, setOf(Sport.TENNIS)).isEmpty())
        assertTrue(SportsCalendar.events(programmes, channels, emptySet()).isEmpty())
    }

    @Test fun `never links a programme to another playlist or an unknown epg id`() {
        val events = SportsCalendar.events(listOf(programme("fr")), listOf(channel("fr", "FR | Sport").copy(playlistId = 2)), Sport.entries.toSet())
        assertTrue(events.isEmpty())
        assertTrue(SportsCalendar.events(listOf(programme("missing")), listOf(channel("fr", "Sport")), Sport.entries.toSet()).isEmpty())
        assertEquals(1, SportsCalendar.events(listOf(programme("FR")), listOf(channel("fr", "Sport")), Sport.entries.toSet()).size)
    }

    @Test fun `Paris dates respect midnight and daylight saving changes`() {
        val spring = SportsCalendar.dayBounds(utc("2026-03-29 12:00"), 0)
        assertEquals(23 * 3600_000L, spring.second - spring.first)
        val autumn = SportsCalendar.dayBounds(utc("2026-10-25 12:00"), 0)
        assertEquals(25 * 3600_000L, autumn.second - autumn.first)
        val midnight = SportsCalendar.dayBounds(utc("2026-10-09 22:30"), 0)
        assertEquals("10 octobre 00:00", SportsCalendar.format(midnight.first, "d MMMM HH:mm"))
        assertEquals(spring.second, SportsCalendar.dayBounds(utc("2026-03-29 12:00"), 1).first)
    }

    @Test fun `replays remain explicitly identifiable`() {
        assertTrue(SportsCalendar.isReplay("UFC — Rediffusion"))
        assertFalse(SportsCalendar.isReplay("UFC : finale"))
    }

    private fun programme(epg: String) = ProgramEntity(1, 1, epg, 1000, 2000, "Paris - Marseille", null, "Football")
    private fun channel(key: String, name: String, epg: String = key) = ChannelEntity(
        playlistId = 1, categoryId = "sports", itemKey = key, name = name, search = name,
        logo = null, url = "https://example.com/$key", epgId = epg, number = 1, position = 1,
    )
    private fun utc(value: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time
}
