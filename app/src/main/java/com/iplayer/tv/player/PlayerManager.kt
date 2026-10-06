package com.iplayer.tv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.iplayer.tv.data.AudioDecoder
import com.iplayer.tv.data.BufferMode
import com.iplayer.tv.data.SettingsStore
import com.iplayer.tv.data.remote.DEFAULT_USER_AGENT
import com.iplayer.tv.data.remote.Http
import com.iplayer.tv.util.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NowPlaying(
    val url: String,
    val title: String,
    val isLive: Boolean,
    val key: String,
)

/**
 * Owns a single ExoPlayer instance shared by the live preview and the full-screen player,
 * so switching between both is instantaneous (no re-buffering).
 */
class PlayerManager(private val context: Context, private val settings: SettingsStore) {
    private val scope = appScope(Dispatchers.Main.immediate)
    private var exo: ExoPlayer? = null
    private var configSignature: String = ""
    private lateinit var httpFactory: OkHttpDataSource.Factory
    private var loadControl: AdaptiveLoadControl? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private var retryCount = 0
    private var retryJob: Job? = null
    private var triedHlsFallback = false
    private var currentUserAgent: String? = null
    private var wasPlayingBeforeStop = false

    /** Session-only fallback to software video decoders after a hardware decoder failure. */
    private var softwareVideo = false

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    /** Emits the current player instance (it is rebuilt when decoding settings change). */
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** One-shot information messages for the player UI (e.g. compatibility mode switched on). */
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun consumeNotice() { _notice.value = null }

    val player: ExoPlayer
        get() {
            val sig = signature()
            val p = exo
            if (p != null && sig == configSignature) return p
            p?.release()
            return build().also {
                exo = it
                configSignature = sig
                _playerFlow.value = it
            }
        }

    val playerOrNull: ExoPlayer? get() = exo

    private fun signature(): String {
        val s = settings.value
        return "${s.bufferMode}|${s.audioDecoder}|${s.tunneling}|${s.compatAudio}|$softwareVideo"
    }

