package no.jodal.salat.apps.brems.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.datetime.TimeZone
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.Pause
import no.jodal.salat.apps.brems.BlockReason
import no.jodal.salat.apps.brems.PauseKind
import no.jodal.salat.apps.brems.appliesTo
import no.jodal.salat.apps.brems.describeBlock
import no.jodal.salat.apps.brems.appName
import no.jodal.salat.apps.brems.appUrl
import no.jodal.salat.apps.brems.formatDuration
import no.jodal.salat.apps.brems.formatPrecise
import no.jodal.salat.apps.brems.formatMinutes
import no.jodal.salat.apps.brems.goalStatus
import no.jodal.salat.apps.brems.reachedGoal
import no.jodal.salat.apps.brems.usageToday
import no.jodal.salat.ui.pressable

/** Hvor man er etter at pausen er over. */
private enum class PauseStep { Waiting, Stopped, GoBack }

/** Pusten: fire sekunder inn, fire sekunder ut. */
private const val BreathMillis = 4_000L

/**
 * Pausen før en app åpnes. Øverst står appen og dagens bruk, i midten en kule som puster med
 * nedtellingen inni, og nederst en påminnelse og spørsmålet om hvorfor. Pausen kan ikke hoppes over,
 * men man kan gå ut av Brems: den teller videre, og åpner man appen igjen, kommer man tilbake hit.
 */
@Composable
fun PauseScreen(store: BremsStore, pause: Pause, onLeave: () -> Unit) {
    var now by remember { mutableLongStateOf(store.nowMillis()) }
    LaunchedEffect(Unit) {
        val wallStart = store.nowMillis()
        var frameStart = -1L
        while (true) {
            withFrameMillis { frame ->
                if (frameStart < 0) frameStart = frame
                now = wallStart + (frame - frameStart)
            }
        }
    }
    val remainingMillis = (pause.untilMillis - now).coerceAtLeast(0)
    val done = remainingMillis == 0L
    LaunchedEffect(done) { if (done) Haptics.success() }
    var step by remember { mutableStateOf(PauseStep.Waiting) }
    val name = appName(pause.app).replaceFirstChar { it.uppercase() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Night.Top)
            .drawBehind {
                // En svak glød nedenfra, rund, så overgangen ikke blir stripete.
                drawRect(
                    Brush.radialGradient(
                        listOf(Night.Bottom, Night.Top),
                        center = Offset(size.width / 2, size.height * 1.1f),
                        radius = size.height * 0.75f,
                    ),
                )
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, color = Night.Text)
                    if (store.data.settings.showUsage) {
                        val today = store.data.usageToday(now, TimeZone.currentSystemDefault())
                        Text(
                            "${today.opens}. gang i dag  ·  ${formatPrecise(today.millis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Night.Dim,
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(top = 2.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).pressable(onLeave, pressedScale = 0.85f),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Gå ut av pausen", tint = Night.Dim, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) },
                modifier = Modifier.weight(1f),
            ) { current ->
                when (current) {
                    PauseStep.Waiting -> Waiting(
                        store = store,
                        pause = pause,
                        now = now,
                        remainingMillis = remainingMillis,
                        onContinue = {
                            val known = appUrl(pause.app) != null
                            store.continueToApp()
                            if (known) onLeave() else step = PauseStep.GoBack
                        },
                        onStop = {
                            store.stop()
                            step = PauseStep.Stopped
                        },
                    )
                    PauseStep.Stopped -> Ending("Godt valgt.", stoppedText(store), onLeave)
                    PauseStep.GoBack -> Ending(
                        "Bytt tilbake.",
                        "Sveip tilbake til ${appName(pause.app)}. Om to minutter tar Brems en ny pause.",
                        onLeave,
                    )
                }
            }
        }
    }
}

