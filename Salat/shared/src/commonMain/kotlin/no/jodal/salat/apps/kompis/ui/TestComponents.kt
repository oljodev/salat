package no.jodal.salat.apps.kompis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.jodal.salat.apps.kompis.OpenRouterModel
import no.jodal.salat.apps.kompis.formatReleased

// Det testskjermene for OpenRouter har felles.

/** Valgt modell, med [details] under navnet. Trykk for å velge en annen. */
@Composable
internal fun ModelRow(name: String, details: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Modell",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Velg modell", tint = colors.onSurfaceVariant)
        }
    }
}

/** Alle [models], de nyeste først, med søk. [averageMillis] vises for modeller som er testet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSheet(
    models: List<OpenRouterModel>,
    isLoading: Boolean,
    error: String?,
    selectedId: String,
    averageMillis: (String) -> Long?,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val now = remember { kotlin.time.Clock.System.now().toEpochMilliseconds() }

    fun close(then: () -> Unit = {}) {
        then()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.background,
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Spacer(Modifier.size(64.dp, 1.dp))
                Text("Velg modell", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = { close() }) { Text("Ferdig", style = MaterialTheme.typography.bodyLarge) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Søk", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            inner()
                        }
                    },
                )
            }

            val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            val shown = models.filter { model -> words.all { it in model.name.lowercase() || it in model.id.lowercase() } }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            ) {
                when {
                    models.isEmpty() && isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.onSurfaceVariant, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                    models.isEmpty() -> item(key = "empty") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                            Text(
                                error ?: "Legg inn en OpenRouter-nøkkel i innstillingene for å se modellene.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (error != null) TextButton(onClick = onRetry) { Text("Prøv igjen") }
                        }
                    }
                    shown.isEmpty() -> item(key = "none") {
                        Text(
                            "Ingen modeller passer med «${query.trim()}».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { model ->
                    if (model != shown.first()) HorizontalDivider(color = colors.outlineVariant)
                    ModelOption(
                        model = model,
                        released = formatReleased(model.createdAtSeconds, now),
                        average = averageMillis(model.id),
                        selected = model.id == selectedId,
                        onClick = { close { onSelect(model.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelOption(model: OpenRouterModel, released: String, average: Long?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(model.shortName, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(
                listOfNotNull(model.maker, released, average?.let { "$it ms" }).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioDot(selected)
    }
}

/** En rund avkrysning, fylt når [selected]. */
@Composable
internal fun RadioDot(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier.border(2.dp, colors.outlineVariant, CircleShape)),
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** En overskrift med valg under, som piller. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Options(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
internal fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else colors.surfaceVariant)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Vises når OpenRouter-nøkkelen mangler. */
@Composable
internal fun KeyNotice(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = colors.onSurface)
        Column(Modifier.weight(1f)) {
            Text("Legg inn OpenRouter-nøkkel", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                "Testen bruker modeller fra OpenRouter.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}
