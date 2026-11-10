package no.jodal.salat.apps.brems.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.appName

/** Hvordan man lager automasjonene i Snarveier som forteller Brems når apper åpnes og lukkes. */
@Composable
fun GuideScreen(bridge: NativeBridge, store: BremsStore, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Brems", onBack)
        Box(Modifier.padding(horizontal = 4.dp)) { LargeTitle("Oppsett") }
        Text(
            "iOS lar ikke apper se hva du bruker telefonen til. Derfor sier Snarveier fra til Brems når en app åpnes og lukkes. " +
                "Du lager to automasjoner, én gang.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textDim,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(20.dp))
        Status(store)

        Section("1", "Når apper åpnes")
        Card(padding = 20.dp) {
            Steps(
                listOf(
                    "Åpne **Snarveier**, gå til **Automatisering** og trykk **+**.",
                    "Velg **App**. Trykk **Velg** og huk av appene du vil bremse, f.eks. Snapchat og YouTube.",
                    "Huk av **Er åpnet**, og ta bort **Er lukket**. Velg **Kjør umiddelbart** og trykk **Neste**.",
                    "Velg **Ny tom automatisering**, søk etter **Brems** og legg til **Brems: App åpnet**.",
                    "Legg til **Hvis** under. Velg **Resultat av Brems: App åpnet** som inndata, **er** som betingelse, og skriv **Pause**.",
                    "Legg til **Åpne URL-er** inne i Hvis, og skriv **salat://brems/pause**. Da åpnes nedtellingen med en gang.",
                ),
            )
        }

        Section("2", "Når apper lukkes")
        Card(padding = 20.dp) {
            Steps(
                listOf(
                    "Lag en ny automasjon med **App** og de samme appene.",
                    "Huk av **Er lukket**, og ta bort **Er åpnet**. Velg **Kjør umiddelbart**.",
                    "Legg til **Brems: App lukket**.",
                ),
            )
        }

        Spacer(Modifier.height(20.dp))
        Card(padding = 20.dp) {
            Text("Tid per app", style = MaterialTheme.typography.titleMedium, color = palette.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Snarveier sier ikke hvilken av appene som ble åpnet. Lag én åpnet-automasjon per app og skriv navnet i App-feltet " +
                    "i handlingen, så ser du tiden per app. Da sender Brems deg også rett tilbake til appen etter pausen.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim,
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton("Åpne Snarveier", { bridge.openUrl("shortcuts://") })

        Spacer(Modifier.height(28.dp))
        GroupLabel("Logg")
        Log(store)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(number: String, title: String) {
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp).clip(CircleShape).background(palette.accent)) {
            Text(number, style = MaterialTheme.typography.labelLarge, color = palette.onAccent)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = palette.text)
    }
}

/** Om Brems har hørt fra Snarveier, så man ser at oppsettet virker. */
@Composable
private fun Status(store: BremsStore) {
    val last = store.data.opens.lastOrNull()
    Card {
        ListRow(
            title = if (last != null) "Brems hører fra Snarveier" else "Venter på Snarveier",
            icon = if (last != null) Icons.Rounded.CheckCircle else Icons.Rounded.HourglassEmpty,
            iconTint = if (last != null) TileColors.Teal else TileColors.Orange,
            subtitle = if (last != null) "Sist: ${appName(last.app)} åpnet kl. ${clock(last.atMillis)}" else "Lag automasjonene, og åpne en av appene for å teste.",
        )
    }
}

@Composable
private fun Steps(steps: List<String>) {
    val accent = palette.accent
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        steps.forEachIndexed { index, step ->
            Row {
                Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = palette.textFaint, modifier = Modifier.width(22.dp))
                Text(step.bold(accent), style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Det siste Brems har hørt fra Snarveier, nyeste først. */
@Composable
private fun Log(store: BremsStore) {
    val entries = store.data.log.asReversed()
    Card(padding = 16.dp) {
        if (entries.isEmpty()) {
            Text("Ingenting ennå.", style = MaterialTheme.typography.bodyMedium, color = palette.textDim)
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row {
                Text(clock(entry.atMillis, seconds = true), style = MaterialTheme.typography.labelMedium, color = palette.textFaint, modifier = Modifier.width(70.dp))
                Text(entry.text, style = MaterialTheme.typography.bodySmall, color = palette.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Gjør **slik** tekst fet, som navnene på knappene i Snarveier. */
private fun String.bold(color: Color) = buildAnnotatedString {
    split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) { append(part) } else append(part)
    }
}

/** «14:02», eller «14:02:09». */
private fun clock(millis: Long, seconds: Boolean = false): String {
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).time
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(time.hour)}:${two(time.minute)}" + if (seconds) ":${two(time.second)}" else ""
}
