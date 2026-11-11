package com.iplayer.tv.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.iplayer.tv.R

/**
 * Lucarne palette (design file iplayer.pen). The interface is monochrome; the dusk gradient is the only brand
 * colour. Design sizes are 1920×1080 px: everything here is the design value divided by two (dp / sp).
 */
object C {
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF1C1C1F)
    val Surface2 = Color(0xFF2A2A2E)
    val Surface3 = Color(0xFF3A3A3E)
    /** Translucent fills ("glass" in the design). */
    val Glass = Color(0x1AFFFFFF)
    val GlassStrong = Color(0x2EFFFFFF)
    /** Rows and cards resting on the background. */
    val Row = Color(0x0FFFFFFF)
    val Panel = Color(0xC71C1C1F)
    val PanelSolid = Color(0xF21C1C1F)
    val Separator = Color(0x1FFFFFFF)
    val Text = Color(0xFFF5F5F7)
    val Text2 = Color(0xFFA1A1A6)
    val Text3 = Color(0xFF7C7C82)
    val Accent = Color(0xFFF5F5F7)
    val Accent2 = Color(0xFF5A3E9E)
    val Red = Color(0xFFFF453A)
    val LiveText = Color(0xFFFF6961)
    val Green = Color(0xFF30D158)
    val Yellow = Color(0xFFFFD23F)
    val Orange = Color(0xFFF5835E)
    val Focus = Color(0xFFF5F5F7)
    val OnFocus = Color(0xFF0A0A0C)
    val Scrim = Color(0xCC0A0A0C)

    /** "Crépuscule": nuit, indigo, mauve, corail, lueur. */
    val Dusk = listOf(Color(0xFF33448C), Color(0xFF5A3E9E), Color(0xFFB9558A), Color(0xFFF5835E), Color(0xFFFFCF92))
    val DuskBrush = Brush.verticalGradient(0f to Dusk[0], 0.36f to Dusk[1], 0.66f to Dusk[2], 0.86f to Dusk[3], 1f to Dusk[4])
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

object T {
    private val base = TextStyle(fontFamily = Inter, color = Color.Unspecified, letterSpacing = 0.sp)
    /** Hero titles (96 px). */
    val Display = base.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, lineHeight = 50.sp)
    /** Screen titles: "Qui regarde ?", "Ajouter une source IPTV" (68 px). */
    val LargeTitle = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp, lineHeight = 40.sp)
    /** Page titles of forms, best result (46 px). */
    val Title1 = base.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp, lineHeight = 28.sp)
    /** Player titles (40 px). */
    val Title2 = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 25.sp)
    /** Shelf titles (34 px). */
    val Title3 = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 22.sp)
    /** Row labels (26 px). */
    val Headline = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
    val Body = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)
    /** Buttons (24 px). */
    val Label = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    val Callout = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    /** Card titles, chips (22 px). */
    val Subhead = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)
    val Footnote = base.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp)
    /** Card meta (18 px). */
    val Caption = base.copy(fontSize = 9.sp, fontWeight = FontWeight.Normal, lineHeight = 12.sp)
    /** "FILM À LA UNE", section headers (18 px, tracked). */
    val Eyebrow = base.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, lineHeight = 12.sp)
    /** Outlined pills, small tracked labels (16 px). */
    val Micro = base.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, lineHeight = 10.sp)
}

@Composable
fun IPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            background = C.Background,
            surface = C.Surface,
            onBackground = C.Text,
            onSurface = C.Text,
        ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides C.Text,
            LocalTextStyle provides T.Body,
            content = content,
        )
    }
}