/** Fargene på pauseskjermen: natt, med ett lys. */
private object Night {
    val Top = Color(0xFF05050C)
    val Bottom = Color(0xFF110E30)
    val Core = Color(0xFF0E0C2A)
    val Middle = Color(0xFF1E1860)
    val Edge = Color(0xFF6F63FF)
    val Glow = Color(0xFF5A4CF0)
    val Text = Color(0xFFFFFFFF)
    val Dim = Color(0x99FFFFFF)
    val Faint = Color(0x4DFFFFFF)
    val Line = Color(0x1FFFFFFF)
}

@Composable
private fun Waiting(store: BremsStore, pause: Pause, now: Long, remainingMillis: Long, onContinue: () -> Unit, onStop: () -> Unit) {
    val settings = store.data.settings
    val zone = TimeZone.currentSystemDefault()
    val done = remainingMillis == 0L
    val name = appName(pause.app)
    val phase = ((now - pause.startMillis) % (BreathMillis * 2)).toFloat() / (BreathMillis * 2)
    val blocked = pause.kind == PauseKind.Block && !done
    val breathing = settings.breathing && !done
    val breath = if (breathing) ((1 - cos(phase * 2 * PI)) / 2).toFloat() else 0.5f

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.7f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(270.dp)) {
            Box(Modifier.size(270.dp)) { Orb(breath = breath, scale = 1f) }
            Crossfade(targetState = done, animationSpec = tween(600)) { finished ->
                Text(
                    when {
                        finished -> "Klar"
                        blocked -> blockLeft(remainingMillis)
                        else -> "${(remainingMillis + 999) / 1000}"
                    },
                    style = if (finished || blocked) {
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light)
                    } else {
                        MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp)
                    },
                    color = Night.Text,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Crossfade(
            targetState = if (breathing) (if (phase < 0.5f) "Pust inn" else "Pust ut") else "",
            animationSpec = tween(800),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { cue ->
            Text(cue, style = MaterialTheme.typography.bodyMedium, color = Night.Dim, modifier = Modifier.height(20.dp))
        }
        Spacer(Modifier.weight(1f))

        // Hvorfor pausen er lang: grensen for økta eller dagens mål.
        val reason = when (pause.kind) {
            PauseKind.Limit -> "Forrige økt varte over ${formatMinutes(settings.limitMinutes)}."
            PauseKind.Goal -> store.data.reachedGoal(pause.app, now, zone)?.let { goal ->
                val streak = store.data.goalStatus(goal, now, zone).bestStreak
                "Du har brukt opp ${formatMinutes(goal.minutesPerDay)} for i dag." + if (streak > 1) " Beste rekke: $streak dager." else ""
            }
            PauseKind.Block -> store.data.goals.takeIf { pause.blockReason == BlockReason.Goal }
                ?.let { goals -> goals.filter { it.hard && it.appliesTo(pause.app) }.minByOrNull { it.minutesPerDay } }
                .let { goal -> describeBlock(pause.blockReason, pause.untilMillis, goal, pause.app, now, zone) }
            PauseKind.Open -> null
        }
        if (reason != null) {
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = Night.Dim)
            Spacer(Modifier.height(10.dp))
        }
        val quote = pause.quote
        if (quote != null && settings.showQuote) {
            Text(
                quote,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp, lineHeight = 32.sp),
                color = Night.Text,
            )
            Spacer(Modifier.height(28.dp))
        }

        if (settings.askWhy && settings.reasons.isNotEmpty()) {
            val why = store.data.pauses.lastOrNull()?.why
            Text("Hvorfor åpner du $name?", style = MaterialTheme.typography.bodySmall, color = Night.Faint)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                settings.reasons.forEach { reason ->
                    ReasonTag(reason, selected = why == reason) { store.answerWhy(if (why == reason) null else reason) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        AnimatedContent(
            targetState = done,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(200)) },
            contentKey = { if (blocked) "blocked" else it },
            modifier = Modifier.fillMaxWidth().height(124.dp),
        ) { finished ->
            if (blocked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WhiteButton("Jeg er ferdig", onStop)
                    Text("Sperren kan ikke oppheves. Åpner du $name, kommer du hit igjen.", style = MaterialTheme.typography.bodySmall, color = Night.Faint)
                }
            } else if (finished) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WhiteButton("Jeg er ferdig", onStop)
                    OutlineButton("Fortsett til $name", onContinue)
                }
            } else {
                Column {
                    Progress(1f - remainingMillis.toFloat() / (pause.untilMillis - pause.startMillis).coerceAtLeast(1))
                    Spacer(Modifier.height(12.dp))
                    Text("Du kan gå ut av Brems. Pausen teller videre.", style = MaterialTheme.typography.bodySmall, color = Night.Faint)
                }
            }
        }
    }
}

