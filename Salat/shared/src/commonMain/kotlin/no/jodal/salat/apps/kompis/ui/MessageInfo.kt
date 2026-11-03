package no.jodal.salat.apps.kompis.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import no.jodal.salat.apps.kompis.MessageInfo
import no.jodal.salat.apps.kompis.Role
import no.jodal.salat.ui.LocalDarkTheme

/** Målinger vises bare når man ber om dem via et langt trykk. */
@Composable
fun ChatMessage(role: Role, text: String, dark: Boolean, draft: Boolean = false, info: MessageInfo? = null) {
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().combinedClickable(
        enabled = !draft,
        onClick = {},
        onLongClickLabel = "Vis meldingsvalg",
        onLongClick = { menu = true },
    )) {
        ChatMessageBody(role, text, dark, draft)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Vis meldingsinfo") }, onClick = {
                menu = false
                details = true
            })
        }
    }
    if (details) {
        KompisTheme(dark = dark || LocalDarkTheme.current) {
            MessageInfoSheet(role, info, onDismiss = { details = false })
        }
    }
}
