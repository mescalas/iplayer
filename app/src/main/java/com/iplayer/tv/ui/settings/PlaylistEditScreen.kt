package com.iplayer.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iplayer.tv.data.LibraryCounts
import com.iplayer.tv.data.SyncState
import com.iplayer.tv.data.db.PlaylistEntity
import com.iplayer.tv.data.db.PlaylistType
import com.iplayer.tv.ui.LocalContainer
import com.iplayer.tv.ui.LocalNav
import com.iplayer.tv.ui.components.BackPill
import com.iplayer.tv.ui.components.FocusSurface
import com.iplayer.tv.ui.components.IconTile
import com.iplayer.tv.ui.components.LogoLockup
import com.iplayer.tv.ui.components.LogoMark
import com.iplayer.tv.ui.components.PillButton
import com.iplayer.tv.ui.components.ProgressLine
import com.iplayer.tv.ui.components.ScreenEdge
import com.iplayer.tv.ui.components.TextInputDialog
import com.iplayer.tv.ui.components.tryFocus
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Field(val title: String, val placeholder: String, val keyboard: KeyboardType = KeyboardType.Text, val shortcuts: List<String> = emptyList()) {
    NAME("Nom de la source", "Mon fournisseur"),
    SERVER("Adresse du serveur", "http://monserveur.com:8080", KeyboardType.Uri, listOf("http://", "https://", ".", "/", ":8080", ".tv", ".com")),
    USER("Identifiant", "Votre identifiant"),
    PASS("Mot de passe", "Votre mot de passe", KeyboardType.Password),
    URL("Adresse de la playlist (.m3u)", "https://…/get.php?username=…&type=m3u_plus", KeyboardType.Uri, listOf("https://", "http://", ".", "/", "?", "&", "=", "get.php")),
    EPG("Guide des programmes XMLTV · facultatif", "Laisser vide : guide fourni par le serveur", KeyboardType.Uri, listOf("https://", "http://", ".", "/", "?", "&", "=", "xmltv.php")),
    UA("User-Agent · facultatif", "Par défaut"),
}

private enum class Step { TYPE, FORM, IMPORT, DONE }

