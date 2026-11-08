package no.jodal.salat.apps.ekko.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import no.jodal.salat.Haptics
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.ekko.EkkoImporter
import no.jodal.salat.apps.ekko.EkkoPlayer
import no.jodal.salat.ui.pressable

/** Legg til en ny lyd: velg hvor den kommer fra, vent mens den hentes, og klipp den. */
@Composable
fun AddClipScreen(
    importer: EkkoImporter,
    player: EkkoPlayer,
    bridge: NativeBridge,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val state = importer.state
    LaunchedEffect(state is EkkoImporter.State.Ready) {
        if (state is EkkoImporter.State.Ready) Haptics.success()
    }
    AnimatedContent(
        targetState = state is EkkoImporter.State.Ready,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { ready ->
        val draft = (state as? EkkoImporter.State.Ready)?.draft
        if (ready && draft != null) {
            ClipEditor(
                heading = "Ny lyd",
                clipId = draft.clipId,
                initialTitle = draft.audio.title,
                initialArtist = draft.audio.artist,
                sourceSeconds = draft.audio.durationSeconds,
                waveform = draft.audio.waveform,
                initialStart = 0.0,
                initialEnd = draft.audio.durationSeconds,
                player = player,
                onCancel = {
                    importer.cancel()
                    onClose()
                },
                onSave = { title, artist, start, end ->
                    importer.save(draft, title, artist, start, end)
                    onSaved()
                },
            )
        } else {
            SourcePicker(
                state = state,
                importer = importer,
                bridge = bridge,
                onClose = {
                    importer.cancel()
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun SourcePicker(state: EkkoImporter.State, importer: EkkoImporter, bridge: NativeBridge, onClose: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    val fetching = state as? EkkoImporter.State.Fetching

    Column(Modifier.fillMaxSize().background(EkkoPalette.Ink).imePadding()) {
        EditorTopBar(title = "Ny lyd", onCancel = onClose)
        if (fetching != null) {
            Fetching(fetching, onCancel = importer::cancel)
            return@Column
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text("Hvor er lyden fra?", style = MaterialTheme.typography.headlineMedium, color = EkkoPalette.Text, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            SectionLabel("Lenke")
            EkkoTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = "youtube.com/shorts/…",
                capitalize = false,
                imeAction = ImeAction.Go,
                onDone = { if (link.isNotBlank()) importer.fromText(link) },
                trailing = {
                    if (link.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EkkoPalette.SurfaceHigh)
                                .pressable({ bridge.ekkoPasteboardText()?.let { link = it.trim() } })
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = EkkoPalette.Lime, modifier = Modifier.size(16.dp))
                            Text("Lim inn", style = MaterialTheme.typography.labelLarge, color = EkkoPalette.Text)
                        }
                    } else {
                        RoundIconButton(Icons.Rounded.Close, "Tøm", { link = "" }, size = 24.dp, background = EkkoPalette.Line, iconSize = 14.dp)
                    }
                },
            )
            (state as? EkkoImporter.State.Failed)?.let { failed ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = EkkoPalette.Danger, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                    Text(failed.message, style = MaterialTheme.typography.bodyMedium, color = EkkoPalette.Danger)
                }
            }
            PrimaryButton("Hent lyd", { importer.fromText(link) }, enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                Hairline(Modifier.weight(1f))
                Text("eller", style = MaterialTheme.typography.labelMedium, color = EkkoPalette.TextDim)
                Hairline(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceTile(Icons.Rounded.VideoLibrary, "Bilder", "Lagrede videoer", importer::fromPhotos, Modifier.weight(1f))
                SourceTile(Icons.Rounded.Folder, "Filer", "Lyd eller video", importer::fromFiles, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Virker med YouTube, YouTube Shorts og videoer i offentlige poster på X. Snapchat Spotlight virker for noen videoer. " +
                    "Fra andre apper kan du lagre videoen eller ta opp skjermen, og hente den fra Bilder.",
                style = MaterialTheme.typography.bodyMedium,
                color = EkkoPalette.TextDim,
            )
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(EkkoPalette.Surface)
            .pressable(onClick, pressedScale = 0.97f)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EkkoPalette.Lime.copy(alpha = 0.14f)),
        ) {
            Icon(icon, contentDescription = null, tint = EkkoPalette.Lime, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = EkkoPalette.Text)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EkkoPalette.TextDim)
    }
}

/** Mens lyden hentes: en lydbølge som bølger, og fylles opp etter hvert som lyden lastes ned. */
@Composable
private fun Fetching(state: EkkoImporter.State.Fetching, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        LoadingWave(state.progress, Modifier.size(width = 220.dp, height = 72.dp))
        Spacer(Modifier.height(4.dp))
        Text("Henter lyd fra ${state.source}", style = MaterialTheme.typography.titleLarge, color = EkkoPalette.Text, textAlign = TextAlign.Center)
        Text(
            state.progress?.let { "${(it * 100).roundToInt()} %" } ?: "Dette tar bare noen sekunder.",
            style = MaterialTheme.typography.bodyMedium,
            color = EkkoPalette.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Avbryt", onCancel)
    }
}

@Composable
private fun LoadingWave(progress: Float?, modifier: Modifier) {
    val transition = rememberInfiniteTransition()
    val time by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)))
    Canvas(modifier) {
        val bars = 24
        val slot = size.width / bars
        val barWidth = slot * 0.55f
        for (i in 0 until bars) {
            val level = 0.25f + 0.75f * ((sin(time - i * 0.45f) + 1) / 2) * (0.6f + 0.4f * sin(i * 1.3f).let { it * it })
            val h = size.height * level
            val filled = progress == null || (i + 0.5f) / bars <= progress
            drawRoundRect(
                color = if (filled) EkkoPalette.Lime else EkkoPalette.Lime.copy(alpha = 0.22f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** Toppen av skjermene for å legge til og redigere: «Avbryt», tittel og eventuelt «Lagre». */
@Composable
fun EditorTopBar(title: String, onCancel: () -> Unit, onSave: (() -> Unit)? = null, canSave: Boolean = true) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = 12.dp, end = 16.dp)) {
        Text(
            "Avbryt",
            style = MaterialTheme.typography.bodyLarge,
            color = EkkoPalette.TextDim,
            modifier = Modifier.align(Alignment.CenterStart).clip(CircleShape).pressable(onCancel).padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = EkkoPalette.Text, modifier = Modifier.align(Alignment.Center))
        if (onSave != null) {
            Text(
                "Lagre",
                style = MaterialTheme.typography.titleSmall,
                color = EkkoPalette.OnLime,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .background(if (canSave) EkkoPalette.Lime else EkkoPalette.Lime.copy(alpha = 0.35f))
                    .pressable(onSave, enabled = canSave, haptic = Haptics::success)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}
