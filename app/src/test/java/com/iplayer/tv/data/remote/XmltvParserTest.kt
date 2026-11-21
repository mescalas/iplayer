package com.iplayer.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * The pull parser that the app uses reports a body that stops between elements as a normal end of
 * document, so these cases feed it a cut guide to check that a partial one is refused.
 */
class XmltvParserTest {

    private fun parse(xml: String, accept: (String) -> Boolean = { true }): List<XmltvProgramme> {
        val programmes = ArrayList<XmltvProgramme>()
        XmltvParser.parse(
            KXmlParser(),
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            onChannel = { _, _ -> },
            onChannelsDone = {},
            accept = accept,
            offsetMs = 0,
            minStop = 0,
            maxStart = Long.MAX_VALUE,
            onProgramme = { programmes += it },
        )
        return programmes
    }

    private fun expectIncomplete(xml: String) {
        try {
            parse(xml)
            fail("a guide that stops before </tv> must not pass as complete")
        } catch (e: IOException) {
            assertEquals("Réponse incomplète du serveur de guide.", e.message)
        }
    }

    @Test
    fun `reads both programmes of a complete guide`() {
        val programmes = parse(HEAD + PROGRAMME_1 + PROGRAMME_2 + "</tv>\n")
        assertEquals(2, programmes.size)
        assertEquals("Premier", programmes[0].title)
        assertEquals("Second", programmes[1].title)
        assertEquals("c1", programmes[0].channel)
    }

    @Test
    fun `reads a complete guide that carries no programme for an accepted channel`() {
        assertEquals(0, parse(HEAD + PROGRAMME_1 + "</tv>", accept = { false }).size)
    }

    @Test
    fun `refuses a guide cut between two elements`() {
        expectIncomplete(HEAD + PROGRAMME_1)
    }

    @Test
    fun `refuses a guide cut inside a programme`() {
        expectIncomplete(HEAD + PROGRAMME_1 + " <programme start=\"20260101130000 +0100\" stop=\"20260101140000 +0100\" channel=\"c1\"><title>Second</title>")
    }

    @Test
    fun `refuses a guide cut inside a title`() {
        expectIncomplete(HEAD + PROGRAMME_1 + " <programme start=\"20260101130000 +0100\" stop=\"20260101140000 +0100\" channel=\"c1\"><title>Sec")
    }

    @Test
    fun `refuses an empty body`() {
        expectIncomplete("")
    }

    @Test
    fun `refuses a body that holds only the channels`() {
        expectIncomplete(HEAD)
    }

    @Test
    fun `refuses a guide cut after a complete programme`() {
        val programmes = ArrayList<XmltvProgramme>()
        try {
            XmltvParser.parse(
                KXmlParser(),
                ByteArrayInputStream((HEAD + PROGRAMME_1).toByteArray(Charsets.UTF_8)),
                onChannel = { _, _ -> },
                onChannelsDone = {},
                accept = { true },
                offsetMs = 0,
                minStop = 0,
                maxStart = Long.MAX_VALUE,
                onProgramme = { programmes += it },
            )
            fail("a guide that stops before </tv> must not pass as complete")
        } catch (e: IOException) {
            assertEquals(1, programmes.size)
        }
    }

    private companion object {
        const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n <channel id=\"c1\"><display-name>Chaîne 1</display-name></channel>\n"
        const val PROGRAMME_1 = " <programme start=\"20260101120000 +0100\" stop=\"20260101130000 +0100\" channel=\"c1\"><title>Premier</title></programme>\n"
        const val PROGRAMME_2 = " <programme start=\"20260101130000 +0100\" stop=\"20260101140000 +0100\" channel=\"c1\"><title>Second</title></programme>\n"
    }
}