private enum class KeyMode(val label: String, val keys: List<String>) {
    LOWER("abc", "abcdefghijklmnopqrstuvwxyz".map { it.toString() }),
    UPPER("ABC", "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() }),
    DIGITS("123", "1234567890".map { it.toString() }),
    SYMBOLS("#+=", listOf(".", "/", ":", "-", "_", "@", "?", "&", "=", "%", "+", "~", "#", "!", "*", "$")),
}

/** Import steps shown while the source loads, in the order the repository reports them. */
private val IMPORT_STEPS = listOf(
    "Connexion au serveur" to listOf("Connexion", "Téléchargement"),
    "Chaînes TV" to listOf("Chargement des chaînes", "Analyse"),
    "Films" to listOf("Chargement des films"),
    "Séries" to listOf("Chargement des séries", "Enregistrement"),
)

/**
 * "Ajouter une source IPTV": type → identifiers (on-screen keyboard) → import → ready.
 * Editing an existing source starts on the form.
 */
@Composable
fun PlaylistEditScreen(id: Long) {
    val container = LocalContainer.current
    val nav = LocalNav.current
    val repo = container.repository

    var step by rememberSaveable { mutableStateOf(if (id > 0) Step.FORM else Step.TYPE) }
    var type by rememberSaveable { mutableStateOf(PlaylistType.XTREAM) }
    var values by remember { mutableStateOf(mapOf<Field, String>()) }
    var existing by remember { mutableStateOf<PlaylistEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedId by remember { mutableStateOf(0L) }
    var counts by remember { mutableStateOf<LibraryCounts?>(null) }
    val syncState by repo.syncState.collectAsState()

    LaunchedEffect(id) {
        if (id > 0) {
            repo.playlists.value?.firstOrNull { it.id == id }?.let { p ->
                existing = p
                type = p.type
                values = mapOf(
                    Field.NAME to p.name,
                    Field.SERVER to if (p.isXtream) p.url else "",
                    Field.USER to p.username,
                    Field.PASS to p.password,
                    Field.URL to if (!p.isXtream) p.url else "",
                    Field.EPG to p.epgUrl,
                    Field.UA to p.userAgent,
                )
            }
        }
    }

    fun v(f: Field) = values[f].orEmpty()

    fun save() {
        error = null
        val xt = type == PlaylistType.XTREAM
        if (xt && (v(Field.SERVER).isBlank() || v(Field.USER).isBlank() || v(Field.PASS).isBlank())) {
            error = "Renseignez l'adresse du serveur, l'identifiant et le mot de passe."
            return
        }
        if (!xt && v(Field.URL).isBlank()) {
            error = "Renseignez l'adresse de la playlist."
            return
        }
        val base = existing ?: PlaylistEntity(name = "", type = type, url = "")
        val entity = base.copy(
            name = v(Field.NAME).ifBlank { if (xt) "Mon fournisseur" else "Ma playlist" },
            type = type,
            url = if (xt) v(Field.SERVER).trim() else v(Field.URL).trim(),
            username = if (xt) v(Field.USER).trim() else "",
            password = if (xt) v(Field.PASS).trim() else "",
            epgUrl = v(Field.EPG).trim(),
            userAgent = v(Field.UA).trim(),
        )
        step = Step.IMPORT
        // The app scope: the import goes on if the user leaves to watch TV meanwhile.
        repo.scope.launch {
            val pid = try {
                repo.addPlaylist(entity)
            } catch (e: Exception) {
                error = e.message ?: "Erreur inattendue."
                step = Step.FORM
                return@launch
            }
            savedId = pid
            val ok = repo.sync(pid)
            if (ok) {
                counts = repo.libraryCounts(pid)
                step = Step.DONE
            } else {
                error = (repo.syncState.value as? SyncState.Failed)?.message ?: "Le chargement a échoué."
                step = Step.FORM
            }
        }
    }

    BackHandler {
        when (step) {
            Step.FORM -> if (id > 0) nav.back() else step = Step.TYPE
            Step.IMPORT -> nav.backToMain()
            Step.DONE -> nav.backToMain()
            Step.TYPE -> nav.back()
        }
    }

    val totalSteps = 3
    val stepIndex = when (step) { Step.TYPE -> 0; Step.FORM -> 1; else -> 2 }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16161A), C.Background)))) {
        // ---- top: back, step dots, step label
        Box(Modifier.fillMaxWidth().padding(horizontal = ScreenEdge, vertical = 20.dp)) {
            if (step == Step.TYPE || step == Step.FORM) {
                BackPill(if (step == Step.FORM && id == 0L) "Type de source" else "Paramètres", onClick = {
                    if (step == Step.FORM && id == 0L) step = Step.TYPE else nav.back()
                })
            }
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(totalSteps) { i ->
                    Box(Modifier.size(width = if (i == stepIndex) 10.dp else 3.dp, height = 3.dp).clip(CircleShape).background(if (i == stepIndex) C.Text else Color(0x4DFFFFFF)))
                }
            }
            Text(
                if (step == Step.DONE) "Terminé" else "Étape ${stepIndex + 1} sur $totalSteps",
                style = T.Subhead.copy(fontWeight = FontWeight.Normal),
                color = C.Text2,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        when (step) {
            Step.TYPE -> TypeStep { t -> type = t; step = Step.FORM }
            Step.FORM -> FormStep(
                type = type,
                editing = existing != null,
                values = values,
                error = error,
                onChange = { f, s -> values = values + (f to s) },
                onSubmit = { save() },
            )
            Step.IMPORT -> ImportStep(
                type = type,
                name = v(Field.NAME).ifBlank { if (type == PlaylistType.XTREAM) "Mon fournisseur" else "Ma playlist" },
                host = (if (type == PlaylistType.XTREAM) v(Field.SERVER) else v(Field.URL)).substringAfter("://").substringBefore('/').substringBefore(':'),
                message = (syncState as? SyncState.Running)?.takeIf { it.playlistId == savedId || savedId == 0L }?.message,
                onWatch = { nav.backToMain() },
            )
            Step.DONE -> DoneStep(
                name = v(Field.NAME).ifBlank { if (type == PlaylistType.XTREAM) "Mon fournisseur" else "Ma playlist" },
                counts = counts,
                onStart = { nav.backToMain() },
                onAnother = {
                    values = emptyMap()
                    existing = null
                    error = null
                    step = Step.TYPE
                },
            )
        }
    }
}

