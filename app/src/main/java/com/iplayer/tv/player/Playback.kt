package com.iplayer.tv.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.PlaylistEntity

data class VodItem(
    val kind: Int,
    val key: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val url: String,
    val parentKey: String? = null,
    /** -1 = resume from history when available, otherwise an explicit position (0 = from the start). */
    val startPosition: Long = -1,
    val trackHistory: Boolean = true,
    val description: String? = null,
)

sealed interface PlayRequest {
    val playlist: PlaylistEntity

    data class Live(
        override val playlist: PlaylistEntity,
        val channels: List<ChannelEntity>,
        val index: Int,
    ) : PlayRequest

    data class Vod(
        override val playlist: PlaylistEntity,
        val items: List<VodItem>,
        val index: Int,
    ) : PlayRequest
}

/** In-memory hand-off between screens and the player (avoids serialising big lists in routes). */
class PlaybackHolder {
    var request: PlayRequest? = null
    /** Keep the stream running when leaving the full-screen player (live preview in the TV tab). */
    var keepAliveOnExit: Boolean = false
}

fun AspectMode.resizeMode(): Int = when (this) {
    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

@Composable
fun VideoSurface(player: Player?, resizeMode: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                keepScreenOn = true
                // Subtitles are drawn by SubtitleLayer, styled from the settings.
                subtitleView?.visibility = View.GONE
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            if (view.resizeMode != resizeMode) view.resizeMode = resizeMode
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
