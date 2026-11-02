package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.jodal.salat.apps.kompis.MessageInfo
import no.jodal.salat.apps.kompis.Role
import no.jodal.salat.apps.kompis.formatUsd

/** Målinger vises bare når man ber om dem via et langt trykk. */
@Composable
fun ChatMessage(role: Role, text: String, dark: Boolean, draft: Boolean = false, info: MessageInfo? = null) {
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().combinedClickable(
        enabled = !draft,
        onClick = {},
        onLongClickLabel = "Vis meldingsvalg",
        onLongClick = { menu = true },
    )) {
        ChatMessageBody(role, text, dark, draft)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Vis meldingsinfo") }, onClick = {
                menu = false
                details = true
            })
        }
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("Meldingsinfo") },
            confirmButton = { TextButton(onClick = { details = false }) { Text("Ferdig") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (info == null) {
                        Text(if (role == Role.User) "Din melding. Ingen LLM-målinger." else "Målinger ble ikke lagret for denne meldingen.")
                    } else {
                        Text("Motor: ${info.engine}")
                        Text("LLM: ${info.model}")
                        val stats = info.stats
                        Text("TPS: ${stats?.tokensPerSecond?.let { "$it tokens/s" } ?: "Ikke tilgjengelig"}")
                        stats?.provider?.let { Text("Leverandør: $it") }
                        stats?.firstTokenMillis?.let { Text("Første token (latency): $it ms") }
                        stats?.firstSentenceMillis?.let { Text("Første setning: $it ms") }
                        stats?.let { Text("LLM ferdig: ${it.totalMillis} ms") }
                        stats?.promptTokens?.let { Text("Tokens inn: $it") }
                        stats?.cachedTokens?.let { Text("Tokens fra cache: $it") }
                        stats?.completionTokens?.let { Text("Tokens ut: $it") }
                        stats?.reasoningTokens?.let { Text("Tokens til tenking: $it") }
                        stats?.costUsd?.let { Text("LLM-kostnad: ${formatUsd(it)}") }
                        info.timings?.let { timing ->
                            Text("Tale til tekst: ${timing.sttMillis} ms")
                            timing.voiceMillis?.let { Text("Første lyd fra stemmen: $it ms") }
                            timing.heardAfterMillis?.let { Text("Fra du sluttet til Kompis snakket: $it ms") }
                            timing.totalMillis?.let { Text("Hele svarturen: $it ms") }
                        }
                        if (info.interrupted) Text("Svaret ble avbrutt. Målingene kan være ufullstendige.")
                        if (stats == null) Text("Tokenmålinger og LLM-latency er ikke tilgjengelige for dette svaret.")
                        else Text("TPS er et anslag målt på telefonen: (tokens ut uten tenking − 1) / tiden fra første token til strømmen er ferdig. Nettverk inngår; taletiden gjør ikke det.")
                    }
                }
            },
        )
    }
}
