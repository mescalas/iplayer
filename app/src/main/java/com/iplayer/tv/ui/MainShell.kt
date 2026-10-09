package com.iplayer.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.iplayer.tv.R
import com.iplayer.tv.data.SyncState
import com.iplayer.tv.ui.components.ActionDialog
import com.iplayer.tv.ui.components.DialogAction
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.home.HomeScreen
import com.iplayer.tv.ui.live.LiveScreen
import com.iplayer.tv.ui.search.SearchScreen
import com.iplayer.tv.ui.sports.SportsScreen
import com.iplayer.tv.ui.settings.SettingsScreen
import com.iplayer.tv.ui.settings.WelcomeScreen
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.ui.vod.MoviesScreen
import com.iplayer.tv.ui.vod.SeriesScreen
import com.iplayer.tv.update.UpdateState
import com.iplayer.tv.util.formatClock
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector? = null) {
    HOME("Accueil"), LIVE("TV en direct"), SPORTS("Sports"), MOVIES("Films"), SERIES("Séries"),
    SEARCH("Recherche", Icons.Rounded.Search), SETTINGS("Réglages", Icons.Rounded.Settings)
}

/** Shell-level services exposed to tabs. */
class ShellController(
    val backdrop: MutableState<String?>,
    val toastState: MutableState<String?>,
    val focusTabs: () -> Unit,
) {
    /** The home screen is scrolled past its hero: the tab bar hides until it gets focus. */
    val homeScrolled = mutableStateOf(false)
    val barFocused = mutableStateOf(false)

    /**
     * Where "down" from the tab bar lands on the home screen. Home runs under the bar, so its list starts above
     * the tabs and the geometric focus search never picks it: the bar moves focus there explicitly.
     */
    val homeEntry = FocusRequester()

    fun toast(message: String) { toastState.value = message }
}

val LocalShell = compositionLocalOf<ShellController> { error("no shell") }

@Composable
fun MainShell() {
    val container = LocalContainer.current
    val nav = LocalNav.current
    val playlists by container.repository.playlists.collectAsState()
    val list = playlists
    if (list == null) {
        Box(Modifier.fillMaxSize().background(C.Background))
        return
    }
    if (list.isEmpty()) {
        WelcomeScreen()
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var focusedTab by remember { mutableStateOf<Tab?>(null) }
    var barHasFocus by remember { mutableStateOf(false) }
    val tabRequesters = remember { Tab.entries.associateWith { FocusRequester() } }
    val stateHolder = rememberSaveableStateHolder()
    val backdrop = remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as? Activity
    val toast = remember { mutableStateOf<String?>(null) }
    val shell = remember { ShellController(backdrop, toast) { tabRequesters[tab]?.tryFocus() } }

    // tvOS-like: focusing a tab switches to it (debounced so fast scrolling across tabs stays smooth)
    LaunchedEffect(focusedTab) {
        val t = focusedTab ?: return@LaunchedEffect
        if (t != tab) {
            delay(220)
            tab = t
        }
    }
    LaunchedEffect(tab) {
        backdrop.value = null
    }
    LaunchedEffect(Unit) {
        if (!nav.restoreFocus) {
            delay(50)
            tabRequesters[tab]?.tryFocus()
        }
        // Safety net: if the tab failed to restore focus, fall back on the tab bar.
        delay(600)
        nav.restoreFocus = false
    }
    LaunchedEffect(Unit) {
        container.updater.consumeJustUpdated()?.let { toast.value = "iPlayer mis à jour en version $it" }
    }

    BackHandler {
        when {
            !barHasFocus -> tabRequesters[tab]?.tryFocus()
            tab != Tab.HOME -> {
                tab = Tab.HOME
                tabRequesters[Tab.HOME]?.tryFocus()
            }
            else -> activity?.finish()
        }
    }

    CompositionLocalProvider(LocalShell provides shell) {
        Box(Modifier.fillMaxSize().background(C.Background)) {
            Backdrop(backdrop)
            // Home is immersive (its hero runs under the tab bar); other tabs start below the bar.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = if (tab == Tab.HOME) 0.dp else 76.dp)
                    // Left/Right inside a tab never land on the tab bar (focusing a tab switches to it):
                    // only Up reaches it.
                    .focusProperties {
                        exit = { dir ->
                            if (dir == FocusDirection.Left || dir == FocusDirection.Right) FocusRequester.Cancel
                            else FocusRequester.Default
                        }
                    }
                    .focusGroup()
            ) {
                stateHolder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.HOME -> HomeScreen()
                        Tab.LIVE -> LiveScreen()
                        Tab.SPORTS -> SportsScreen()
                        Tab.MOVIES -> MoviesScreen()
                        Tab.SERIES -> SeriesScreen()
                        Tab.SEARCH -> SearchScreen()
                        Tab.SETTINGS -> SettingsScreen()
                    }
                }
            }
            val barVisible = barHasFocus || !(tab == Tab.HOME && shell.homeScrolled.value)
            val barAlpha by animateFloatAsState(if (barVisible) 1f else 0f, tween(220), label = "bar")
            TopBar(
                selected = tab,
                requesters = tabRequesters,
                onFocusTab = { focusedTab = it },
                onSelect = { tab = it },
                modifier = Modifier
                    .onPreviewKeyEvent { ev ->
                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown && tab == Tab.HOME &&
                            shell.homeEntry.tryFocus()
                    }
                    .graphicsLayer { alpha = barAlpha }
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x66000000), Color.Transparent)))
                    .onFocusChanged {
                        barHasFocus = it.hasFocus
                        shell.barFocused.value = it.hasFocus
                    },
            )
            SyncPill(Modifier.align(Alignment.BottomEnd).padding(24.dp))
            UpdatePill(Modifier.align(Alignment.BottomStart).padding(24.dp))
            Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp))
        }
        UpdatePrompt()
    }
}

