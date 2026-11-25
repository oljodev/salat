package no.jodal.salat.apps.brems.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.PauseStats
import no.jodal.salat.apps.brems.RangeStats
import no.jodal.salat.apps.brems.StatsRange
import no.jodal.salat.apps.brems.formatClock
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.formatDuration
import no.jodal.salat.apps.brems.formatPrecise
import no.jodal.salat.apps.brems.today
import no.jodal.salat.apps.brems.peakHour
import no.jodal.salat.apps.brems.peakWeekday
import no.jodal.salat.apps.brems.stats
import no.jodal.salat.ui.pressable

private val Weekdays = listOf("mandag", "tirsdag", "onsdag", "torsdag", "fredag", "lørdag", "søndag")
private val WeekdayLetters = listOf("M", "T", "O", "T", "F", "L", "S")

/** Statistikk for siste uke eller måned. */
@Composable
fun StatsScreen(store: BremsStore, onBack: () -> Unit) {
    var range by remember { mutableStateOf(StatsRange.Week) }
    val stats = store.data.stats(range, store.nowMillis(), TimeZone.currentSystemDefault())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Brems", onBack)
        Box(Modifier.padding(horizontal = 4.dp)) { LargeTitle("Statistikk") }
        Segmented(StatsRange.entries.map { it.label }, range.ordinal) { range = StatsRange.entries[it] }

        Spacer(Modifier.height(16.dp))
        Overview(stats)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(formatPrecise(stats.totalMillis), "Totalt", Modifier.weight(1f))
            Tile("${stats.opens}", "Åpninger", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(formatPrecise(stats.averageSessionMillis), "Snitt per økt", Modifier.weight(1f))
            Tile(stats.longestSession?.let { formatPrecise(it.millis) } ?: "–", "Lengste økt", Modifier.weight(1f))
        }

        Section("Pausene")
        Pauses(stats)

        Section("Når på døgnet")
        Hours(stats)

        Section("Ukedager")
        Weekdays(stats)

        if (stats.apps.isNotEmpty()) {
            Section("Apper")
            Apps(stats)
        }

        if (stats.recentSessions.isNotEmpty()) {
            Section("Siste økter")
            Sessions(stats)
        }

        Section("Hvorfor du åpner")
        Reasons(stats)

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    GroupLabel(title)
}

/** Valg mellom noen få alternativer, som UISegmentedControl i iOS. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.cardHigh).padding(2.dp),
    ) {
        val width = maxWidth / options.size
        val offset by animateDpAsState(width * selected, spring(dampingRatio = 0.8f, stiffness = 500f))
        Box(
            Modifier
                .offset(x = offset)
                .width(width)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(palette.card),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val color by animateColorAsState(if (index == selected) palette.text else palette.textDim)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight().pressable({ onSelect(index) }, pressedScale = 0.97f, haptic = Haptics::select),
                ) {
                    Text(option, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

/** Snitt per dag, endringen fra perioden før, og en søyle per dag. */
@Composable
private fun Overview(stats: RangeStats) {
    Card(padding = 20.dp) {
        Text("Snitt per dag", style = MaterialTheme.typography.titleSmall, color = palette.textDim)
        Spacer(Modifier.height(4.dp))
        Text(formatPrecise(stats.averageMillis), style = MaterialTheme.typography.displaySmall, color = palette.text)
        Spacer(Modifier.height(6.dp))
        Trend(stats)
        Spacer(Modifier.height(20.dp))
        DayBars(stats)
    }
}

@Composable
private fun Trend(stats: RangeStats) {
    val trend = stats.trend
    if (trend == null) {
        Text("Ikke nok data til å sammenligne ennå", style = MaterialTheme.typography.bodySmall, color = palette.textFaint)
        return
    }
    val percent = (abs(trend) * 100).roundToInt()
    val (icon, color, text) = when {
        percent == 0 -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, palette.textDim, "Like mye som ${stats.range.previousLabel}")
        trend < 0 -> Triple(Icons.AutoMirrored.Rounded.TrendingDown, TileColors.Teal, "$percent % mindre enn ${stats.range.previousLabel}")
        else -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, palette.warn, "$percent % mer enn ${stats.range.previousLabel}")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 6.dp))
    }
}

