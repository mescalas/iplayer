package com.iplayer.tv.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.iplayer.tv.BuildConfig
import com.iplayer.tv.data.AppSettings
import com.iplayer.tv.data.AspectMode
import com.iplayer.tv.data.AudioDecoder
import com.iplayer.tv.data.BufferMode
import com.iplayer.tv.data.LiveFormat
import com.iplayer.tv.data.SubtitleStyle
import com.iplayer.tv.data.cycle
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.player.SubtitlePreview
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.LocalShell
import com.iplayer.tv.ui.components.ActionDialog
import com.iplayer.tv.ui.components.DialogAction
import com.iplayer.tv.ui.components.Eyebrow
import com.iplayer.tv.ui.components.IconTile
import com.iplayer.tv.ui.components.SettingsRow
import com.iplayer.tv.ui.components.TextInputDialog
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import com.iplayer.tv.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LANGS = listOf("" to "Automatique", "fr" to "Français", "en" to "Anglais", "es" to "Espagnol", "de" to "Allemand", "it" to "Italien", "ar" to "Arabe", "pt" to "Portugais", "tr" to "Turc")

private fun <T> List<T>.step(current: T, by: Int): T = this[(indexOf(current) + by).mod(size)]

private fun onOff(b: Boolean) = if (b) "Activé" else "Désactivé"

/** What the left half of the screen shows for the focused row. */
private data class Illustration(val icon: ImageVector, val title: String, val text: String, val subtitlePreview: Boolean = false)

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

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

/**
 * Paramètres: one list grouped in sections on the right, the focused row explained on the left
 * (large icon, title, what the setting does; a live preview for the subtitle style).
 */
