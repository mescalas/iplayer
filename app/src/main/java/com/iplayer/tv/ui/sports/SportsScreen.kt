package com.iplayer.tv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iplayer.tv.data.Sport
import com.iplayer.tv.data.SportsCalendar
import com.iplayer.tv.data.SportsEvent
import com.iplayer.tv.data.SyncState
import com.iplayer.tv.data.db.ChannelEntity
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.appViewModel
import com.iplayer.tv.ui.components.ChannelLogo
import com.iplayer.tv.ui.components.EmptyState
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.SideListItem
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.util.mediaName
import kotlinx.coroutines.delay

@Composable
fun SportsScreen() {
    val vm = appViewModel { SportsViewModel(it) }
    val container = LocalContainer.current
    val nav = LocalNav.current
    val shell = LocalShell.current
    val state by vm.state.collectAsState()
    val now by vm.now.collectAsState()
    val day by vm.day.collectAsState()
    val settings by container.settings.flow.collectAsState()
    val sync by container.repository.syncState.collectAsState()
    val refreshing = sync is SyncState.Running
    val playlist = state.playlist
    val missingGuide = playlist != null && !playlist.isXtream && playlist.epgUrl.isBlank() && playlist.detectedEpgUrl.isBlank()
    var preferences by rememberSaveable { mutableStateOf(false) }
    var chosenKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val restoreFocus = remember { FocusRequester() }
    val expectedDay = SportsCalendar.dayBounds(now, day).first
    val loading = state.loading || state.dayStart != expectedDay && state.playlist != null
    val events = remember(state.events, now, day) {
        if (day == 0) state.events.filter { it.programme.endAt > now } else state.events
    }

    LaunchedEffect(loading, events) {
        if (!nav.restoreFocus || loading) return@LaunchedEffect
        val index = events.indexOfFirst { it.key == vm.lastFocusedKey }
        if (index >= 0) {
            listState.scrollToItem(index)
            delay(60)
            if (!restoreFocus.tryFocus()) shell.focusTabs()
        } else shell.focusTabs()
        nav.restoreFocus = false
    }
    LaunchedEffect(day) { if (!nav.restoreFocus) listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ton calendrier sportif", style = T.Title2)
                Text("Guide de ta playlist · Heure de Paris · Chaînes FR en priorité", style = T.Footnote, color = C.Text2)
            }
            PillButton("Mes sports (${settings.sports.size})", onClick = { preferences = true })
            Spacer(Modifier.width(12.dp))
            PillButton(if (refreshing) "Actualisation…" else "Actualiser", onClick = {
                if (missingGuide && playlist != null) nav.editPlaylist(playlist.id)
                else if (!refreshing) vm.refresh()
            })
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            items((0..6).toList()) { offset ->
                val start = SportsCalendar.dayBounds(now, offset).first
                val label = when (offset) {
                    0 -> "Aujourd’hui"
                    1 -> "Demain"
                    else -> SportsCalendar.format(start, "EEE d MMM")
                }
                FocusSurface(
                    onClick = { vm.day.value = offset },
                    color = if (day == offset) C.Surface3 else C.Surface,
                    shape = RoundedCornerShape(18.dp),
                    focusedScale = 1.03f,
                ) { Text(label, style = T.Callout, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                settings.sports.isEmpty() -> EmptyState(Icons.Rounded.SportsSoccer, "Choisis tes sports", "Sélectionne les sports que tu veux retrouver dans ton calendrier.", Modifier.fillMaxSize()) {
                    PillButton("Choisir mes sports", onClick = { preferences = true })
                }
                missingGuide -> EmptyState(Icons.Rounded.SportsSoccer, "Ajoute un guide TV", "Renseigne l’adresse du guide XMLTV dans les réglages de ta playlist pour retrouver ses diffusions sportives.", Modifier.fillMaxSize()) {
                    PillButton("Configurer le guide", onClick = { playlist?.let { nav.editPlaylist(it.id) } })
                }
                loading -> Loading(Modifier.fillMaxSize(), "Chargement du calendrier…")
                state.error != null -> EmptyState(Icons.Rounded.SportsSoccer, "Calendrier indisponible", state.error, Modifier.fillMaxSize())
                events.isEmpty() -> EmptyState(
                    Icons.Rounded.SportsSoccer,
                    "Aucune rencontre annoncée",
                    "Le guide ne contient pas de rencontre identifiable pour tes sports à cette date. Actualise-le ou choisis un autre jour. La couverture dépend de ta playlist.",
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(4.dp, 6.dp, 4.dp, 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(events, key = { it.key }) { event ->
                        EventCard(
                            event, now,
                            modifier = if (event.key == vm.lastFocusedKey) Modifier.focusRequester(restoreFocus) else Modifier,
                            onClick = {
                                vm.lastFocusedKey = event.key
                                val playlist = state.playlist
                                if (playlist != null) {
                                    if (event.channels.size == 1 && event.programme.startAt <= System.currentTimeMillis()) {
                                        nav.playLive(playlist, event.channels, 0)
                                    } else chosenKey = event.key
                                }
                            },
                        )
                    }
                }
            }
        }
        Text("Horaires et diffusions annoncés par le guide TV. Un programme à l’antenne peut être une rediffusion.", style = T.Caption, color = C.Text2)
    }

    if (preferences) SportsPreferences(settings.sports, vm::toggle) { preferences = false }
    val chosen = events.firstOrNull { it.key == chosenKey }
    if (chosen != null && !loading) BroadcastDialog(chosen, now, onSelect = { index ->
        state.playlist?.let { nav.playLive(it, chosen.channels, index) }
        chosenKey = null
    }, onDismiss = { chosenKey = null })
}

@Composable
private fun EventCard(event: SportsEvent, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val p = event.programme
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.015f, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(105.dp)) {
                Text(SportsCalendar.format(p.startAt, "HH:mm"), style = T.Title3)
                val status = when {
                    SportsCalendar.isReplay(p.title) -> "Rediffusion"
                    p.startAt <= now && p.endAt > now -> "À l’antenne"
                    p.endAt <= now -> "Terminé"
                    else -> "À venir"
                }
                Text(status, style = T.Caption)
            }
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(event.sport.label, style = T.Caption, color = LocalContentColor.current.copy(alpha = 0.65f))
                Text(p.title, style = T.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    channelLabel(event.channels.first()) + if (event.channels.size > 1) " · +${event.channels.size - 1} chaîne(s)" else "",
                    style = T.Footnote, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val channel = event.channels.first()
            ChannelLogo(channel.logo, channel.name, Modifier.size(48.dp))
        }
    }
}

