package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import no.jodal.salat.apps.kompis.*
import no.jodal.salat.apps.rise.ui.SettingsSection

/** Avbrutte hentinger får ikke overskrive et nyere modell-/nøkkelvalg. */
private suspend fun fetchCatalog(store: KompisStore, key: String, path: String): Pair<String?, String?> =
    suspendCancellableCoroutine { continuation ->
        store.openRouterGet(path, key.trim()) { body, error ->
            if (continuation.isActive) continuation.resume(body to error)
        }
    }

@Composable
internal fun OpenRouterModels(store: KompisStore, key: String, settings: KompisSettings, onChange: (KompisSettings) -> Unit) {
    CatalogSection("1. Tale til tekst", OpenRouter.SttModelsPath, settings.sttModel, key, store,
        onSelect = { onChange(settings.copy(sttModel = it)) },
    ) { _ ->
        ChoiceMenu("Språk", SttLanguage.byId(settings.sttLanguage).label,
            SttLanguage.entries.map { it.id to it.label },
        ) { onChange(settings.copy(sttLanguage = it)) }
        Hint("OpenRouter velger leverandør automatisk for tale til tekst.")
    }
    CatalogSection("2. Språkmodell", OpenRouter.LlmModelsPath, settings.llmModel, key, store,
        onSelect = { onChange(settings.copy(llmModel = it, llmProviders = emptyList())) },
    ) { _ ->
        ProviderSettings(store, key, settings, onChange)
    }
    CatalogSection("3. Tekst til tale", OpenRouter.TtsModelsPath, settings.ttsModel, key, store,
        onSelect = { onChange(settings.copy(ttsModel = it)) },
    ) { models ->
        val voices = models.firstOrNull { it.id == settings.ttsModel }?.voices.orEmpty().sortedBy { !isNorwegianVoice(it) }
        if (voices.isNotEmpty()) ChoiceMenu("Stemme", settings.ttsVoices[settings.ttsModel] ?: voices.first(), voices.map { it to it }) {
            onChange(settings.copy(ttsVoices = settings.ttsVoices + (settings.ttsModel to it)))
        }
        OutlinedTextField(
            value = settings.ttsInstructions,
            onValueChange = { onChange(settings.copy(ttsInstructions = it)) },
            label = { Text("Stemmens stil") }, placeholder = { Text("For eksempel varm og rolig") },
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("Stil støttes av noen stemmemodeller. OpenRouter velger leverandør automatisk for tekst til tale.")
    }
}

@Composable
private fun CatalogSection(
    title: String, path: String, selected: String, key: String, store: KompisStore,
    onSelect: (String) -> Unit, content: @Composable (List<OpenRouterModel>) -> Unit,
) {
    var models by remember { mutableStateOf(emptyList<OpenRouterModel>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(key.trim(), retry) {
        models = emptyList()
        error = null
        loading = false
        if (key.isBlank()) { error = "Legg inn en OpenRouter-nøkkel først."; return@LaunchedEffect }
        loading = true
        delay(500)
        val (body, failure) = fetchCatalog(store, key, path)
        val parsed = body?.let { runCatching { OpenRouter.parseModels(it) }.getOrNull() }
        models = parsed.orEmpty()
        error = if (parsed == null) failure ?: "Kunne ikke hente modeller." else null
        loading = false
        if (parsed != null) when (path) {
            OpenRouter.LlmModelsPath -> ModelCache.llm = parsed
            OpenRouter.TtsModelsPath -> ModelCache.tts = parsed
        }
    }
    SettingsSection(title) {
        ModelRow(models.firstOrNull { it.id == selected }?.shortName ?: selected.substringAfter('/'), selected) { picker = true }
        content(models)
    }
    if (picker) ModelSheet(
        models = models, isLoading = loading, error = error, selectedId = selected,
        averageMillis = { null }, onRetry = { retry++ }, onSelect = { if (it != selected) onSelect(it) },
        onDismiss = { picker = false },
    )
}

@Composable
private fun ProviderSettings(store: KompisStore, key: String, settings: KompisSettings, onChange: (KompisSettings) -> Unit) {
    val routing = settings.llmRouting
    ChoiceMenu("Velg leverandør etter", when (routing.strategy) {
        "latency" -> "Lavest latency"
        "price" -> "Lavest pris"
        "auto" -> "OpenRouter velger"
        else -> "Min rekkefølge"
    }, listOf("auto" to "OpenRouter velger", "latency" to "Lavest latency", "price" to "Lavest pris", "manual" to "Min rekkefølge")) {
        onChange(settings.copy(llmRouting = routing.copy(strategy = it)))
    }
    if (routing.strategy == "auto") {
        Hint("OpenRouter balanserer pris, fart og tilgjengelighet. Prisgrensene under gjelder fortsatt.")
        return
    }
    var providers by remember { mutableStateOf(emptyList<OpenRouterProvider>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(settings.llmModel, key.trim(), retry) {
        providers = emptyList()
        error = null
        loading = false
        if (key.isBlank()) { error = "Legg inn OpenRouter-nøkkelen for å hente leverandører."; return@LaunchedEffect }
        loading = true
        delay(500)
        val (body, failure) = fetchCatalog(store, key, OpenRouter.endpointsPath(settings.llmModel))
        val parsed = body?.let { runCatching { OpenRouter.parseProviders(it) }.getOrNull() }
        providers = parsed.orEmpty()
        error = if (parsed == null) failure ?: "Kunne ikke hente leverandører." else null
        loading = false
    }
    val order = settings.llmProviders
    Hint(if (routing.strategy == "manual") "Velg leverandører og flytt dem opp eller ned. Bare de valgte brukes, i denne rekkefølgen. Uten valg bestemmer OpenRouter."
        else "OpenRouter prioriterer ${if (routing.strategy == "latency") "lav ventetid til første token" else "lav pris"}. Velg leverandører under for å begrense utvalget; uten valg brukes alle tilgjengelige.")
    order.forEachIndexed { index, slug ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (routing.strategy == "manual") "${index + 1}. " else "") + (providers.firstOrNull { it.slug == slug }?.name ?: slug), Modifier.weight(1f))
            if (routing.strategy == "manual") {
                IconButton(enabled = index > 0, onClick = {
                    val reordered = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    onChange(settings.copy(llmProviders = reordered))
                }) { Icon(Icons.Rounded.ArrowUpward, "Flytt $slug opp") }
                IconButton(enabled = index < order.lastIndex, onClick = {
                    val reordered = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    onChange(settings.copy(llmProviders = reordered))
                }) { Icon(Icons.Rounded.ArrowDownward, "Flytt $slug ned") }
            }
            IconButton(onClick = { onChange(settings.copy(llmProviders = order - slug)) }) { Icon(Icons.Rounded.Close, "Fjern $slug") }
        }
    }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Skjul leverandører" else "Velg leverandører") }
    if (expanded) {
        if (loading) Hint("Henter leverandører …")
        error?.let { Hint(it); TextButton(onClick = { retry++ }) { Text("Prøv igjen") } }
        providers.filter { it.slug !in order }.forEach { provider ->
            TextButton(onClick = { onChange(settings.copy(llmProviders = order + provider.slug)) }) { Text("+ ${provider.name}") }
        }
        if (!loading && error == null && providers.isEmpty()) Hint("Ingen leverandører ble oppgitt for denne modellen.")
    }
}

@Composable
internal fun ChoiceMenu(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("$label: $selected") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(id) }) }
        }
    }
}

@Composable
internal fun PriceLimit(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = { Text("Ingen grense") }, singleLine = true,
        isError = !validPriceLimit(value),
        supportingText = { if (!validPriceLimit(value)) Text("Skriv et tall som er 0 eller større.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
