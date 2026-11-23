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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import no.jodal.salat.Haptics
import no.jodal.salat.apps.brems.formatDay
import no.jodal.salat.apps.brems.ui.Card
import no.jodal.salat.apps.brems.ui.Footnote
import no.jodal.salat.apps.brems.ui.GroupLabel
import no.jodal.salat.apps.brems.ui.LargeTitle
import no.jodal.salat.apps.brems.ui.NavBar
import no.jodal.salat.apps.brems.ui.PrimaryButton
import no.jodal.salat.apps.brems.ui.RowDivider
import no.jodal.salat.apps.brems.ui.palette
import no.jodal.salat.apps.kurs.DayLog
import no.jodal.salat.apps.kurs.FocusTag
import no.jodal.salat.apps.kurs.KursStore
import no.jodal.salat.apps.kurs.MaxDayPoints
import no.jodal.salat.apps.kurs.MaxWinLength
import no.jodal.salat.apps.kurs.Points
import no.jodal.salat.apps.kurs.ScreenQuality
import no.jodal.salat.apps.kurs.points
import no.jodal.salat.ui.pressable

/** Check-in for én dag, på én side man blar gjennom. [onSaved] får dagen som ble lagret. */
@Composable
fun CheckInScreen(store: KursStore, initialDate: LocalDate, onBack: () -> Unit, onSaved: (LocalDate) -> Unit) {
    val today = store.today()
    val yesterday = today.minus(DatePeriod(days = 1))
    var date by remember { mutableStateOf(initialDate) }
    var log by remember { mutableStateOf(store.log(initialDate) ?: store.prefill(initialDate)) }
    val existing = store.log(date) != null

    fun switchTo(other: LocalDate) {
        date = other
        log = store.log(other) ?: store.prefill(other)
    }

    val focus = LocalFocusManager.current
    // imePadding på hele siden, så Lagre-knappen ligger over tastaturet.
    Column(Modifier.fillMaxSize().background(palette.background).imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                // Et trykk utenfor et tekstfelt lukker tastaturet.
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            NavBar("Kurs", onBack)
            Box(Modifier.padding(horizontal = 4.dp)) {
                LargeTitle(if (existing) "Rediger logg" else "Logg dagen", subtitle = formatDay(date))
            }
            if (date == today || date == yesterday) {
                Segmented(
                    options = listOf(yesterday to "I går", today to "I dag"),
                    selected = date,
                    onSelect = { if (it != null) switchTo(it) },
                )
                Spacer(Modifier.height(20.dp))
            }

            GroupLabel("1 · Søvn og kveld")
            Card {
                Question(
                    "Garmin Sleep Score",
                    if (date == today) "For natten som kommer. Kan fylles inn i morgen tidlig." else "For natten etter ${formatDay(date).lowercase()}.",
                ) {
                    SleepScoreInput(log.sleepScore) { log = log.copy(sleepScore = it) }
                }
                RowDivider(16.dp)
                YesNoQuestion("Skjermer av før 22:00?", "+${Points.ScreensOff} poeng", log.screensOffBefore22) { log = log.copy(screensOffBefore22 = it) }
                RowDivider(16.dp)
                YesNoQuestion("Refleksjon på papir?", "2–3 avsnitt og de faste spørsmålene. +${Points.Reflection}", log.paperReflection) { log = log.copy(paperReflection = it) }
                RowDivider(16.dp)
                YesNoQuestion("Fysisk lesing i sengen?", "Business eller faglitteratur. +${Points.Reading}", log.physicalReading) { log = log.copy(physicalReading = it) }
            }

            Spacer(Modifier.height(24.dp))
            GroupLabel("2 · Skjermtid og fokus")
            Card {
                Question("Kvalitetsskjermtid", qualityHint(log.screenQuality)) {
                    Segmented(
                        options = listOf(ScreenQuality.High to "Høy", ScreenQuality.Mixed to "Blandet", ScreenQuality.Low to "Lav"),
                        selected = log.screenQuality,
                        onSelect = { log = log.copy(screenQuality = it) },
                        color = {
                            when (it) {
                                ScreenQuality.High -> KursAccent
                                ScreenQuality.Mixed -> KursGold
                                ScreenQuality.Low -> KursNo
                            }
                        },
                    )
                }
                RowDivider(16.dp)
                YesNoQuestion(
                    "Holdt skjerm- og appsperrer?",
                    if (log.keptAppLimitsFromBrems) "Fylt ut fra Brems. +${Points.AppLimits}" else "+${Points.AppLimits} poeng",
                    log.keptAppLimits,
                ) { log = log.copy(keptAppLimits = it, keptAppLimitsFromBrems = false) }
            }

            Spacer(Modifier.height(24.dp))
            GroupLabel("3 · Fysisk og mental reset")
            Card {
                Question("Dagstype") {
                    Segmented(
                        options = listOf(true to "Treningsdag", false to "Treningsfri"),
                        selected = log.isTrainingDay,
                        onSelect = { log = log.copy(isTrainingDay = it) },
                    )
                }
                RowDivider(16.dp)
                YesNoQuestion(
                    if (log.isTrainingDay == true) "Fullført økt og mikropauser ved PC-en?" else "Gikk tur ute mellom skjermøktene (15+ min)?",
                    "+${Points.Moved} poeng",
                    log.movedOrRested,
                ) { log = log.copy(movedOrRested = it) }
            }

            Spacer(Modifier.height(24.dp))
            GroupLabel("4 · Ship & Earn")
            Card {
                YesNoQuestion(
                    "Flyttet du et prosjekt nærmere lansering, salg eller ferdigstillelse?",
                    "+${Points.ShipAndEarn} poeng",
                    log.shipAndEarn,
                ) { log = log.copy(shipAndEarn = it) }
                RowDivider(16.dp)
                Question("Fokusområde", "Valgfritt") {
                    Pills(FocusTag.entries.map { it to it.label }, log.focusTag) { log = log.copy(focusTag = it) }
                }
            }

            Spacer(Modifier.height(24.dp))
            GroupLabel("5 · Tilstand")
            Card {
                Question("Kropp og hode", feelingLabel(log.bodyFeeling)) {
                    Segmented(
                        options = (1..5).map { it to "$it" },
                        selected = log.bodyFeeling,
                        onSelect = { log = log.copy(bodyFeeling = it) },
                    )
                }
                RowDivider(16.dp)
                Question("Dagens viktigste seier eller innsikt") {
                    WinField(log.dailyWin) { log = log.copy(dailyWin = it) }
                }
            }
            if (existing) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Slett loggen",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.warn,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().pressable({
                        store.delete(date)
                        onBack()
                    }).padding(12.dp),
                )
            }
            Footnote("Sleep Score og poengene kan endres senere. Alt lagres bare på telefonen.")
            Spacer(Modifier.height(24.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().background(palette.background).navigationBarsPadding().padding(16.dp),
        ) {
            Column {
                Text("${log.points}", style = MaterialTheme.typography.headlineMedium, color = if (log.points >= Points.ApprovedDay) KursAccent else palette.text)
                Text("av $MaxDayPoints", style = MaterialTheme.typography.labelMedium, color = palette.textDim)
            }
            PrimaryButton(
                if (existing) "Lagre endringer" else "Lagre",
                onClick = {
                    focus.clearFocus()
                    Haptics.success()
                    store.save(log.copy(date = date))
                    onSaved(date)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Sleep Score: et tall man kan skrive, med hurtigknapper og ±1. */
@Composable
private fun SleepScoreInput(value: Int?, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                val digits = new.filter(Char::isDigit).take(3)
                text = digits
                onChange(digits.toIntOrNull()?.coerceIn(0, 100))
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = palette.text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(KursAccent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.cardHigh)
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) Text("–", style = MaterialTheme.typography.headlineMedium, color = palette.textFaint)
                    inner()
                }
            },
        )
        listOf(-1, 1).forEach { step ->
            SmallButton(if (step < 0) "−" else "+") { onChange(((value ?: 80) + step).coerceIn(0, 100)) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            when {
                value == null -> ""
                value >= 85 -> "+${Points.SleepHigh}"
                value >= 75 -> "+${Points.SleepMid}"
                else -> "+0"
            },
            style = MaterialTheme.typography.titleMedium,
            color = KursAccent,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(70, 75, 80, 85, 90).forEach { quick ->
            val on = value == quick
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) KursAccent else palette.cardHigh)
                    .pressable({ onChange(quick) }, pressedScale = 0.94f, haptic = Haptics::select)
                    .padding(vertical = 8.dp),
            ) {
                Text("$quick", style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else palette.text)
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.cardHigh)
            .pressable(onClick, pressedScale = 0.9f, haptic = Haptics::select),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = palette.text)
    }
}

