package no.jodal.salat.apps.brems.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DoNotTouch
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import no.jodal.salat.DeepLinks
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.brems.BremsSettings
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.DayUsage
import no.jodal.salat.apps.brems.PauseKind
import no.jodal.salat.apps.brems.formatClock
import no.jodal.salat.apps.brems.formatUntil
import no.jodal.salat.apps.brems.appName
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.formatDuration
import no.jodal.salat.apps.brems.goalStatus
import no.jodal.salat.apps.brems.hourlyMinutes
import no.jodal.salat.apps.brems.today
import no.jodal.salat.apps.brems.usageOn
import no.jodal.salat.apps.brems.usageToday
import no.jodal.salat.apps.brems.yesterday
import no.jodal.salat.ui.pressable

private sealed interface BremsDestination {
    data object Home : BremsDestination
    data object Settings : BremsDestination
    data object Guide : BremsDestination
    data object Pause : BremsDestination
    data object Stats : BremsDestination

    /** Lag et nytt mål ([id] er `null`) eller endre et. */
    data class GoalEdit(val id: String?) : BremsDestination
}

/** Fargene på rutene foran radene, som i Innstillinger i iOS. */
object TileColors {
    val Indigo = Color(0xFF5B5BD6)
    val Orange = Color(0xFFF2994A)
    val Pink = Color(0xFFE2557A)
    val Teal = Color(0xFF2BAE9B)
    val Blue = Color(0xFF3B82F6)
    val Gray = Color(0xFF8E8E98)
}

