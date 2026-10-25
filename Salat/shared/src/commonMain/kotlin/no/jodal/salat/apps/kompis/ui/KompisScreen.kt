package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import no.jodal.salat.resources.Res
import no.jodal.salat.resources.kompis_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.jodal.salat.DeepLinks
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.Conversation
import no.jodal.salat.apps.kompis.KompisLive
import no.jodal.salat.apps.kompis.KompisStore
import no.jodal.salat.apps.kompis.Period
import no.jodal.salat.apps.kompis.formatDuration
import no.jodal.salat.apps.kompis.formatWhen
import no.jodal.salat.apps.kompis.periodOf
import no.jodal.salat.ui.BackButton
import no.jodal.salat.ui.LocalDarkTheme

private sealed interface KompisDestination {
    data object Home : KompisDestination
    data class Chat(val id: String) : KompisDestination
    data class Live(val conversationId: String?) : KompisDestination
    data object SttTest : KompisDestination
    data object LlmTest : KompisDestination
    data object TtsTest : KompisDestination
}

@Composable
fun KompisScreen(bridge: NativeBridge, onBack: () -> Unit) {
    KompisTheme {
        val store = remember { KompisStore.get(bridge) }
        val live = remember { KompisLive.get(store, bridge) }
        var destination by remember { mutableStateOf<KompisDestination>(KompisDestination.Home) }
        var showSettings by remember { mutableStateOf(false) }
        var showTranscript by remember { mutableStateOf(false) }

        // «Snakk med Kompis» fra widgeten eller kontrollsenteret starter en samtale med en gang.
        LaunchedEffect(DeepLinks.pending) {
            if (DeepLinks.consume("kompis", "start")) {
                if (!live.isActive) live.start(continuing = null)
                destination = KompisDestination.Live(live.conversationId)
            }
        }

        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                val duration = 300
                when {
                    targetState is KompisDestination.Live ->
                        slideInVertically(tween(duration)) { it } togetherWith fadeOut(tween(duration))
                    initialState is KompisDestination.Live ->
                        fadeIn(tween(duration)) togetherWith slideOutVertically(tween(duration)) { it }
                    targetState is KompisDestination.Chat || targetState is KompisDestination.SttTest || targetState is KompisDestination.LlmTest ||
                        targetState is KompisDestination.TtsTest ->
                        slideInHorizontally(tween(duration)) { it } togetherWith fadeOut(tween(duration))
                    else -> fadeIn(tween(duration)) togetherWith slideOutHorizontally(tween(duration)) { it }
                }
            },
        ) { current ->
            when (current) {
                KompisDestination.Home -> KompisHome(
                    store = store,
                    live = live,
                    onBack = onBack,
                    onStart = {
                        live.start(continuing = null)
                        destination = KompisDestination.Live(live.conversationId)
                    },
                    onReturnToLive = { destination = KompisDestination.Live(live.conversationId) },
                    onOpen = { destination = KompisDestination.Chat(it.id) },
                    onSettings = { showSettings = true },
                    onSttTest = { destination = KompisDestination.SttTest },
                    onLlmTest = { destination = KompisDestination.LlmTest },
                    onTtsTest = { destination = KompisDestination.TtsTest },
                )
                KompisDestination.TtsTest -> TtsTestScreen(
                    store = store,
                    bridge = bridge,
                    onBack = { destination = KompisDestination.Home },
                    onSettings = { showSettings = true },
                )
                KompisDestination.LlmTest -> LlmTestScreen(
                    store = store,
                    bridge = bridge,
                    onBack = { destination = KompisDestination.Home },
                    onSettings = { showSettings = true },
                )
                KompisDestination.SttTest -> SttTestScreen(
                    store = store,
                    bridge = bridge,
                    onBack = { destination = KompisDestination.Home },
                    onSettings = { showSettings = true },
                )
                is KompisDestination.Chat -> {
                    val conversation = store.conversation(current.id)
                    if (conversation == null) {
                        destination = KompisDestination.Home
                    } else {
                        ConversationScreen(
                            conversation = conversation,
                            nowMillis = store.nowMillis(),
                            onBack = { destination = KompisDestination.Home },
                            onContinue = {
                                if (live.conversationId != conversation.id) {
                                    live.end()
                                    live.start(continuing = conversation)
                                }
                                destination = KompisDestination.Live(conversation.id)
                            },
                            onRename = { store.renameConversation(conversation.id, it) },
                            onDelete = {
                                store.deleteConversation(conversation.id)
                                destination = KompisDestination.Home
                            },
                        )
                    }
                }
                is KompisDestination.Live -> {
                    // Etterpå går man til samtalen, hvis noe ble sagt, ellers til oversikten.
                    fun leave() {
                        val saved = current.conversationId?.let(store::conversation)
                        destination = saved?.let { KompisDestination.Chat(it.id) } ?: KompisDestination.Home
                    }
                    val elapsed by produceState(0, live.startedAtMillis, live.isActive) {
                        while (live.isActive) {
                            value = ((store.nowMillis() - live.startedAtMillis) / 1000).toInt()
                            delay(1000)
                        }
                    }
                    LiveScreen(
                        state = live.state.copy(elapsedSeconds = elapsed),
                        showTranscript = showTranscript,
                        onToggleTranscript = { showTranscript = !showTranscript },
                        onToggleMute = live::toggleMute,
                        onEnd = {
                            live.end()
                            leave()
                        },
                        onMinimize = ::leave,
                    )
                }
            }
        }

        if (showSettings) {
            KompisSettingsSheet(store = store, onDismiss = { showSettings = false })
        }
    }
}

