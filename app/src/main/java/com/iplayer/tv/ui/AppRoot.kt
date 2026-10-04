package com.iplayer.tv.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iplayer.tv.App
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.player.PlayRequest
import com.iplayer.tv.ui.player.PlayerScreen
import com.iplayer.tv.ui.settings.PlaylistEditScreen
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.vod.MovieDetailScreen
import com.iplayer.tv.ui.vod.SeriesDetailScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no container") }
val LocalNav = staticCompositionLocalOf<AppNav> { error("no nav") }

class AppNav(private val nav: NavController, private val container: AppContainer) {
    /** Set when leaving a tab for a full-screen destination, so the tab restores its focus on return. */
    var restoreFocus = false

    /** True while the full-screen player is (or is about to be) displayed. */
    var inPlayer = false

    fun play(request: PlayRequest, keepAliveOnExit: Boolean = false) {
        container.playback.request = request
        container.playback.keepAliveOnExit = keepAliveOnExit
        restoreFocus = true
        inPlayer = true
        if (nav.currentDestination?.route == "player") return
        nav.navigate("player") { launchSingleTop = true }
    }

    fun playLive(playlist: PlaylistEntity, channels: List<ChannelEntity>, index: Int, keepAliveOnExit: Boolean = false) =
        play(PlayRequest.Live(playlist, channels, index.coerceIn(0, (channels.size - 1).coerceAtLeast(0))), keepAliveOnExit)

    fun movie(id: Long) { restoreFocus = true; nav.navigate("movie/$id") }
    fun series(id: Long) { restoreFocus = true; nav.navigate("series/$id") }
    fun editPlaylist(id: Long = 0) { restoreFocus = true; nav.navigate("playlist?id=$id") }
    fun back() { nav.popBackStack() }
    fun backToMain() { nav.popBackStack("main", inclusive = false) }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as App).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as App).container }
    val navController = rememberNavController()
    val appNav = remember(navController) { AppNav(navController, container) }

    CompositionLocalProvider(LocalContainer provides container, LocalNav provides appNav) {
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize().background(C.Background),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(110)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(110)) },
        ) {
            composable("main") { MainShell() }
            composable(
                "player",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { PlayerScreen() }
            composable("movie/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                MovieDetailScreen(it.arguments?.getLong("id") ?: 0L)
            }
            composable("series/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                SeriesDetailScreen(it.arguments?.getLong("id") ?: 0L)
            }
            composable("playlist?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
                PlaylistEditScreen(it.arguments?.getLong("id") ?: 0L)
            }
        }
    }

    // Optional: jump straight into the last watched channel on launch.
    LaunchedEffect(Unit) {
        val s = container.settings.value
        if (!s.autoplayLastChannel || s.lastChannelKey.isBlank()) return@LaunchedEffect
        val playlist = container.repository.activePlaylist.filterNotNull().first()
        val ch = container.repository.channelByKey(playlist.id, s.lastChannelKey) ?: return@LaunchedEffect
        val list = container.repository.channels(playlist.id, ch.categoryId).first()
        appNav.playLive(playlist, list, list.indexOfFirst { it.id == ch.id }.coerceAtLeast(0))
    }
}
