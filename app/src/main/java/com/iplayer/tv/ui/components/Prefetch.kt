package com.iplayer.tv.ui.components

import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow

/**
 * How far outside the screen lazy lists keep items ready: the next cards are composed (and their images
 * requested) in the idle time between frames, instead of all at once in the frame where they scroll in.
 * Items just scrolled past stay composed, so going back is free too.
 */
val ScreenAhead = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)

/** Lighter window for the home screen, whose items are whole shelves. */
val ShelfAhead = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.5f)
