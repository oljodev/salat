package no.jodal.salat.apps.kurs.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.ui.palette
import no.jodal.salat.ui.pressable

/** Et spørsmål i check-in: tittel, en valgfri forklaring og svaret under. */
@Composable
fun Question(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textDim)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Knapper side om side der én kan velges. Trykker man på den valgte, blir ingen valgt. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    color: (T) -> Color = { KursAccent },
    modifier: Modifier = Modifier.fillMaxWidth(),
    /** Lavere knapper, f.eks. over en graf. */
    compact: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        modifier = modifier.clip(RoundedCornerShape(if (compact) 10.dp else 14.dp)).background(palette.cardHigh).padding(if (compact) 2.dp else 4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val background by animateColorAsState(if (on) color(value) else Color.Transparent)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = if (compact) 28.dp else 44.dp)
                    .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
                    .background(background)
                    .pressable({ onSelect(if (on) null else value) }, pressedScale = 0.96f, haptic = Haptics::select)
                    .padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 8.dp),
            ) {
                Text(
                    label,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else palette.text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Et ja/nei-spørsmål, med knappene til høyre for teksten. */
@Composable
fun YesNoQuestion(title: String, subtitle: String?, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textDim)
            }
        }
        Spacer(Modifier.width(12.dp))
        YesNo(value, onChange, Modifier.width(128.dp))
    }
}

/** Ja eller nei, eller ikke svart. */
@Composable
fun YesNo(value: Boolean?, onChange: (Boolean?) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) = Segmented(
    options = listOf(true to "Ja", false to "Nei"),
    selected = value,
    onSelect = onChange,
    color = { if (it) KursAccent else KursNo },
    modifier = modifier,
)

/** Små piller der én kan velges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Pills(options: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Color.White else palette.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) KursAccent else palette.cardHigh)
                    .pressable({ onSelect(if (on) null else value) }, pressedScale = 0.94f, haptic = Haptics::select)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** Aksentfargen i Kurs: en dyp grønnblå. */
val KursAccent = Color(0xFF1F9E89)
val KursNo = Color(0xFF8E8E98)
val KursGold = Color(0xFFE0A526)