@Composable
fun SettingsScreen() {
    val container = LocalContainer.current
    val nav = LocalNav.current
    val shell = LocalShell.current
    val repo = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by container.settings.flow.collectAsState()
    val update: ((AppSettings) -> AppSettings) -> Unit = { container.settings.update(it) }
    val playlists by repo.playlists.collectAsState()
    val active by repo.activePlaylist.collectAsState()
    val updater = container.updater
    val updateState by updater.flow.collectAsState()

    var shown by remember { mutableStateOf(Illustration(Icons.AutoMirrored.Rounded.PlaylistPlay, "Sources IPTV", "Vos abonnements Xtream Codes et listes M3U : chaînes, films et séries.")) }
    var menuFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var editUa by remember { mutableStateOf(false) }

    fun ill(icon: ImageVector, title: String, text: String, preview: Boolean = false): () -> Unit = { shown = Illustration(icon, title, text, preview) }

    Row(Modifier.fillMaxSize()) {
        // ---- illustration of the focused row
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 40.dp, end = 30.dp, bottom = 40.dp), contentAlignment = Alignment.Center) {
            Crossfade(shown, animationSpec = tween(160), label = "ill") { il ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (il.subtitlePreview) {
                        SubtitlePreview(s.subtitleStyle, Modifier.width(300.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
                    } else {
                        IconTile(il.icon, size = 75.dp)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(il.title, style = T.Title3.copy(fontSize = T.Title3.fontSize * 1.06f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(il.text, style = T.Subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = T.Subhead.lineHeight * 1.12f), color = C.Text2, textAlign = TextAlign.Center, modifier = Modifier.width(290.dp))
                }
            }
        }

        // ---- the list
        LazyColumn(
            Modifier.width(440.dp).fillMaxHeight().padding(end = 40.dp).focusRestorer(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val p0 = active
            if (p0 != null && p0.isXtream && p0.expiresAt > 0) {
                header("Compte")
                item("account") {
                    val left = (p0.expiresAt - System.currentTimeMillis()) / 86_400_000L
                    SettingsRow(
                        "Abonnement",
                        icon = Icons.Rounded.VerifiedUser,
                        value = (if (left >= 0) "Actif" else "Expiré") + " · jusqu'au " + dateFmt.format(Date(p0.expiresAt)),
                        valueColor = if (left in 0..7) C.Orange else null,
                        onFocused = ill(
                            Icons.Rounded.VerifiedUser, "Abonnement",
                            "« ${p0.name} »" + (if (p0.maxConnections > 0) " · ${p0.maxConnections} connexion${if (p0.maxConnections > 1) "s" else ""} simultanée${if (p0.maxConnections > 1) "s" else ""}" else "") +
                                ". " + (if (left >= 0) "Expire dans $left jour${if (left > 1) "s" else ""}." else "Renouvelez-le auprès de votre fournisseur."),
                        ),
                    ) { menuFor = p0 }
                }
            }

            header("Sources IPTV")
            playlists.orEmpty().forEach { p ->
                item("pl${p.id}") {
                    val isActive = p.id == active?.id
                    SettingsRow(
                        p.name,
                        icon = if (p.isXtream) Icons.Rounded.Dns else Icons.AutoMirrored.Rounded.PlaylistPlay,
                        value = listOfNotNull(if (isActive) "Utilisée" else null, if (p.isXtream) "Xtream Codes" else "M3U").joinToString(" · "),
                        onFocused = ill(
                            if (p.isXtream) Icons.Rounded.Dns else Icons.AutoMirrored.Rounded.PlaylistPlay,
                            if (p.isXtream) "Serveur Xtream Codes" else "Liste de lecture M3U",
                            "Chaînes, films et séries mis à jour ${ago(p.lastSync)}. OK pour utiliser, actualiser, modifier ou supprimer cette source.",
                        ),
                    ) { menuFor = p }
                }
            }
            item("epg") {
                SettingsRow(
                    "Guide des programmes (EPG)",
                    icon = Icons.Rounded.CalendarMonth,
                    value = "Mis à jour " + ago(active?.lastEpgSync ?: 0),
                    onFocused = ill(Icons.Rounded.CalendarMonth, "Guide des programmes", "Le guide TV est actualisé automatiquement. OK pour le mettre à jour maintenant."),
                ) {
                    active?.let { repo.syncInBackground(it.id, epgOnly = true); shell.toast("Mise à jour du guide lancée") }
                }
            }
            item("epgoffset") {
                SettingsRow(
                    "Décalage horaire du guide",
                    icon = Icons.Rounded.Schedule,
                    value = (if (s.epgOffsetHours > 0) "+" else "") + "${s.epgOffsetHours} h",
                    chevron = false,
                    onLeftRight = { d -> update { it.copy(epgOffsetHours = (it.epgOffsetHours + d).coerceIn(-12, 12)) } },
                    onFocused = ill(Icons.Rounded.Schedule, "Décalage horaire du guide", "Corrige un guide décalé par rapport à l'heure réelle. Appliqué à la prochaine mise à jour du guide."),
                ) { update { it.copy(epgOffsetHours = if (it.epgOffsetHours >= 3) -3 else it.epgOffsetHours + 1) } }
            }
            item("add") {
                SettingsRow(
                    "Ajouter une source",
                    icon = Icons.Rounded.AddCircleOutline,
                    value = "Xtream ou M3U",
                    onFocused = ill(Icons.Rounded.AddCircleOutline, "Ajouter une source", "Connectez un abonnement Xtream Codes ou collez l'adresse d'une liste M3U."),
                ) { nav.editPlaylist(0) }
            }

            header("Lecture")
            cycleRow("buffer", "Mémoire tampon", Icons.Rounded.Speed, s.bufferMode.label, "Rapide démarre instantanément, Stable limite les coupures sur les connexions irrégulières.", ::ill) { d ->
                update { it.copy(bufferMode = it.bufferMode.cycle(d)) }
            }
            cycleRow("audio", "Langue audio", Icons.Rounded.GraphicEq, LANGS.firstOrNull { it.first == s.preferredAudioLang }?.second ?: s.preferredAudioLang, "Piste choisie en priorité quand un film ou une chaîne en propose plusieurs.", ::ill) { d ->
                update { st -> st.copy(preferredAudioLang = LANGS.map { it.first }.step(st.preferredAudioLang, d)) }
            }
            cycleRow("subs", "Sous-titres", Icons.Rounded.Subtitles, if (s.preferredSubtitleLang.isEmpty()) "Désactivés" else LANGS.firstOrNull { it.first == s.preferredSubtitleLang }?.second ?: s.preferredSubtitleLang, "Langue des sous-titres activés automatiquement.", ::ill) { d ->
                update { st -> st.copy(preferredSubtitleLang = LANGS.map { it.first }.step(st.preferredSubtitleLang, d)) }
            }
            subtitleRow("subsize", "Taille des sous-titres", Icons.Rounded.FormatSize, s.subtitleStyle.size.label, ::ill) { d -> update { it.copy(subtitleStyle = it.subtitleStyle.copy(size = it.subtitleStyle.size.cycle(d))) } }
            subtitleRow("subcolor", "Couleur des sous-titres", Icons.Rounded.FormatColorText, s.subtitleStyle.color.label, ::ill) { d -> update { it.copy(subtitleStyle = it.subtitleStyle.copy(color = it.subtitleStyle.color.cycle(d))) } }
            subtitleRow("subbg", "Fond des sous-titres", Icons.Rounded.Wallpaper, s.subtitleStyle.background.label, ::ill) { d -> update { it.copy(subtitleStyle = it.subtitleStyle.copy(background = it.subtitleStyle.background.cycle(d))) } }
            subtitleRow("subfont", "Police des sous-titres", Icons.Rounded.FontDownload, s.subtitleStyle.font.label, ::ill) { d -> update { it.copy(subtitleStyle = it.subtitleStyle.copy(font = it.subtitleStyle.font.cycle(d))) } }
            subtitleRow("subpos", "Position des sous-titres", Icons.Rounded.VerticalAlignBottom, s.subtitleStyle.position.label, ::ill) { d -> update { it.copy(subtitleStyle = it.subtitleStyle.copy(position = it.subtitleStyle.position.cycle(d))) } }
            if (s.subtitleStyle != SubtitleStyle()) item("subreset") {
                SettingsRow("Rétablir le style des sous-titres", icon = Icons.Rounded.Restore, chevron = false, onFocused = ill(Icons.Rounded.Subtitles, "Style par défaut", "Texte blanc sur fond translucide, taille moyenne, en bas.", preview = true)) {
                    update { it.copy(subtitleStyle = SubtitleStyle()) }
                }
            }
            cycleRow("aspect", "Format d'image", Icons.Rounded.AspectRatio, s.aspectMode.label, "Format appliqué au démarrage de chaque vidéo ; modifiable pendant la lecture.", ::ill) { d ->
                update { it.copy(aspectMode = it.aspectMode.cycle(d)) }
            }
            toggleRow("next", "Épisode suivant automatique", Icons.Rounded.SkipNext, s.autoNextEpisode, "Enchaîne l'épisode suivant à la fin du générique.", ::ill) { update { it.copy(autoNextEpisode = !it.autoNextEpisode) } }
            cycleRow("decoder", "Décodage audio", Icons.Rounded.Memory, s.audioDecoder.label, "FFmpeg lit AC3, E-AC3, DTS et TrueHD sur tous les appareils.", ::ill) { d ->
                update { it.copy(audioDecoder = it.audioDecoder.cycle(d)) }
            }
            toggleRow("compat", "Mode audio compatible", Icons.Rounded.GraphicEq, s.compatAudio, "Son décodé par l'application (Dolby, DTS…). S'active seul en cas d'erreur de codec.", ::ill) { update { it.copy(compatAudio = !it.compatAudio) } }
            cycleRow("format", "Format des flux en direct", Icons.Rounded.LiveTv, s.liveFormat.label, "Comptes Xtream : MPEG-TS est généralement le plus rapide.", ::ill) { d ->
                update { it.copy(liveFormat = it.liveFormat.cycle(d)) }
            }
            toggleRow("tunnel", "Lecture tunnelisée", Icons.Rounded.Tune, s.tunneling, "Peut améliorer la fluidité sur certains téléviseurs.", ::ill) { update { it.copy(tunneling = !it.tunneling) } }
            item("ua") {
                SettingsRow(
                    "User-Agent",
                    icon = Icons.Rounded.Http,
                    value = s.userAgent.ifBlank { "Par défaut" },
                    onFocused = ill(Icons.Rounded.Http, "User-Agent", "À modifier seulement si votre fournisseur l'exige."),
                ) { editUa = true }
            }

            header("Général")
            toggleRow("preview", "Aperçu vidéo de la chaîne", Icons.Rounded.PictureInPicture, s.livePreview, "Au retour du plein écran, la chaîne continue en fond de l'onglet Télévision.", ::ill) { update { it.copy(livePreview = !it.livePreview) } }
            toggleRow("numbers", "Numéros de chaînes", Icons.Rounded.Numbers, s.showChannelNumbers, "Affiche le numéro devant le nom des chaînes ; tapez un numéro pendant le direct pour y aller.", ::ill) { update { it.copy(showChannelNumbers = !it.showChannelNumbers) } }
            toggleRow("zap", "Zapping haut / bas inversé", Icons.Rounded.SwapVert, s.invertZapping, "Inverse le sens des flèches haut et bas pour changer de chaîne.", ::ill) { update { it.copy(invertZapping = !it.invertZapping) } }
            toggleRow("autoplay", "Démarrer sur la dernière chaîne", Icons.Rounded.PlayCircle, s.autoplayLastChannel, "Lance directement la dernière chaîne regardée à l'ouverture de l'application.", ::ill) { update { it.copy(autoplayLastChannel = !it.autoplayLastChannel) } }
            if (updater.supported) {
                item("update") {
                    val st = updateState
                    val (title, value) = when (st) {
                        is UpdateState.Available -> "Installer la version ${st.release.versionName}" to "Disponible"
                        is UpdateState.Checking -> "Rechercher une mise à jour" to "Vérification…"
                        is UpdateState.UpToDate -> "Rechercher une mise à jour" to "À jour"
                        is UpdateState.Downloading -> "Mise à jour en cours" to "${(st.progress * 100).toInt()} %"
                        is UpdateState.Installing -> "Mise à jour en cours" to "Installation…"
                        is UpdateState.Failed -> (st.release?.let { "Réessayer la mise à jour" } ?: "Rechercher une mise à jour") to "Échec"
                        UpdateState.Idle -> "Rechercher une mise à jour" to null
                    }
                    SettingsRow(
                        title,
                        icon = Icons.Rounded.SystemUpdate,
                        value = value,
                        onFocused = ill(Icons.Rounded.SystemUpdate, "Mises à jour", (st as? UpdateState.Failed)?.message ?: "Télécharge et installe la dernière version de Lucarne."),
                    ) {
                        when (st) {
                            is UpdateState.Available -> updater.install(st.release)
                            is UpdateState.Failed -> st.release?.let { updater.install(it) } ?: updater.check()
                            is UpdateState.Downloading, is UpdateState.Checking -> Unit
                            else -> updater.check()
                        }
                    }
                }
                toggleRow("autoupdate", "Vérifier les mises à jour au démarrage", Icons.Rounded.SystemUpdate, s.autoUpdateCheck, "Propose les nouvelles versions à l'ouverture de l'application.", ::ill) { update { it.copy(autoUpdateCheck = !it.autoUpdateCheck) } }
            }
            item("cache") {
                SettingsRow(
                    "Vider le cache des images",
                    icon = Icons.Rounded.CleaningServices,
                    chevron = false,
                    onFocused = ill(Icons.Rounded.CleaningServices, "Cache des images", "Libère l'espace occupé par les affiches et logos téléchargés."),
                ) {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    // Deleting hundreds of MB of files on the main thread would freeze the app.
                    scope.launch {
                        withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                        shell.toast("Cache vidé")
                    }
                }
            }
            item("about") {
                SettingsRow(
                    "À propos",
                    icon = Icons.Rounded.Info,
                    value = "Version ${BuildConfig.VERSION_NAME}",
                    chevron = false,
                    onFocused = ill(
                        Icons.Rounded.Info, "Lucarne ${BuildConfig.VERSION_NAME}",
                        "Direct : ▲▼ zapper · OK infos puis liste · ◀ liste des chaînes · ▶ ou Menu options · chiffres pour aller à un numéro. " +
                            "Films : OK pause · ◀▶ avancer/reculer · ▼ options. Maintenir OK sur une chaîne ou une affiche : favori.",
                    ),
                ) {}
            }
        }
    }

    menuFor?.let { p ->
        val actions = buildList {
            if (p.id != active?.id) add(DialogAction("Utiliser cette source") { repo.setActive(p.id); menuFor = null })
            add(DialogAction("Actualiser les chaînes et le catalogue") { repo.syncInBackground(p.id); shell.toast("Actualisation lancée"); menuFor = null })
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
                DialogAction("Supprimer", destructive = true) { repo.scope.launch { repo.deletePlaylist(p.id) }; confirmDelete = null },
                DialogAction("Annuler") { confirmDelete = null },
            ),
        ) { confirmDelete = null }
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

private fun LazyListScope.header(text: String) {
    item("h$text") { Eyebrow(text, Modifier.padding(start = 15.dp, top = 12.dp, bottom = 3.dp)) }
}

/** A value changed with OK (next) or ◀ ▶. */
private fun LazyListScope.cycleRow(
    key: String,
    title: String,
    icon: ImageVector,
    value: String,
    text: String,
    ill: (ImageVector, String, String, Boolean) -> () -> Unit,
    onStep: (Int) -> Unit,
) {
    item(key) {
        SettingsRow(title, icon = icon, value = value, chevron = false, onLeftRight = onStep, onFocused = ill(icon, title, text, false)) { onStep(1) }
    }
}

private fun LazyListScope.subtitleRow(
    key: String,
    title: String,
    icon: ImageVector,
    value: String,
    ill: (ImageVector, String, String, Boolean) -> () -> Unit,
    onStep: (Int) -> Unit,
) {
    item(key) {
        SettingsRow(title, icon = icon, value = value, chevron = false, onLeftRight = onStep, onFocused = ill(icon, title, "◀ ▶ pour ajuster. Réglable aussi pendant la lecture, dans le panneau Audio et sous-titres.", true)) { onStep(1) }
    }
}

private fun LazyListScope.toggleRow(
    key: String,
    title: String,
    icon: ImageVector,
    on: Boolean,
    text: String,
    ill: (ImageVector, String, String, Boolean) -> () -> Unit,
    onToggle: () -> Unit,
) {
    item(key) {
        SettingsRow(title, icon = icon, value = onOff(on), chevron = false, onFocused = ill(icon, title, text, false), onClick = onToggle)
    }
}
