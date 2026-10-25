package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.jodal.salat.apps.kompis.Conversation
import no.jodal.salat.apps.kompis.formatDuration
import no.jodal.salat.apps.kompis.formatWhen

/** En lagret samtale, som en chat. */
@Composable
fun ConversationScreen(
    conversation: Conversation,
    nowMillis: Long,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    startRenaming: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    // Navnet som redigeres, eller `null` når man ikke endrer navn.
    var newTitle by remember {
        mutableStateOf(if (startRenaming) conversation.title.selectedAll() else null)
    }
    val editing = newTitle
    val canSave = editing?.text?.isNotBlank() == true

    fun startRenaming() {
        newTitle = conversation.title.selectedAll()
    }

    fun saveTitle() {
        val title = newTitle?.text?.trim().orEmpty()
        if (title.isNotEmpty() && title != conversation.title) onRename(title)
        newTitle = null
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            ) {
                AnimatedContent(
                    targetState = editing != null,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxWidth(),
                ) { isEditing ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isEditing) {
                            Text(
                                "Avbryt",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { newTitle = null }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Lagre",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onPrimary,
                                modifier = Modifier
                                    .alpha(if (canSave) 1f else 0.35f)
                                    .clip(CircleShape)
                                    .background(colors.primary)
                                    .clickable(enabled = canSave, onClick = ::saveTitle)
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                        } else {
                            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Tilbake", onClick = onBack)
                            Spacer(Modifier.weight(1f))
                            CircleIconButton(Icons.Rounded.Edit, "Endre navn", onClick = ::startRenaming)
                            CircleIconButton(Icons.Rounded.DeleteOutline, "Slett samtalen", onClick = { confirmDelete = true })
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        if (editing != null) {
                            TitleEditor(
                                value = editing,
                                onChange = { newTitle = it },
                                onDone = { if (canSave) saveTitle() },
                            )
                        } else {
                            Text(
                                conversation.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.onBackground,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = ::startRenaming),
                            )
                        }
                        Text(
                            "${formatWhen(conversation.createdAtMillis, nowMillis).replaceFirstChar { it.uppercase() }}  ·  ${formatDuration(conversation.durationSeconds)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                items(conversation.messages) { ChatMessage(it.role, it.text, dark = false) }
            }
        }

        // «Fortsett samtalen» nederst, over en myk overgang. Skjult mens man endrer navn.
        if (editing == null) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = onContinue),
            ) {
                VoiceBars(level = 1f, color = colors.onPrimary, animated = false, modifier = Modifier.height(18.dp))
                Text("Fortsett samtalen", style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
            }
        }
    }

    if (confirmDelete) {
        DeleteConversationDialog(
            title = conversation.title,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Tittelen som et felt man skriver rett i, i samme store skrift som før. */
@Composable
private fun TitleEditor(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        val selection = TextSelectionColors(handleColor = KompisPalette.Blue, backgroundColor = KompisPalette.Blue.copy(alpha = 0.25f))
        CompositionLocalProvider(LocalTextSelectionColors provides selection) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = colors.onBackground),
                cursorBrush = SolidColor(KompisPalette.Blue),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty()) {
                            Text("Gi samtalen et navn", style = MaterialTheme.typography.headlineMedium, color = colors.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
        if (value.text.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.25f))
                    .clickable { onChange(TextFieldValue("")) },
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Tøm", tint = colors.onSurface, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Teksten med alt markert, så man kan skrive rett over det gamle navnet. */
private fun String.selectedAll() = TextFieldValue(this, selection = TextRange(0, length))