@Composable
fun BremsScreen(bridge: NativeBridge, onBack: () -> Unit) {
    BremsTheme {
        val store = remember { BremsStore.get(bridge) }
        var destination by remember {
            mutableStateOf(if (store.currentPause() != null) BremsDestination.Pause else BremsDestination.Home)
        }
        // Snarveier åpner Brems med salat://brems/pause når en app skal vente.
        val link = DeepLinks.pending
        LaunchedEffect(link) {
            if (DeepLinks.consume("brems", "pause") && store.currentPause() != null) destination = BremsDestination.Pause
        }

        Box(Modifier.fillMaxSize().background(palette.background)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val duration = 300
                    when {
                        targetState == BremsDestination.Pause ->
                            fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 1.04f) togetherWith fadeOut(tween(duration))
                        initialState == BremsDestination.Pause ->
                            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                        targetState == BremsDestination.Home ->
                            slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration)) togetherWith slideOutHorizontally(tween(duration)) { it }
                        else ->
                            slideInHorizontally(tween(duration)) { it } togetherWith slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration))
                    }
                },
            ) { current ->
                when (current) {
                    BremsDestination.Home -> Home(store, onBack = onBack, onOpen = { destination = it })
                    BremsDestination.Settings -> SettingsScreen(store, onBack = { destination = BremsDestination.Home })
                    BremsDestination.Guide -> GuideScreen(bridge, store, onBack = { destination = BremsDestination.Home })
                    BremsDestination.Stats -> StatsScreen(store, onBack = { destination = BremsDestination.Home })
                    is BremsDestination.GoalEdit -> GoalScreen(store, current.id, onBack = { destination = BremsDestination.Home })
                    BremsDestination.Pause -> {
                        val pause = remember { store.currentPause() ?: store.data.pauses.lastOrNull() }
                        if (pause == null) {
                            LaunchedEffect(Unit) { destination = BremsDestination.Home }
                        } else {
                            PauseScreen(store, pause, onLeave = { destination = BremsDestination.Home })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(store: BremsStore, onBack: () -> Unit, onOpen: (BremsDestination) -> Unit) {
    // Forsiden oppdateres hvert sekund, så nedtellingen og tiden i appen som er åpen går.
    var now by remember { mutableLongStateOf(store.nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = store.nowMillis()
        }
    }
    val data = store.data
    val zone = TimeZone.currentSystemDefault()
    val today = data.usageToday(now, zone)
    val before = data.usageOn(yesterday(now, zone), now, zone)
    val pause = store.currentPause()?.takeIf { now < it.untilMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Salat", onBack) {
            CircleIconButton(Icons.Rounded.Tune, "Innstillinger") { onOpen(BremsDestination.Settings) }
        }
        Box(Modifier.padding(horizontal = 4.dp)) {
            LargeTitle("Brems", subtitle = if (data.settings.enabled) formatDay(today(now, zone)) else "Skrudd av")
        }

        if (pause != null) {
            if (pause.kind == PauseKind.Block) {
                PauseBanner("Sperret", "Til ${formatUntil(pause.untilMillis, now, zone)}", formatDuration(pause.untilMillis - now + 59_999)) { onOpen(BremsDestination.Pause) }
            } else {
                PauseBanner("Pause pågår", "Før ${appName(pause.app)}", "${(pause.untilMillis - now + 999) / 1000} s") { onOpen(BremsDestination.Pause) }
            }
            Spacer(Modifier.height(16.dp))
        }

        TodayCard(today, before, store, now, zone) { onOpen(BremsDestination.Stats) }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Rounded.TouchApp, TileColors.Blue, "${today.opens}", "Åpninger", Modifier.weight(1f))
            StatCard(Icons.Rounded.PauseCircle, TileColors.Indigo, "${today.pauses}", "Pauser", Modifier.weight(1f))
            StatCard(Icons.Rounded.DoNotTouch, TileColors.Teal, "${today.stopped}", "Lot være", Modifier.weight(1f))
        }

        if (data.opens.isEmpty()) {
            Spacer(Modifier.height(28.dp))
            SetupCard { onOpen(BremsDestination.Guide) }
        }

        Spacer(Modifier.height(28.dp))
        GroupLabel("Mål", action = "Nytt mål", onAction = { onOpen(BremsDestination.GoalEdit(null)) })
        if (data.goals.isEmpty()) {
            Card(onClick = { onOpen(BremsDestination.GoalEdit(null)) }, padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Rounded.Flag, TileColors.Orange, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Sett deg et mål", style = MaterialTheme.typography.titleMedium, color = palette.text)
                        Text("For eksempel under 15 minutter Snapchat om dagen. Hold det, og bygg en rekke.", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                data.goals.forEach { goal ->
                    GoalCard(data.goalStatus(goal, now, zone)) { onOpen(BremsDestination.GoalEdit(goal.id)) }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        GroupLabel("Regler", action = "Endre", onAction = { onOpen(BremsDestination.Settings) })
        Rules(data.settings) { onOpen(BremsDestination.Settings) }

        if (today.apps.any { it.first.isNotEmpty() }) {
            Spacer(Modifier.height(28.dp))
            GroupLabel("Apper i dag")
            Apps(today)
        }

        Spacer(Modifier.height(28.dp))
        GroupLabel("Mer")
        Card {
            ListRow(
                "Statistikk",
                icon = Icons.Rounded.BarChart,
                iconTint = TileColors.Indigo,
                subtitle = "Uke og måned, pauser, tider og grunner",
                chevron = true,
                onClick = { onOpen(BremsDestination.Stats) },
            )
            RowDivider()
            ListRow(
                "Snarveier og logg",
                icon = Icons.Rounded.Bolt,
                iconTint = TileColors.Gray,
                chevron = true,
                onClick = { onOpen(BremsDestination.Guide) },
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PauseBanner(title: String, subtitle: String, left: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.98f)
            .clip(RoundedCornerShape(22.dp))
            .background(Dusk.Middle)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Dusk.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Dusk.TextDim)
        }
        Text(left, style = MaterialTheme.typography.headlineSmall, color = Dusk.Text)
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = Dusk.TextFaint,
            modifier = Modifier.padding(start = 12.dp).size(13.dp),
        )
    }
}

@Composable
private fun TodayCard(today: DayUsage, before: DayUsage, store: BremsStore, now: Long, zone: TimeZone, onClick: () -> Unit) {
    Card(onClick = onClick, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tid i dag", style = MaterialTheme.typography.titleSmall, color = palette.textDim, modifier = Modifier.weight(1f))
            Text("Statistikk", style = MaterialTheme.typography.titleSmall, color = palette.accent)
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = palette.accent, modifier = Modifier.padding(start = 4.dp).size(11.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val minutes = today.millis / 60_000
            if (minutes >= 60) {
                BigNumber("${minutes / 60}")
                UnitLabel("t")
            }
            BigNumber("${minutes % 60}".let { if (minutes >= 60) it.padStart(2, '0') else it })
            UnitLabel("min")
        }
        Comparison(today.millis, before.millis)

        Spacer(Modifier.height(20.dp))
        HourBars(store.data.hourlyMinutes(today(now, zone), now, zone))

        val active = store.data.active
        if (active != null) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.line))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(TileColors.Teal))
                Text(
                    "${appName(active.app).replaceFirstChar { it.uppercase() }} er åpen",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.text,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                Text(formatDuration(now - active.startMillis), style = MaterialTheme.typography.labelLarge, color = palette.textDim)
            }
        }
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(text, style = MaterialTheme.typography.displayMedium, color = palette.text)
}

@Composable
private fun UnitLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = palette.textDim,
        modifier = Modifier.padding(start = 4.dp, end = 10.dp, bottom = 9.dp),
    )
}

