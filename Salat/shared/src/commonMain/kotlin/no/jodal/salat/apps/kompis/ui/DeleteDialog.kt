package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Spør om en samtale skal slettes: et rundt kort med en rød søppelkasse og to store knapper. */
@Composable
internal fun DeleteConversationDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surfaceContainer)
                .padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(KompisPalette.EndCall.copy(alpha = 0.14f)),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = KompisPalette.EndCall, modifier = Modifier.size(30.dp))
            }
            Text(
                "Slette samtalen?",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "«$title» blir borte for godt.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                DialogButton("Avbryt", colors.surfaceVariant, colors.onSurface, onDismiss, Modifier.weight(1f))
                DialogButton("Slett", KompisPalette.EndCall, Color.White, onConfirm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, background: Color, content: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(52.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = content)
    }
}
