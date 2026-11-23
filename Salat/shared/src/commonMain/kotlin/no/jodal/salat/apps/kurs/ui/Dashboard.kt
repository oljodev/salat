package no.jodal.salat.apps.kurs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.LocalFireDepartment
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.ui.Card
import no.jodal.salat.apps.brems.ui.PrimaryButton
import no.jodal.salat.apps.brems.ui.palette
import no.jodal.salat.apps.kurs.DayLog
import no.jodal.salat.apps.kurs.KursSummary
import no.jodal.salat.apps.kurs.Points
import no.jodal.salat.apps.kurs.ScreenQuality
import no.jodal.salat.apps.kurs.WeekGoal
import no.jodal.salat.apps.kurs.isApproved
import no.jodal.salat.apps.kurs.plusDays
import no.jodal.salat.apps.kurs.points
import no.jodal.salat.apps.kurs.weekDays
import no.jodal.salat.apps.kurs.weekStart
import no.jodal.salat.ui.pressable

/** Toppkortet: ukesrekka, uka så langt, freezes, totalpoeng og knappen for å logge. */
@Composable
fun StatusCard(summary: KursSummary, logs: Map<LocalDate, DayLog>, today: LocalDate, buttonText: String, onLog: () -> Unit) {
    Card(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = KursGold, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("${summary.weeklyStreak}", style = MaterialTheme.typography.displaySmall, color = palette.text)
                Text(if (summary.weeklyStreak == 1) "uke på rad" else "uker på rad", style = MaterialTheme.typography.labelMedium, color = palette.textDim)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AcUnit, null, tint = Color(0xFF5AA9E6), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${summary.availableFreezes}/${Points.MaxFreezes} freezes", style = MaterialTheme.typography.labelLarge, color = palette.text)
                }
                Spacer(Modifier.height(4.dp))
                Text("${formatPoints(summary.totalPoints)} poeng totalt", style = MaterialTheme.typography.labelMedium, color = palette.textDim)
                Text("${summary.daily22Streak} dager med 22:00-stopp", style = MaterialTheme.typography.labelMedium, color = palette.textDim)
            }
        }
        Spacer(Modifier.height(18.dp))
        WeekProgress(logs, today)
        Spacer(Modifier.height(18.dp))
        PrimaryButton(buttonText, onLog)
    }
}

/** Uka mandag–søndag: en prikk per dag, og hvor mange av de [WeekGoal] som trengs som er godkjent. */
@Composable
private fun WeekProgress(logs: Map<LocalDate, DayLog>, today: LocalDate) {
    val days = weekDays(today.weekStart())
    val approved = days.count { logs[it]?.isApproved == true }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Denne uka", style = MaterialTheme.typography.titleSmall, color = palette.text, modifier = Modifier.weight(1f))
        Text(
            if (approved >= 7) "7/7 · perfekt uke!" else "$approved / $WeekGoal dager",
            style = MaterialTheme.typography.titleSmall,
            color = if (approved >= WeekGoal) KursAccent else palette.textDim,
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { index, date ->
            val log = logs[date]
            val color = when {
                log?.isApproved == true -> if (log.points >= GoldPoints) KursGold else KursAccent
                log != null -> KursAccent.copy(alpha = 0.3f)
                else -> palette.cardHigh
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                Spacer(Modifier.height(4.dp))
                Text(
                    "MTOTFLS"[index].toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == today) palette.text else palette.textFaint,
                )
            }
        }
    }
    // Markør for dag 6 og 7: bonusdagene etter målet.
    if (approved >= WeekGoal && approved < 7) {
        Spacer(Modifier.height(6.dp))
        Text("Mål nådd. Dag 6 og 7 er bonus – 7/7 gir +${Points.PerfectWeekBonus}.", style = MaterialTheme.typography.bodySmall, color = palette.textDim)
    }
}