/** «12 min mindre enn i går», i grønt, eller mer, i rødt. */
@Composable
private fun Comparison(today: Long, yesterday: Long) {
    if (yesterday == 0L) {
        Text("Ingen tid i går å sammenligne med", style = MaterialTheme.typography.bodySmall, color = palette.textFaint)
        return
    }
    val diff = today - yesterday
    val text = when {
        diff < 0 -> "${formatDuration(-diff)} mindre enn i går"
        diff > 0 -> "${formatDuration(diff)} mer enn i går"
        else -> "Like mye som i går"
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = if (diff <= 0) TileColors.Teal else palette.warn)
}

/** Et søylediagram over døgnet, én søyle per time. */
@Composable
private fun HourBars(minutes: List<Float>) {
    val accent = palette.accent
    val empty = palette.cardHigh
    val max = maxOf(minutes.maxOrNull() ?: 0f, 15f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 3.dp.toPx()
            val width = (size.width - gap * 23) / 24
            minutes.forEachIndexed { hour, value ->
                val h = if (value <= 0f) 3.dp.toPx() else maxOf(4.dp.toPx(), size.height * value / max)
                drawRoundRect(
                    color = if (value <= 0f) empty else accent,
                    topLeft = Offset(hour * (width + gap), size.height - h),
                    size = Size(width, h),
                    cornerRadius = CornerRadius(width / 2),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = palette.textFaint, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier) {
    Card(modifier, padding = 14.dp) {
        IconTile(icon, tint, size = 26.dp)
        Spacer(Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.textDim, maxLines = 1)
    }
}

@Composable
private fun SetupCard(onClick: () -> Unit) {
    Card(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.Bolt, palette.accent, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text("Koble til Snarveier", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text("Én automasjon med handlingen Brems. Tar et minutt.", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Sett opp", onClick)
    }
}

@Composable
private fun Rules(settings: BremsSettings, onClick: () -> Unit) {
    fun seconds(value: Int) = when {
        value == 0 -> "Av"
        value < 60 -> "$value s"
        value % 60 == 0 -> "${value / 60} min"
        else -> "${value / 60} min ${value % 60} s"
    }
    fun minutes(value: Int) = if (value == 0) "Av" else "$value min"
    Card {
        ListRow("Åpningspause", Icons.Rounded.Timer, TileColors.Indigo, value = seconds(settings.openPauseSeconds), chevron = true, onClick = onClick)
        RowDivider()
        ListRow("Første varsel", Icons.Rounded.NotificationsActive, TileColors.Orange, value = minutes(settings.reminderMinutes), chevron = true, onClick = onClick)
        RowDivider()
        ListRow("Grense per økt", Icons.Rounded.HourglassBottom, TileColors.Pink, value = minutes(settings.limitMinutes), chevron = true, onClick = onClick)
        RowDivider()
        ListRow("Grensepause", Icons.Rounded.SelfImprovement, TileColors.Teal, value = seconds(settings.limitPauseSeconds), chevron = true, onClick = onClick)
        RowDivider()
        val bedtime = settings.bedtime
        ListRow(
            "Sengetid",
            Icons.Rounded.Bedtime,
            TileColors.Blue,
            value = if (bedtime.enabled) "${formatClock(bedtime.fromMinute)}–${formatClock(bedtime.toMinute)}" else "Av",
            chevron = true,
            onClick = onClick,
        )
    }
}

@Composable
private fun Apps(today: DayUsage) {
    val max = today.apps.maxOf { it.second }.coerceAtLeast(1)
    Card {
        today.apps.forEachIndexed { index, (app, millis) ->
            if (index > 0) RowDivider(inset = 66.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AppAvatar(app)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.ifEmpty { "Andre apper" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatDuration(millis), style = MaterialTheme.typography.labelLarge, color = palette.textDim)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(palette.cardHigh)) {
                        Box(Modifier.fillMaxWidth(millis.toFloat() / max).fillMaxHeight().clip(CircleShape).background(avatarColor(app)))
                    }
                }
            }
        }
    }
}
