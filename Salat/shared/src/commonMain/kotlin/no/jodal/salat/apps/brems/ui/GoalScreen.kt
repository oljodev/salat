package no.jodal.salat.apps.brems.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.DayResult
import no.jodal.salat.apps.brems.Goal
import no.jodal.salat.apps.brems.GoalStatus
import no.jodal.salat.apps.brems.formatDuration
import no.jodal.salat.apps.brems.formatPrecise
import no.jodal.salat.apps.brems.hardGoalReached
import no.jodal.salat.apps.brems.label
import no.jodal.salat.apps.brems.today
import no.jodal.salat.ui.pressable

private val GoalSteps = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

/** Appene man kan velge i et mål, selv om Brems ikke har hørt om dem ennå. */
private val CommonApps = listOf("Snapchat", "TikTok", "Instagram", "YouTube")

/** Et mål på forsiden: hvor mye som er brukt i dag, rekka og den siste uka. */
@Composable
fun GoalCard(status: GoalStatus, onClick: () -> Unit) {
    val goal = status.goal
    val used = status.usedTodayMillis
    val over = used - status.limitMillis
    Card(onClick = onClick, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (goal.app.isEmpty()) IconTile(Icons.Rounded.Apps, TileColors.Gray, size = 36.dp) else AppAvatar(goal.app)
            Column(Modifier.weight(1f)) {
                Text(goal.label, style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text("Under ${goal.minutesPerDay} min om dagen" + if (goal.hard) "  ·  hard grense" else "", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
            }
            Streak(status.streak)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(palette.cardHigh)) {
            Box(
                Modifier
                    .fillMaxWidth((used.toFloat() / status.limitMillis).coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (status.reached) palette.warn else palette.accent),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text("${formatPrecise(used)} i dag", style = MaterialTheme.typography.labelMedium, color = palette.textDim, modifier = Modifier.weight(1f))
            Text(
                if (over >= 0) "${formatPrecise(over)} over" else "${formatPrecise(-over)} igjen",
                style = MaterialTheme.typography.labelMedium,
                color = if (over >= 0) palette.warn else palette.textDim,
            )
        }
        Spacer(Modifier.height(16.dp))
        Week(status)
    }
}

/** Rekka: en flamme og antall dager på rad. */
@Composable
private fun Streak(days: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape).background(if (days > 0) TileColors.Orange.copy(alpha = 0.14f) else palette.cardHigh).padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = if (days > 0) TileColors.Orange else palette.textFaint, modifier = Modifier.size(18.dp))
        Text(
            if (days == 1) "1 dag" else "$days dager",
            style = MaterialTheme.typography.labelLarge,
            color = if (days > 0) TileColors.Orange else palette.textDim,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private val WeekdayLetters = listOf("M", "T", "O", "T", "F", "L", "S")

/** Den siste uka: én prikk per dag, grønn under målet og rød over. */
@Composable
private fun Week(status: GoalStatus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        status.week.forEach { (date, result) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(WeekdayLetters[date.dayOfWeek.ordinal], style = MaterialTheme.typography.labelMedium, color = palette.textFaint)
                Spacer(Modifier.height(6.dp))
                val (fill, mark) = when (result) {
                    DayResult.Met -> TileColors.Teal to true
                    DayResult.Missed -> palette.warn to false
                    DayResult.OnTrack -> palette.accentSoft to false
                    DayResult.Before -> palette.cardHigh to false
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp).clip(CircleShape).background(fill)) {
                    if (mark) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    if (result == DayResult.OnTrack) Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
                }
            }
        }
    }
}

