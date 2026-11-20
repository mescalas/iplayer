package com.iplayer.tv.data.remote

import java.io.BufferedReader
import java.io.IOException

class M3uEntry(
    val name: String,
    val url: String,
    val attrs: Map<String, String>,
    val group: String,
)

/** Streaming M3U/M3U8 (extended) parser: handles tvg-*, group-title, #EXTGRP and catchup attributes. */
object M3uParser {
    private val ATTR = Regex("([A-Za-z0-9_-]+)=(\"([^\"]*)\"|([^\\s\"]+))")

    /**
     * Reads [reader] to its end and reports every entry it contains.
     *
     * An #EXTINF line describes an address that arrives on the next line, so an end of stream
     * between the two means the body stopped partway. The function then throws [IOException]:
     * a short download must not pass for a shorter playlist.
     */
    fun parse(reader: BufferedReader, onHeader: (Map<String, String>) -> Unit, onEntry: (M3uEntry) -> Unit) {
        var attrs: Map<String, String> = emptyMap()
        var name: String? = null
        var group: String? = null
        var awaitingUrl = false
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                when {
                    line.startsWith("#EXTM3U", ignoreCase = true) -> onHeader(parseAttrs(line))
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val body = line.substring(line.indexOf(':') + 1)
                        var inQuote = false
                        var comma = -1
                        for (i in body.indices) {
                            val c = body[i]
                            if (c == '"') inQuote = !inQuote
                            else if (c == ',' && !inQuote) { comma = i; break }
                        }
                        val attrPart = if (comma >= 0) body.substring(0, comma) else body
                        attrs = parseAttrs(attrPart)
                        name = if (comma >= 0) body.substring(comma + 1).trim() else null
                        awaitingUrl = true
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substring(8).trim()
                }
                continue
            }
            val n = name?.takeIf { it.isNotEmpty() } ?: attrs["tvg-name"] ?: line.substringAfterLast('/')
            val g = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: group ?: ""
            onEntry(M3uEntry(n, line, attrs, g.trim()))
            awaitingUrl = false
            attrs = emptyMap()
            name = null
            group = null
        }
        if (awaitingUrl) throw IOException("Playlist M3U incomplète : le téléchargement s'est arrêté avant la fin.")
    }

    private fun parseAttrs(s: String): Map<String, String> {
        if (s.indexOf('=') < 0) return emptyMap()
        val map = HashMap<String, String>(8)
        for (m in ATTR.findAll(s)) {
            val v = m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            map[m.groupValues[1].lowercase()] = v
        }
        return map
    }
}
