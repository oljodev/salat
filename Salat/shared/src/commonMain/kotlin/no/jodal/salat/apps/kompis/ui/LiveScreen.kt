package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.AnimatedContent
import no.jodal.salat.apps.kompis.TurnTimings
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.jodal.salat.apps.kompis.Message
import no.jodal.salat.apps.kompis.Role
import no.jodal.salat.apps.kompis.formatTimer

/** Hvor samtalen er akkurat nå. */
enum class LiveStatus(val label: String, val voice: VoiceMode) {
    NeedsApiKey("Mangler nøkkel", VoiceMode.Idle),
    Connecting("Kobler til", VoiceMode.Thinking),
    Listening("Lytter", VoiceMode.Listening),
    Recording("Tar opp", VoiceMode.Listening),
    Thinking("Tenker", VoiceMode.Thinking),
    Speaking("Snakker", VoiceMode.Speaking),
    Reconnecting("Kobler til igjen", VoiceMode.Thinking),
    Muted("Mikrofonen er av", VoiceMode.Idle),
    Failed("Noe gikk galt", VoiceMode.Idle),
    Ended("Avsluttet", VoiceMode.Idle),
}

/** Alt samtaleskjermen viser. Fylles av koblingen til Gemini. */
data class LiveUiState(
    val title: String = "Ny samtale",
    val status: LiveStatus = LiveStatus.Connecting,
    val elapsedSeconds: Int = 0,
    /** Lydnivået til den som snakker, fra 0 til 1. */
    val level: Float = 0f,
    val messages: List<Message> = emptyList(),
    /** Det du sier akkurat nå, før det er ferdig. */
    val userDraft: String? = null,
    /** Det Kompis sier akkurat nå, før det er ferdig. */
    val kompisDraft: String? = null,
    val muted: Boolean = false,
    /** Hvor lyden går, f.eks. «AirPods Pro» eller «Høyttaler». */
    val audioRoute: String = "iPhone",
    val message: String? = null,
    /** Hva som skjer i tilkoblingen, vist nederst i teksten for å finne feil. */
    val debug: String? = null,
    /** Man holder inne en knapp for å snakke, i stedet for at mikrofonen alltid er på. */
    val pushToTalk: Boolean = false,
    /** Hvor raskt forrige tur gikk, når samtalen går gjennom OpenRouter. */
    val timings: TurnTimings? = null,
)

private val White = Color.White
private val Secondary = KompisPalette.NightSecondary

@Composable
fun LiveScreen(
    state: LiveUiState,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onToggleMute: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
    onEnd: () -> Unit,
    onMinimize: () -> Unit,
) {
    val mode = state.displayMode()
    Box(Modifier.fillMaxSize().background(KompisPalette.Night)) {
        Aurora(
            mode = mode,
            level = if (showTranscript) 0f else state.level,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (showTranscript) 200.dp else 440.dp),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(state, showTranscript, onMinimize = onMinimize, onToggleTranscript = onToggleTranscript)
            AnimatedContent(
                targetState = showTranscript,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { transcript ->
                if (transcript) Transcript(state) else Captions(state)
            }
            Controls(state, onToggleMute = onToggleMute, onTalkStart = onTalkStart, onTalkStop = onTalkStop, onEnd = onEnd)
        }
    }
}

@Composable
private fun TopBar(state: LiveUiState, showTranscript: Boolean, onMinimize: () -> Unit, onToggleTranscript: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        GlassButton(Icons.Rounded.KeyboardArrowDown, "Lukk", onClick = onMinimize)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                color = White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            StatusLine(state)
        }
        GlassButton(
            Icons.AutoMirrored.Rounded.Notes,
            if (showTranscript) "Skjul tekst" else "Vis tekst",
            selected = showTranscript,
            onClick = onToggleTranscript,
        )
    }
}

/** Hvordan det ser ut at samtalen er. Med knapp for å snakke lytter ikke mikrofonen før man holder inne. */
private fun LiveUiState.displayMode(): VoiceMode = when {
    muted -> VoiceMode.Idle
    pushToTalk && status == LiveStatus.Listening -> VoiceMode.Idle
    else -> status.voice
}

/** Det som står i statuslinjen. Uten at mikrofonen lytter, er man bare klar. */
private fun LiveUiState.displayLabel(): String =
    if (pushToTalk && status == LiveStatus.Listening) "Klar" else status.label