/** Offers a newly published version once per session (until the user answers "Plus tard"). */
@Composable
private fun UpdatePrompt() {
    val updater = LocalContainer.current.updater
    val state by updater.flow.collectAsState()
    val dismissed by updater.dismissedCode.collectAsState()
    val release = (state as? UpdateState.Available)?.release ?: return
    if (release.versionCode <= dismissed) return
    ActionDialog(
        "Mise à jour disponible",
        "iPlayer ${release.versionName}" + release.notes.lines().filter { it.isNotBlank() }.take(6)
            .joinToString("") { "\n• " + it.trim().removePrefix("- ").removePrefix("* ") },
        listOf(
            DialogAction("Mettre à jour") { updater.install(release) },
            DialogAction("Plus tard") { updater.dismiss(release) },
        ),
    ) { updater.dismiss(release) }
}

@Composable
private fun UpdatePill(modifier: Modifier = Modifier) {
    val state by LocalContainer.current.updater.flow.collectAsState()
    var visibleError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Failed && s.release != null) {
            visibleError = s.message
            delay(8000)
            visibleError = null
        } else visibleError = null
    }
    val text = when (val s = state) {
        is UpdateState.Downloading -> "Téléchargement de la mise à jour… ${(s.progress * 100).toInt()} %"
        is UpdateState.Installing -> "Installation de la mise à jour…"
        else -> visibleError
    }
    AnimatedVisibility(text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE61C1C1E))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (visibleError == null) Icons.Rounded.SystemUpdate else Icons.Rounded.ErrorOutline,
                null,
                Modifier.size(18.dp),
                tint = if (visibleError == null) C.Text2 else C.Red,
            )
            Spacer(Modifier.width(10.dp))
            Text(text ?: "", style = T.Footnote, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(320.dp))
        }
    }
}

/** Takes the state itself: focus moves update the backdrop without recomposing the whole shell. */
@Composable
private fun Backdrop(state: State<String?>) {
    val url = state.value
    var shown by remember { mutableStateOf(url) }
    LaunchedEffect(url) {
        delay(280)
        shown = url
    }
    val current = shown ?: return
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.42f,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight(0.78f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to C.Background, 0.30f to C.Background, 0.62f to Color(0x99000000), 1f to Color(0x22000000)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x66000000), 0.45f to Color.Transparent, 0.78f to C.Background)
            )
        )
    }
}

@Composable
private fun TopBar(
    selected: Tab,
    requesters: Map<Tab, FocusRequester>,
    onFocusTab: (Tab) -> Unit,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(76.dp).padding(horizontal = 40.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.mipmap.ic_launcher),
                null,
                Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text("iPlayer", style = T.Title3)
        }
        Row(
            Modifier
                .align(Alignment.Center)
                .focusProperties { enter = { requesters[selected] ?: FocusRequester.Default } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val isSel = t == selected
                FocusSurface(
                    onClick = { onSelect(t) },
                    modifier = Modifier.height(40.dp).focusRequester(requesters.getValue(t)),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSel) C.Surface2 else Color.Transparent,
                    contentColor = if (isSel) C.Text else C.Text2,
                    focusedScale = 1.06f,
                    elevation = 10.dp,
                    contentAlignment = Alignment.Center,
                    onFocusChange = { if (it) onFocusTab(t) },
                ) {
                    if (t.icon != null) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(t.icon, t.label, Modifier.size(20.dp))
                        }
                    } else {
                        Text(t.label, style = T.Headline, modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
        }
        Clock(Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    Text(formatClock(now), style = T.Title3, color = C.Text2, modifier = modifier)
}

@Composable
private fun SyncPill(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val state by container.repository.syncState.collectAsState()
    var visibleError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        val s = state
        if (s is SyncState.Failed) {
            visibleError = s.message
            delay(8000)
            visibleError = null
        } else visibleError = null
    }
    val running = state as? SyncState.Running
    val text = running?.message ?: visibleError
    AnimatedVisibility(text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE61C1C1E))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (running != null) Icons.Rounded.Sync else Icons.Rounded.ErrorOutline,
                null,
                Modifier.size(18.dp),
                tint = if (running != null) C.Text2 else C.Red,
            )
            Spacer(Modifier.width(10.dp))
            Text(text ?: "", style = T.Footnote, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(320.dp))
        }
    }
}

@Composable
fun Toast(state: MutableState<String?>, modifier: Modifier = Modifier) {
    val message = state.value
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            delay(2200)
            state.value = null
        }
    }
    AnimatedVisibility(message != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            shown,
            style = T.Headline,
            color = C.OnFocus,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF2FFFFFF))
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}
