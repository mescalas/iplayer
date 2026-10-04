package com.iplayer.tv.util

import com.iplayer.tv.data.db.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NamesTest {

    private fun title(raw: String) = raw.mediaNameUncached().title

    @Test
    fun movieTitles() {
        assertEquals("Dune", title("|FR| DUNE (2021) [MULTI] 4K HDR"))
        assertEquals("Dune", title("FR - Dune (2021)"))
        assertEquals("Dune", title("4K-FR | Dune"))
        assertEquals("Dune", title("[FR] Dune 4K"))
        assertEquals("Dune: Part Two", title("Dune: Part Two (2024) (4K)"))
        assertEquals("Avatar", title("Avatar 2009 4K HDR"))
        assertEquals("Avatar", title("★ Avatar ★"))
        assertEquals("Dune Part Two", title("Dune.Part.Two.2024.MULTi.2160p.WEB-DL.DV.HDR.H265-GRP"))
        assertEquals("Le Seigneur des Anneaux", title("LE SEIGNEUR DES ANNEAUX"))
        assertEquals("L'Arme Fatale 2", title("L'ARME FATALE 2"))
        assertEquals("Spider-Man", title("SPIDER-MAN (2002)"))
        assertEquals("The Office (US)", title("The Office (US)"))
        assertEquals("Dune", title("Dune (VOSTFR)"))
        assertEquals("Dune", title("Dune ᵁᴴᴰ"))
        assertEquals("Dune", title("Dune | 4K"))
        assertEquals("Dune", title("NF - Dune"))
    }

    @Test
    fun titlesThatMustNotChange() {
        assertEquals("Blade Runner 2049", title("Blade Runner 2049"))
        assertEquals("Wonder Woman 1984", title("Wonder Woman 1984"))
        assertEquals("1917", title("1917"))
        assertEquals("2012", title("2012"))
        assertEquals("The French Dispatch", title("The French Dispatch"))
        assertEquals("CSI: Miami", title("CSI: Miami"))
        assertEquals("X-Men", title("X-MEN"))
        assertEquals("S.W.A.T.", title("S.W.A.T."))
        assertEquals("[REC] 2", title("[REC] 2"))
        assertEquals("Mission: Impossible", title("Mission: Impossible"))
        assertEquals("Spider-Man: No Way Home", title("Spider-Man: No Way Home"))
        assertEquals("Toy Story 2", title("Toy Story 2"))
        assertEquals("Ça", title("Ça"))
        assertEquals("Qu'Est-ce qu'On a Fait au Bon Dieu ?", title("QU'EST-CE QU'ON A FAIT AU BON DIEU ?"))
        assertEquals("Un P'tit Truc en Plus", title("UN P'TIT TRUC EN PLUS"))
    }

    @Test
    fun extractedDetails() {
        val n = "|FR| DUNE (2021) [MULTI] 4K HDR".mediaNameUncached()
        assertEquals("FR", n.tag)
        assertEquals("2021", n.year)
        assertEquals("4K", n.quality)
        assertEquals("HDR", n.hdr)
        assertEquals("MULTI", n.language)
        assertEquals(listOf("4K", "HDR", "MULTI"), n.badges)

        val scene = "Dune.Part.Two.2024.MULTi.2160p.WEB-DL.DV.HDR.H265-GRP".mediaNameUncached()
        assertEquals("2024", scene.year)
        assertEquals("Dolby Vision", scene.hdr)
        assertEquals("4K", scene.quality)

        // French audio and HD are the norm: no pill for them.
        assertEquals(emptyList<String>(), "Dune (VF) 1080p".mediaNameUncached().badges)
        assertEquals(listOf("VOSTFR"), "Dune [VOSTFR]".mediaNameUncached().badges)
        assertEquals(listOf("4K", "Dolby Vision"), "Dune 4K Dolby Vision".mediaNameUncached().badges)
    }

    @Test
    fun series() {
        val n = "FR - Lupin S02".mediaNameUncached()
        assertEquals("Lupin", n.title)
        assertEquals(2, n.season)
        assertEquals(listOf("Saison 2"), n.badges)
        assertEquals("The Witcher", title("The Witcher S01-S03"))
        assertEquals("Breaking Bad", title("Breaking Bad (2008)"))
        assertEquals("La Casa de Papel", title("La Casa de Papel [MULTI]"))
        assertEquals("Lupin", title("Lupin - Saison 2"))
    }

    @Test
    fun episodes() {
        val n = "Breaking Bad - S01E01 - Pilot".mediaNameUncached()
        assertEquals("Breaking Bad", n.title)
        assertEquals(1, n.season)
        assertEquals(1, n.episode)
        assertEquals("Pilot", n.episodeTitle)

        assertEquals("Pilot", episodeTitle("Breaking Bad - S01E01 - Pilot", "Breaking Bad (2008)"))
        assertEquals("Pilot", episodeTitle("FR - Breaking Bad S01 E01 Pilot 4K", "Breaking Bad"))
        assertNull(episodeTitle("Breaking Bad - S01E01", "Breaking Bad"))
        assertNull(episodeTitle("FR - BREAKING BAD - S01E01", "Breaking Bad"))
        assertNull(episodeTitle("Episode 3", "Lupin"))
        assertNull(episodeTitle("Épisode 03", "Lupin"))
        assertNull(episodeTitle("Lupin", "Lupin"))
        assertEquals("Chapitre 3", episodeTitle("Lupin 2x03 - Chapitre 3", "Lupin"))
        assertEquals("Le Retour", episodeTitle("Episode 3 - Le Retour", "Lupin"))
        assertEquals("Le retour", episodeTitle("3. Le retour", "Lupin"))
        assertEquals("Le retour", episodeTitle("Lupin: Le retour", "Lupin"))
        assertEquals("Pilot", episodeTitle("S01E01 - Pilot", "Breaking Bad"))
        assertEquals("24 heures", episodeTitle("24 heures", "Lupin"))
        assertNull(episodeTitle("One Piece - 1x1071", "One Piece"))
        assertNull(episodeTitle("Dragon Ball Z - Ep 12", "Dragon Ball Z"))
        assertEquals(1071, "One Piece - 1x1071".mediaNameUncached().episode)
    }

    @Test
    fun channels() {
        val tf1 = "FR: TF1 FHD ᴴᴰ".mediaNameUncached()
        assertEquals("TF1", tf1.title)
        assertEquals("FHD", tf1.channelBadge)
        assertEquals("TF1", title("|FR| TF1 HD"))
        assertEquals("HD", "|FR| TF1 HD".mediaNameUncached().channelBadge)
        assertEquals("4K", "FR | M6 4K".mediaNameUncached().channelBadge)
        assertEquals("Canal+ Sport 360", title("FR| CANAL+ SPORT 360 FHD"))
        assertEquals("beIN Sports 1", title("FR: BEIN SPORTS 1 HD"))
        assertEquals("RMC Story", title("RMC STORY"))
        assertEquals("13eme Rue", title("13EME RUE HD"))
        assertEquals("BFM TV", title("BFM TV"))
        assertEquals("TF1 +1", title("TF1 +1"))
        assertEquals("France 3 Côte d'Azur", title("FRANCE 3 CÔTE D'AZUR"))
        assertEquals("BBC One", title("UK: BBC ONE HD"))
        assertNull("France 2".mediaNameUncached().channelBadge)
    }

    private fun cat(raw: String, kind: Int = Kind.MOVIE) = raw.categoryLabel(kind)

    @Test
    fun categories() {
        assertEquals(TaggedName("FR", "Action"), cat("FR| FILMS ACTION"))
        assertEquals(TaggedName("FR", "Action"), cat("|FR| ACTION"))
        assertEquals(TaggedName("FR", "Action"), cat("VOD - FR - ACTION"))
        assertEquals(TaggedName("FR", "Action"), cat("FR ACTION"))
        assertEquals(TaggedName("FR", "Action"), cat("ACTION [FR]"))
        assertEquals(TaggedName("FR", "Horreur"), cat("FR | FILMS D'HORREUR"))
        assertEquals(TaggedName("FR", "Nouveautés"), cat("★ FR | NOUVEAUTÉS ★"))
        assertEquals(TaggedName(null, "Action & Aventure"), cat("ACTION & AVENTURE"))
        assertEquals(TaggedName("FR", "Films 2024"), cat("FR| FILMS 2024"))
        assertEquals(TaggedName("FR", "Films 4K"), cat("FR| FILMS 4K"))
        assertEquals(TaggedName("4K", "Films"), cat("4K | FILMS"))
        assertEquals(TaggedName("EN", "Netflix"), cat("|EN| NETFLIX MOVIES"))
        assertEquals(TaggedName("FR", "Netflix"), cat("FR - SÉRIES NETFLIX", Kind.SERIES))
        assertEquals(TaggedName("FR", "Séries TV"), cat("FR | SÉRIES TV", Kind.SERIES))
        assertEquals(TaggedName("FR", "TNT"), cat("FRANCE | TNT", Kind.LIVE))
        assertEquals(TaggedName("FR", "Sport"), cat("==== FR SPORT ====", Kind.LIVE))
        assertEquals(TaggedName(null, "Documentaires"), cat("Documentaires"))
        assertEquals(TaggedName("FR", "Netflix · Action"), cat("FR| NETFLIX | ACTION"))
    }
}
