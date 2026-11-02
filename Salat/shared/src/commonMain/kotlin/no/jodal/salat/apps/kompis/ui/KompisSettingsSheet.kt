package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.jodal.salat.apps.kompis.DefaultPersonality
import no.jodal.salat.apps.kompis.KompisEngine
import no.jodal.salat.apps.kompis.KompisStore
import no.jodal.salat.apps.kompis.KompisVoice
import no.jodal.salat.apps.rise.ui.SectionDivider
import no.jodal.salat.apps.rise.ui.SettingsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KompisSettingsSheet(store: KompisStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        KompisSettingsContent(store) { save ->
            save()
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
}

/** Innholdet i [KompisSettingsSheet]. [onClose] kalles med det som skal skje før arket lukkes. */
@Composable
fun KompisSettingsContent(store: KompisStore, onClose: (save: () -> Unit) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var openRouterKey by remember { mutableStateOf(store.openRouterKey) }
    var draft by remember { mutableStateOf(store.data.settings) }
    var showGemini by remember { mutableStateOf(false) }
    var maxInput by remember { mutableStateOf(draft.llmRouting.maxInput?.toString().orEmpty()) }
    var maxOutput by remember { mutableStateOf(draft.llmRouting.maxOutput?.toString().orEmpty()) }
    val validPrices = no.jodal.salat.apps.kompis.validPriceLimit(maxInput) && no.jodal.salat.apps.kompis.validPriceLimit(maxOutput)
    val uriHandler = LocalUriHandler.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = { onClose {} }) { Text("Avbryt", style = MaterialTheme.typography.bodyLarge) }
            Text("Innstillinger", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TextButton(enabled = validPrices, onClick = {
                onClose {
                    store.saveApiKey(apiKey)
                    store.saveOpenRouterKey(openRouterKey)
                    store.update { it.copy(settings = draft.copy(
                        personality = draft.personality.trim().ifEmpty { DefaultPersonality },
                        llmRouting = draft.llmRouting.copy(
                            maxInput = no.jodal.salat.apps.kompis.parsePriceLimit(maxInput),
                            maxOutput = no.jodal.salat.apps.kompis.parsePriceLimit(maxOutput),
                        ),
                    )) }
                }
            }) { Text("Lagre", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) }
        }

        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection("OpenRouter") {
                KeyField(openRouterKey, onValueChange = { openRouterKey = it })
                Text("Nøkkelen brukes til alle tre stegene og lagres bare i nøkkelringen på telefonen.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = { uriHandler.openUri("https://openrouter.ai/settings/keys") }) { Text("Lag en OpenRouter-nøkkel") }
            }

            OpenRouterModels(store, openRouterKey, draft) { draft = it }

            SettingsSection("Makspris for språkmodellen") {
                Text("USD per million tokens. Tomt felt betyr ingen grense. 0 betyr bare gratis.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                PriceLimit("Tokens inn", maxInput) { maxInput = it }
                PriceLimit("Tokens ut", maxOutput) { maxOutput = it }
                Text("Hvis ingen leverandør oppfyller prisgrensene, får du en feil i stedet for et dyrere svar. Grensene gjelder LLM-en, ikke lydstegene.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }

            SettingsSection("Samtale") {
                RadioRow("Hold inne og snakk", "Hold inne knappen mens du snakker.", selected = !draft.handsFree) { draft = draft.copy(handsFree = false) }
                SectionDivider()
                RadioRow("Håndfri", "Kompis hører når du snakker og når du er ferdig. Du kan avbryte den.", selected = draft.handsFree) { draft = draft.copy(handsFree = true) }
            }

            SettingsSection("Personlighet") {
                BasicTextField(
                    value = draft.personality,
                    onValueChange = { draft = draft.copy(personality = it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                )
                SectionDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hvordan Kompis skal være og snakke.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { draft = draft.copy(personality = DefaultPersonality) }) { Text("Tilbakestill") }
                }
            }

            Text("Trykk Lagre for å ta valgene i bruk. Motorbytte gjelder neste samtaleøkt. Samtalene lagres bare på telefonen.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

            if (showGemini) {
                SettingsSection("Gemini Live") {
                    Text("Gemini Live bruker sin egen nøkkel og stemme, i stedet for de tre OpenRouter-stegene.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    KeyField(apiKey, onValueChange = { apiKey = it })
                    TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) { Text("Lag en nøkkel i Google AI Studio") }
                    ChoiceMenu("Stemme", draft.voice, KompisVoice.entries.map { it.id to "${it.id} – ${it.description}" }) { draft = draft.copy(voice = it) }
                    TextButton(onClick = { draft = draft.copy(engine = KompisEngine.OpenRouter.id); showGemini = false }) { Text("Slå av Gemini Live og bruk OpenRouter") }
                }
            }
            TextButton(
                onClick = {
                    if (showGemini) showGemini = false
                    else { showGemini = true; draft = draft.copy(engine = KompisEngine.GeminiLive.id) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(when {
                    showGemini -> "Skjul Gemini Live-innstillinger"
                    draft.engine == KompisEngine.GeminiLive.id -> "Gemini Live er på · vis innstillinger"
                    else -> "Slå på Gemini Live"
                })
            }
            Box(Modifier.height(32.dp))
        }
    }
}

/** Et felt for en API-nøkkel, skjult til man trykker på øyet. */
@Composable
private fun KeyField(value: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showKey by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Lim inn nøkkelen her", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable { showKey = !showKey },
        ) {
            Icon(
                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (showKey) "Skjul nøkkelen" else "Vis nøkkelen",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** En rad med tittel og beskrivelse, og en rund avkrysning til høyre. */
@Composable
private fun RadioRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        RadioDot(selected)
    }
}
