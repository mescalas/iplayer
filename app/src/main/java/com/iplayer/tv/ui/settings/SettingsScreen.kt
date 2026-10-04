package com.iplayer.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.iplayer.tv.BuildConfig
import com.iplayer.tv.data.AppSettings
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.AudioDecoder
import com.iplayer.tv.data.BufferMode
import com.iplayer.tv.data.LiveFormat
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.components.ActionDialog
import com.iplayer.tv.ui.components.DialogAction
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.SideListItem
import com.iplayer.tv.ui.components.TextInputDialog
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Section(val label: String, val icon: ImageVector) {
    PLAYLISTS("Comptes & playlists", Icons.Rounded.ViewList),
    PLAYBACK("Lecture", Icons.Rounded.PlayCircle),
    GUIDE("Guide TV", Icons.Rounded.CalendarMonth),
    INTERFACE("Interface", Icons.Rounded.Tune),
    ABOUT("À propos", Icons.Rounded.Info),
}

private val LANGS = listOf("" to "Automatique", "fr" to "Français", "en" to "Anglais", "es" to "Espagnol", "de" to "Allemand", "it" to "Italien", "ar" to "Arabe", "pt" to "Portugais", "tr" to "Turc")

private fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1) % size]

@Composable
fun SettingsScreen() {
    var section by rememberSaveable { mutableStateOf(Section.PLAYLISTS) }
    Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 48.dp, top = 6.dp)) {
        Column(Modifier.width(250.dp).fillMaxHeight().focusRestorer(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Section.entries.forEach { s ->
                SideListItem(s.label, selected = s == section, icon = s.icon, onClick = { section = s }, onFocused = { section = s })
            }
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                Section.PLAYLISTS -> PlaylistsSection()
                Section.PLAYBACK -> PlaybackSection()
                Section.GUIDE -> GuideSection()
                Section.INTERFACE -> InterfaceSection()
                Section.ABOUT -> AboutSection()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (subtitle != null) 64.dp else 52.dp),
        shape = RoundedCornerShape(12.dp),
        color = C.Surface,
        focusedScale = 1.02f,
        elevation = 10.dp,
    ) {
        val content = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = T.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = T.Footnote, color = content.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, style = T.Callout, color = content.copy(alpha = 0.6f), maxLines = 1)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp), tint = content.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 6.dp, end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = T.Footnote, color = C.Text3, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun rememberSettings(): Pair<AppSettings, ((AppSettings) -> AppSettings) -> Unit> {
    val container = LocalContainer.current
    val s by container.settings.flow.collectAsState()
    val update: ((AppSettings) -> AppSettings) -> Unit = { container.settings.update(it) }
    return s to update
}

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)

private fun ago(ts: Long): String {
    if (ts <= 0) return "jamais"
    val min = (System.currentTimeMillis() - ts) / 60000
    return when {
        min < 1 -> "à l'instant"
        min < 60 -> "il y a $min min"
        min < 1440 -> "il y a ${min / 60} h"
        else -> "il y a ${min / 1440} j"
    }
}

@Composable
private fun PlaylistsSection() {
    val container = LocalContainer.current
    val nav = LocalNav.current
    val shell = LocalShell.current
    val repo = container.repository
    val playlists by repo.playlists.collectAsState()
    val active by repo.activePlaylist.collectAsState()
    var menuFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    val scope = rememberCoroutineScope()

    SettingsList {
        items(playlists.orEmpty(), key = { it.id }) { p ->
            val parts = mutableListOf(if (p.isXtream) "Xtream Codes" else "M3U")
            if (p.expiresAt > 0) parts += "expire le " + dateFmt.format(Date(p.expiresAt))
            parts += "mis à jour " + ago(p.lastSync)
            SettingRow(
                title = p.name,
                subtitle = parts.joinToString(" · "),
                icon = if (p.id == active?.id) Icons.Rounded.CheckCircle else null,
                chevron = true,
                onClick = { menuFor = p },
            )
        }
        item { SettingRow("Ajouter un compte ou une playlist", icon = Icons.Rounded.Add, onClick = { nav.editPlaylist(0) }) }
    }

    menuFor?.let { p ->
        val actions = buildList {
            if (p.id != active?.id) add(DialogAction("Utiliser ce compte") { repo.setActive(p.id); menuFor = null })
            add(DialogAction("Actualiser les chaînes et VOD") { repo.syncInBackground(p.id); shell.toast("Actualisation lancée"); menuFor = null })
            add(DialogAction("Actualiser le guide TV") { repo.syncInBackground(p.id, epgOnly = true); shell.toast("Mise à jour du guide lancée"); menuFor = null })
            add(DialogAction("Modifier") { menuFor = null; nav.editPlaylist(p.id) })
            add(DialogAction("Supprimer", destructive = true) { menuFor = null; confirmDelete = p })
        }
        ActionDialog(p.name, null, actions) { menuFor = null }
    }
    confirmDelete?.let { p ->
        ActionDialog(
            "Supprimer « ${p.name} » ?",
            "Les favoris et l'historique associés seront effacés.",
            listOf(
                DialogAction("Supprimer", destructive = true) { scope.launch { repo.deletePlaylist(p.id) }; confirmDelete = null },
                DialogAction("Annuler") { confirmDelete = null },
            ),
        ) { confirmDelete = null }
    }
}