/** «● Lytter · 04:12» */
@Composable
private fun StatusLine(state: LiveUiState) {
    val dot by animateColorAsState(
        when (state.displayMode()) {
            VoiceMode.Listening -> KompisPalette.Cyan
            VoiceMode.Speaking -> KompisPalette.Pink
            VoiceMode.Thinking -> KompisPalette.Violet
            VoiceMode.Idle -> Secondary
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(
            "${state.displayLabel()}  ·  ${formatTimer(state.elapsedSeconds)}",
            style = MaterialTheme.typography.labelMedium,
            color = Secondary,
        )
    }
}

/** Det som sies akkurat nå, i stor tekst, med det forrige over i grått. */
@Composable
private fun Captions(state: LiveUiState) {
    val userIsTalking = state.userDraft != null && state.status != LiveStatus.Speaking
    val recordingNow = state.pushToTalk && state.status == LiveStatus.Recording
    val current = when {
        state.message != null -> state.message
        recordingNow -> "Snakk nå …"
        userIsTalking -> state.userDraft
        else -> state.kompisDraft ?: state.messages.lastOrNull { it.role == Role.Kompis }?.text
    }
    // Det som ble sagt før, så man husker hva man svarer på.
    val previous = when {
        state.message != null -> null
        recordingNow -> state.messages.lastOrNull { it.role == Role.Kompis }?.text
        userIsTalking -> state.messages.lastOrNull { it.role == Role.Kompis }?.text
        state.kompisDraft != null -> state.messages.lastOrNull { it.role == Role.User }?.text
        else -> null
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 40.dp),
    ) {
        if (current == null) {
            Text(
                when {
                    state.status == LiveStatus.Listening && state.pushToTalk -> "Hold inne knappen for å snakke."
                    state.status == LiveStatus.Listening -> "Si noe, så svarer Kompis."
                    else -> state.status.label + " …"
                },
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Medium,
                color = White.copy(alpha = 0.35f),
            )
            return@Column
        }
        previous?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = Secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            current,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.3).sp,
            color = if (userIsTalking && state.message == null) White.copy(alpha = 0.75f) else White,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        if (userIsTalking && state.message == null) {
            Text("Du", style = MaterialTheme.typography.labelLarge, color = KompisPalette.Cyan)
        }
    }
}

@Composable
private fun Transcript(state: LiveUiState) {
    val listState = rememberLazyListState()
    val count = state.messages.size + listOfNotNull(state.userDraft, state.kompisDraft, state.debug).size
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.messages) { ChatMessage(it.role, it.text, dark = true) }
        state.userDraft?.let { item { ChatMessage(Role.User, it, dark = true, draft = true) } }
        state.kompisDraft?.let { item { ChatMessage(Role.Kompis, it, dark = true, draft = true) } }
        state.debug?.let {
            item {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Controls(
    state: LiveUiState,
    onToggleMute: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
    onEnd: () -> Unit,
) {
    if (state.pushToTalk) {
        PushToTalkControls(state, onTalkStart = onTalkStart, onTalkStop = onTalkStop, onEnd = onEnd)
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (state.muted) White else White.copy(alpha = 0.12f))
                    .clickable(onClick = onToggleMute),
            ) {
                Icon(
                    if (state.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = if (state.muted) "Slå på mikrofonen" else "Demp mikrofonen",
                    tint = if (state.muted) Color.Black else White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(KompisPalette.EndCall)
                    .clickable(onClick = onEnd)
                    .padding(horizontal = 26.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = White, modifier = Modifier.size(22.dp))
                Text("Avslutt", style = MaterialTheme.typography.titleMedium, color = White)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Icon(
                if (state.audioRoute.contains("AirPods", ignoreCase = true)) Icons.Rounded.Headphones else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = White.copy(alpha = 0.55f),
                modifier = Modifier.size(15.dp),
            )
            Text(
                "${state.audioRoute}  ·  Du kan låse skjermen",
                style = MaterialTheme.typography.labelMedium,
                color = White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GlassButton(icon: ImageVector, description: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) White else White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = if (selected) Color.Black else White, modifier = Modifier.size(22.dp))
    }
}

/**
 * Én melding i en samtale. Det du sa står i en grå boble til høyre, og det Kompis sa står som ren
 * tekst, som i en moderne chat. Brukes både på samtaleskjermen ([dark]) og i samtalehistorikken.
 */
@Composable
fun ChatMessage(role: Role, text: String, dark: Boolean, draft: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val alpha = if (draft) 0.55f else 1f
    if (role == Role.User) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = (if (dark) White else colors.onSurface).copy(alpha = alpha),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (dark) KompisPalette.NightSurface else colors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    } else {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = (if (dark) White else colors.onSurface).copy(alpha = alpha),
            modifier = Modifier.fillMaxWidth().padding(end = 24.dp),
        )
    }
}

