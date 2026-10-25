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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.launch
import no.jodal.salat.Haptics
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.KompisStore
import no.jodal.salat.apps.kompis.OpenRouterModel
import no.jodal.salat.apps.kompis.SttLanguage
import no.jodal.salat.apps.kompis.SttResult
import no.jodal.salat.apps.kompis.SttTest
import no.jodal.salat.apps.kompis.formatKilobytes
import no.jodal.salat.apps.kompis.formatReleased
import no.jodal.salat.apps.kompis.formatSeconds
import no.jodal.salat.apps.kompis.formatUsd

/** Test av tale til tekst gjennom OpenRouter: hold inne, snakk, slipp, og se hvor raskt teksten kommer. */
@Composable
fun SttTestScreen(store: KompisStore, bridge: NativeBridge, onBack: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val test = remember { SttTest(store, bridge) }
    var choosingModel by remember { mutableStateOf(false) }
    DisposableEffect(test) {
        test.open()
        onDispose { test.close() }
    }
    // Også når nøkkelen legges inn mens testen er åpen.
    LaunchedEffect(store.openRouterKey) { test.loadModels() }

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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("Tale til tekst", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
                        Text(
                            "Hold inne knappen og snakk, og slipp for å sende. Eller trykk én gang for å starte og én gang til for å sende.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (!store.hasOpenRouterKey) {
                    item(key = "key") { KeyNotice(onSettings) }
                }
                item(key = "model") {
                    ModelRow(
                        name = test.modelName(test.modelId),
                        maker = test.models.firstOrNull { it.id == test.modelId }?.maker,
                        average = test.averageMillis(test.modelId),
                        attempts = test.attempts(test.modelId),
                        onClick = { choosingModel = true },
                    )
                }
                item(key = "language") {
                    Options("Språk") {
                        SttLanguage.entries.forEach { language ->
                            OptionChip(language.label, selected = language == test.language) { test.selectLanguage(language) }
                        }
                    }
                }
                test.message?.let { message ->
                    item(key = "message") {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
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
                .padding(top = 36.dp, bottom = 16.dp),
        ) {
            TalkButton(test)
            Text(
                when {
                    test.isRecording && test.isTapMode -> "Trykk for å sende"
                    test.isRecording -> "Slipp for å sende"
                    test.isWaiting -> "Venter på teksten …"
                    else -> "Hold inne og snakk"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
            )
            MicrophoneStatus(test)
        }
    }

    if (choosingModel) {
        ModelSheet(test, onDismiss = { choosingModel = false })
    }
}

/** Hvilken mikrofon som brukes, med lydnivået, eller hvorfor den ikke virker. */
@Composable
private fun MicrophoneStatus(test: SttTest) {
    val colors = MaterialTheme.colorScheme
    val error = test.microphoneError
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        if (error != null) {
            Icon(Icons.Rounded.MicOff, contentDescription = null, tint = colors.error, modifier = Modifier.size(14.dp))
            Text(error, style = MaterialTheme.typography.labelMedium, color = colors.error, textAlign = TextAlign.Center)
        } else {
            VoiceBars(level = test.level, color = colors.onSurfaceVariant, barWidth = 2.dp, modifier = Modifier.height(12.dp))
            Text(test.microphone ?: "Starter mikrofonen …", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelRow(name: String, maker: String?, average: Long?, attempts: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Modell",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    listOfNotNull(
                        maker,
                        if (average == null) "ikke testet ennå" else "$average ms i snitt over $attempts forsøk",
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Velg modell", tint = colors.onSurfaceVariant)
        }
    }
}

/** Alle modellene for tale til tekst hos OpenRouter, de nyeste først, med søk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(test: SttTest, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val now = remember { kotlin.time.Clock.System.now().toEpochMilliseconds() }

    fun close(then: () -> Unit = {}) {
        then()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.background,
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Spacer(Modifier.size(64.dp, 1.dp))
                Text("Velg modell", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = { close() }) { Text("Ferdig", style = MaterialTheme.typography.bodyLarge) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Søk", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            inner()
                        }
                    },
                )
            }

            val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            val shown = test.models.filter { model -> words.all { it in model.name.lowercase() || it in model.id.lowercase() } }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            ) {
                when {
                    test.models.isEmpty() && test.isLoadingModels -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.onSurfaceVariant, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                    test.models.isEmpty() -> item(key = "empty") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                            Text(
                                test.modelsError ?: "Legg inn en OpenRouter-nøkkel i innstillingene for å se modellene.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (test.modelsError != null) TextButton(onClick = test::loadModels) { Text("Prøv igjen") }
                        }
                    }
                    shown.isEmpty() -> item(key = "none") {
                        Text(
                            "Ingen modeller passer med «${query.trim()}».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { model ->
                    if (model != shown.first()) HorizontalDivider(color = colors.outlineVariant)
                    ModelOption(
                        model = model,
                        released = formatReleased(model.createdAtSeconds, now),
                        average = test.averageMillis(model.id),
                        selected = model.id == test.modelId,
                        onClick = { close { test.selectModel(model.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelOption(model: OpenRouterModel, released: String, average: Long?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(model.shortName, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(
                listOfNotNull(model.maker, released, average?.let { "$it ms" }).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(colors.primary) else Modifier.border(2.dp, colors.outlineVariant, CircleShape)),
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${result.millis} ms", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                "${result.modelName}  ·  ${result.language.label}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/**
 * Den runde knappen. Holdes den inne, sendes opptaket når man slipper. Et kort trykk, eller et trykk
 * iOS avbryter (f.eks. nær kanten nederst), starter opptaket, og neste trykk sender det.
 */
@Composable
private fun TalkButton(test: SttTest) {
    val colors = MaterialTheme.colorScheme
    val recording by rememberUpdatedState(test.isRecording)
    val scale by animateFloatAsState(if (test.isRecording) 1.12f else 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (test.isRecording) Brush.linearGradient(KompisPalette.Aurora) else Brush.linearGradient(listOf(colors.primary, colors.primary)))
            .pointerInput(test) {
                detectTapGestures(
                    onPress = {
                        if (recording) {
                            // Andre trykk i trykk-modus.
                            Haptics.tap()
                            test.stopRecording()
                            return@detectTapGestures
                        }
                        Haptics.firm()
                        test.startRecording()
                        val pressedAt = TimeSource.Monotonic.markNow()
                        val released = tryAwaitRelease()
                        if (released && pressedAt.elapsedNow() >= HoldThreshold) {
                            Haptics.tap()
                            test.stopRecording()
                        } else {
                            test.switchToTapMode()
                        }
                    },
                )
            },
    ) {
        when {
            test.isRecording -> VoiceBars(level = test.level, color = Color.White, barWidth = 4.dp, modifier = Modifier.height(30.dp))
            test.isWaiting -> CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            else -> Icon(Icons.Rounded.Mic, contentDescription = "Hold inne og snakk", tint = colors.onPrimary, modifier = Modifier.size(34.dp))
        }
    }
}

/** Kortere trykk enn dette regnes som et trykk, ikke at knappen holdes inne. */
private val HoldThreshold = 400.milliseconds