private fun channelLabel(channel: ChannelEntity): String {
    val name = channel.name.mediaName()
    return listOfNotNull(name.title, name.tag, name.channelBadge).joinToString(" · ")
}

@Composable
private fun SportsPreferences(selected: Set<Sport>, onToggle: (Sport) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(440.dp).clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(24.dp)) {
            Text("Mes sports", style = T.Title2)
            Text("OK pour sélectionner ou retirer un sport. Tes choix sont enregistrés.", style = T.Subhead, color = C.Text2)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 260.dp), contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(Sport.entries) { index, sport ->
                    SideListItem(
                        sport.label, sport in selected, onClick = { onToggle(sport) },
                        trailing = if (sport in selected) "✓" else "+",
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Terminer", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(Unit) { delay(80); first.tryFocus() }
}

@Composable
private fun BroadcastDialog(event: SportsEvent, now: Long, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(520.dp).clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(24.dp)) {
            Text(event.programme.title, style = T.Title3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                if (event.programme.startAt > now)
                    "Prévu le ${SportsCalendar.format(event.programme.startAt, "d MMM à HH:mm")} (Paris). Ouvrir la chaîne maintenant affiche son programme actuel."
                else "Choisis la chaîne à ouvrir.",
                style = T.Subhead, color = C.Text2,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.heightIn(max = 240.dp), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(event.channels, key = { _, channel -> channel.itemKey }) { index, channel ->
                    SideListItem(channelLabel(channel), false, onClick = { onSelect(index) }, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Fermer", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(Unit) { delay(80); first.tryFocus() }
}
