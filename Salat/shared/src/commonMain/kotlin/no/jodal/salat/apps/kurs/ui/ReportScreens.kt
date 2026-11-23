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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.EmojiEvents
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.ui.Card
import no.jodal.salat.apps.brems.ui.GroupLabel
import no.jodal.salat.apps.brems.ui.LargeTitle
import no.jodal.salat.apps.brems.ui.NavBar
import no.jodal.salat.apps.brems.ui.RowDivider
import no.jodal.salat.apps.brems.ui.palette
import no.jodal.salat.apps.kurs.KursStore
import no.jodal.salat.apps.kurs.achievements
import no.jodal.salat.apps.kurs.plusDays
import no.jodal.salat.apps.kurs.reportWeek
import no.jodal.salat.apps.kurs.weekReport
import no.jodal.salat.ui.pressable

/** Ukentlig «Forskningsrapport», med piler for å bla mellom ukene. */
@Composable
fun ReportScreen(store: KursStore, onBack: () -> Unit) {
    var monday by remember { mutableStateOf(reportWeek(store.today())) }
    val report = weekReport(store.data.logs, monday)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Kurs", onBack)
        Box(Modifier.padding(horizontal = 4.dp)) { LargeTitle("Forskningsrapport", subtitle = "Uka fra ${formatDay(monday).lowercase()}") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            WeekArrow(Icons.AutoMirrored.Rounded.ArrowBackIos, "Forrige uke") { monday = monday.plusDays(-7) }
            Spacer(Modifier.weight(1f))
            Text("${report.approvedDays}/7 godkjente · ${report.loggedDays} logget", style = MaterialTheme.typography.labelLarge, color = palette.textDim)
            Spacer(Modifier.weight(1f))
            val canForward = monday.plusDays(7) <= store.today()
            Box(Modifier.alpha(if (canForward) 1f else 0.3f)) {
                WeekArrow(Icons.AutoMirrored.Rounded.ArrowForwardIos, "Neste uke") { if (canForward) monday = monday.plusDays(7) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Big(formatPoints(report.totalPoints), "poeng", report.changePercent?.let { "${if (it >= 0) "+" else ""}$it % fra forrige uke" }, Modifier.weight(1f))
            Big("${report.screensOffDays}/7", "skjermstopp før 22", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Big(report.avgSleep?.roundToInt()?.toString() ?: "–", "snitt Sleep Score", null, Modifier.weight(1f))
            Big(report.avgFeeling?.let { ((it * 10).roundToInt() / 10.0).toString().replace('.', ',') } ?: "–", "snitt følelse (1–5)", null, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        GroupLabel("Ukas seire")
        Card(padding = 16.dp) {
            if (report.wins.isEmpty()) {
                Text("Ingen seire skrevet ned denne uka.", style = MaterialTheme.typography.bodyMedium, color = palette.textDim)
            }
            report.wins.forEachIndexed { index, (date, win) ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Text(formatDay(date), style = MaterialTheme.typography.labelMedium, color = palette.textDim)
                Text(win, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun WeekArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(CircleShape).background(palette.cardHigh).pressable(onClick, pressedScale = 0.88f),
    ) {
        Icon(icon, contentDescription = description, tint = palette.text, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Big(value: String, label: String, note: String?, modifier: Modifier) {
    Card(modifier, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.text)
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.textDim)
        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = if (note.startsWith("-")) palette.warn else KursAccent)
    }
}

/** Alle achievements, med fremdrift for de som ikke er låst opp. */
@Composable
fun AchievementsScreen(store: KursStore, onBack: () -> Unit) {
    val list = achievements(store.data.logs)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NavBar("Kurs", onBack)
        Box(Modifier.padding(horizontal = 4.dp)) {
            LargeTitle("Milepæler", subtitle = "${list.count { it.unlockedOn != null }} av ${list.size} låst opp")
        }
        Card {
            list.forEachIndexed { index, status ->
                if (index > 0) RowDivider()
                val unlocked = status.unlockedOn != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (unlocked) KursGold else palette.cardHigh),
                    ) {
                        Icon(
                            if (unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                            null,
                            tint = if (unlocked) Color.White else palette.textFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.achievement.title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                        Text(status.achievement.description, style = MaterialTheme.typography.bodySmall, color = palette.textDim)
                        if (!unlocked) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(palette.cardHigh)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(status.progress.toFloat() / status.goal)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(KursAccent),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        status.unlockedOn?.let { formatDay(it) } ?: "${status.progress}/${status.goal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