@Composable
private fun WinField(value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.replace('\n', ' ').take(MaxWinLength)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.text),
        cursorBrush = SolidColor(KursAccent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.cardHigh)
            .padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Hva gjorde deg stolt eller ga fremgang i dag?", style = MaterialTheme.typography.bodyLarge, color = palette.textFaint)
                }
                inner()
            }
        },
    )
    Text(
        "${value.length}/$MaxWinLength",
        style = MaterialTheme.typography.labelMedium,
        color = palette.textFaint,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

private fun qualityHint(quality: ScreenQuality?): String = when (quality) {
    ScreenQuality.High -> "Bygging, koding, AI-lab, læring. +${Points.QualityHigh}"
    ScreenQuality.Mixed -> "Noe bra arbeid, men også litt surfing. +${Points.QualityMixed}"
    ScreenQuality.Low -> "Mye passiv konsumering. +0"
    null -> "Høy +${Points.QualityHigh}, blandet +${Points.QualityMixed}, lav +0"
}

private fun feelingLabel(feeling: Int?): String = when (feeling) {
    1 -> "Helt utladet"
    2 -> "Sliten"
    3 -> "Helt ok"
    4 -> "God"
    5 -> "Skarp og full av energi"
    else -> "1 = helt utladet, 5 = skarp og full av energi"
}
