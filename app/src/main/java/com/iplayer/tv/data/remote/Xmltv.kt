package com.iplayer.tv.data.remote

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

class XmltvProgramme(
    val channel: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String?,
    val categories: List<String> = emptyList(),
)

/** Streaming XMLTV parser. Programmes for channels not accepted by [accept] are skipped cheaply. */
object XmltvParser {

    fun maybeGunzip(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
    }

    fun parse(
        input: InputStream,
        onChannel: (id: String, names: List<String>) -> Unit,
        onChannelsDone: () -> Unit,
        accept: (channelId: String) -> Boolean,
        offsetMs: Long,
        minStop: Long,
        maxStart: Long,
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        var channelsDone = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> {
                        val id = p.getAttributeValue(null, "id")
                        val names = ArrayList<String>(2)
                        val depth = p.depth
                        while (true) {
                            val e = p.next()
                            if (e == XmlPullParser.END_DOCUMENT) break
                            if (e == XmlPullParser.END_TAG && p.depth == depth) break
                            if (e == XmlPullParser.START_TAG && p.name == "display-name") names += readText(p)
                        }
                        if (id != null) onChannel(id, names)
                    }
                    "programme" -> {
                        if (!channelsDone) { channelsDone = true; onChannelsDone() }
                        val ch = p.getAttributeValue(null, "channel")
                        if (ch == null || !accept(ch)) {
                            skip(p)
                        } else {
                            val start = parseTime(p.getAttributeValue(null, "start")) + offsetMs
                            val stop = parseTime(p.getAttributeValue(null, "stop")) + offsetMs
                            if (stop < minStop || start > maxStart || start <= offsetMs) {
                                skip(p)
                            } else {
                                var title: String? = null
                                var desc: String? = null
                                val categories = ArrayList<String>()
                                val depth = p.depth
                                while (true) {
                                    val e = p.next()
                                    if (e == XmlPullParser.END_DOCUMENT) break
                                    if (e == XmlPullParser.END_TAG && p.depth == depth) break
                                    if (e == XmlPullParser.START_TAG) {
                                        when (p.name) {
                                            "title" -> if (title == null) title = readText(p) else skip(p)
                                            "desc" -> if (desc == null) desc = readText(p) else skip(p)
                                            "category" -> categories += readText(p)
                                            else -> skip(p)
                                        }
                                    }
                                }
                                onProgramme(XmltvProgramme(ch, start, if (stop > start) stop else start + 1_800_000, title ?: "", desc, categories))
                            }
                        }
                    }
                }
            }
            event = p.next()
        }
        if (!channelsDone) onChannelsDone()
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.TEXT) sb.append(p.text)
            else if (e == XmlPullParser.END_TAG && p.depth == depth) break
            else if (e == XmlPullParser.END_DOCUMENT) break
        }
        return sb.toString().trim()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (p.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Parses "20240101120000 +0100" into epoch millis (UTC). */
    fun parseTime(s: String?): Long {
        if (s == null || s.length < 12) return 0
        fun num(from: Int, to: Int): Int {
            var v = 0
            for (i in from until minOf(to, s.length)) {
                val c = s[i]
                if (c < '0' || c > '9') return v
                v = v * 10 + (c - '0')
            }
            return v
        }
        val y = num(0, 4); val mo = num(4, 6); val d = num(6, 8)
        val h = num(8, 10); val mi = num(10, 12); val sec = if (s.length >= 14) num(12, 14) else 0
        var offsetSec = 0
        val signIdx = s.indexOfAny(charArrayOf('+', '-'), 12)
        if (signIdx > 0 && s.length >= signIdx + 5) {
            val oh = num(signIdx + 1, signIdx + 3)
            val om = num(signIdx + 3, signIdx + 5)
            offsetSec = (oh * 3600 + om * 60) * if (s[signIdx] == '-') -1 else 1
        }
        val days = daysFromCivil(y, mo, d)
        return ((days * 86400L) + h * 3600L + mi * 60L + sec - offsetSec) * 1000L
    }

    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