/** Grafen med poeng, 7-dagers snitt og Sleep Score. Trykk på en dag for detaljer. */
@Composable
fun ScoreChart(logs: Map<LocalDate, DayLog>, today: LocalDate) {
    var range by remember { mutableStateOf(30) }
    var showAverage by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val days = (range - 1 downTo 0).map { today.plusDays(-it) }
    val points = days.map { logs[it]?.points }
    val average = days.indices.map { i ->
        val window = (maxOf(0, i - 6)..i).mapNotNull { points[it] }
        if (window.isEmpty() || points[i] == null) null else window.average().toFloat()
    }
    val sleep = days.map { logs[it]?.sleepScore }
    val pointColor = KursAccent
    val sleepColor = Color(0xFF7B6CFF)
    val grid = palette.line

    Card(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Poeng og søvn", style = MaterialTheme.typography.titleMedium, color = palette.text, modifier = Modifier.weight(1f))
            Segmented(listOf(7 to "7D", 30 to "30D", 90 to "90D"), range, { if (it != null) range = it; selected = null }, modifier = Modifier.width(160.dp), compact = true)
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(range) {
                    detectTapGestures { offset ->
                        val index = ((offset.x / size.width) * days.size).toInt().coerceIn(0, days.size - 1)
                        selected = days[index].takeIf { logs[it] != null }
                        Haptics.select()
                    }
                },
        ) {
            val step = size.width / days.size
            fun x(i: Int) = step * i + step / 2
            fun yPoints(v: Float) = size.height * (1 - v / 220f)
            fun ySleep(v: Float) = size.height * (1 - v / 100f)
            // Linjen for godkjent dag.
            val approvedY = yPoints(Points.ApprovedDay.toFloat())
            drawLine(grid, Offset(0f, approvedY), Offset(size.width, approvedY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val sel = selected?.let { days.indexOf(it) }
            if (sel != null && sel >= 0) {
                drawRect(pointColor.copy(alpha = 0.12f), Offset(x(sel) - step / 2, 0f), Size(step, size.height))
            }
            fun line(values: List<Float?>, y: (Float) -> Float, color: Color, width: Float, dashed: Boolean) {
                var path: Path? = null
                values.forEachIndexed { i, v ->
                    if (v == null) {
                        path?.let { drawPath(it, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)) }
                        path = null
                    } else {
                        val p = path
                        if (p == null) path = Path().apply { moveTo(x(i), y(v)) } else p.lineTo(x(i), y(v))
                    }
                }
                path?.let { drawPath(it, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)) }
            }
            line(sleep.map { it?.toFloat() }, ::ySleep, sleepColor, 2.dp.toPx(), dashed = true)
            if (showAverage) line(average, ::yPoints, pointColor.copy(alpha = 0.45f), 4.dp.toPx(), dashed = false)
            line(points.map { it?.toFloat() }, ::yPoints, pointColor, 2.dp.toPx(), dashed = false)
            if (range <= 30) {
                points.forEachIndexed { i, v -> if (v != null) drawCircle(pointColor, 3.dp.toPx(), Offset(x(i), yPoints(v.toFloat()))) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(pointColor, "Poeng")
            Box(Modifier.pressable({ showAverage = !showAverage })) {
                Legend(if (showAverage) pointColor.copy(alpha = 0.45f) else palette.textFaint, "7-dagers snitt")
            }
            Legend(sleepColor, "Sleep Score")
        }
        val detail = selected?.let { logs[it] }
        if (detail != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.cardHigh).padding(12.dp)) {
                Text(formatDay(detail.date), style = MaterialTheme.typography.titleSmall, color = palette.text)
                Text(
                    "${detail.points} poeng · Sleep Score ${detail.sleepScore ?: "–"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textDim,
                )
                if (detail.dailyWin.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("«${detail.dailyWin}»", style = MaterialTheme.typography.bodyMedium, color = palette.text)
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.textDim)
    }
}

/** Rutenett med de siste 12 ukene, én rute per dag, farget etter poengene. */
@Composable
fun Heatmap(logs: Map<LocalDate, DayLog>, today: LocalDate, weeks: Int = 12) {
    val firstMonday = today.weekStart().plusDays(-7 * (weeks - 1))
    val empty = palette.cardHigh
    Card(padding = 16.dp) {
        Text("Siste $weeks uker", style = MaterialTheme.typography.titleMedium, color = palette.text)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(weeks) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    repeat(7) { d ->
                        val date = firstMonday.plusDays(w * 7 + d)
                        val log = logs[date]
                        val color = when {
                            date > today -> Color.Transparent
                            log == null -> empty
                            log.points >= GoldPoints -> KursGold
                            log.isApproved -> KursAccent
                            else -> KursAccent.copy(alpha = 0.3f)
                        }
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(color))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(empty, "Ikke logget")
            Legend(KursAccent.copy(alpha = 0.3f), "<${Points.ApprovedDay}")
            Legend(KursAccent, "${Points.ApprovedDay}+")
            Legend(KursGold, "$GoldPoints+")
        }
    }
}

/** De fire nøkkeltallene. */
@Composable
fun KeyMetrics(logs: Map<LocalDate, DayLog>, today: LocalDate) {
    val last14 = (0 until 14).mapNotNull { logs[today.plusDays(-it)] }
    val screensOff = last14.mapNotNull { it.screensOffBefore22 }
    val sleep = last14.mapNotNull { it.sleepScore }
    val quality = last14.mapNotNull { it.screenQuality }
    val month = logs.values.count { it.date.year == today.year && it.date.month == today.month && it.shipAndEarn == true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(percent(screensOff.count { it }, screensOff.size), "22:00-treff", "siste 14 dager", Modifier.weight(1f))
            Metric(if (sleep.isEmpty()) "–" else "${sleep.average().toInt()}", "Snitt Sleep Score", "siste 14 dager", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(percent(quality.count { it == ScreenQuality.High }, quality.size), "Høy skjermkvalitet", "siste 14 dager", Modifier.weight(1f))
            Metric("$month", "Ship & Earn-dager", "denne måneden", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(value: String, label: String, period: String, modifier: Modifier) {
    Card(modifier, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.text)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.text)
        Text(period, style = MaterialTheme.typography.labelMedium, color = palette.textDim)
    }
}

private fun percent(part: Int, total: Int): String = if (total == 0) "–" else "${part * 100 / total} %"

/** «2 450» med mellomrom som tusenskille. */
fun formatPoints(points: Int): String = points.toString().reversed().chunked(3).joinToString(" ").reversed()

/** Fra så mange poeng er dagen gull. */
const val GoldPoints = 160
