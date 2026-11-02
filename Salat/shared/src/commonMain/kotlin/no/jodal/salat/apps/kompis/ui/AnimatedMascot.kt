package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import no.jodal.salat.apps.kompis.KompisMascots
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

private enum class MascotReaction { None, Hello, Joy, Surprise, Concern }

/** Reaksjoner på samtalen og på berøring, med de eksisterende figurene. */
@Composable
fun AnimatedMascot(
    modifier: Modifier = Modifier,
    mascot: DrawableResource = KompisMascots.current,
    mode: VoiceMode = VoiceMode.Idle,
    level: Float = 0f,
    status: LiveStatus? = null,
) {
    var taps by remember { mutableIntStateOf(0) }
    var handledTaps by remember { mutableIntStateOf(0) }
    var previousStatus by remember { mutableStateOf<LiveStatus?>(null) }
    var reaction by remember { mutableStateOf(MascotReaction.None) }
    val reactionProgress = remember { Animatable(1f) }
    LaunchedEffect(taps, status) {
        val oldStatus = previousStatus
        previousStatus = status
        val next = when {
            taps != handledTaps -> {
                handledTaps = taps
                when (taps % 3) {
                    1 -> MascotReaction.Joy
                    2 -> MascotReaction.Hello
                    else -> MascotReaction.Surprise
                }
            }
            status == LiveStatus.Failed -> MascotReaction.Concern
            status == LiveStatus.Listening && (oldStatus == LiveStatus.Connecting || oldStatus == LiveStatus.Reconnecting) -> MascotReaction.Hello
            status == null -> MascotReaction.Hello
            else -> MascotReaction.None
        }
        reaction = next
        reactionProgress.snapTo(0f)
        if (next != MascotReaction.None) reactionProgress.animateTo(1f, tween(1100, easing = LinearEasing))
    }
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
    val resting by animateFloatAsState(
        if (status == LiveStatus.Muted || status == LiveStatus.Ended) 1f else 0f,
        spring(stiffness = 100f),
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { contentDescription = "Kompis-maskot" }
            .clickable(role = Role.Button, onClickLabel = "Hils på Kompis") { taps++ },
    ) {
        Image(
            painter = painterResource(mascot),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val breath = sin(phase)
                val chatter = sin(phase * 6)
                val bounce = speaking * (0.5f + 0.5f * chatter) * (0.015f + voice * 0.045f)
                val progress = reactionProgress.value
                val envelope = sin(progress * PI).toFloat()
                val wiggle = sin(progress * PI * 6).toFloat() * envelope
                val hop = if (reaction == MascotReaction.Joy) envelope * 0.10f else 0f
                val surprise = if (reaction == MascotReaction.Surprise) envelope else 0f
                scaleX = 1f + breath * 0.012f - bounce * 0.35f - surprise * 0.07f
                scaleY = 1f + breath * 0.018f + bounce + surprise * 0.10f - resting * 0.035f
                rotationZ = -5f * listening + sin(phase * 2) * (4f * thinking + speaking * 1.5f) + resting * 9f +
                    when (reaction) {
                        MascotReaction.Hello -> wiggle * 12f
                        MascotReaction.Joy -> wiggle * 5f
                        MascotReaction.Concern -> wiggle * 6f
                        else -> 0f
                    }
                translationY = -size.height * (breath * 0.012f + bounce * 0.4f + hop)
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            val progress = reactionProgress.value
            val envelope = sin(progress * PI).toFloat().coerceAtLeast(0f)
            // Små tenkeprikker følger en bue over figuren.
            if (thinking > 0.01f) repeat(3) { index ->
                val pulse = 0.55f + 0.45f * sin(phase * 2 - index * 0.8f)
                drawCircle(
                    KompisPalette.Violet.copy(alpha = thinking * pulse),
                    radius = unit * (0.018f + 0.008f * pulse),
                    center = Offset(unit * (0.76f + index * 0.065f), unit * (0.18f - index * 0.045f)),
                )
            }
            if (envelope > 0f && reaction != MascotReaction.None) {
                val color = when (reaction) {
                    MascotReaction.Concern -> KompisPalette.Cyan
                    MascotReaction.Joy -> KompisPalette.Pink
                    else -> Color(0xFFFFD875)
                }.copy(alpha = envelope)
                if (reaction == MascotReaction.Joy) {
                    // Et lite hjerte stiger ved siden av figuren.
                    val x = unit * 0.84f
                    val y = unit * (0.30f - progress * 0.18f)
                    val r = unit * 0.07f
                    val heart = Path().apply {
                        moveTo(x, y + r)
                        cubicTo(x - r * 2, y, x - r, y - r, x, y - r * 0.3f)
                        cubicTo(x + r, y - r, x + r * 2, y, x, y + r)
                        close()
                    }
                    drawPath(heart, color)
                } else if (reaction == MascotReaction.Concern) {
                    drawCircle(color, unit * 0.065f, Offset(unit * 0.87f, unit * 0.18f), style = Stroke(unit * 0.018f))
                    drawLine(color, Offset(unit * 0.87f, unit * 0.14f), Offset(unit * 0.87f, unit * 0.18f), unit * 0.014f, StrokeCap.Round)
                    drawCircle(color, unit * 0.009f, Offset(unit * 0.87f, unit * 0.21f))
                } else {
                    // Glimt på begge sider ved hilsing og overraskelse.
                    repeat(2) { index ->
                        val center = Offset(unit * (if (index == 0) 0.12f else 0.88f), unit * (0.25f - progress * 0.08f))
                        val radius = unit * 0.07f * envelope
                        repeat(4) { ray ->
                            val angle = ray * PI.toFloat() / 2
                            drawLine(color, center, center + Offset(cos(angle) * radius, sin(angle) * radius), unit * 0.018f, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}