// ============================================================================ step 1

@Composable
private fun TypeStep(onPick: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); first.tryFocus() }
    Column(Modifier.fillMaxSize().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ajouter une source IPTV", style = T.LargeTitle)
        Spacer(Modifier.height(10.dp))
        Text(
            "Choisissez comment vous connecter à votre fournisseur. Vous pourrez ajouter d'autres sources plus tard.",
            style = T.Body,
            color = C.Text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(440.dp),
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceCard(
                Icons.Rounded.Dns, "Xtream Codes", "Serveur, identifiant et mot de passe fournis par votre abonnement.",
                listOf("Chaînes", "Films", "Séries", "EPG"), recommended = true, modifier = Modifier.focusRequester(first),
            ) { onPick(PlaylistType.XTREAM) }
            SourceCard(
                Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlist M3U", "Collez l'adresse d'une liste .m3u ou .m3u8, avec un guide XMLTV optionnel.",
                listOf("Chaînes", "Films"),
            ) { onPick(PlaylistType.M3U) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(9.dp), tint = C.Text3)
            Spacer(Modifier.width(5.dp))
            Text("Vos identifiants restent sur cet appareil.", style = T.Caption, color = C.Text3)
        }
    }
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    title: String,
    text: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
    recommended: Boolean = false,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width = 230.dp, height = 210.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E22),
        focusedColor = Color(0xFF2E2E33),
        focusedContentColor = C.Text,
        focusedScale = 1.06f,
        elevation = 28.dp,
    ) { focused ->
        Box(Modifier.fillMaxSize().then(if (focused) Modifier.border(1.dp, C.Text, RoundedCornerShape(14.dp)) else Modifier))
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(icon, size = 42.dp, light = focused)
                Spacer(Modifier.weight(1f))
                if (recommended) {
                    Text(
                        "Recommandé",
                        style = T.Micro.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                        color = C.Green,
                        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0x2630D158)).padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = T.Title2)
            Spacer(Modifier.height(8.dp))
            Text(text, style = T.Subhead.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp), color = C.Text2, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach {
                    Text(
                        it,
                        style = T.Micro.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                        color = C.Text2,
                        modifier = Modifier.border(0.5.dp, Color(0x33FFFFFF), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ============================================================================ step 2

@Composable
private fun FormStep(
    type: String,
    editing: Boolean,
    values: Map<Field, String>,
    error: String?,
    onChange: (Field, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val xt = type == PlaylistType.XTREAM
    var showAdvanced by remember { mutableStateOf(!values[Field.UA].isNullOrBlank() || (xt && !values[Field.EPG].isNullOrBlank())) }
    val fields = buildList {
        add(Field.NAME)
        if (xt) { add(Field.SERVER); add(Field.USER); add(Field.PASS) } else { add(Field.URL); add(Field.EPG) }
        if (showAdvanced) { if (xt) add(Field.EPG); add(Field.UA) }
    }
    var active by remember { mutableStateOf(if (xt) Field.SERVER else Field.URL) }
    var systemEdit by remember { mutableStateOf<Field?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(KeyMode.LOWER) }
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); firstField.tryFocus() }

    fun value(f: Field) = values[f].orEmpty()
    fun type(s: String) = onChange(active, value(active) + s)

    Column(Modifier.fillMaxSize().padding(top = 64.dp)) {
        Row(Modifier.weight(1f).padding(horizontal = ScreenEdge)) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        editing -> "Modifier la source"
                        xt -> "Connexion Xtream Codes"
                        else -> "Playlist M3U"
                    },
                    style = T.Title1.copy(fontSize = 23.sp),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (xt) "Ces informations figurent dans l'e-mail de votre fournisseur."
                    else "Collez l'adresse fournie par votre fournisseur. Le guide TV est facultatif mais recommandé.",
                    style = T.Subhead.copy(fontWeight = FontWeight.Normal),
                    color = C.Text2,
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    fields.forEachIndexed { i, f ->
                        InputField(
                            field = f,
                            value = value(f),
                            active = f == active,
                            secret = f == Field.PASS && !reveal,
                            valid = when (f) {
                                Field.NAME -> value(f).isNotBlank()
                                Field.SERVER, Field.URL, Field.EPG -> value(f).let { it.startsWith("http://") || it.startsWith("https://") } && value(f).length > 10 && f != active
                                Field.USER -> value(f).isNotBlank() && f != active
                                else -> false
                            },
                            trailingIcon = if (f == Field.PASS) (if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility) else null,
                            onTrailing = { reveal = !reveal },
                            modifier = if (i == 0) Modifier.focusRequester(firstField) else Modifier,
                            onFocused = { active = f },
                            onClick = { systemEdit = f },
                        )
                    }
                    if (!showAdvanced) {
                        FocusSurface(
                            onClick = { showAdvanced = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = C.Row,
                            focusedScale = 1.02f,
                        ) {
                            val content = LocalContentColor.current
                            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp + 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Options avancées", style = T.Caption.copy(fontWeight = FontWeight.SemiBold), color = content.copy(alpha = 0.6f))
                                    Text(if (xt) "Guide XMLTV, User-Agent" else "User-Agent", style = T.Subhead.copy(fontWeight = FontWeight.Normal), color = content.copy(alpha = 0.8f))
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(12.dp), tint = content.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(12.dp), tint = C.LiveText)
                        Spacer(Modifier.width(6.dp))
                        Text(error, style = T.Callout, color = C.LiveText, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            // Right card: the remote is slow for long URLs, the system keyboard and voice help.
            Column(
                Modifier
                    .width(250.dp)
                    .padding(top = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E22))
                    .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Plus simple au clavier", style = T.Headline.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                Spacer(Modifier.height(12.dp))
                IconTile(Icons.Rounded.Keyboard, size = 60.dp, light = true)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Appuyez sur OK sur un champ pour utiliser le clavier du système ou la dictée vocale de la télécommande.",
                    style = T.Caption.copy(lineHeight = 13.sp),
                    color = C.Text2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // ---- on-screen keyboard: types into the field that has (or last had) focus
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenEdge, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                KeyIcon(Icons.Rounded.SpaceBar) { type(" ") }
                mode.keys.forEach { k -> Key(k) { type(k) } }
                KeyIcon(Icons.AutoMirrored.Rounded.Backspace, onLongClick = { onChange(active, "") }) { onChange(active, value(active).dropLast(1)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).background(C.Glass).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    KeyMode.entries.forEach { m ->
                        FocusSurface(
                            onClick = { mode = m },
                            modifier = Modifier.height(20.dp),
                            shape = CircleShape,
                            color = if (m == mode) C.GlassStrong else Color.Transparent,
                            contentColor = if (m == mode) C.Text else C.Text2,
                            focusedScale = 1.06f,
                            contentAlignment = Alignment.Center,
                        ) { Text(m.label, style = T.Caption.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 10.dp)) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    active.shortcuts.forEach { sc ->
                        FocusSurface(
                            onClick = { type(sc) },
                            modifier = Modifier.height(20.dp),
                            shape = RoundedCornerShape(5.dp),
                            color = Color.Transparent,
                            focusedScale = 1.08f,
                            contentAlignment = Alignment.Center,
                        ) { focused ->
                            Text(
                                sc,
                                style = T.Caption.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier
                                    .then(if (!focused) Modifier.border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(5.dp)) else Modifier)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                PillButton(if (editing) "Enregistrer" else "Suivant", onClick = onSubmit, leading = null, icon = Icons.AutoMirrored.Rounded.ArrowForward)
            }
        }
    }

    systemEdit?.let { f ->
        TextInputDialog(f.title.substringBefore(" ·"), value(f), f.placeholder, f.keyboard, onDone = { s ->
            onChange(f, s)
            systemEdit = null
        }, onDismiss = { systemEdit = null })
    }
}

@Composable
private fun InputField(
    field: Field,
    value: String,
    active: Boolean,
    secret: Boolean,
    valid: Boolean,
    trailingIcon: ImageVector?,
    onTrailing: () -> Unit,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = if (trailingIcon != null) onTrailing else null,
        modifier = modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = C.Row,
        focusedColor = Color(0x1AFFFFFF),
        focusedContentColor = C.Text,
        focusedScale = 1.015f,
        elevation = 0.dp,
        onFocusChange = { if (it) onFocused() },
    ) { focused ->
        Box(Modifier.fillMaxSize().then(if (focused || active) Modifier.border(0.75.dp, if (focused) C.Text else Color(0x66F5F5F7), RoundedCornerShape(8.dp)) else Modifier))
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(field.title, style = T.Caption.copy(fontWeight = FontWeight.SemiBold), color = C.Text3, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            value.isEmpty() -> field.placeholder
                            secret -> "•".repeat(value.length.coerceAtMost(24))
                            else -> value
                        },
                        style = T.Headline.copy(fontWeight = FontWeight.Normal),
                        color = if (value.isEmpty()) C.Text3 else C.Text,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier.weight(1f, false),
                    )
                    if (active) Box(Modifier.padding(start = 1.dp).width(1.dp).height(13.dp).background(C.Text))
                }
            }
            if (valid) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(11.dp), tint = C.Green)
            if (trailingIcon != null) Icon(trailingIcon, "Maintenez OK pour afficher", Modifier.size(12.dp), tint = C.Text3)
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.size(22.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        contentColor = C.Text.copy(alpha = 0.75f),
        focusedScale = 1.75f,
        elevation = 10.dp,
        contentAlignment = Alignment.Center,
    ) { focused -> Text(label, style = T.Body.copy(fontSize = 16.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)) }
}

@Composable
private fun KeyIcon(icon: ImageVector, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(width = 26.dp, height = 22.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        contentColor = C.Text.copy(alpha = 0.75f),
        focusedScale = 1.5f,
        elevation = 10.dp,
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(13.dp)) }
}

// ============================================================================ step 3

@Composable
private fun ImportStep(type: String, name: String, host: String, message: String?, onWatch: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); focus.tryFocus() }
    // Which step the repository is on, from its progress message; never goes back.
    var reached by remember { mutableStateOf(0) }
    val current = message?.let { m -> IMPORT_STEPS.indexOfFirst { (_, keys) -> keys.any { m.startsWith(it) } } } ?: -1
    if (current > reached) reached = current
    Column(Modifier.fillMaxSize().padding(top = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SpinnerTile(if (type == PlaylistType.XTREAM) Icons.Rounded.Dns else Icons.AutoMirrored.Rounded.PlaylistPlay)
        Spacer(Modifier.height(16.dp))
        Text("Importation en cours", style = T.LargeTitle.copy(fontSize = 26.sp))
        Spacer(Modifier.height(6.dp))
        Text(listOf(name, host).filter { it.isNotBlank() }.joinToString(" · "), style = T.Body, color = C.Text2)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.width(205.dp * 2).clip(RoundedCornerShape(11.dp)).background(Color(0xFF17171A)).padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IMPORT_STEPS.forEachIndexed { i, (label, _) ->
                val state = when {
                    i < reached -> 2
                    i == reached -> 1
                    else -> 0
                }
                ImportRow(label, if (state == 1) message else null, state)
            }
            ImportRow("Guide des programmes (EPG)", null, 0, trailing = "Ensuite")
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton("Regarder la TV maintenant", onClick = onWatch, icon = Icons.Rounded.Tv, modifier = Modifier.focusRequester(focus))
            Spacer(Modifier.width(12.dp))
            Text("L'import se poursuit en arrière-plan.", style = T.Caption, color = C.Text2)
        }
    }
}