    private fun build(): ExoPlayer {
        val s = settings.value
        val compatAudio = s.compatAudio
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                if (!compatAudio) return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                // Plain PCM output: no Dolby/DTS passthrough, which some TVs advertise but cannot open.
                @Suppress("DEPRECATION")
                return DefaultAudioSink.Builder()
                    .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
            .setExtensionRendererMode(
                when {
                    compatAudio || s.audioDecoder == AudioDecoder.FFMPEG_FIRST -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    s.audioDecoder == AudioDecoder.HARDWARE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )
            .setMediaCodecSelector(if (softwareVideo) MediaCodecSelector.PREFER_SOFTWARE else MediaCodecSelector.DEFAULT)
            .setEnableDecoderFallback(true)

        val (bufMs, startBuf, rebuf) = when (s.bufferMode) {
            BufferMode.FAST -> listOf(30_000, 800, 2_000)
            BufferMode.BALANCED -> listOf(50_000, 1_500, 3_000)
            BufferMode.STABLE -> listOf(90_000, 3_000, 6_000)
            BufferMode.MAX -> listOf(180_000, 5_000, 10_000)
        }
        // Min buffer = max buffer: the player keeps reading continuously instead of filling the buffer
        // then leaving the connection idle for tens of seconds, which many IPTV servers drop (the
        // stale connection then counts against the account's limit and the reconnection stalls).
        // The memory cap keeps high-bitrate titles from exhausting the heap.
        val loadControl = AdaptiveLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(bufMs, bufMs, startBuf, rebuf)
                .setTargetBufferBytes(targetBufferBytes())
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBackBuffer(10_000, false)
                .build(),
            maxBufferMs = bufMs,
            onSlowSource = { _notice.value = "Source lente : mise en mémoire plus longue pour limiter les coupures" },
        )
        this.loadControl = loadControl

        httpFactory = OkHttpDataSource.Factory(Http.client)
            .setUserAgent(currentUserAgent ?: DEFAULT_USER_AGENT)
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory), extractors)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(4))

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(s.preferredAudioLang.ifBlank { null })
                    .setPreferredTextLanguage(s.preferredSubtitleLang.ifBlank { null })
                    .setTunnelingEnabled(s.tunneling)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
            )
        }

        return ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { it.addListener(listener) }
    }

    /** About a third of the (large) heap, which the buffered media lives in. */
    private fun targetBufferBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 3).coerceIn(48L shl 20, 192L shl 20).toInt()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                retryCount = 0
                _reconnecting.value = false
                _error.value = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val np = _nowPlaying.value ?: return
            val p = exo ?: return
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    p.seekToDefaultPosition()
                    p.prepare()
                }
                isDecoderError(error) && tryCompatibilityFallback(error, np) -> Unit
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !triedHlsFallback -> {
                    // Unknown extension: the stream might be an HLS playlist served without .m3u8
                    triedHlsFallback = true
                    p.setMediaItem(buildItem(np.url, np.title, MimeTypes.APPLICATION_M3U8))
                    p.prepare()
                }
                isRetryable(error) && retryCount < if (np.isLive) 8 else 6 -> {
                    retryCount++
                    _reconnecting.value = true
                    retryJob?.cancel()
                    retryJob = scope.launch {
                        delay(minOf(1000L * retryCount, 5000L))
                        val pos = p.currentPosition
                        if (np.isLive) p.seekToDefaultPosition() else p.seekTo(pos)
                        p.prepare()
                        p.play()
                    }
                }
                else -> {
                    _reconnecting.value = false
                    _error.value = describe(error)
                }
            }
        }
    }

    private fun isRetryable(e: PlaybackException) = e.errorCode in RETRYABLE_ERRORS

    private fun isDecoderError(e: PlaybackException) = e.errorCode in DECODER_ERRORS

    private fun failingMime(e: PlaybackException): String? =
        (e as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
            ?: (e.cause as? MediaCodecRenderer.DecoderInitializationException)?.mimeType

    /**
     * Decoder trouble is very common on TVs (vendor Dolby decoders closed to third-party apps,
     * passthrough advertised but not working, picky hardware video decoders). Instead of failing,
     * switch to safer decoders and resume where we were.
     */
    private fun tryCompatibilityFallback(e: PlaybackException, np: NowPlaying): Boolean {
        val mime = failingMime(e)
        val audioFailed = (mime != null && MimeTypes.isAudio(mime)) ||
            e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        val videoFailed = mime != null && MimeTypes.isVideo(mime)
        val s = settings.value
        when {
            (audioFailed || !videoFailed) && !s.compatAudio -> {
                settings.update { it.copy(compatAudio = true) }
                _notice.value = "Mode audio compatible activé"
            }
            (videoFailed || audioFailed.not()) && !softwareVideo -> {
                softwareVideo = true
                _notice.value = "Décodage vidéo logiciel activé"
            }
            else -> return false
        }
        val position = exo?.currentPosition ?: 0L
        scope.launch {
            delay(150) // never rebuild the player from inside its own callback
            play(np.url, np.title, np.key, np.isLive, currentUserAgent, if (np.isLive) 0 else position, keepSoftwareVideo = true)
        }
        return true
    }

    private fun describe(e: PlaybackException): String {
        val base = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Le serveur a refusé le flux (connexions max atteintes ou chaîne indisponible)."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion impossible au serveur."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Format de flux non pris en charge."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Codec non pris en charge par cet appareil."
            else -> "Lecture impossible."
        }
        val details = listOfNotNull(
            failingMime(e)?.let { codecName(it) },
            (e as? ExoPlaybackException)?.rendererFormat?.let { f -> if (f.height > 0) "${f.width}×${f.height}" else null },
            (e.cause as? MediaCodecRenderer.DecoderInitializationException)?.codecInfo?.name,
            e.errorCodeName,
        )
        return base + "\n" + details.joinToString(" · ")
    }

    private fun codecName(mime: String): String = when (mime) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC (H.265)"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC3)"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+ (E-AC3)"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MPEG audio"
        else -> mime
    }

    private fun buildItem(url: String, title: String, mime: String?): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(url))
            .apply { if (mime != null) setMimeType(mime) }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    private fun inferMime(url: String): String? {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> null
        }
    }

    fun play(
        url: String,
        title: String,
        key: String,
        isLive: Boolean,
        userAgent: String?,
        startPositionMs: Long = 0,
        keepSoftwareVideo: Boolean = false,
    ) {
        if (!keepSoftwareVideo) softwareVideo = false
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        currentUserAgent = ua
        val p = player
        httpFactory.setUserAgent(ua)
        loadControl?.newItem(isLive)
        retryJob?.cancel()
        retryCount = 0
        triedHlsFallback = false
        _error.value = null
        _reconnecting.value = false
        _nowPlaying.value = NowPlaying(url, title, isLive, key)
        val item = buildItem(url, title, inferMime(url))
        if (isLive || startPositionMs <= 0) p.setMediaItem(item) else p.setMediaItem(item, startPositionMs)
        p.prepare()
        p.playWhenReady = true
    }

    fun isPlaying(key: String) = _nowPlaying.value?.key == key

    fun retry() {
        val np = _nowPlaying.value ?: return
        val p = exo ?: return
        play(np.url, np.title, np.key, np.isLive, currentUserAgent, if (np.isLive) 0 else p.currentPosition, keepSoftwareVideo = true)
    }

    fun stop() {
        retryJob?.cancel()
        exo?.stop()
        exo?.clearMediaItems()
        _nowPlaying.value = null
        _error.value = null
        _reconnecting.value = false
    }

    /** Called when the app goes to background: free the decoders but remember what was playing. */
    fun onBackground() {
        val p = exo ?: return
        wasPlayingBeforeStop = _nowPlaying.value != null && p.playWhenReady
        if (_nowPlaying.value?.isLive == true) p.stop() else p.pause()
    }

    fun onForeground() {
        val p = exo ?: return
        val np = _nowPlaying.value ?: return
        if (wasPlayingBeforeStop) {
            if (np.isLive) { p.seekToDefaultPosition(); p.prepare() }
            p.play()
        }
    }

    fun release() {
        exo?.release()
        exo = null
    }

    private companion object {
        private val RETRYABLE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_TIMEOUT,
        )

        private val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        )
    }
}