@Composable
fun KompisHome(
    store: KompisStore,
    live: KompisLive,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onReturnToLive: () -> Unit,
    onOpen: (Conversation) -> Unit,
    onSettings: () -> Unit,
    onSttTest: () -> Unit,
    onLlmTest: () -> Unit,
    onTtsTest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val now = store.nowMillis()
    val conversations = store.data.conversations.sortedByDescending { it.updatedAtMillis }
    val grouped = conversations.groupBy { periodOf(it.updatedAtMillis, now) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp,
        ),
    ) {
        item(key = "top") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(52.dp),
            ) {
                Box(Modifier.offset(x = (-10).dp)) { BackButton(onBack, color = colors.onBackground) }
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.Tune, "Innstillinger", onClick = onSettings)
            }
        }
        item(key = "title") { Title() }
        item(key = "start") {
            if (live.isActive) OngoingRow(live, onClick = onReturnToLive) else StartButton(onStart)
        }
        if (!store.hasApiKey) {
            item(key = "key") { ApiKeyRow(onClick = onSettings) }
        }
        item(key = "stt") {
            TestRow(Icons.Rounded.Mic, "Test tale til tekst", "Prøv modeller fra OpenRouter, og se hvor raskt teksten kommer.", onSttTest)
        }
        item(key = "llm") {
            TestRow(Icons.Rounded.Forum, "Test språkmodell", "Chat med en modell fra OpenRouter, og se hvor raskt svarene kommer.", onLlmTest)
        }
        item(key = "tts") {
            TestRow(Icons.Rounded.RecordVoiceOver, "Test tekst til tale", "Hør hvordan stemmene fra OpenRouter leser norsk.", onTtsTest)
        }

        item(key = "heading") {
            Text(
                "Samtaler",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.padding(top = 36.dp, bottom = 4.dp),
            )
        }
        if (conversations.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Samtalene dine lagres her, så du kan lese dem og fortsette der dere slapp.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        for (period in Period.entries) {
            val items = grouped[period] ?: continue
            item(key = "period-$period") {
                Text(
                    period.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                )
            }
            items.forEachIndexed { index, conversation ->
                item(key = conversation.id) {
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    ConversationRow(conversation, now, onClick = { onOpen(conversation) })
                }
            }
        }
    }
}

@Composable
private fun Title() {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Image(
            painterResource(Res.drawable.kompis_logo),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                // En tynn kant, så den mørke logoen synes mot svart bakgrunn.
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)),
        )
        Column {
            Text("Kompis", style = MaterialTheme.typography.displaySmall, color = colors.onBackground)
            Text(
                "Snakk om hva som helst",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartButton(onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(onClick = onStart),
        ) {
            VoiceBars(level = 1f, color = colors.onPrimary, animated = false, modifier = Modifier.height(20.dp))
            Text("Start samtale", style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Feature(Icons.Rounded.Headphones, "Med AirPods")
            Feature(Icons.Rounded.Lock, "Med skjermen låst")
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** Vises i stedet for startknappen mens en samtale pågår. */
@Composable
private fun OngoingRow(live: KompisLive, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 28.dp)
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(KompisPalette.Night)
            .clickable(onClick = onClick),
    ) {
        Aurora(
            mode = live.state.status.voice,
            level = live.state.level,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            VoiceBars(level = live.state.level, color = Color.White, modifier = Modifier.height(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Samtale pågår", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(live.state.status.label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun ApiKeyRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = colors.onSurface)
        Column(Modifier.weight(1f)) {
            Text("Legg inn Gemini-nøkkel", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                "Kompis trenger en nøkkel fra Google AI Studio for å snakke.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** Fører til en av testene på veien mot Kompis med OpenRouter. */
@Composable
private fun TestRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, nowMillis: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatWhen(conversation.updatedAtMillis, nowMillis).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        conversation.messages.lastOrNull()?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.surfaceVariant).clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = colors.onSurface, modifier = Modifier.size(20.dp))
    }
}
