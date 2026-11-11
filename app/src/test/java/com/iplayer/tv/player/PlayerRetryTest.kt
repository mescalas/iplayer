package com.iplayer.tv.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the busy-answer handling: the slow-wait branch in onPlayerError owns
 * HTTP 503 / 429 / 509, so the generic retry path must never pick a busy answer up again once
 * that branch has spent its five waits.
 */
class PlayerRetryTest {
    private val badHttp = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

    @Test
    fun busyAnswersStayOutOfTheGenericRetryPath() {
        // After the five slow waits the error must be final, not retried fast another six times.
        assertFalse(PlayerManager.isRetryableError(badHttp, 503, isLive = false, retryCount = 5))
        assertFalse(PlayerManager.isRetryableError(badHttp, 429, isLive = false, retryCount = 5))
        assertFalse(PlayerManager.isRetryableError(badHttp, 509, isLive = false, retryCount = 5))
    }

    @Test
    fun busyAnswersStayOutOfTheGenericRetryPathForLiveToo() {
        assertFalse(PlayerManager.isRetryableError(badHttp, 503, isLive = true, retryCount = 0))
    }

    @Test
    fun vodServerErrorsStillGetTheGenericRetries() {
        assertTrue(PlayerManager.isRetryableError(badHttp, 500, isLive = false, retryCount = 0))
        assertTrue(PlayerManager.isRetryableError(badHttp, 502, isLive = false, retryCount = 3))
    }

    @Test
    fun vodClientErrorsGetASingleRetry() {
        assertTrue(PlayerManager.isRetryableError(badHttp, 403, isLive = false, retryCount = 0))
        assertFalse(PlayerManager.isRetryableError(badHttp, 403, isLive = false, retryCount = 1))
    }
}
