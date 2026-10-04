package com.iplayer.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.iplayer.tv.data.Repository
import com.iplayer.tv.data.SettingsStore
import com.iplayer.tv.data.db.AppDatabase
import com.iplayer.tv.data.remote.Http
import com.iplayer.tv.player.PlaybackHolder
import com.iplayer.tv.player.PlayerManager
import okio.Path.Companion.toOkioPath

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val db = AppDatabase.create(app)
    val repository = Repository(db, settings)
    val player = PlayerManager(app, settings)
    val playback = PlaybackHolder()
}

class App : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .crossfade(140)
            .allowRgb565(true)
            .build()
}
