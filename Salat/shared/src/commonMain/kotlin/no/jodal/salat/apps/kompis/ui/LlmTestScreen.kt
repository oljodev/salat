package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.KompisStore
import no.jodal.salat.apps.kompis.LlmStats
import no.jodal.salat.apps.kompis.LlmTest
import no.jodal.salat.apps.kompis.Role
import no.jodal.salat.apps.kompis.formatSeconds
import no.jodal.salat.apps.kompis.formatUsd

/** Test av språkmodellen gjennom OpenRouter: en chat som viser hvor raskt svarene kommer. */
@Composable
fun LlmTestScreen(store: KompisStore, bridge: NativeBridge, onBack: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val test = remember { LlmTest(store, bridge) }
    var input by remember { mutableStateOf("") }
    var choosingModel by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(test) {
        test.open()
        onDispose { test.close() }
    }
    LaunchedEffect(store.openRouterKey) {
        test.loadModels()
        test.loadProviders()
    }
    // Forbindelsen åpnes mens man skriver, så den er klar når man sender.
    LaunchedEffect(input.isNotEmpty()) {
        if (input.isNotEmpty()) bridge.openRouterWarmUp()
    }
    // Følg med nederst mens svaret kommer.
    LaunchedEffect(test.messages.size, test.draft?.length) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0 && (test.messages.isNotEmpty() || test.isStreaming)) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Tilbake", onClick = onBack)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Description, "Se prompten", onClick = { showPrompt = true })
            CircleIconButton(Icons.Rounded.Refresh, "Ny samtale", onClick = test::clear)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Språkmodell", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
                    Text(
                        "Skriv som om du snakket. Svarene får samme instruksjoner som når de skal leses opp, og du ser hvor raskt de kommer.",
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
                ModelRow(
                    name = test.modelName(test.modelId),
                    details = listOfNotNull(model?.maker, test.modelId).joinToString("  ·  "),
                    onClick = { choosingModel = true },
                )
            }
            item(key = "providers") { Providers(test) }

            test.messages.forEachIndexed { index, message ->
                item(key = "message-$index") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChatMessage(message.role, message.text, dark = false)
                        message.stats?.let { StatsLine(it) }
                    }
                }
            }
            test.draft?.let { draft ->
                item(key = "draft") { ChatMessage(Role.Kompis, draft.ifEmpty { "…" }, dark = false, draft = true) }
            }
            test.message?.let { message ->
                item(key = "error") { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            streaming = test.isStreaming,
            onSend = {
                test.send(input)
                if (test.isStreaming) input = ""
            },
            onStop = test::stop,
        )
    }

    if (choosingModel) {
        ModelSheet(
            models = test.models,
            isLoading = test.isLoadingModels,
            error = test.modelsError,
            selectedId = test.modelId,
            averageMillis = { null },
            onRetry = test::loadModels,
            onSelect = test::selectModel,
            onDismiss = { choosingModel = false },
        )
    }
    if (showPrompt) {
        PromptSheet(test.prompt(), onDismiss = { showPrompt = false })
    }
}

/** Leverandørene som kjører modellen. De valgte prøves i rekkefølgen de ble valgt, og ingen andre. */
@Composable
private fun Providers(test: LlmTest) {
    val colors = MaterialTheme.colorScheme
    val order = test.providerOrder
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Options("Leverandører") {
            // De valgte vises selv før listen er hentet.
            val slugs = (order + test.providers.map { it.slug }).distinct()
            slugs.forEach { slug ->
                val name = test.providers.firstOrNull { it.slug == slug }?.name ?: slug
                val position = order.indexOf(slug)
                OptionChip(
                    if (position >= 0) "${position + 1}. $name" else name,
                    selected = position >= 0,
                ) { test.toggleProvider(slug) }
            }
        }
        val names = order.map { slug -> test.providers.firstOrNull { it.slug == slug }?.name ?: slug }
        Text(
            when {
                names.isEmpty() && test.isLoadingProviders -> "Henter leverandørene …"
                names.isEmpty() -> "OpenRouter velger leverandør, og bytter hvis én feiler."
                names.size == 1 -> "Bruker bare ${names.first()}."
                else -> "Prøver ${names.joinToString(", så ")}, og ingen andre."
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Hvor raskt svaret kom, og hva det kostet. */
@Composable
private fun StatsLine(stats: LlmStats) {
    val colors = MaterialTheme.colorScheme
    val timing = listOfNotNull(
        stats.firstTokenMillis?.let { "første token $it ms" },
        stats.firstSentenceMillis?.let { "første setning $it ms" },
        "ferdig ${formatSeconds(stats.totalMillis / 1000.0)}",
    )
    val details = listOfNotNull(
        stats.provider,
        stats.tokensPerSecond?.let { "$it tokens/s" },
        stats.promptTokens?.let { prompt ->
            "$prompt inn" + (stats.cachedTokens?.takeIf { it > 0 }?.let { " ($it bufret)" } ?: "")
        },
        stats.completionTokens?.let { "$it ut" },
        stats.reasoningTokens?.takeIf { it > 0 }?.let { "tenkte $it tokens" },
        stats.costUsd?.let(::formatUsd),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(timing.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        Text(details.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, streaming: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val canSend = value.isNotBlank()
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLines = 5,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Si noe til Kompis", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    inner()
                }
            },
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .alpha(if (streaming || canSend) 1f else 0.35f)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(enabled = streaming || canSend, onClick = if (streaming) onStop else onSend),
        ) {
            Icon(
                if (streaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                contentDescription = if (streaming) "Stopp svaret" else "Send",
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Hele prompten modellen får, så den er lett å lese og forbedre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptSheet(prompt: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.background,
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Spacer(Modifier.size(64.dp, 1.dp))
                Text("Prompten", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }) {
                    Text("Ferdig", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            )
        }
    }
}
