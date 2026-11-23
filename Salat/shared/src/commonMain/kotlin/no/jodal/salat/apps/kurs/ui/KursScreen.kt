package no.jodal.salat.apps.kurs.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import no.jodal.salat.DeepLinks
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.ui.BremsTheme
import no.jodal.salat.apps.brems.ui.Card
import no.jodal.salat.apps.brems.ui.ListRow
import no.jodal.salat.apps.brems.ui.RowDivider
import no.jodal.salat.apps.brems.ui.TileColors
import no.jodal.salat.apps.brems.ui.CircleIconButton
import no.jodal.salat.apps.brems.ui.GroupLabel
import no.jodal.salat.apps.brems.ui.LargeTitle
import no.jodal.salat.apps.brems.ui.NavBar
import no.jodal.salat.apps.brems.ui.palette
import no.jodal.salat.apps.kurs.Achievement
import no.jodal.salat.apps.kurs.KursStore
import no.jodal.salat.apps.kurs.achievements
import no.jodal.salat.apps.kurs.defaultLogDate

private sealed interface KursDestination {
    data object Home : KursDestination
    data object Settings : KursDestination
    data object Report : KursDestination
    data object Achievements : KursDestination
    data class CheckIn(val date: LocalDate) : KursDestination
    data class Saved(val date: LocalDate) : KursDestination
}

@Composable
fun KursScreen(bridge: NativeBridge, onBack: () -> Unit) {
    BremsTheme(accent = KursAccent) {
        val store = remember { KursStore.get(bridge) }
        var destination by remember { mutableStateOf<KursDestination>(KursDestination.Home) }
        // Varslene åpner salat://kurs/logg.
        val link = DeepLinks.pending
        LaunchedEffect(link) {
            if (DeepLinks.consume("kurs", "logg")) destination = KursDestination.CheckIn(defaultLogDate(store.now(), store.data.logs.map { it.date }.toSet()))
        }

        Box(Modifier.fillMaxSize().background(palette.background)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val duration = 300
                    if (targetState == KursDestination.Home) {
                        slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration)) togetherWith slideOutHorizontally(tween(duration)) { it }
                    } else {
                        slideInHorizontally(tween(duration)) { it } togetherWith slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration))
                    }
                },
            ) { current ->
                val home = { destination = KursDestination.Home }
                when (current) {
                    KursDestination.Home -> Home(store, onBack, onOpen = { destination = it })
                    KursDestination.Settings -> SettingsScreen(store, onBack = home)
                    KursDestination.Report -> ReportScreen(store, onBack = home)
                    KursDestination.Achievements -> AchievementsScreen(store, onBack = home)
                    is KursDestination.CheckIn -> CheckInScreen(store, current.date, onBack = home, onSaved = { destination = KursDestination.Saved(it) })
                    is KursDestination.Saved -> SavedScreen(store, current.date, onDone = home, onEdit = { destination = KursDestination.CheckIn(current.date) })
                }
            }
        }
    }
}

@Composable
private fun Home(store: KursStore, onBack: () -> Unit, onOpen: (KursDestination) -> Unit) {
    val data = store.data
    val today = store.today()
    val logs = data.logs.associateBy { it.date }
    val summary = store.summary()
    val target = defaultLogDate(store.now(), logs.keys)
    val buttonText = when {
        target in logs -> "Rediger dagens logg"
        target == today -> "Logg dag · i dag"
        else -> "Logg dag · i går"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Salat", onBack) {
            CircleIconButton(Icons.Rounded.Tune, "Innstillinger") { onOpen(KursDestination.Settings) }
        }
        Box(Modifier.padding(horizontal = 4.dp)) {
            LargeTitle("Kurs", subtitle = formatDay(today))
        }
        StatusCard(summary, logs, today, buttonText) { onOpen(KursDestination.CheckIn(target)) }

        // Gårsdagen mangler Sleep Score: en snarvei for å fylle den inn.
        val yesterday = logs[today.minus(DatePeriod(days = 1))]
        if (yesterday != null && yesterday.sleepScore == null) {
            Spacer(Modifier.height(12.dp))
            ReminderCard("Legg inn nattens Sleep Score", "Gårsdagen er logget uten søvn.") { onOpen(KursDestination.CheckIn(yesterday.date)) }
        }

        Spacer(Modifier.height(12.dp))
        Card {
            val unlocked = achievements(data.logs).count { it.unlockedOn != null }
            ListRow(
                "Forskningsrapport",
                icon = Icons.Rounded.Science,
                iconTint = TileColors.Indigo,
                subtitle = if (today.dayOfWeek == DayOfWeek.SUNDAY) "Uka som går mot slutten" else "Forrige uke",
                chevron = true,
                onClick = { onOpen(KursDestination.Report) },
            )
            RowDivider()
            ListRow(
                "Milepæler",
                icon = Icons.Rounded.EmojiEvents,
                iconTint = KursGold,
                value = "$unlocked/${Achievement.entries.size}",
                chevron = true,
                onClick = { onOpen(KursDestination.Achievements) },
            )
        }

        Spacer(Modifier.height(24.dp))
        GroupLabel("Nøkkeltall")
        KeyMetrics(logs, today)
        Spacer(Modifier.height(24.dp))
        ScoreChart(logs, today)
        Spacer(Modifier.height(12.dp))
        Heatmap(logs, today)
        Spacer(Modifier.height(32.dp))
    }
}
