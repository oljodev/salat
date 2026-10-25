package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.KompisStore
import no.jodal.salat.apps.kompis.TtsResult
import no.jodal.salat.apps.kompis.TtsSample
import no.jodal.salat.apps.kompis.TtsSampleRates
import no.jodal.salat.apps.kompis.TtsTest
import no.jodal.salat.apps.kompis.formatKilobytes
import no.jodal.salat.apps.kompis.formatPricePerMillion
import no.jodal.salat.apps.kompis.formatSeconds
import no.jodal.salat.apps.kompis.formatUsd
import no.jodal.salat.apps.kompis.isNorwegianVoice

/** Test av tekst til tale gjennom OpenRouter: velg modell og stemme, og hør hvor raskt og hvor bra det blir. */
@Composable
fun TtsTestScreen(store: KompisStore, bridge: NativeBridge, onBack: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val test = remember { TtsTest(store, bridge) }
    var choosingModel by remember { mutableStateOf(false) }
    var choosingVoice by remember { mutableStateOf(false) }

    DisposableEffect(test) {
        test.open()
        onDispose { test.close() }
    }
    LaunchedEffect(store.openRouterKey) { test.loadModels() }

    Box(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            ) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Tilbake", onClick = onBack)
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tekst til tale", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
                        Text(
                            "Hør hvordan modellene leser norsk, og hvor raskt den første lyden kommer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (!store.hasOpenRouterKey) {
                    item(key = "key") { KeyNotice(onSettings) }
                }
                item(key = "model") {
                    val model = test.models.firstOrNull { it.id == test.modelId }
                    val average = test.averageFirstAudio(test.modelId)
                    ModelRow(
                        name = test.modelName(test.modelId),
                        details = listOfNotNull(
                            model?.maker,
                            model?.inputPrice?.takeIf { it > 0 }?.let { formatPricePerMillion(it, "tegn") },
                            average?.let { "første lyd $it ms i snitt" },
                        ).joinToString("  ·  "),
                        onClick = { choosingModel = true },
                    )
                }
                item(key = "voice") {
                    val voice = test.voice
                    if (voice == null) {
                        Text(
                            "Modellen har ingen stemmeliste, så den bruker sin egen stemme.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    } else {
                        val norwegian = test.voices.count(::isNorwegianVoice)
                        ModelRow(
                            label = "Stemme",
                            name = voice,
                            details = "${test.voices.size} stemmer" + if (norwegian > 0) ", $norwegian norske" else "",
                            onClick = { choosingVoice = true },
                        )
                    }
                }
                item(key = "text") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Options("Tekst") {
                            TtsSample.entries.forEach { sample ->
                                OptionChip(sample.label, selected = test.text == sample.text) { test.text = sample.text }
                            }
                        }
                        TextArea(test.text, onValueChange = { test.text = it }, placeholder = "Skriv noe som skal leses opp")
                    }
                }
                item(key = "instructions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Regi",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        TextArea(test.instructions, onValueChange = test::setInstructions, placeholder = "F.eks. «varm og rolig»", singleLine = true)
                        Text(
                            "Brukes av Fish Audio, Gemini og OpenAI. De andre modellene ser bort fra den.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                item(key = "rate") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Options("Samplingsfrekvens") {
                            TtsSampleRates.forEach { rate ->
                                OptionChip(if (rate == 0) "Auto" else formatRate(rate), selected = test.sampleRate == rate) { test.selectSampleRate(rate) }
                            }
                        }
                        Text(
                            "Høres stemmen for lys og rask, eller mørk og treg ut, er frekvensen feil. Prøv en annen for denne modellen.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                item(key = "audio") {
                    Options("Lyd") {
                        OptionChip("Som i samtaler", selected = test.callAudio) { test.setCallAudio(true) }
                        OptionChip("Best kvalitet", selected = !test.callAudio) { test.setCallAudio(false) }
                    }
                }
                test.message?.let { message ->
                    item(key = "message") { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
                }
                if (test.results.isNotEmpty()) {
                    item(key = "results") {
                        Text("Forsøk", style = MaterialTheme.typography.titleLarge, color = colors.onBackground, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                items(test.results, key = { it.id }) { result ->
                    ResultCard(
                        result = result,
                        playing = test.playingId == result.id,
                        onPlay = { test.replay(result.id) },
                        onStop = test::stop,
                    )
                }
            }
        }

        // «Les opp» nederst, over en myk overgang.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 16.dp),
        ) {
            val busy = test.isBusy || test.playingId != null
            val canSpeak = test.text.isNotBlank()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .alpha(if (busy || canSpeak) 1f else 0.35f)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(enabled = busy || canSpeak, onClick = if (busy) test::stop else test::speak),
            ) {
                if (busy) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, tint = colors.onPrimary)
                    Text("Stopp", style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
                } else {
                    VoiceBars(level = 1f, color = colors.onPrimary, animated = false, modifier = Modifier.height(18.dp))
                    Text("Les opp", style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
                }
            }
        }
    }

    if (choosingModel) {
        ModelSheet(
            models = test.models,
            isLoading = test.isLoadingModels,
            error = test.modelsError,
            selectedId = test.modelId,
            averageMillis = test::averageFirstAudio,
            onRetry = test::loadModels,
            onSelect = test::selectModel,
            onDismiss = { choosingModel = false },
        )
    }
    if (choosingVoice) {
        ChoiceSheet(
            title = "Velg stemme",
            options = test.voices,
            selected = test.voice,
            highlight = { if (isNorwegianVoice(it)) "Norsk" else null },
            onSelect = test::selectVoice,
            onDismiss = { choosingVoice = false },
        )
    }
}

/** F.eks. «24 kHz» eller «22,05 kHz». */
private fun formatRate(rate: Int): String =
    if (rate % 1000 == 0) "${rate / 1000} kHz" else "${rate / 1000},${(rate % 1000).toString().padStart(3, '0').trimEnd('0')} kHz"

@Composable
private fun TextArea(value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 0.dp else 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                inner()
            }
        },
    )
}

@Composable
private fun ResultCard(result: TtsResult, playing: Boolean, onPlay: () -> Unit, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        val canPlay = result.totalMillis != null
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .alpha(if (playing || canPlay) 1f else 0.35f)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(enabled = playing || canPlay, onClick = if (playing) onStop else onPlay),
        ) {
            when {
                result.isLoading && !playing -> CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                playing -> Icon(Icons.Rounded.Stop, contentDescription = "Stopp", tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Spill av", tint = colors.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val timing = listOfNotNull(
                result.firstAudioMillis?.let { "første lyd $it ms" },
                result.totalMillis?.let { "ferdig ${formatSeconds(it / 1000.0)}" },
            )
            Text(
                timing.joinToString("  ·  ").ifEmpty { if (result.error == null) "Henter …" else "Feilet" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(result.text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    result.modelName,
                    result.voice,
                    result.audioSeconds?.let { "${formatSeconds(it)} lyd" },
                    result.bytes?.let(::formatKilobytes),
                    result.format,
                    result.costUsd?.let { "ca. ${formatUsd(it)}" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            result.error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.error) }
        }
    }
}