/** En tynn linje som fylles mens pausen går. */
@Composable
private fun Progress(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Night.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Night.Dim))
    }
}

/**
 * Kula: mørk inni og lysende i kanten, med en myk glød rundt som vokser når man puster inn.
 * [breath] går fra 0 (pustet ut) til 1 (pustet inn), og [scale] krymper den når pausen er over.
 */
@Composable
private fun Orb(breath: Float, scale: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val max = size.minDimension / 2
        val radius = max * (0.56f + 0.2f * breath) * scale
        // Gløden rundt.
        drawCircle(
            Brush.radialGradient(
                0f to Night.Glow.copy(alpha = 0.24f + 0.12f * breath),
                0.55f to Night.Glow.copy(alpha = 0.07f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 1.75f,
            ),
            radius = radius * 1.75f,
        )
        // Selve kula, mørkest i midten så tallet synes godt, og lysere ut mot kanten.
        drawCircle(
            Brush.radialGradient(
                0f to Night.Core,
                0.62f to Night.Middle,
                1f to Night.Edge.copy(alpha = 0.55f),
                center = center,
                radius = radius,
            ),
            radius = radius,
        )
        // En myk, lysende kant som glir ut i gløden i stedet for en skarp strek.
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.80f to Color.Transparent,
                0.87f to Night.Edge.copy(alpha = 0.6f),
                0.90f to Color(0xFFB4ACFF).copy(alpha = 0.4f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 1.12f,
            ),
            radius = radius * 1.12f,
        )
        // Et svakt lys oppe til venstre, så den ser rund ut.
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.45f),
                radius = radius * 0.7f,
            ),
            radius = radius * 0.7f,
            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.45f),
        )
    }
}

@Composable
private fun ReasonTag(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Night.Text else Color.Transparent)
            .border(1.dp, if (selected) Night.Text else Night.Line, shape)
            .pressable(onClick, pressedScale = 0.94f, haptic = Haptics::select)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Night.Top else Night.Text)
    }
}

@Composable
private fun WhiteButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Night.Text).pressable(onClick, pressedScale = 0.97f),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Night.Top)
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(shape).border(1.dp, Night.Line, shape).pressable(onClick, pressedScale = 0.97f),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Night.Dim)
    }
}

@Composable
private fun Ending(title: String, body: String, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.7f))
        Box(Modifier.size(180.dp).align(Alignment.CenterHorizontally)) { Orb(breath = 0.3f, scale = 1f) }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Night.Text)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Night.Dim, modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.height(32.dp))
        Box(Modifier.padding(bottom = 24.dp)) { WhiteButton("Ferdig", onDone) }
    }
}

/** Tiden igjen av en sperre: «2 t 14 min», «45 min», «30 s». */
private fun blockLeft(millis: Long): String = when {
    millis < 60_000 -> "${(millis + 999) / 1000} s"
    else -> formatDuration(millis + 59_999)
}

private fun stoppedText(store: BremsStore): String {
    val stopped = store.data.usageToday(store.nowMillis(), TimeZone.currentSystemDefault()).stopped
    val times = if (stopped <= 1) "Første gang i dag." else "$stopped. gang i dag."
    return "Du lot være. $times Legg fra deg telefonen og gjør noe annet."
}