@Composable
private fun ImportRow(label: String, detail: String?, state: Int, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (state == 1) 44.dp else 30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (state == 1) Color(0x14FFFFFF) else Color.Transparent)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            when (state) {
                2 -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(12.dp), tint = C.Green)
                1 -> Spinner(10.dp)
                else -> Box(Modifier.size(10.dp).border(0.75.dp, Color(0x40FFFFFF), CircleShape))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = T.Headline.copy(fontWeight = FontWeight.SemiBold), color = if (state == 0) C.Text3 else C.Text)
            if (detail != null) {
                Text(detail, style = T.Caption, color = C.Text2, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                ProgressLine(0.6f, Modifier.width(140.dp), height = 2.dp, track = Color(0x26FFFFFF))
            }
        }
        val t = trailing ?: if (state == 0) "En attente" else null
        if (t != null) Text(t, style = T.Subhead.copy(fontWeight = FontWeight.SemiBold), color = C.Text3)
    }
}

@Composable
private fun Spinner(size: Dp, stroke: Dp = 1.5.dp, color: Color = C.Text) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(Modifier.size(size)) {
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(stroke.toPx()))
        drawArc(color, angle, 100f, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
    }
}

/** Icon tile inside a turning ring (the import is running). */
@Composable
private fun SpinnerTile(icon: ImageVector) {
    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(68.dp)) {
            val s = 1.5.dp.toPx()
            drawArc(Color(0x1FFFFFFF), 0f, 360f, false, topLeft = Offset(s, s), size = Size(size.width - 2 * s, size.height - 2 * s), style = Stroke(s))
            drawArc(Color.White, angle, 250f, false, topLeft = Offset(s, s), size = Size(size.width - 2 * s, size.height - 2 * s), style = Stroke(s, cap = StrokeCap.Round))
        }
        IconTile(icon, size = 46.dp)
    }
}

