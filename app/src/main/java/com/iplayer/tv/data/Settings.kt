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

/** Text height as a fraction of the video height. */
enum class SubtitleSize(val label: String, val fraction: Float) {
    SMALL("Petite", 0.034f), MEDIUM("Moyenne", 0.042f), LARGE("Grande", 0.052f), XLARGE("Très grande", 0.064f)
}

enum class SubtitleColor(val label: String, val argb: Long) {
    WHITE("Blanc", 0xFFFFFFFF), YELLOW("Jaune", 0xFFFFE066), CYAN("Cyan", 0xFF7FDBFF), GREEN("Vert", 0xFF8EF0A4)
}

enum class SubtitleBackground(val label: String, val argb: Long) {
    TRANSLUCENT("Translucide", 0x8C1E1E21), DARK("Sombre", 0xD9000000), NONE("Aucun (ombré)", 0x00000000)
}

enum class SubtitleFont(val label: String) { MODERN("Moderne"), SYSTEM("Système"), CLASSIC("Classique") }

/** Distance from the bottom edge as a fraction of the video height (TOP pins every line to the top). */
enum class SubtitlePosition(val label: String, val margin: Float) {
    BOTTOM("En bas", 0.06f), RAISED("Plus haut", 0.14f), TOP("En haut", 0.06f)
}

data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.MEDIUM,
    val color: SubtitleColor = SubtitleColor.WHITE,
    val background: SubtitleBackground = SubtitleBackground.TRANSLUCENT,
    val font: SubtitleFont = SubtitleFont.MODERN,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
)

/** Next (or previous, with a negative [step]) value of an enum, wrapping around. */
inline fun <reified E : Enum<E>> E.cycle(step: Int = 1): E {
    val values = enumValues<E>()
    return values[(ordinal + step).mod(values.size)]
}

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
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val sports: Set<Sport> = Sport.defaults,
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
            sports = prefs.getStringSet("sports", null)?.let { saved ->
                Sport.entries.filter { it.name in saved }.toSet()
            } ?: d.sports,
            subtitleStyle = SubtitleStyle(
                size = enumOr(prefs.getString("subtitleSize", null), d.subtitleStyle.size),
                color = enumOr(prefs.getString("subtitleColor", null), d.subtitleStyle.color),
                background = enumOr(prefs.getString("subtitleBackground", null), d.subtitleStyle.background),
                font = enumOr(prefs.getString("subtitleFont", null), d.subtitleStyle.font),
                position = enumOr(prefs.getString("subtitlePosition", null), d.subtitleStyle.position),
            ),
        )
    }

    /** Called from the UI and from background syncs: serialised so that no change is lost. */
    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val old = state.value
        val s = transform(old)
        if (s == old) return
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
            .putStringSet("sports", s.sports.map { it.name }.toSet())
            .putString("subtitleSize", s.subtitleStyle.size.name)
            .putString("subtitleColor", s.subtitleStyle.color.name)
            .putString("subtitleBackground", s.subtitleStyle.background.name)
            .putString("subtitleFont", s.subtitleStyle.font.name)
            .putString("subtitlePosition", s.subtitleStyle.position.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
