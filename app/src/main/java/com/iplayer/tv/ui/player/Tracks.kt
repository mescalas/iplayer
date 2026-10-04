package com.iplayer.tv.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import java.util.Locale

data class TrackOption(
    val label: String,
    val type: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val selected: Boolean,
)

object Tracks {
    fun list(player: Player, type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != type) return@forEachIndexed
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i, true)) continue
                out += TrackOption(label(g.getTrackFormat(i), type, out.size + 1), type, gi, i, g.isTrackSelected(i))
            }
        }
        return out
    }

    fun select(player: Player, option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun auto(player: Player, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .build()
    }

    fun disable(player: Player, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, true)
            .build()
    }

    fun isDisabled(player: Player, type: Int) = type in player.trackSelectionParameters.disabledTrackTypes

    fun hasOverride(player: Player, type: Int) =
        player.trackSelectionParameters.overrides.values.any { it.type == type }

    private fun label(f: Format, type: Int, n: Int): String {
        val parts = ArrayList<String>()
        val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
            Locale(code).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() } ?: code
        }
        when (type) {
            C.TRACK_TYPE_VIDEO -> {
                if (f.height > 0) parts += "${f.height}p"
                if (f.frameRate > 0) parts += "${f.frameRate.toInt()} ips"
                if (f.bitrate > 0) parts += String.format(Locale.ROOT, "%.1f Mb/s", f.bitrate / 1_000_000f)
            }
            C.TRACK_TYPE_AUDIO -> {
                parts += lang ?: f.label ?: "Piste $n"
                codec(f.sampleMimeType)?.let { parts += it }
                when (f.channelCount) {
                    1 -> parts += "Mono"
                    2 -> parts += "Stéréo"
                    6 -> parts += "5.1"
                    8 -> parts += "7.1"
                    else -> if (f.channelCount > 0) parts += "${f.channelCount} canaux"
                }
            }
            else -> {
                parts += lang ?: f.label ?: "Sous-titres $n"
                if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) parts += "forcés"
            }
        }
        if (type != C.TRACK_TYPE_VIDEO && f.label != null && lang != null && f.label != lang) parts += f.label!!
        return parts.joinToString(" · ").ifBlank { "Piste $n" }
    }

    private fun codec(mime: String?): String? = when (mime) {
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_AC4 -> "AC-4"
        else -> null
    }
}
