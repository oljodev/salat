package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
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
    var voice by remember { mutableStateOf(store.data.settings.voice) }
    var personality by remember { mutableStateOf(store.data.settings.personality) }
    val uriHandler = LocalUriHandler.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = { onClose {} }) { Text("Avbryt", style = MaterialTheme.typography.bodyLarge) }
            Text("Innstillinger", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                onClose {
                    store.saveApiKey(apiKey)
                    store.saveOpenRouterKey(openRouterKey)
                    store.update { it.copy(settings = it.settings.copy(voice = voice, personality = personality.trim().ifEmpty { DefaultPersonality })) }
                }
            }) {
                Text("Lagre", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection("Gemini-nøkkel") {
                KeyField(apiKey, onValueChange = { apiKey = it })
                SectionDivider()
                Text(
                    "Nøkkelen lagres bare i nøkkelringen på telefonen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "Lag en nøkkel i Google AI Studio",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { uriHandler.openUri("https://aistudio.google.com/apikey") },
                )
            }

            SettingsSection("OpenRouter-nøkkel") {
                KeyField(openRouterKey, onValueChange = { openRouterKey = it })
                SectionDivider()
                Text(
                    "Brukes til å teste tale til tekst. Lagres bare i nøkkelringen på telefonen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "Lag en nøkkel hos OpenRouter",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { uriHandler.openUri("https://openrouter.ai/settings/keys") },
                )
            }

            SettingsSection("Stemme") {
                KompisVoice.entries.forEachIndexed { index, option ->
                    if (index > 0) SectionDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { voice = option.id },
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.id, style = MaterialTheme.typography.bodyLarge)
                            Text(option.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        val selected = option.id == voice
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.background(colors.primary)
                                    } else {
                                        Modifier.border(2.dp, colors.outlineVariant, CircleShape)
                                    },
                                ),
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            SettingsSection("Personlighet") {
                BasicTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                )
                SectionDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Hvordan Kompis skal være og snakke.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { personality = DefaultPersonality }) { Text("Tilbakestill") }
                }
            }

            Text(
                "Kompis bruker Gemini 3.8 Live. Samtalene lagres bare på telefonen.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
