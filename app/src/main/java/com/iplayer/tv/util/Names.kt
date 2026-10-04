package com.iplayer.tv.util

import java.util.Locale

/** A display name split into an optional provider tag ("FR", "VOD", "4K"…) and the clean name. */
data class TaggedName(val tag: String?, val name: String)

// "FR| Action", "|FR| Action", "FR - Action", "FR: Action", "[FR] Action", "VOD » Action"
private val TAG_PREFIX = Regex("^\\s*(?:\\|\\s*([A-Z0-9]{2,4})\\s*\\||\\[([A-Z0-9]{2,4})]|([A-Z0-9]{2,4})\\s*[|:»•\\-–—])\\s*")
private val TRAILING_YEAR = Regex("\\s*[(\\[]\\s*(19|20)\\d{2}\\s*[)\\]]\\s*$|\\s+[-–]\\s+(19|20)\\d{2}\\s*$")
private val SPACES = Regex("\\s{2,}")

private val tagCache = object : LinkedHashMap<String, TaggedName>(512, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TaggedName>?) = size > 4000
}

fun String.tagged(): TaggedName = synchronized(tagCache) {
    tagCache.getOrPut(this) {
        val m = TAG_PREFIX.find(this)
        if (m == null) TaggedName(null, prettyCase(trim()))
        else {
            val tag = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            val rest = substring(m.range.last + 1).trim()
            if (rest.length < 2 || tag == null || tag.all { it.isDigit() }) TaggedName(null, prettyCase(trim()))
            else TaggedName(tag, prettyCase(rest))
        }
    }
}

/** Title without provider tag nor trailing "(2019)". */
fun String.cleanTitle(): String = TRAILING_YEAR.replace(tagged().name, "").trim().ifEmpty { tagged().name }

/** "ACTION & AVENTURE" -> "Action & Aventure" (short words such as "TV", "USA", "4K" are kept). */
private fun prettyCase(s: String): String {
    val letters = s.filter { it.isLetter() }
    if (letters.length < 4 || letters != letters.uppercase()) return SPACES.replace(s, " ")
    return SPACES.replace(s, " ").split(' ').joinToString(" ") { w ->
        if (w.length <= 3) w else w.lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }
}
