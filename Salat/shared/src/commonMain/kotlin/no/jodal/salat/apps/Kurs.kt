package no.jodal.salat.apps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.ui.graphics.Color
import no.jodal.salat.MiniApp
import no.jodal.salat.MiniAppIcon
import no.jodal.salat.apps.kurs.ui.KursScreen

val Kurs = MiniApp(
    title = "Kurs",
    description = "Daglig logg av søvn, skjermtid og vaner",
    icon = MiniAppIcon.Symbol(Icons.Rounded.Explore),
    color = Color(0xFF1F9E89),
    customTopBar = true,
    content = { KursScreen(it.bridge, onBack = it.onBack) },
)
