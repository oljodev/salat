package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import no.jodal.salat.apps.kompis.KompisMascots
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.sin

/** Beveger den eksisterende figuren uten å forandre ansiktet eller lage nye bildefiler. */
@Composable
fun AnimatedMascot(
    modifier: Modifier = Modifier,
    mascot: DrawableResource = KompisMascots.current,
    mode: VoiceMode = VoiceMode.Idle,
    level: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "maskot")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "pust",
    )
    // Myke overganger mellom turene. Lyd fra brukeren skal ikke få figuren til å snakke.
    val speaking by animateFloatAsState(if (mode == VoiceMode.Speaking) 1f else 0f, spring(stiffness = 180f))
    val thinking by animateFloatAsState(if (mode == VoiceMode.Thinking) 1f else 0f, spring(stiffness = 100f))
    val listening by animateFloatAsState(if (mode == VoiceMode.Listening) 1f else 0f, spring(stiffness = 100f))
    val voice by animateFloatAsState(
        if (mode == VoiceMode.Speaking) level.coerceIn(0f, 1f) else 0f,
        spring(stiffness = 300f),
    )

    Image(
        painter = painterResource(mascot),
        contentDescription = null, // Samtalestatusen står allerede som tekst ved siden av figuren.
        modifier = modifier.graphicsLayer {
            val breath = sin(phase)
            val chatter = sin(phase * 6)
            val bounce = speaking * (0.5f + 0.5f * chatter) * (0.015f + voice * 0.045f)
            scaleX = 1f + breath * 0.012f - bounce * 0.35f
            scaleY = 1f + breath * 0.018f + bounce
            rotationZ = -5f * listening + sin(phase * 2) * (4f * thinking + speaking * 1.5f)
            translationY = -size.height * (breath * 0.012f + bounce * 0.4f)
        },
    )
}
