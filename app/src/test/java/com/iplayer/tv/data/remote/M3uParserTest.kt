package com.iplayer.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader

class M3uParserTest {
    private fun parse(body: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        M3uParser.parse(BufferedReader(StringReader(body)), onHeader = {}, onEntry = { entries += it })
        return entries
    }

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="tf1" group-title="TNT",TF1
        http://panel.example/live/user/pass/1.ts
        #EXTINF:-1 tvg-id="f2" group-title="TNT",France 2
        http://panel.example/live/user/pass/2.ts
    """.trimIndent() + "\n"

    @Test
    fun completePlaylistReportsEveryEntry() {
        val entries = parse(playlist)
        assertEquals(listOf("TF1", "France 2"), entries.map { it.name })
        assertEquals("TNT", entries[0].group)
    }

    @Test
    fun streamThatStopsBeforeTheAddressOfAnEntryIsIncomplete() {
        val truncated = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1" group-title="TNT",TF1
            http://panel.example/live/user/pass/1.ts
            #EXTINF:-1 tvg-id="f2" group-title="TNT",France 2
        """.trimIndent() + "\n"
        val error = assertThrows(IOException::class.java) { parse(truncated) }
        assertTrue(error.message.orEmpty().contains("incomplète"))
    }

    @Test
    fun streamThatStopsInsideTheExtinfLineIsIncomplete() {
        val truncated = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1" group-title="TNT",TF1
            http://panel.example/live/user/pass/1.ts
            #EXTINF:-1 tvg-id="f2" group-title="TN
        """.trimIndent() + "\n"
        assertThrows(IOException::class.java) { parse(truncated) }
    }

    @Test
    fun headerOnlyPlaylistIsLeftToTheCaller() {
        // An empty answer stays the caller's guard: the parser only reports what it read.
        assertEquals(0, parse("#EXTM3U\n").size)
    }
}
