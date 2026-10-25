package no.jodal.salat

/**
 * Det appen trenger fra iOS som ikke kan gjøres i Kotlin.
 *
 * Implementeres i Swift i `iosApp/Salat/IosNativeBridge.swift`.
 */
interface NativeBridge {
    /** Når SideStore-signeringen går ut, i millisekunder siden 1970, eller `null` hvis ukjent. */
    fun signingExpiresAtMillis(): Long?

    /** Planlegger et varsel dagen før signeringen går ut. Erstatter et tidligere planlagt varsel. */
    fun scheduleExpiryReminder()

    /** Sender et testvarsel etter [delaySeconds] sekunder. */
    fun sendTestNotification(delaySeconds: Int)

    /**
     * Planlegger en testalarm med AlarmKit etter [delaySeconds] sekunder. [snoozeMinutes] = 0 gir ingen slumring.
     * [onResult] kalles med en feilmelding, eller `null` hvis alarmen ble planlagt.
     */
    fun scheduleTestAlarm(delaySeconds: Int, snoozeMinutes: Int, onResult: (String?) -> Unit)

    /** Leser tekst som er lagret med [saveText], eller `null`. */
    fun loadText(key: String): String?

    /** Lagrer tekst på telefonen. */
    fun saveText(key: String, value: String)

    /** Leser en hemmelighet, f.eks. en API-nøkkel, fra nøkkelringen, eller `null`. */
    fun loadSecret(key: String): String?

    /** Lagrer en hemmelighet i nøkkelringen. Tom tekst sletter den. */
    fun saveSecret(key: String, value: String)

    /**
     * Gjør alarmene og varslene fra Rise lik [planJson], en `RisePlan` som JSON.
     * [onResult] kalles med en feilmelding, eller `null` hvis alt ble planlagt.
     */
    fun applyRiseSchedule(planJson: String, onResult: (String?) -> Unit)

    /** Spiller av en lyd fra Rise, f.eks. `rise-myk`. */
    fun previewSound(sound: String)

    fun stopSoundPreview()

    /**
     * Starter en samtale med Gemini Live. [configJson] er en `LiveConfig` som JSON.
     * Lyd og tilkobling håndteres i Swift, og [listener] får vite hva som skjer.
     */
    fun startLiveSession(configJson: String, listener: LiveListener)

    fun stopLiveSession()

    fun setLiveMuted(muted: Boolean)

    /**
     * Starter en ny økt i den pågående samtalen, med [historyJson] (en liste av `HistoryTurn`) som
     * tekst. Det er billigere enn at Gemini husker samtalen som lyd. Skjer først når det er stille.
     */
    fun rollLiveSession(historyJson: String)

    // Kompis: test av tale til tekst gjennom OpenRouter.

    /** Slår på mikrofonen, så opptak kan begynne med en gang. [listener] får lydnivået og resultatene. */
    fun kompisSttOpen(listener: SttListener)

    /** Begynner å ta vare på lyden fra mikrofonen. */
    fun kompisSttStartRecording()

    /** Stopper opptaket og sender lyden til OpenRouter. [configJson] er en `SttConfig` som JSON. */
    fun kompisSttStopRecording(configJson: String)

    /** Slår av mikrofonen. Et svar som er på vei, kommer likevel. */
    fun kompisSttClose()

    /**
     * Henter [path] fra OpenRouter sitt API, f.eks. `models/user`, med nøkkelen [apiKey].
     * [onResult] kalles med svaret, eller med `null` og en feilmelding.
     */
    fun openRouterGet(path: String, apiKey: String, onResult: (body: String?, error: String?) -> Unit)

    /** Åpner forbindelsen til OpenRouter på forhånd, så neste kall slipper å vente på den. */
    fun openRouterWarmUp()

    /**
     * Sender [bodyJson] til `chat/completions` hos OpenRouter med strømming, og gir hver hendelse
     * til [listener]. Erstatter et svar som allerede strømmer.
     */
    fun openRouterChat(apiKey: String, bodyJson: String, listener: ChatListener)

    /** Stopper svaret som strømmer. [ChatListener] får ikke vite mer om det. */
    fun openRouterCancelChat()

    /**
     * Sender [bodyJson] til `audio/speech` hos OpenRouter og spiller av lyden mens den kommer. Lyden
     * tas vare på som [id], så den kan spilles igjen. [sampleRate] = 0 lar Swift finne den ut.
     * [callAudio] spiller av som i en samtale, ellers med best mulig lyd. Stopper det som spilles.
     */
    fun ttsSpeak(id: String, apiKey: String, bodyJson: String, sampleRate: Int, callAudio: Boolean, listener: TtsListener)

    /** Spiller av [id] på nytt. */
    fun ttsReplay(id: String, callAudio: Boolean, listener: TtsListener)

    /** Stopper avspillingen og det som lastes ned. */
    fun ttsStop()

    /** Stopper alt og glemmer lyden. */
    fun ttsClose()

    // Ekko. Lyden til hver lyd ligger i en fil som heter det samme som lyden sin id.

    /** Henter lyden fra YouTube-videoen [videoId] og lagrer den som [clipId]. */
    fun ekkoImportYouTube(clipId: String, videoId: String, listener: EkkoImportListener)

    /** Leter etter en video på nettsiden [url] (f.eks. Snapchat Spotlight), og lagrer lyden som [clipId]. */
    fun ekkoImportWeb(clipId: String, url: String, listener: EkkoImportListener)

    /** Lar brukeren velge en video fra Bilder ([fromPhotos]) eller en lyd- eller videofil fra Filer. */
    fun ekkoImportFile(clipId: String, fromPhotos: Boolean, listener: EkkoImportListener)

