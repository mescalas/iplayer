package com.iplayer.tv.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.iplayer.tv.R
import com.iplayer.tv.ui.theme.C

private val EaseOut = CubicBezierEasing(.22f, 1f, .36f, 1f)
private val EaseZoom = CubicBezierEasing(.7f, 0f, .2f, 1f)
private val EaseStd = CubicBezierEasing(.25f, .1f, .25f, 1f)

/** The arch of the logo, in its 77 × 100 design units. */
private val ArchPath = PathParser().parsePathString(
    "M0 38.5A38.5 38.5 0 0 1 77 38.5L77 93.07Q77 100 70.07 100L6.93 100Q0 100 0 93.07Z"
).toPath()

/** Progress of a step running from [start] to [start] + [duration] ms, eased. */
private fun step(t: Float, start: Float, duration: Float, easing: Easing): Float =
    easing.transform(((t - start) / duration).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * Launch animation "Fenêtre ouverte" (lancement.html in the design): the logo appears, the name fades out while
 * the arch slides to the centre, then the arch grows until it fills the screen and opens on the app (~1.9 s).
 * With animations turned off in the system settings, the final logo shows, then fades.
 */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val total = if (reduced) 1100f else 1900f
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // A frame for the app underneath to lay out, so the reveal opens on a drawn screen.
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        onDone()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = clock.value
                alpha = if (reduced) 1f - step(t, 800f, 300f, EaseStd) else 1f - step(t, 1400f, 500f, EaseOut)
            }
            .background(C.Background),
        contentAlignment = Alignment.Center,
    ) {
        // Lockup geometry (146 × 36 units, arch 19.37 × 25.16 at y 3.99), arch height = 8 % of the width.
        val archH = maxWidth * 0.08f
        val u = archH / 25.16f
        val rowW = u * 146f
        val rowH = u * 36f
        val shiftPx = with(LocalDensity.current) { (u * (73f - 9.685f)).toPx() }
        val wordShiftPx = with(LocalDensity.current) { (maxWidth * 0.012f).toPx() }

        Box(
            Modifier
                .size(rowW, rowH)
                .graphicsLayer {
                    val t = clock.value
                    val f = if (reduced) 1f else step(t, 0f, 350f, EaseOut)
                    alpha = f
                    val sc = lerp(.97f, 1f, f)
                    scaleX = sc
                    scaleY = sc
                }
        ) {
            // The name
            Image(
                painterResource(R.drawable.logo_word),
                contentDescription = "Lucarne",
                modifier = Modifier
                    .offset(x = u * 25.15f)
                    .size(u * 120.85f, rowH)
                    .graphicsLayer {
                        val f = if (reduced) 0f else step(clock.value, 750f, 250f, EaseStd)
                        alpha = 1f - f
                        translationX = wordShiftPx * f
                    },
            )
            // The window: halo + arch, slid to the centre then zoomed through
            Box(
                Modifier
                    .offset(y = u * 3.99f)
                    .size(u * 19.37f, u * 25.16f)
                    .graphicsLayer {
                        val t = clock.value
                        if (reduced) return@graphicsLayer
                        translationX = shiftPx * step(t, 750f, 250f, EaseOut)
                        val z = lerp(1f, 34f, step(t, 1000f, 600f, EaseZoom))
                        scaleX = z
                        scaleY = z
                        transformOrigin = TransformOrigin(0.5f, 0.62f)
                    }
            ) {
                // Halo: the light spilling out of the window, a soft dusk glow behind the arch.
                Canvas(
                    Modifier
                        .align(Alignment.Center)
                        .requiredSize(u * 19.37f * 2.4f, u * 25.16f * 2f)
                        .graphicsLayer {
                            val t = clock.value
                            alpha = if (reduced) .6f else lerp(0f, .6f, step(t, 150f, 600f, EaseOut)) + .3f * step(t, 1000f, 600f, EaseZoom)
                        }
                ) {
                    drawOval(
                        Brush.radialGradient(
                            0f to C.Dusk[2].copy(alpha = .55f), 0.45f to C.Dusk[1].copy(alpha = .25f), 1f to C.Dusk[0].copy(alpha = 0f),
                            center = center.copy(y = size.height * 0.56f),
                            radius = size.minDimension / 2f,
                        )
                    )
                }
                Arch(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Arch(modifier: Modifier) {
    // Drawn as a path (not a cached vector bitmap) so it stays sharp when zoomed 34 times.
    Canvas(modifier) {
        scale(size.width / 77f, size.height / 100f, pivot = Offset.Zero) {
            drawPath(ArchPath, ArchBrush)
        }
    }
}

private val ArchBrush = Brush.verticalGradient(
    0f to C.Dusk[0], 0.36f to C.Dusk[1], 0.66f to C.Dusk[2], 0.86f to C.Dusk[3], 1f to C.Dusk[4],
    startY = 0f, endY = 100f,
)
