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
import androidx.media3.exoplayer.ExoPlayer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exo: ExoPlayer? = null
    private var configSignature: String = ""
    private lateinit var httpFactory: OkHttpDataSource.Factory

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

    val player: ExoPlayer
        get() {
            val sig = signature()
            val p = exo
            if (p != null && sig == configSignature) return p
            p?.release()
            return build().also { exo = it; configSignature = sig }
        }

    val playerOrNull: ExoPlayer? get() = exo

    private fun signature(): String {
        val s = settings.value
        return "${s.bufferMode}|${s.audioDecoder}|${s.tunneling}"
    }

    private fun build(): ExoPlayer {
        val s = settings.value
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(
                when (s.audioDecoder) {
                    AudioDecoder.HARDWARE_FFMPEG -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    AudioDecoder.FFMPEG_FIRST -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    AudioDecoder.HARDWARE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
            .setEnableDecoderFallback(true)

        val (minBuf, maxBuf, startBuf, rebuf) = when (s.bufferMode) {
            BufferMode.FAST -> listOf(8_000, 30_000, 800, 2_000)
            BufferMode.BALANCED -> listOf(15_000, 50_000, 1_500, 3_000)
            BufferMode.STABLE -> listOf(30_000, 90_000, 3_000, 6_000)
            BufferMode.MAX -> listOf(60_000, 180_000, 5_000, 10_000)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, startBuf, rebuf)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, false)
            .build()

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
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !triedHlsFallback -> {
                    // Unknown extension: the stream might be an HLS playlist served without .m3u8
                    triedHlsFallback = true
                    p.setMediaItem(buildItem(np.url, np.title, MimeTypes.APPLICATION_M3U8))
                    p.prepare()
                }
                isRetryable(error) && retryCount < if (np.isLive) 8 else 3 -> {
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

    private fun isRetryable(e: PlaybackException) = e.errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Le serveur a refusé le flux (connexions max atteintes ou chaîne indisponible)."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion impossible au serveur."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Format de flux non pris en charge."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Codec non pris en charge par cet appareil."
        else -> "Lecture impossible (${e.errorCodeName})."
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

    fun play(url: String, title: String, key: String, isLive: Boolean, userAgent: String?, startPositionMs: Long = 0) {
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        currentUserAgent = ua
        val p = player
        httpFactory.setUserAgent(ua)
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
        play(np.url, np.title, np.key, np.isLive, currentUserAgent, if (np.isLive) 0 else p.currentPosition)
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
}
