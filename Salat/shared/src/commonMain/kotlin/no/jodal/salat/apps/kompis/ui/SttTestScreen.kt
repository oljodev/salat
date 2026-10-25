package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import no.jodal.salat.Haptics
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.KompisStore
import no.jodal.salat.apps.kompis.SttLanguage
import no.jodal.salat.apps.kompis.SttModel
import no.jodal.salat.apps.kompis.SttResult
import no.jodal.salat.apps.kompis.SttTest
import no.jodal.salat.apps.kompis.formatKilobytes
import no.jodal.salat.apps.kompis.formatSeconds
import no.jodal.salat.apps.kompis.formatUsd

/** Test av tale til tekst gjennom OpenRouter: hold inne, snakk, slipp, og se hvor raskt teksten kommer. */
@Composable
fun SttTestScreen(store: KompisStore, bridge: NativeBridge, onBack: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val test = remember { SttTest(store, bridge) }
    DisposableEffect(test) {
        test.open()
        onDispose { test.close() }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            ) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Tilbake", onClick = onBack)
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("Tale til tekst", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
                        Text(
                            "Hold inne knappen og snakk. Når du slipper, vises teksten og tiden fra du slapp til den kom.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (!store.hasOpenRouterKey) {
                    item(key = "key") { KeyNotice(onSettings) }
                }
                item(key = "model") {
                    Options("Modell") {
                        SttModel.entries.forEach { model ->
                            OptionChip(model.label, selected = model == test.model) { test.selectModel(model) }
                        }
                    }
                }
                item(key = "language") {
                    Options("Språk") {
                        SttLanguage.entries.forEach { language ->
                            OptionChip(language.label, selected = language == test.language) { test.selectLanguage(language) }
                        }
                    }
                }
                item(key = "average") {
                    val average = test.averageMillis(test.model)
                    val count = test.results.count { it.model == test.model }
                    Text(
                        if (average == null) "${test.model.label} er ikke testet ennå." else "${test.model.label}: $average ms i snitt over $count forsøk",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                test.message?.let { message ->
                    item(key = "message") {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                itemsIndexed(test.results, key = { index, _ -> test.results.size - index }) { _, result -> ResultCard(result) }
            }
        }

        // Knappen nederst, over en myk overgang.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                .navigationBarsPadding()
                .padding(top = 36.dp, bottom = 20.dp),
        ) {
            HoldToTalkButton(
                recording = test.isRecording,
                waiting = test.isWaiting,
                level = test.level,
                onPress = test::startRecording,
                onRelease = test::stopRecording,
            )
            Text(
                when {
                    test.isRecording -> "Slipp for å sende"
                    test.isWaiting -> "Venter på teksten …"
                    else -> "Hold inne og snakk"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Options(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else colors.surfaceVariant)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResultCard(result: SttResult) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${result.millis} ms", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                "${result.model.label}  ·  ${result.language.label}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            result.text.ifEmpty { "Hørte ingenting." },
            style = MaterialTheme.typography.bodyLarge,
            color = if (result.text.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
        )
        Text(
            listOfNotNull(
                "${formatSeconds(result.audioSeconds)} lyd",
                formatKilobytes(result.uploadBytes),
                result.costUsd?.let(::formatUsd),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyNotice(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = colors.onSurface)
        Column(Modifier.weight(1f)) {
            Text("Legg inn OpenRouter-nøkkel", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                "Testen sender opptakene til OpenRouter.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** En rund knapp man holder inne mens man snakker. Lyser som nordlyset mens den tar opp. */
@Composable
private fun HoldToTalkButton(recording: Boolean, waiting: Boolean, level: Float, onPress: () -> Unit, onRelease: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val scale by animateFloatAsState(if (recording) 1.12f else 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (recording) Brush.linearGradient(KompisPalette.Aurora) else Brush.linearGradient(listOf(colors.primary, colors.primary)))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        Haptics.firm()
                        press()
                        tryAwaitRelease()
                        Haptics.tap()
                        release()
                    },
                )
            },
    ) {
        when {
            recording -> VoiceBars(level = level, color = Color.White, barWidth = 4.dp, modifier = Modifier.height(30.dp))
            waiting -> CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            else -> Icon(Icons.Rounded.Mic, contentDescription = "Hold inne og snakk", tint = colors.onPrimary, modifier = Modifier.size(34.dp))
        }
    }
}
