package no.jodal.salat.apps.brems.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.DefaultQuotes
import no.jodal.salat.apps.brems.DefaultReasons
import no.jodal.salat.ui.pressable

private val OpenPauseSteps = listOf(0, 5, 10, 15, 20, 30, 45, 60)
private val ReminderSteps = listOf(0, 1, 2, 3, 5, 7, 10, 15, 20, 30, 45, 60)
private val LimitSteps = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)
private val LimitPauseSteps = listOf(15, 30, 45, 60, 90, 120, 180, 300, 600)

@Composable
fun SettingsScreen(store: BremsStore, onBack: () -> Unit) {
    val settings = store.data.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Brems", onBack)
        Box(Modifier.padding(horizontal = 4.dp)) { LargeTitle("Innstillinger") }

        Card {
            ListRow("Brems er på", Icons.Rounded.PowerSettingsNew, TileColors.Teal, trailing = {
                Switch(settings.enabled) { on -> store.updateSettings { it.copy(enabled = on) } }
            })
        }
        Footnote("Skru av for å slippe pauser og varsler en stund.")

        Section("Pauser og grenser")
        Card {
            StepRow("Åpningspause", Icons.Rounded.Timer, TileColors.Indigo, settings.openPauseSeconds, OpenPauseSteps, ::seconds) { v ->
                store.updateSettings { it.copy(openPauseSeconds = v) }
            }
            RowDivider()
            StepRow("Første varsel", Icons.Rounded.NotificationsActive, TileColors.Orange, settings.reminderMinutes, ReminderSteps, ::minutes) { v ->
                store.updateSettings { it.copy(reminderMinutes = v) }
            }
            RowDivider()
            StepRow("Grense per økt", Icons.Rounded.HourglassBottom, TileColors.Pink, settings.limitMinutes, LimitSteps, ::minutes) { v ->
                store.updateSettings { it.copy(limitMinutes = v) }
            }
            RowDivider()
            StepRow("Grensepause", Icons.Rounded.SelfImprovement, TileColors.Teal, settings.limitPauseSeconds, LimitPauseSteps, ::seconds) { v ->
                store.updateSettings { it.copy(limitPauseSeconds = v) }
            }
            RowDivider()
            ToggleRow("Varsle igjen etter grensen", Icons.Rounded.Repeat, TileColors.Blue, settings.nagMinutes > 0) { on ->
                store.updateSettings { it.copy(nagMinutes = if (on) 2 else 0) }
            }
        }
        Footnote(
            "En økt er tiden fra du åpner en app til du lukker den. Går den over grensen, venter en lengre pause neste gang. " +
                "Tiden du er borte fra appene, teller med.",
        )

        Section("Pauseskjermen")
        Card {
            ToggleRow("Pust", Icons.Rounded.Air, TileColors.Blue, settings.breathing) { on -> store.updateSettings { it.copy(breathing = on) } }
            RowDivider()
            ToggleRow("Dagens bruk", Icons.Rounded.BarChart, TileColors.Indigo, settings.showUsage) { on -> store.updateSettings { it.copy(showUsage = on) } }
            RowDivider()
            ToggleRow("Spør hvorfor", Icons.Rounded.QuestionMark, TileColors.Orange, settings.askWhy) { on -> store.updateSettings { it.copy(askWhy = on) } }
            RowDivider()
            ToggleRow("Påminnelse", Icons.Rounded.FormatQuote, TileColors.Pink, settings.showQuote) { on -> store.updateSettings { it.copy(showQuote = on) } }
        }

        if (settings.askWhy) {
            Section("Grunner")
            TextList(
                items = settings.reasons,
                placeholder = "Ny grunn",
                onChange = { list -> store.updateSettings { it.copy(reasons = list) } },
                onReset = if (settings.reasons != DefaultReasons) ({ store.updateSettings { it.copy(reasons = DefaultReasons) } }) else null,
            )
        }

        if (settings.showQuote) {
            Section("Påminnelser")
            TextList(
                items = settings.quotes,
                placeholder = "Skriv din egen",
                onChange = { list -> store.updateSettings { it.copy(quotes = list) } },
                onReset = if (settings.quotes != DefaultQuotes) ({ store.updateSettings { it.copy(quotes = DefaultQuotes) } }) else null,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    GroupLabel(title)
}

@Composable
private fun ToggleRow(title: String, icon: ImageVector, tint: Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, icon, tint, trailing = { Switch(checked, onChange) })
}

@Composable
private fun StepRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    value: Int,
    steps: List<Int>,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    val index = steps.indexOfFirst { it >= value }.let { if (it < 0) steps.lastIndex else it }
    ListRow(title, icon, tint, trailing = {
        Stepper(
            value = format(value),
            onMinus = { onChange(steps[(index - 1).coerceAtLeast(0)]) },
            onPlus = { onChange(steps[(if (steps[index] == value) index + 1 else index).coerceAtMost(steps.lastIndex)]) },
            canMinus = value > steps.first(),
            canPlus = value < steps.last(),
        )
    })
}

private fun seconds(value: Int): String = when {
    value == 0 -> "Av"
    value < 60 -> "$value s"
    value % 60 == 0 -> "${value / 60} min"
    else -> "${value / 60}:${(value % 60).toString().padStart(2, '0')}"
}

private fun minutes(value: Int): String = if (value == 0) "Av" else "$value min"

/** En liste med tekster man kan slette fra og legge til i, som en liste i iOS. */
@Composable
private fun TextList(items: List<String>, placeholder: String, onChange: (List<String>) -> Unit, onReset: (() -> Unit)?) {
    var draft by remember { mutableStateOf("") }
    fun add() {
        val text = draft.trim()
        if (text.isNotEmpty() && text !in items) {
            onChange(items + text)
            Haptics.success()
        }
        draft = ""
    }
    Card {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 8.dp, end = 16.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape).pressable({ onChange(items - item) }, pressedScale = 0.85f),
                ) {
                    Icon(Icons.Rounded.RemoveCircle, contentDescription = "Fjern", tint = palette.warn, modifier = Modifier.size(22.dp))
                }
                Text(item, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            }
            RowDivider(inset = 56.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 8.dp, end = 16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).pressable(::add, enabled = draft.isNotBlank(), pressedScale = 0.85f),
            ) {
                Icon(Icons.Rounded.AddCircle, contentDescription = "Legg til", tint = TileColors.Teal, modifier = Modifier.size(22.dp))
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.text),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = palette.textFaint)
                        inner()
                    }
                },
            )
        }
    }
    if (onReset != null) {
        Text(
            "Tilbakestill",
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp).pressable(onReset).padding(8.dp),
        )
    }
}