/** Forbokstaven i appen i en farget rute, siden vi ikke har ikonene til andre apper. */
@Composable
fun AppAvatar(app: String, size: Dp = 36.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(avatarColor(app)),
    ) {
        Text(app.firstOrNull()?.uppercase() ?: "·", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

private val AvatarColors = listOf(TileColors.Indigo, TileColors.Pink, TileColors.Orange, TileColors.Teal, TileColors.Blue)

fun avatarColor(app: String): Color = if (app.isEmpty()) TileColors.Gray else AvatarColors[app.lowercase().hashCode().mod(AvatarColors.size)]

/** Lag eller endre et mål. [goalId] er `null` for et nytt mål. */
@Composable
fun GoalScreen(store: BremsStore, goalId: String?, onBack: () -> Unit) {
    val existing = store.data.goals.firstOrNull { it.id == goalId }
    var app by remember { mutableStateOf(existing?.app ?: "") }
    var minutes by remember { mutableStateOf(existing?.minutesPerDay ?: 15) }
    var custom by remember { mutableStateOf("") }
    var hard by remember { mutableStateOf(existing?.hard ?: false) }
    // Et mål med hard grense som er nådd i dag, kan ikke endres før i morgen. Ellers er grensen ikke hard.
    val locked = existing != null && store.data.hardGoalReached(existing, store.nowMillis(), TimeZone.currentSystemDefault())
    val choices = (CommonApps + store.knownApps() + listOfNotNull(existing?.app?.takeIf { it.isNotEmpty() }))
        .distinctBy { it.lowercase() }

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
        Box(Modifier.padding(horizontal = 4.dp)) { LargeTitle(if (existing == null) "Nytt mål" else "Endre mål") }

        GroupLabel("Hvor lenge om dagen")
        Card(padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$minutes min", style = MaterialTheme.typography.displaySmall, color = palette.text)
                    Text("eller mindre hver dag", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
                }
                val index = GoalSteps.indexOfFirst { it >= minutes }.coerceAtLeast(0)
                Stepper(
                    value = "",
                    onMinus = { minutes = GoalSteps[(index - 1).coerceAtLeast(0)] },
                    onPlus = { minutes = GoalSteps[(index + 1).coerceAtMost(GoalSteps.lastIndex)] },
                    canMinus = minutes > GoalSteps.first(),
                    canPlus = minutes < GoalSteps.last(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        GroupLabel("App")
        Card {
            AppChoice("Alle apper", selected = app.isEmpty(), avatar = { IconTile(Icons.Rounded.Apps, TileColors.Gray) }) { app = "" }
            choices.forEach { name ->
                RowDivider()
                AppChoice(name, selected = app.equals(name, ignoreCase = true), avatar = { AppAvatar(name, 30.dp) }) { app = name }
            }
            RowDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp)) {
                BasicTextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        if (it.isNotBlank()) app = it.trim()
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.text),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f).padding(start = 44.dp),
                    decorationBox = { inner ->
                        Box {
                            if (custom.isEmpty()) Text("En annen app", style = MaterialTheme.typography.bodyLarge, color = palette.textFaint)
                            inner()
                        }
                    },
                )
            }
        }
        Footnote(
            "Et mål for én app teller bare når automasjonen i Snarveier har navnet på appen i App-feltet. " +
                "Alle apper teller alt Brems følger med på.",
        )

        Spacer(Modifier.height(28.dp))
        Card {
            ListRow(
                "Hard grense",
                Icons.Rounded.Lock,
                TileColors.Pink,
                subtitle = "Sperr appen resten av dagen når tiden er brukt opp",
                trailing = { Switch(hard) { on -> if (!locked) hard = on } },
            )
        }
        Footnote(
            if (locked) {
                "Målet er nådd og har hard grense, så det kan ikke endres eller slettes før i morgen."
            } else {
                "Uten hard grense får du en lang pause hver gang. Med hard grense er appen sperret til midnatt, uten knapp for å fortsette."
            },
        )

        Spacer(Modifier.height(32.dp))
        if (!locked) PrimaryButton("Lagre", {
            val zone = TimeZone.currentSystemDefault()
            store.saveGoal(
                Goal(
                    id = existing?.id ?: "g${store.nowMillis()}",
                    app = app.trim(),
                    minutesPerDay = minutes,
                    since = existing?.since ?: today(store.nowMillis(), zone),
                    hard = hard,
                ),
            )
            Haptics.success()
            onBack()
        })
        if (existing != null && !locked) {
            Text(
                "Slett målet",
                style = MaterialTheme.typography.titleMedium,
                color = palette.warn,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).pressable({
                    store.deleteGoal(existing.id)
                    onBack()
                }).padding(vertical = 16.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AppChoice(name: String, selected: Boolean, avatar: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).pressable(onClick, pressedScale = 0.99f, haptic = Haptics::select).padding(horizontal = 16.dp),
    ) {
        avatar()
        Text(name, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Valgt", tint = palette.accent, modifier = Modifier.size(20.dp))
    }
}
