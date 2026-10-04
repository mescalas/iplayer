package com.iplayer.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BufferMode(val label: String) {
    FAST("Rapide"), BALANCED("Équilibré"), STABLE("Stable"), MAX("Très stable")
}

enum class AudioDecoder(val label: String) {
    HARDWARE_FFMPEG("Matériel + FFmpeg"), FFMPEG_FIRST("FFmpeg prioritaire"), HARDWARE_ONLY("Matériel uniquement")
}

enum class LiveFormat(val label: String, val ext: String) { TS("MPEG-TS", "ts"), HLS("HLS (m3u8)", "m3u8") }

enum class AspectMode(val label: String) { FIT("Adapté"), FILL("Étiré"), ZOOM("Zoom") }

data class AppSettings(
    val activePlaylistId: Long = 0,
    val bufferMode: BufferMode = BufferMode.BALANCED,
    val audioDecoder: AudioDecoder = AudioDecoder.HARDWARE_FFMPEG,
    val liveFormat: LiveFormat = LiveFormat.TS,
    val aspectMode: AspectMode = AspectMode.FIT,
    val invertZapping: Boolean = false,
    val autoplayLastChannel: Boolean = false,
    val livePreview: Boolean = true,
    val showChannelNumbers: Boolean = true,
    val tunneling: Boolean = false,
    val epgOffsetHours: Int = 0,
    val userAgent: String = "",
    val preferredAudioLang: String = "fr",
    val preferredSubtitleLang: String = "",
    val lastChannelKey: String = "",
    val autoNextEpisode: Boolean = true,
    /** Software (FFmpeg) audio + PCM output: enabled automatically after an audio decoder failure. */
    val compatAudio: Boolean = false,
    val autoUpdateCheck: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val value: AppSettings get() = state.value

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            activePlaylistId = prefs.getLong("activePlaylistId", d.activePlaylistId),
            bufferMode = enumOr(prefs.getString("bufferMode", null), d.bufferMode),
            audioDecoder = enumOr(prefs.getString("audioDecoder", null), d.audioDecoder),
            liveFormat = enumOr(prefs.getString("liveFormat", null), d.liveFormat),
            aspectMode = enumOr(prefs.getString("aspectMode", null), d.aspectMode),
            invertZapping = prefs.getBoolean("invertZapping", d.invertZapping),
            autoplayLastChannel = prefs.getBoolean("autoplayLastChannel", d.autoplayLastChannel),
            livePreview = prefs.getBoolean("livePreview", d.livePreview),
            showChannelNumbers = prefs.getBoolean("showChannelNumbers", d.showChannelNumbers),
            tunneling = prefs.getBoolean("tunneling", d.tunneling),
            epgOffsetHours = prefs.getInt("epgOffsetHours", d.epgOffsetHours),
            userAgent = prefs.getString("userAgent", d.userAgent) ?: "",
            preferredAudioLang = prefs.getString("preferredAudioLang", d.preferredAudioLang) ?: "",
            preferredSubtitleLang = prefs.getString("preferredSubtitleLang", d.preferredSubtitleLang) ?: "",
            lastChannelKey = prefs.getString("lastChannelKey", d.lastChannelKey) ?: "",
            autoNextEpisode = prefs.getBoolean("autoNextEpisode", d.autoNextEpisode),
            compatAudio = prefs.getBoolean("compatAudio", d.compatAudio),
            autoUpdateCheck = prefs.getBoolean("autoUpdateCheck", d.autoUpdateCheck),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(state.value)
        state.value = s
        prefs.edit()
            .putLong("activePlaylistId", s.activePlaylistId)
            .putString("bufferMode", s.bufferMode.name)
            .putString("audioDecoder", s.audioDecoder.name)
            .putString("liveFormat", s.liveFormat.name)
            .putString("aspectMode", s.aspectMode.name)
            .putBoolean("invertZapping", s.invertZapping)
            .putBoolean("autoplayLastChannel", s.autoplayLastChannel)
            .putBoolean("livePreview", s.livePreview)
            .putBoolean("showChannelNumbers", s.showChannelNumbers)
            .putBoolean("tunneling", s.tunneling)
            .putInt("epgOffsetHours", s.epgOffsetHours)
            .putString("userAgent", s.userAgent)
            .putString("preferredAudioLang", s.preferredAudioLang)
            .putString("preferredSubtitleLang", s.preferredSubtitleLang)
            .putString("lastChannelKey", s.lastChannelKey)
            .putBoolean("autoNextEpisode", s.autoNextEpisode)
            .putBoolean("compatAudio", s.compatAudio)
            .putBoolean("autoUpdateCheck", s.autoUpdateCheck)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
