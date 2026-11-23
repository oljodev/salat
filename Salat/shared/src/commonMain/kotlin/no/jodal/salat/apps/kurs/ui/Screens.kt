package no.jodal.salat.apps.kurs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.ui.Card
import no.jodal.salat.apps.brems.ui.Footnote
import no.jodal.salat.apps.brems.ui.GroupLabel
import no.jodal.salat.apps.brems.ui.LargeTitle
import no.jodal.salat.apps.brems.ui.ListRow
import no.jodal.salat.apps.brems.ui.NavBar
import no.jodal.salat.apps.brems.ui.PrimaryButton
import no.jodal.salat.apps.brems.ui.RowDivider
import no.jodal.salat.apps.brems.ui.Switch
import no.jodal.salat.apps.brems.ui.TileColors
import no.jodal.salat.apps.brems.ui.palette
import no.jodal.salat.apps.kurs.KursStore
import no.jodal.salat.apps.kurs.Points
import no.jodal.salat.apps.kurs.insights
import no.jodal.salat.apps.kurs.points
import no.jodal.salat.ui.pressable

/** Et lite kort som minner om noe, og åpner det når man trykker. */
@Composable
fun ReminderCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        ListRow(title, icon = Icons.Rounded.Bedtime, iconTint = TileColors.Indigo, subtitle = subtitle, chevron = true)
    }
}

/** Etter check-in: poengene og tilbakemeldingen fra innsiktsmotoren. */
@Composable
fun SavedScreen(store: KursStore, date: LocalDate, onDone: () -> Unit, onEdit: () -> Unit) {
    val log = store.log(date) ?: return onDone()
    val lines = remember(date) { insights(store.data.logs, date, store.today()) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(formatDay(date).uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.textDim)
        Spacer(Modifier.height(8.dp))
        Text("${log.points}", style = MaterialTheme.typography.displayLarge, color = if (log.points >= Points.ApprovedDay) KursAccent else palette.text)
        Text(
            if (log.points >= Points.ApprovedDay) "poeng · godkjent dag" else "poeng · ${Points.ApprovedDay - log.points} igjen til godkjent",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textDim,
        )
        Spacer(Modifier.height(32.dp))
        Card(padding = 18.dp) {
            lines.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Text(line, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Ferdig", onDone)
        Text(
            "Endre loggen",
            style = MaterialTheme.typography.titleSmall,
            color = KursAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().pressable(onEdit).padding(14.dp),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
fun SettingsScreen(store: KursStore, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var message by remember { mutableStateOf<String?>(null) }
    val settings = store.data.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Kurs", onBack)
        Box(Modifier.padding(horizontal = 4.dp)) { LargeTitle("Innstillinger") }

        GroupLabel("Påminnelser")
        Card {
            ListRow("Skjermstopp kl. 21:45", icon = Icons.Rounded.Bedtime, iconTint = TileColors.Indigo, subtitle = "15 minutter før skjermene skal av") {
                Switch(settings.eveningReminder) { on -> store.updateSettings { it.copy(eveningReminder = on) } }
            }
            RowDivider()
            ListRow("Morgenlogg", icon = Icons.Rounded.WbSunny, iconTint = TileColors.Orange, subtitle = "08:15 på hverdager, 09:30 i helgen, hvis gårsdagen mangler") {
                Switch(settings.morningReminder) { on -> store.updateSettings { it.copy(morningReminder = on) } }
            }
            RowDivider()
            ListRow("Luftetur kl. 16:30", icon = Icons.Rounded.DirectionsWalk, iconTint = TileColors.Teal, subtitle = "På dager som ikke er treningsdager") {
                Switch(settings.afternoonReminder) { on -> store.updateSettings { it.copy(afternoonReminder = on) } }
            }
        }

        Spacer(Modifier.height(24.dp))
        GroupLabel("Sikkerhetskopi")
        Card {
            ListRow("Kopier alt som JSON", icon = Icons.Rounded.ContentCopy, iconTint = TileColors.Blue, onClick = {
                clipboard.setText(AnnotatedString(store.exportJson()))
                message = "${store.data.logs.size} logger er kopiert. Lim dem inn i Notater eller en fil."
            })
            RowDivider()
            ListRow("Hent fra utklippstavlen", icon = Icons.Rounded.ContentPaste, iconTint = TileColors.Gray, subtitle = "Erstatter alt i Kurs", onClick = {
                val text = clipboard.getText()?.text.orEmpty()
                message = store.importJson(text) ?: "Hentet ${store.data.logs.size} logger."
            })
        }
        message?.let { Footnote(it) }
        Spacer(Modifier.height(32.dp))
    }
}