    /** Avbryter hentingen som pågår. Listeneren får `onFailed("")`. */
    fun ekkoCancelImport()

    /** Sletter lydfilen til [clipId]. */
    fun ekkoDeleteAudio(clipId: String)

    /** Det som ligger på utklippstavlen, eller `null`. iOS kan spørre brukeren om lov først. */
    fun ekkoPasteboardText(): String?

    /**
     * Spiller av [clipId] fra [fromSeconds] til [endSeconds] i lydfilen, og viser [title] og [artist]
     * på låseskjermen. [startSeconds] er der utsnittet begynner, så låseskjermen kan vise tiden i lyden.
     * Erstatter det som spilles nå. [lockScreen] = false brukes når man hører på et utsnitt mens man klipper.
     */
    fun ekkoPlay(
        clipId: String,
        startSeconds: Double,
        endSeconds: Double,
        fromSeconds: Double,
        title: String,
        artist: String,
        lockScreen: Boolean,
        listener: EkkoPlayerListener,
    )

    fun ekkoPause()

    fun ekkoResume()

    /** Hopper til [seconds] i lydfilen. */
    fun ekkoSeek(seconds: Double)

    fun ekkoStop()

    /**
     * Lagrer [json] der widgetene kan lese det, under navnet [name] (f.eks. `ekko`), og ber
     * widgetene tegne seg på nytt hvis noe er endret.
     */
    fun saveWidgetData(name: String, json: String)

    /** Åpner [url] i en annen app, f.eks. `shortcuts://` eller `tiktok://`. */
    fun openUrl(url: String)

    /**
     * Erstatter varslene fra Brems med [noticesJson], en liste av `ScheduledNotice`. En tom liste
     * fjerner dem.
     */
    fun bremsNotify(noticesJson: String)

    /** En liten vibrasjon: `light`, `selection`, `rigid`, `success` eller `warning`. */
    fun haptic(kind: String)
}

/** Det Swift forteller om testen av tale til tekst. Kalles alltid på hovedtråden. */
interface SttListener {
    /** Lydnivået fra mikrofonen, fra 0 til 1. */
    fun onLevel(level: Float)

    /** Mikrofonen virker. [name] er hvilken det er, f.eks. «iPhone-mikrofon» eller «AirPods Pro». */
    fun onMicrophone(name: String)

    /** Mikrofonen kunne ikke startes. */
    fun onMicrophoneError(message: String)

    /**
     * Svaret fra OpenRouter kom. [responseJson] er svaret som det er, [audioSeconds] hvor langt
     * opptaket var, [uploadBytes] hvor mye som ble sendt, og [millis] tiden fra opptaket stoppet.
     */
    fun onTranscribed(responseJson: String, audioSeconds: Double, uploadBytes: Int, millis: Long)

    fun onError(message: String)
}

/** Et svar som strømmer fra OpenRouter. Kalles alltid på hovedtråden. */
interface ChatListener {
    /** En hendelse: JSON-en etter `data:`. [millis] er tiden siden forespørselen ble sendt. */
    fun onEvent(dataJson: String, millis: Long)

    /** Strømmen er ferdig. */
    fun onDone(millis: Long)

    fun onError(message: String)
}

/** Det som skjer med en lyd fra tekst til tale. Kalles alltid på hovedtråden. */
interface TtsListener {
    /** Den første lyden kom, [millis] etter at forespørselen ble sendt. */
    fun onFirstAudio(id: String, millis: Long)

    /** All lyden har kommet. [format] er f.eks. `pcm 24 kHz` eller `mp3`. */
    fun onReceived(id: String, millis: Long, audioSeconds: Double, bytes: Int, format: String)

    fun onPlaying(id: String, playing: Boolean)

    fun onError(id: String, message: String)
}

/** Hvordan det går med å hente en lyd. Kalles alltid på hovedtråden. */
interface EkkoImportListener {
    /** Hvor langt nedlastingen har kommet, fra 0 til 1. */
    fun onProgress(fraction: Float)

    /** Lyden er lagret. [resultJson] er en `ImportedAudio` som JSON. */
    fun onImported(resultJson: String)

    /** Noe gikk galt. Tom [message] betyr at brukeren avbrøt. */
    fun onFailed(message: String)
}

/** Det som skjer med avspillingen i Ekko. Kalles alltid på hovedtråden. */
interface EkkoPlayerListener {
    fun onPlaying(playing: Boolean)

    /** Hvor langt man har kommet, i sekunder fra starten av lydfilen. */
    fun onPosition(seconds: Double)

    /** Lyden er spilt ferdig. */
    fun onFinished()

    /** Neste og forrige fra låseskjermen, kontrollsenteret eller AirPods. */
    fun onNext()

    fun onPrevious()

    fun onError(message: String)
}

/** Det Swift forteller om en samtale med Gemini Live. Kalles alltid på hovedtråden. */
interface LiveListener {
    /** `connecting`, `listening`, `thinking`, `speaking`, `reconnecting` eller `ended`. */
    fun onStatus(status: String)

    /** Lydnivået til den som snakker, fra 0 til 1. */
    fun onLevel(level: Float)

    /** En bit av det brukeren sier. */
    fun onUserText(text: String)

    /** En bit av det Kompis sier. */
    fun onKompisText(text: String)

    /** Kompis er ferdig med å snakke. */
    fun onTurnComplete()

    /** Brukeren avbrøt Kompis. */
    fun onInterrupted()

    /** Hvor lyden går, f.eks. «AirPods Pro». */
    fun onAudioRoute(name: String)

    /** Noe gikk galt, og samtalen er stoppet. */
    fun onError(message: String)

    /** En kort oppsummering av hva som skjer i tilkoblingen, for å finne feil. */
    fun onDebug(text: String)
}
