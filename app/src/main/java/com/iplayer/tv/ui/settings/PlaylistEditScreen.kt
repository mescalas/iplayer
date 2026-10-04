package com.iplayer.tv.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.iplayer.tv.ui.components.RowShape
import com.iplayer.tv.ui.components.Wordmark
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iplayer.tv.R
import com.iplayer.tv.data.SyncState
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.PlaylistType
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.Loading
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.TextInputDialog
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Field(val title: String, val hint: String?, val keyboard: KeyboardType = KeyboardType.Text) {
    NAME("Nom", "Le nom affiché dans l'application."),
    SERVER("Adresse du serveur", "Exemple : http://monserveur.com:8080", KeyboardType.Uri),
    USER("Nom d'utilisateur", null),
    PASS("Mot de passe", null, KeyboardType.Password),
    URL("URL de la playlist M3U", "Lien http(s) vers votre fichier .m3u / .m3u8", KeyboardType.Uri),
    EPG("URL du guide TV (facultatif)", "Laisser vide pour utiliser le guide fourni par le serveur.", KeyboardType.Uri),
    UA("User-Agent (facultatif)", "Laisser vide sauf demande de votre fournisseur."),
}

@Composable
fun PlaylistEditScreen(id: Long) {
    val container = LocalContainer.current
    val nav = LocalNav.current
    val repo = container.repository
    val scope = rememberCoroutineScope()

    var type by rememberSaveable { mutableStateOf(PlaylistType.XTREAM) }
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var ua by rememberSaveable { mutableStateOf("") }
    var existing by remember { mutableStateOf<PlaylistEntity?>(null) }
    var editing by remember { mutableStateOf<Field?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    val syncState by repo.syncState.collectAsState()

    LaunchedEffect(id) {
        if (id > 0) {
            repo.playlists.value?.firstOrNull { it.id == id }?.let { p ->
                existing = p
                type = p.type
                name = p.name
                if (p.isXtream) { server = p.url; user = p.username; pass = p.password } else url = p.url
                epg = p.epgUrl
                ua = p.userAgent
            }
        }
        delay(60)
        firstFocus.tryFocus()
    }

    BackHandler(enabled = !syncing) { nav.back() }

    fun save() {
        error = null
        val xt = type == PlaylistType.XTREAM
        if (xt && (server.isBlank() || user.isBlank() || pass.isBlank())) {
            error = "Renseignez l'adresse du serveur, le nom d'utilisateur et le mot de passe."
            return
        }
        if (!xt && url.isBlank()) {
            error = "Renseignez l'URL de la playlist."
            return
        }
        val base = existing ?: PlaylistEntity(name = "", type = type, url = "")
        val entity = base.copy(
            name = name.ifBlank { if (xt) "Mon IPTV" else "Ma playlist" },
            type = type,
            url = if (xt) server.trim() else url.trim(),
            username = if (xt) user.trim() else "",
            password = if (xt) pass.trim() else "",
            epgUrl = epg.trim(),
            userAgent = ua.trim(),
        )
        syncing = true
        scope.launch {
            val pid = try {
                repo.addPlaylist(entity)
            } catch (e: Exception) {
                syncing = false
                error = e.message ?: "Erreur inattendue."
                return@launch
            }
            val ok = repo.sync(pid)
            syncing = false
            if (ok) nav.backToMain()
            else error = (repo.syncState.value as? SyncState.Failed)?.message ?: "Le chargement a échoué."
        }
    }

    Box(Modifier.fillMaxSize().background(C.Background)) {
        if (syncing) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Loading(message = (syncState as? SyncState.Running)?.message ?: "Connexion…")
                Spacer(Modifier.height(10.dp))
                Text("Le premier chargement peut prendre un peu de temps.", style = T.Footnote, color = C.Text3)
            }
            return@Box
        }
        Column(
            Modifier.align(Alignment.TopCenter).width(640.dp).verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 40.dp)
        ) {
            Wordmark(color = C.Gold)
            Spacer(Modifier.height(6.dp))
            Text(if (existing != null) "Modifier le compte" else "Ajouter un compte", style = T.Display.copy(fontSize = 36.sp, lineHeight = 40.sp))
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0x14FFFFFF)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Segment("Xtream Codes", type == PlaylistType.XTREAM, Modifier.focusRequester(firstFocus)) { type = PlaylistType.XTREAM }
                Segment("Playlist M3U", type == PlaylistType.M3U) { type = PlaylistType.M3U }
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldRow(Field.NAME, name) { editing = Field.NAME }
                if (type == PlaylistType.XTREAM) {
                    FieldRow(Field.SERVER, server) { editing = Field.SERVER }
                    FieldRow(Field.USER, user) { editing = Field.USER }
                    FieldRow(Field.PASS, pass, secret = true) { editing = Field.PASS }
                } else {
                    FieldRow(Field.URL, url) { editing = Field.URL }
                }
                FieldRow(Field.EPG, epg) { editing = Field.EPG }
                FieldRow(Field.UA, ua) { editing = Field.UA }
            }
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = C.Red)
                    Spacer(Modifier.width(8.dp))
                    Text(error ?: "", style = T.Subhead, color = C.Red)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(if (existing != null) "Enregistrer et actualiser" else "Se connecter", onClick = { save() }, primary = true)
                PillButton("Annuler", onClick = { nav.back() })
            }
        }
    }

    editing?.let { f ->
        val current = when (f) {
            Field.NAME -> name; Field.SERVER -> server; Field.USER -> user; Field.PASS -> pass
            Field.URL -> url; Field.EPG -> epg; Field.UA -> ua
        }
        TextInputDialog(f.title, current, f.hint, f.keyboard, onDone = { v ->
            when (f) {
                Field.NAME -> name = v; Field.SERVER -> server = v; Field.USER -> user = v; Field.PASS -> pass = v
                Field.URL -> url = v; Field.EPG -> epg = v; Field.UA -> ua = v
            }
            editing = null
        }, onDismiss = { editing = null })
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.width(200.dp).height(42.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0x33FFFFFF) else Color.Transparent,
        contentColor = if (selected) C.Text else C.Text2,
        focusedScale = 1.04f,
        contentAlignment = Alignment.Center,
    ) { Text(label.uppercase(java.util.Locale.FRENCH), style = T.Label.copy(fontSize = 12.sp)) }
}

@Composable
private fun FieldRow(field: Field, value: String, secret: Boolean = false, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RowShape,
        color = Color(0x12FFFFFF),
        focusedScale = 1.02f,
        elevation = 10.dp,
    ) {
        val content = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(field.title, style = T.Headline, modifier = Modifier.width(250.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    value.isBlank() -> "Non renseigné"
                    secret -> "•".repeat(value.length.coerceAtMost(12))
                    else -> value
                },
                style = T.Callout,
                color = content.copy(alpha = if (value.isBlank()) 0.4f else 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun WelcomeScreen() {
    val nav = LocalNav.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focus.tryFocus()
    }
    Box(Modifier.fillMaxSize().background(C.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(28.dp))
            Wordmark(color = C.Gold)
            Spacer(Modifier.height(8.dp))
            Text("Bienvenue sur iPlayer", style = T.Display)
            Spacer(Modifier.height(10.dp))
            Text(
                "Connectez votre abonnement Xtream Codes ou une playlist M3U\npour retrouver vos chaînes, films et séries.",
                style = T.Body,
                color = C.Text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            PillButton("Ajouter un compte", onClick = { nav.editPlaylist(0) }, icon = Icons.Rounded.Add, primary = true, modifier = Modifier.focusRequester(focus))
        }
    }
}