// ============================================================================ step 4

@Composable
private fun DoneStep(name: String, counts: LibraryCounts?, onStart: () -> Unit, onAnother: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); focus.tryFocus() }
    Column(Modifier.fillMaxSize().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(34.dp * 2).clip(CircleShape).background(Color(0x2630D158)).border(0.75.dp, Color(0x6630D158), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, Modifier.size(26.dp), tint = C.Green) }
        Spacer(Modifier.height(18.dp))
        Text("Tout est prêt", style = T.LargeTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "« $name » a été ajouté. Votre bibliothèque est disponible dans les onglets Télévision, Films et Séries.",
            style = T.Body,
            color = C.Text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(440.dp),
        )
        Spacer(Modifier.height(18.dp))
        if (counts != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountCard(Icons.Rounded.Tv, counts.channels, "chaînes")
                CountCard(Icons.Rounded.Movie, counts.movies, "films")
                CountCard(Icons.Rounded.VideoLibrary, counts.series, "séries")
                CountCard(Icons.Rounded.CalendarMonth, counts.programs, "programmes au guide", pending = counts.programs == 0)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Commencer", onClick = onStart, icon = Icons.AutoMirrored.Rounded.ArrowForward, primary = true, modifier = Modifier.focusRequester(focus))
            PillButton("Ajouter une autre source", onClick = onAnother, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun CountCard(icon: ImageVector, n: Int, label: String, pending: Boolean = false) {
    Column(
        Modifier.width(63.dp * 2).clip(RoundedCornerShape(9.dp)).background(Color(0xFF17171A)).padding(12.dp),
    ) {
        Icon(icon, null, Modifier.size(11.dp), tint = C.Text2)
        Spacer(Modifier.height(8.dp))
        Text(
            if (pending) "…" else java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(n),
            style = T.Title2.copy(fontSize = 20.sp),
        )
        Spacer(Modifier.height(4.dp))
        Text(if (pending) "guide en cours" else label, style = T.Caption, color = C.Text2, maxLines = 1)
    }
}

// ============================================================================ welcome

/** First launch, no source yet: the identity, one button. */
@Composable
fun WelcomeScreen() {
    val nav = LocalNav.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focus.tryFocus()
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10142E), C.Background))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoLockup(height = 40.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Connectez votre abonnement Xtream Codes ou une playlist M3U\npour retrouver vos chaînes, films et séries.",
                style = T.Body,
                color = C.Text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            PillButton("Ajouter une source IPTV", onClick = { nav.editPlaylist(0) }, icon = Icons.Rounded.Add, primary = true, modifier = Modifier.focusRequester(focus))
        }
    }
}