@Composable
private fun PlaybackSection() {
    val (s, update) = rememberSettings()
    var editUa by remember { mutableStateOf(false) }
    SettingsList {
        item { GroupTitle("PERFORMANCES") }
        item {
            SettingRow("Mémoire tampon", s.bufferMode.label, "Rapide = démarrage instantané, Stable = moins de coupures") {
                update { it.copy(bufferMode = BufferMode.entries.after(it.bufferMode)) }
            }
        }
        item {
            SettingRow("Décodage audio", s.audioDecoder.label, "FFmpeg lit AC3, E-AC3, DTS, TrueHD sur tous les appareils") {
                update { it.copy(audioDecoder = AudioDecoder.entries.after(it.audioDecoder)) }
            }
        }
        item {
            SettingRow("Format des flux en direct", s.liveFormat.label, "Comptes Xtream : MPEG-TS est généralement le plus rapide") {
                update { it.copy(liveFormat = LiveFormat.entries.after(it.liveFormat)) }
            }
        }
        item {
            SettingRow("Lecture tunnelisée", if (s.tunneling) "Activée" else "Désactivée", "Peut améliorer la fluidité sur certains téléviseurs") {
                update { it.copy(tunneling = !it.tunneling) }
            }
        }
        item { GroupTitle("IMAGE & SON") }
        item { SettingRow("Format d'image par défaut", s.aspectMode.label) { update { it.copy(aspectMode = AspectMode.entries.after(it.aspectMode)) } } }
        item {
            SettingRow("Langue audio préférée", LANGS.firstOrNull { it.first == s.preferredAudioLang }?.second ?: s.preferredAudioLang) {
                update { st -> st.copy(preferredAudioLang = LANGS.map { it.first }.after(st.preferredAudioLang)) }
            }
        }
        item {
            SettingRow("Sous-titres préférés", LANGS.firstOrNull { it.first == s.preferredSubtitleLang }?.second?.let { if (s.preferredSubtitleLang.isEmpty()) "Aucun" else it } ?: s.preferredSubtitleLang) {
                update { st -> st.copy(preferredSubtitleLang = LANGS.map { it.first }.after(st.preferredSubtitleLang)) }
            }
        }
        item { GroupTitle("FILMS & SÉRIES") }
        item { SettingRow("Épisode suivant automatique", if (s.autoNextEpisode) "Activé" else "Désactivé") { update { it.copy(autoNextEpisode = !it.autoNextEpisode) } } }
        item { GroupTitle("RÉSEAU") }
        item { SettingRow("User-Agent", s.userAgent.ifBlank { "Par défaut" }, "À modifier seulement si votre fournisseur l'exige") { editUa = true } }
    }
    if (editUa) {
        TextInputDialog(
            "User-Agent",
            s.userAgent,
            "Laisser vide pour utiliser la valeur par défaut.",
            onDone = { v -> update { it.copy(userAgent = v) }; editUa = false },
            onDismiss = { editUa = false },
        )
    }
}

@Composable
private fun GuideSection() {
    val (s, update) = rememberSettings()
    val container = LocalContainer.current
    val shell = LocalShell.current
    val active by container.repository.activePlaylist.collectAsState()
    SettingsList {
        item {
            SettingRow("Actualiser le guide maintenant", subtitle = "Dernière mise à jour " + ago(active?.lastEpgSync ?: 0)) {
                active?.let { container.repository.syncInBackground(it.id, epgOnly = true); shell.toast("Mise à jour du guide lancée") }
            }
        }
        item {
            SettingRow(
                "Décalage horaire du guide",
                (if (s.epgOffsetHours > 0) "+" else "") + "${s.epgOffsetHours} h",
                "Corrige un guide décalé (appliqué à la prochaine mise à jour)",
            ) {
                update { it.copy(epgOffsetHours = if (it.epgOffsetHours >= 3) -3 else it.epgOffsetHours + 1) }
            }
        }
    }
}

@Composable
private fun InterfaceSection() {
    val (s, update) = rememberSettings()
    SettingsList {
        item { SettingRow("Aperçu vidéo dans la liste des chaînes", if (s.livePreview) "Activé" else "Désactivé", "La chaîne continue dans la mini-fenêtre au retour") { update { it.copy(livePreview = !it.livePreview) } } }
        item { SettingRow("Numéros de chaînes", if (s.showChannelNumbers) "Affichés" else "Masqués") { update { it.copy(showChannelNumbers = !it.showChannelNumbers) } } }
        item { SettingRow("Zapping haut / bas inversé", if (s.invertZapping) "Activé" else "Désactivé") { update { it.copy(invertZapping = !it.invertZapping) } } }
        item { SettingRow("Démarrer sur la dernière chaîne", if (s.autoplayLastChannel) "Activé" else "Désactivé", "Lance directement la TV à l'ouverture de l'app") { update { it.copy(autoplayLastChannel = !it.autoplayLastChannel) } } }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val shell = LocalShell.current
    SettingsList {
        item { SettingRow("Version", BuildConfig.VERSION_NAME) {} }
        item {
            SettingRow("Vider le cache des images", subtitle = "Libère de l'espace de stockage") {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                shell.toast("Cache vidé")
            }
        }
        item {
            Text(
                "Raccourcis télécommande — Direct : ▲▼ zapper · OK infos puis liste · ◀ liste des chaînes · ▶ ou Menu options · chiffres pour aller à un numéro. " +
                    "Films : OK pause · ◀▶ avancer/reculer (maintenir pour accélérer) · ▼ options. Maintenir OK sur une chaîne ou une affiche : favori.",
                style = T.Subhead,
                color = C.Text3,
                modifier = Modifier.padding(start = 6.dp, top = 12.dp, end = 40.dp),
            )
        }
    }
}
