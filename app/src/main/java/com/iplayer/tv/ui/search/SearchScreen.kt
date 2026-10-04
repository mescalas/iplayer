package com.iplayer.tv.ui.search

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.iplayer.tv.ui.components.RowShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iplayer.tv.AppContainer
import com.iplayer.tv.data.SearchResults
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.PosterCard
import com.iplayer.tv.ui.components.SectionTitle
import com.iplayer.tv.ui.components.TextInputDialog
import com.iplayer.tv.ui.components.WideCard
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val playlist = repo.activePlaylist
    val query = MutableStateFlow("")
    val results: StateFlow<SearchResults> = combine(query.debounce(220), playlist) { q, p -> q to p }
        .mapLatest { (q, p) -> if (p == null) SearchResults() else repo.search(p.id, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())
}

private val KEYS = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }

@Composable
fun SearchScreen() {
    val vm = appViewModel { SearchViewModel(it) }
    val nav = LocalNav.current
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val playlist by vm.playlist.collectAsState()
    var systemKeyboard by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp, top = 8.dp)) {
        // ---- on-screen keyboard (fast with a remote, no IME needed)
        Column(Modifier.width(312.dp)) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RowShape).background(Color(0x14FFFFFF)).border(1.dp, Color(0x33FFFFFF), RowShape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = C.Gold)
                Spacer(Modifier.width(10.dp))
                Text(
                    query.ifEmpty { "RECHERCHER" },
                    style = if (query.isEmpty()) T.Label.copy(fontSize = 14.sp) else T.Title3,
                    color = if (query.isEmpty()) C.Text3 else C.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KEYS.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k -> Key(k, Modifier.size(width = 47.dp, height = 42.dp)) { vm.query.value += k } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyIcon(Icons.Rounded.SpaceBar, Modifier.size(width = 100.dp, height = 42.dp)) { vm.query.value += " " }
                    KeyIcon(Icons.AutoMirrored.Rounded.Backspace, Modifier.size(width = 100.dp, height = 42.dp)) { vm.query.value = vm.query.value.dropLast(1) }
                    Key("Effacer", Modifier.size(width = 100.dp, height = 42.dp)) { vm.query.value = "" }
                }
                FocusSurface(
                    onClick = { systemKeyboard = true },
                    modifier = Modifier.width(312.dp).height(42.dp),
                    shape = RowShape,
                    color = Color(0x14FFFFFF),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Keyboard, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CLAVIER / DICTÉE VOCALE", style = T.Label)
                    }
                }
            }
        }
        Spacer(Modifier.width(28.dp))

        // ---- results
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val p = playlist
            when {
                query.trim().length < 2 -> EmptyState(Icons.Rounded.Search, "Rechercher", "Chaînes, films et séries de votre playlist.", Modifier.fillMaxSize())
                results.isEmpty -> EmptyState(Icons.Rounded.Search, "Aucun résultat", "Aucun contenu ne correspond à « $query ».", Modifier.fillMaxSize())
                p != null -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (results.channels.isNotEmpty()) section("Chaînes") {
                        itemsIndexed(results.channels, key = { _, c -> "c" + c.id }) { i, ch ->
                            WideCard(ch.name, null, ch.logo, logoMode = true, width = 170.dp, onClick = { nav.playLive(p, results.channels, i) })
                        }
                    }
                    if (results.movies.isNotEmpty()) section("Films") {
                        itemsIndexed(results.movies, key = { _, m -> "m" + m.id }) { _, m ->
                            PosterCard(m.name, m.poster, onClick = { nav.movie(m.id) }, width = 118.dp, subtitle = m.year, rating = m.rating)
                        }
                    }
                    if (results.series.isNotEmpty()) section("Séries") {
                        itemsIndexed(results.series, key = { _, s -> "s" + s.id }) { _, s ->
                            PosterCard(s.name, s.cover, onClick = { nav.series(s.id) }, width = 118.dp, subtitle = s.year, rating = s.rating)
                        }
                    }
                }
            }
        }
    }

    if (systemKeyboard) {
        TextInputDialog(
            title = "Rechercher",
            initial = query,
            onDone = { vm.query.value = it; systemKeyboard = false },
            onDismiss = { systemKeyboard = false },
        )
    }
}

private fun LazyListScope.section(title: String, content: LazyListScope.() -> Unit) {
    item(title) {
        Column {
            SectionTitle(title, Modifier.padding(start = 4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.focusRestorer(),
                content = content,
            )
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, shape = RowShape, color = Color(0x14FFFFFF), focusedScale = 1.1f, contentAlignment = Alignment.Center) {
        Text(label.uppercase(), style = if (label.length == 1) T.Headline else T.Label)
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, shape = RowShape, color = Color(0x14FFFFFF), focusedScale = 1.08f, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp))
    }
}
