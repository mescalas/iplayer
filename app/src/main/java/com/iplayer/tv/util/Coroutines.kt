package com.iplayer.tv.util

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Scope for app-wide background work (sync, history saves, retries…). An exception escaping one of its
 * coroutines (a full disk, a database busy during a sync, a provider sending garbage) is logged instead of
 * killing the whole app, which is what an app-level scope without handler does.
 */
fun appScope(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e ->
        Log.w("iPlayer", "Background task failed", e)
    })