/** En søyle per dag, med en stiplet linje for snittet. */
@Composable
private fun DayBars(stats: RangeStats) {
    val accent = palette.accent
    val empty = palette.cardHigh
    val line = palette.textFaint
    val values = stats.days.map { it.millis / 60_000f }
    val max = maxOf(values.maxOrNull() ?: 0f, 10f)
    val average = stats.averageMillis / 60_000f
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val gap = if (values.size > 10) 3.dp.toPx() else 10.dp.toPx()
            val width = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, v ->
                val h = if (v <= 0f) 3.dp.toPx() else maxOf(4.dp.toPx(), size.height * v / max)
                drawRoundRect(
                    color = if (v <= 0f) empty else accent,
                    topLeft = Offset(i * (width + gap), size.height - h),
                    size = Size(width, h),
                    cornerRadius = CornerRadius(minOf(width / 2, 6.dp.toPx())),
                )
            }
            if (average > 0f) {
                val y = size.height - size.height * average / max
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            stats.days.forEachIndexed { i, day ->
                val label = when {
                    stats.days.size <= 7 -> WeekdayLetters[day.date.dayOfWeek.ordinal]
                    i % 5 == 0 || i == stats.days.lastIndex -> "${day.date.day}."
                    else -> ""
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Card(modifier, padding = 16.dp) {
        // Krymper teksten så lange tider til sekundet, som «1 t 6 min 40 s», får plass.
        BasicText(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(color = palette.text),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = MaterialTheme.typography.headlineSmall.fontSize),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.textDim)
    }
}

/** Hvor ofte man lot være: en ring for alle pausene, og en linje for hver slags pause. */
@Composable
private fun Pauses(stats: RangeStats) {
    val all = stats.allPauses
    Card(padding = 20.dp) {
        if (all.total == 0) {
            Text("Ingen pauser i perioden ennå.", style = MaterialTheme.typography.bodyMedium, color = palette.textDim)
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Ring(all.rate)
            Column(Modifier.weight(1f)) {
                Text("Du lot være", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text(
                    "${all.stopped} av ${all.total} ganger Brems tok en pause, gikk du ikke videre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        RateRow("Kort pause ved åpning", stats.openPauses, TileColors.Indigo)
        Spacer(Modifier.height(14.dp))
        RateRow("Lang pause etter grense eller mål", stats.longPauses, TileColors.Teal)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.line))
        Spacer(Modifier.height(14.dp))
        Row {
            Text("Økter over grensen", style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
            Text("${stats.overLimit} av ${stats.sessions}", style = MaterialTheme.typography.bodyMedium, color = palette.textDim)
        }
    }
}

@Composable
private fun Ring(rate: Float) {
    val track = palette.cardHigh
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(TileColors.Teal, -90f, 360f * rate, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(rate * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium, color = palette.text)
    }
}

@Composable
private fun RateRow(label: String, pauses: PauseStats, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, color = palette.textDim, modifier = Modifier.weight(1f))
            Text(
                if (pauses.total == 0) "–" else "${(pauses.rate * 100).roundToInt()} %",
                style = MaterialTheme.typography.labelMedium,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(6.dp))
        Bar(pauses.rate, color)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(palette.cardHigh)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** Snitt per time gjennom døgnet, med den verste timen trukket fram. */
@Composable
private fun Hours(stats: RangeStats) {
    val peak = stats.peakHour()
    val accent = palette.accent
    val soft = palette.accentSoft
    val empty = palette.cardHigh
    val max = maxOf(stats.hourly.maxOrNull() ?: 0f, 1f)
    Card(padding = 20.dp) {
        Text(
            if (peak == null) "Ingen bruk ennå" else "Mest mellom ${two(peak)} og ${two((peak + 1) % 24)}",
            style = MaterialTheme.typography.titleMedium,
            color = palette.text,
        )
        Text("Snitt per dag, time for time", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val gap = 3.dp.toPx()
            val width = (size.width - gap * 23) / 24
            stats.hourly.forEachIndexed { hour, v ->
                val h = if (v <= 0f) 3.dp.toPx() else maxOf(4.dp.toPx(), size.height * v / max)
                drawRoundRect(
                    color = when {
                        v <= 0f -> empty
                        hour == peak -> accent
                        else -> soft
                    },
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

/** Snitt per ukedag. */
@Composable
private fun Weekdays(stats: RangeStats) {
    val peak = stats.peakWeekday()
    val max = maxOf(stats.weekdays.maxOrNull() ?: 0f, 1f)
    Card(padding = 20.dp) {
        Text(
            if (peak == null) "Ingen bruk ennå" else "Mest på ${Weekdays[peak]}er",
            style = MaterialTheme.typography.titleMedium,
            color = palette.text,
        )
        Text("Snitt per ukedag", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            stats.weekdays.forEachIndexed { index, minutes ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        if (minutes >= 1f) "${minutes.roundToInt()}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (minutes <= 0f) 3.dp else (70 * minutes / max).coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    minutes <= 0f -> palette.cardHigh
                                    index == peak -> palette.accent
                                    else -> palette.accentSoft
                                },
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(WeekdayLetters[index], style = MaterialTheme.typography.labelMedium, color = palette.textFaint)
                }
            }
        }
    }
}

@Composable
private fun Apps(stats: RangeStats) {
    val total = stats.totalMillis.coerceAtLeast(1)
    Card {
        stats.apps.forEachIndexed { index, app ->
            if (index > 0) RowDivider(inset = 66.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AppAvatar(app.app)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.app.ifEmpty { "Andre apper" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatPrecise(app.millis), style = MaterialTheme.typography.labelLarge, color = palette.text)
                    }
                    Text(
                        "${(app.millis * 100 / total)} % av tiden  ·  ${app.opens} åpninger",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    Bar(app.millis.toFloat() / stats.apps.first().millis.coerceAtLeast(1), avatarColor(app.app))
                }
            }
        }
    }
}

/** De siste øktene, med start og slutt til sekundet. */
@Composable
private fun Sessions(stats: RangeStats) {
    val zone = TimeZone.currentSystemDefault()
    Card {
        stats.recentSessions.forEachIndexed { index, session ->
            if (index > 0) RowDivider(inset = 66.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                AppAvatar(session.app)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session.app.ifEmpty { "Andre apper" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatPrecise(session.millis), style = MaterialTheme.typography.labelLarge, color = palette.text)
                    }
                    Text(
                        "${formatShortDay(session.startMillis, zone)}  ·  ${formatClock(session.startMillis, zone)}–${formatClock(session.endMillis, zone)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatShortDay(millis: Long, zone: TimeZone): String =
    formatDay(today(millis, zone)).split(' ').let { "${it[0].take(3)} ${it[1]} ${it[2].take(3)}" }

/** Grunnene man har valgt i pausene, som liggende søyler. */
@Composable
private fun Reasons(stats: RangeStats) {
    Card(padding = 20.dp) {
        if (stats.reasons.isEmpty()) {
            Text(
                "Svar på «Hvorfor åpner du …?» i pausene, så ser du her hva som oftest får deg til å åpne appene.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim,
            )
            return@Card
        }
        val top = stats.reasons.first().second
        val answered = stats.reasons.sumOf { it.second }
        Text("Oftest: ${stats.reasons.first().first.lowercase()}", style = MaterialTheme.typography.titleMedium, color = palette.text)
        Text("$answered svar i perioden", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stats.reasons.forEach { (reason, count) ->
                Column {
                    Row {
                        Text(reason, style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
                        Text("$count", style = MaterialTheme.typography.labelMedium, color = palette.textDim)
                    }
                    Spacer(Modifier.height(6.dp))
                    Bar(count.toFloat() / top, TileColors.Orange)
                }
            }
        }
    }
}

private fun two(n: Int) = n.toString().padStart(2, '0')
