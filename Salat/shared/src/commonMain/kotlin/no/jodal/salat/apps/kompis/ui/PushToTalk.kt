package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.TimeSource
import no.jodal.salat.Haptics
import no.jodal.salat.apps.kompis.TurnTimings
import no.jodal.salat.apps.kompis.formatSeconds

private val White = Color.White

/**
 * Kontrollene når man holder inne en knapp for å snakke: hvor raskt forrige tur gikk, en stor
 * snakkeknapp som viser hva som skjer, og avslutt.
 */
@Composable
internal fun PushToTalkControls(state: LiveUiState, onTalkStart: () -> Unit, onTalkStop: () -> Unit, onEnd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
    ) {
        state.timings?.let { TimingsCard(it) }
        TalkButton(state, onStart = onTalkStart, onStop = onTalkStop)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteChip(state.audioRoute)
            EndButton(onEnd)
        }
    }
}

/**
 * Den store knappen. Holdes den inne, sendes opptaket når man slipper. Et kort trykk starter opptaket,
 * og neste trykk sender det. Trykker man mens Kompis snakker, avbrytes Kompis. En ring rundt knappen
 * følger lyden mens man snakker, puster mens Kompis snakker, og går rundt mens Kompis tenker.
 */
@Composable
private fun TalkButton(state: LiveUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val status = state.status
    val recording = status == LiveStatus.Recording
    val speaking = status == LiveStatus.Speaking
    val thinking = status == LiveStatus.Thinking
    val ready = status in setOf(LiveStatus.Listening, LiveStatus.Recording, LiveStatus.Thinking, LiveStatus.Speaking)

    var tapMode by remember { mutableStateOf(false) }
    LaunchedEffect(recording) { if (!recording) tapMode = false }
    val isRecording by rememberUpdatedState(recording)
    val isReady by rememberUpdatedState(ready)
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)

    val breathing = rememberInfiniteTransition()
    val breath by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
    val level by animateFloatAsState(state.level.coerceIn(0f, 1f), spring(stiffness = 300f))
    val coreScale by animateFloatAsState(if (recording) 1.06f else 1f)
    val ringScale = if (recording) 1f + 0.24f * level else if (speaking) breath else 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(156.dp)
                .alpha(if (ready) 1f else 0.4f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (isRecording) {
                                // Det andre trykket i trykk-modus.
                                Haptics.tap()
                                stop()
                                return@detectTapGestures
                            }
                            if (!isReady) return@detectTapGestures
                            Haptics.firm()
                            start()
                            val pressedAt = TimeSource.Monotonic.markNow()
                            val released = tryAwaitRelease()
                            if (released && pressedAt.elapsedNow() >= HoldThreshold) {
                                Haptics.tap()
                                stop()
                            } else {
                                tapMode = true
                            }
                        },
                    )
                },
        ) {
            if (recording || speaking) {
                Box(
                    Modifier
                        .size(124.dp)
                        .graphicsLayer {
                            scaleX = ringScale
                            scaleY = ringScale
                        }
                        .border(
                            2.dp,
                            Brush.linearGradient(if (recording) KompisPalette.Aurora else listOf(KompisPalette.Violet, KompisPalette.Pink)),
                            CircleShape,
                        ),
                )
            }
            if (thinking) {
                CircularProgressIndicator(color = KompisPalette.Violet, strokeWidth = 3.dp, modifier = Modifier.size(132.dp))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = coreScale
                        scaleY = coreScale
                    }
                    .clip(CircleShape)
                    .background(
                        if (recording) Brush.linearGradient(KompisPalette.Aurora) else Brush.linearGradient(listOf(White.copy(alpha = 0.14f), White.copy(alpha = 0.14f))),
                    )
                    .then(if (recording) Modifier else Modifier.border(1.5.dp, White.copy(alpha = 0.22f), CircleShape)),
            ) {
                if (recording) {
                    VoiceBars(level = level, color = White, barWidth = 5.dp, modifier = Modifier.height(40.dp))
                } else {
                    Icon(Icons.Rounded.Mic, contentDescription = "Hold inne og snakk", tint = White, modifier = Modifier.size(42.dp))
                }
            }
        }
        Text(
            when {
                recording && tapMode -> "Trykk for å sende"
                recording -> "Slipp for å sende"
                speaking -> "Hold inne for å avbryte"
                thinking -> "Tenker …"
                status == LiveStatus.Connecting -> "Kobler til …"
                else -> "Hold inne og snakk"
            },
            style = MaterialTheme.typography.titleMedium,
            color = White,
        )
        if (status == LiveStatus.Listening) {
            Text(
                "Eller trykk én gang for å starte, og én gang til for å sende",
                style = MaterialTheme.typography.labelMedium,
                color = White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

/** Hvor raskt forrige tur gikk, fordelt på stegene. «Hørt etter» er det som merkes. */
@Composable
private fun TimingsCard(timings: TurnTimings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(White.copy(alpha = 0.08f))
                .padding(vertical = 10.dp, horizontal = 6.dp),
        ) {
            TimingItem("Tale til tekst", timings.sttMillis, Modifier.weight(1f))
            TimingItem("Språkmodell", timings.sentenceMillis, Modifier.weight(1f))
            TimingItem("Stemme", timings.voiceMillis, Modifier.weight(1f))
            TimingItem("Hørt etter", timings.heardAfterMillis, Modifier.weight(1f), highlight = true)
        }
        timings.totalMillis?.let {
            Text("Ferdig på ${formatSeconds(it / 1000.0)}", style = MaterialTheme.typography.labelSmall, color = White.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun TimingItem(label: String, millis: Long?, modifier: Modifier, highlight: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            millis?.let { "$it ms" } ?: "–",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) White else White.copy(alpha = 0.75f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Hvor lyden går, f.eks. «AirPods Pro» eller «Høyttaler». */
@Composable
private fun RouteChip(route: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(White.copy(alpha = 0.1f))
            .padding(horizontal = 18.dp),
    ) {
        Icon(
            if (route.contains("AirPods", ignoreCase = true)) Icons.Rounded.Headphones else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = White.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp),
        )
        Text(route, style = MaterialTheme.typography.labelLarge, color = White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EndButton(onEnd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(KompisPalette.EndCall)
            .clickable(onClick = onEnd)
            .padding(horizontal = 22.dp),
    ) {
        Icon(Icons.Rounded.Close, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
        Text("Avslutt", style = MaterialTheme.typography.titleSmall, color = White)
    }
}
