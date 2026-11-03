package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.jodal.salat.apps.kompis.MessageInfo
import no.jodal.salat.apps.kompis.Role
import no.jodal.salat.apps.kompis.formatSeconds
import no.jodal.salat.apps.kompis.formatUsd
import no.jodal.salat.apps.rise.ui.SectionDivider
import no.jodal.salat.apps.rise.ui.SettingsSection

/** Samme panel, typografi og grupperte kort som resten av Kompis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageInfoSheet(role: Role, info: MessageInfo?, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var explanation by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.background,
    ) {
        Column(Modifier.fillMaxHeight(if (info == null) 0.45f else 0.9f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Spacer(Modifier.size(64.dp, 1.dp))
                Text("Meldingsinfo", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }) {
                    Text("Ferdig", style = MaterialTheme.typography.bodyLarge)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (role == Role.User) "Din melding" else "Bak svaret",
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.onBackground,
                        )
                        Text(
                            if (info == null) {
                                if (role == Role.User) "Meldinger fra deg har ingen LLM-målinger."
                                else "Denne meldingen har ingen lagrede målinger."
                            } else "Modellen, hastigheten og forbruket for akkurat dette svaret.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (info != null) {
                    val stats = info.stats
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                "Hastighet", stats?.tokensPerSecond?.toString(), "tokens / sekund",
                                Modifier.weight(1f),
                            )
                            MetricCard(
                                "Ventetid", stats?.firstTokenMillis?.let(::duration), "til første token",
                                Modifier.weight(1f),
                            )
                        }
                    }
                    if (info.interrupted) item {
                        Text(
                            "Avbrutt svar · Målingene kan være ufullstendige.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).padding(16.dp),
                        )
                    }
                    item {
                        SettingsSection("Modell") {
                            InfoRow("LLM", info.model)
                            SectionDivider()
                            InfoRow("Motor", info.engine)
                            stats?.provider?.let { SectionDivider(); InfoRow("Leverandør", it) }
                        }
                    }
                    if (stats != null) {
                        item {
                            SettingsSection("Språkmodellens tidsbruk") {
                                stats.firstTokenMillis?.let { InfoRow("Første token", duration(it)) }
                                stats.firstSentenceMillis?.let { InfoRow("Første setning", duration(it)) }
                                InfoRow("Hele LLM-svaret", duration(stats.totalMillis))
                            }
                        }
                        if (listOf(stats.promptTokens, stats.cachedTokens, stats.completionTokens, stats.reasoningTokens).any { it != null }) item {
                            SettingsSection("Tokenforbruk") {
                                stats.promptTokens?.let { InfoRow("Inn", it.toString()) }
                                stats.cachedTokens?.let { InfoRow("Fra cache", it.toString()) }
                                stats.completionTokens?.let { InfoRow("Ut", it.toString()) }
                                stats.reasoningTokens?.let { InfoRow("Til tenking", it.toString()) }
                            }
                        }
                        stats.costUsd?.let { cost -> item {
                            SettingsSection("Kostnad") {
                                InfoRow("Språkmodell", formatUsd(cost))
                                Text("Kun LLM-kallet. Tale til tekst og tekst til tale kommer i tillegg.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        } }
                    }
                    info.timings?.let { timing -> item {
                        SettingsSection("Fra tale til svar") {
                            InfoRow("Tale til tekst", duration(timing.sttMillis))
                            timing.voiceMillis?.let { InfoRow("Første lyd fra stemmen", duration(it)) }
                            timing.heardAfterMillis?.let { InfoRow("Fra du sluttet til du hørte svaret", duration(it)) }
                            timing.totalMillis?.let { InfoRow("Hele svarturen", duration(it)) }
                        }
                    } }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (stats == null) Text(
                                "Tokenforbruk og LLM-latency er ikke målt for dette svaret.",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            )
                            TextButton(onClick = { explanation = !explanation }) {
                                Text(if (explanation) "Skjul forklaring" else "Om målingene")
                            }
                            if (explanation) Text(
                                "TPS er et anslag målt på telefonen: (tokens ut uten tenking − 1) delt på tiden fra første token til strømmen er ferdig. Nettverk inngår; taletiden gjør ikke det.\n\nVentetid er tiden fra LLM-forespørselen ble sendt til første token kom. «Ikke målt» betyr at målingen mangler, ikke at tiden eller forbruket var null.",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String?, unit: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainer).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
        Text(if (value == null) "Ikke målt" else unit, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

private fun duration(millis: Long): String = if (millis < 1000) "$millis ms" else formatSeconds(millis / 1000.0)
