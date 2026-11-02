package no.jodal.salat.apps.ekko.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import no.jodal.salat.Haptics
import no.jodal.salat.apps.ekko.Clip
import no.jodal.salat.apps.ekko.EkkoPlayer
import no.jodal.salat.apps.ekko.EkkoStore
import no.jodal.salat.apps.ekko.Repeat
import no.jodal.salat.apps.ekko.formatClock

/** Spilleren i full skjerm. Dra ned eller trykk på pilen for å lukke. */
@Composable
fun NowPlayingScreen(store: EkkoStore, player: EkkoPlayer, onClose: () -> Unit, onEdit: (Clip) -> Unit) {
    val clip = player.current ?: return
    val (glow, glow2) = EkkoPalette.cover(clip.id)
    var dragged by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(EkkoPalette.Ink)
            .background(Brush.verticalGradient(0f to glow.copy(alpha = 0.32f), 0.35f to glow2.copy(alpha = 0.10f), 0.7f to EkkoPalette.Ink))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > 120.dp.toPx()) onClose() },
                ) { _, amount -> dragged += amount }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                RoundIconButton(Icons.Rounded.KeyboardArrowDown, "Lukk", onClose, size = 40.dp, background = Color.White.copy(alpha = 0.08f), iconSize = 26.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    SectionLabel("Spiller fra")
                    Text(
                        sourceName(player, store),
                        style = MaterialTheme.typography.titleSmall,
                        color = EkkoPalette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundIconButton(Icons.Rounded.ContentCut, "Klipp og endre navn", { onEdit(clip) }, size = 40.dp, background = Color.White.copy(alpha = 0.08f), iconSize = 20.dp)
            }

            Spacer(Modifier.weight(0.6f))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = minOf(maxWidth, 360.dp)
                // Omslaget krymper litt når lyden er på pause, som i Apple Music.
                val scale by animateFloatAsState(if (player.isPlaying) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 260f))
                ClipCover(
                    clip,
                    size,
                    corner = 28.dp,
                    bars = 21,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(40.dp * scale, RoundedCornerShape(28.dp), ambientColor = glow, spotColor = glow),
                )
            }
            Spacer(Modifier.weight(0.5f))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(clip.title, style = MaterialTheme.typography.headlineSmall, color = EkkoPalette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (clip.artist.isNotBlank()) {
                    Text(clip.artist, style = MaterialTheme.typography.bodyLarge, color = EkkoPalette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(24.dp))
            Scrubber(clip, player)
            Spacer(Modifier.height(20.dp))

            val repeat = store.data.repeat
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShuffleButton(store.data.shuffle, player::toggleShuffle)
                RoundIconButton(Icons.Rounded.SkipPrevious, "Forrige", player::previous, size = 56.dp, background = Color.Transparent, iconSize = 38.dp)
                RoundIconButton(
                    if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (player.isPlaying) "Pause" else "Spill av",
                    player::toggle,
                    size = 76.dp,
                    background = EkkoPalette.Lime,
                    tint = EkkoPalette.OnLime,
                    iconSize = 42.dp,
                )
                RoundIconButton(Icons.Rounded.SkipNext, "Neste", player::next, size = 56.dp, background = Color.Transparent, iconSize = 38.dp)
                RoundIconButton(
                    if (repeat == Repeat.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (repeat) {
                        Repeat.Off -> "Gjenta"
                        Repeat.All -> "Gjenta én"
                        Repeat.One -> "Ikke gjenta"
                    },
                    player::cycleRepeat,
                    size = 44.dp,
                    background = if (repeat != Repeat.Off) EkkoPalette.Lime.copy(alpha = 0.14f) else EkkoPalette.Surface,
                    tint = if (repeat != Repeat.Off) EkkoPalette.Lime else EkkoPalette.TextDim,
                    iconSize = 22.dp,
                    haptic = Haptics::select,
                )
            }
            Spacer(Modifier.height(16.dp))
            VolumeSlider(store.data.volume, player::setVolume)
            Spacer(Modifier.weight(0.4f))
        }
    }
}

/**
 * Volumet i Ekko, oppå volumet på telefonen. Glidebryteren går i en kurve, så det er lett å treffe
 * de lave verdiene.
 */
@Composable
private fun VolumeSlider(volume: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.VolumeDown, contentDescription = null, tint = EkkoPalette.TextDim, modifier = Modifier.size(20.dp))
        Slider(
            value = sqrt(volume).coerceIn(MinSlider, 1f),
            onValueChange = { onChange(it * it) },
            valueRange = MinSlider..1f,
            colors = SliderDefaults.colors(
                thumbColor = EkkoPalette.Lime,
                activeTrackColor = EkkoPalette.Lime,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = EkkoPalette.TextDim, modifier = Modifier.size(20.dp))
    }
}

/** Lengst til venstre er volumet 1 %, så lyden aldri blir helt av. */
private const val MinSlider = 0.1f

/** Lydbølgen som fremdriftslinje. Trykk eller dra for å hoppe. */
@Composable
private fun Scrubber(clip: Clip, player: EkkoPlayer) {
    val length = clip.lengthSeconds
    val currentPlayer by rememberUpdatedState(player)
    val currentLength by rememberUpdatedState(length)
    val progress = if (length > 0) (player.position / length).toFloat().coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Waveform(
            levels = clip.trimmedWaveform(),
            bars = 48,
            color = Color.White.copy(alpha = 0.22f),
            playedColor = EkkoPalette.Lime,
            progress = progress,
            gap = 0.55f,
            minHeight = 0.12f,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> currentPlayer.seekTo(offset.x / size.width * currentLength) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        currentPlayer.seekTo((change.position.x / size.width).coerceIn(0f, 1f) * currentLength)
                    }
                },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(player.position), style = MaterialTheme.typography.labelMedium, color = EkkoPalette.TextDim)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatClock(length - player.position),
                style = MaterialTheme.typography.labelMedium,
                color = EkkoPalette.TextDim,
                textAlign = TextAlign.End,
            )
        }
    }
}
