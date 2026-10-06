package com.iplayer.tv.player

import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Some film/series sources deliver barely faster (or even slower) than the video bitrate:
 * playback then stops for a couple of seconds every 10-30 s. After each stall in the same title,
 * wait for a longer buffer before resuming, so the title stalls a few times and then plays for
 * minutes instead of stuttering all along. Live channels keep the plain behavior (a live stream
 * arrives in real time, so waiting longer would only add delay).
 */
class AdaptiveLoadControl(
    private val base: DefaultLoadControl,
    maxBufferMs: Int,
    private val onSlowSource: () -> Unit,
) : LoadControl by base {
    // Written from the main thread, read on the playback thread.
    @Volatile private var adaptive = false
    @Volatile private var resetPending = false

    private var stalls = 0
    private var lastStallMs = C.TIME_UNSET
    private val capMs = (maxBufferMs - 5_000).coerceAtLeast(5_000)

    /** Called for every new title. */
    fun newItem(isLive: Boolean) {
        adaptive = !isLive
        resetPending = true
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (resetPending) {
            resetPending = false
            stalls = 0
            lastStallMs = C.TIME_UNSET
        }
        if (!base.shouldStartPlayback(parameters)) return false
        if (!adaptive || !parameters.rebuffering) return true

        val stallStart = parameters.lastRebufferRealtimeMs
        if (stallStart != C.TIME_UNSET && stallStart != lastStallMs) {
            // Stalls close together mean a slow source; an isolated hiccup much later lowers the level.
            stalls = if (lastStallMs == C.TIME_UNSET || stallStart - lastStallMs < 10 * 60_000) stalls + 1
            else (stalls - 1).coerceAtLeast(1)
            lastStallMs = stallStart
            if (stalls == 2) onSlowSource()
        }
        val requiredMs = when {
            stalls <= 1 -> return true
            stalls == 2 -> 8_000
            stalls == 3 -> 15_000
            stalls == 4 -> 25_000
            else -> 40_000
        }.coerceAtMost(capMs)
        val requiredUs = Util.getMediaDurationForPlayoutDuration(requiredMs * 1000L, parameters.playbackSpeed)
        // Never wait for more than the memory allows: start as soon as the buffer is full.
        return parameters.bufferedDurationUs >= requiredUs || !base.shouldContinueLoading(parameters)
    }
}
