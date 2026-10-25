package no.jodal.salat

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Forhåndsvisning av UI-et på Linux: `./gradlew :shared:run`. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Salat (forhåndsvisning)",
        state = rememberWindowState(width = 390.dp, height = 844.dp),
    ) {
        SalatApp(PreviewBridge)
    }
}

/** Later som appen ble signert i går, og skriver ut i stedet for å sende varsler. */
private object PreviewBridge : NativeBridge {
    private val expiresAt = (Clock.System.now() + 6.days).toEpochMilliseconds()

    override fun signingExpiresAtMillis(): Long = expiresAt

    override fun scheduleExpiryReminder() = println("Forhåndsvisning: påminnelse planlagt")

    override fun sendTestNotification(delaySeconds: Int) =
        println("Forhåndsvisning: testvarsel om $delaySeconds sekunder")

    override fun scheduleTestAlarm(delaySeconds: Int, snoozeMinutes: Int, onResult: (String?) -> Unit) {
        println("Forhåndsvisning: testalarm om $delaySeconds sekunder, slumring $snoozeMinutes min")
        onResult(null)
    }

    private val storage = mutableMapOf<String, String>()

    override fun loadText(key: String): String? = storage[key]

    override fun saveText(key: String, value: String) {
        storage[key] = value
    }

    override fun loadSecret(key: String): String? = storage["secret.$key"]

    override fun saveSecret(key: String, value: String) {
        storage["secret.$key"] = value
    }

    override fun applyRiseSchedule(planJson: String, onResult: (String?) -> Unit) {
        println("Forhåndsvisning: Rise-plan på ${planJson.length} tegn")
        onResult(null)
    }

    override fun previewSound(sound: String) = println("Forhåndsvisning: spiller $sound")

    override fun stopSoundPreview() = Unit

    override fun startLiveSession(configJson: String, listener: LiveListener) =
        listener.onError("Samtaler med Gemini virker bare på iPhone.")

    override fun stopLiveSession() = Unit

    override fun setLiveMuted(muted: Boolean) = Unit

    override fun rollLiveSession(historyJson: String) = Unit

    // Kompis: later som opptaket ble til tekst etter et lite øyeblikk.

    private var sttListener: SttListener? = null

    override fun kompisSttOpen(listener: SttListener) {
        sttListener = listener
        listener.onMicrophone("Mikrofon på PC-en")
    }

    override fun kompisSttStartRecording() = sttListener?.onLevel(0.5f) ?: Unit

    override fun kompisSttStopRecording(configJson: String) {
        sttListener?.onLevel(0f)
        sttListener?.onTranscribed(
            """{"text":"Hei, dette er en test av tale til tekst.","usage":{"cost":0.00014,"seconds":2.4}}""",
            2.4,
            76_844,
            640,
        )
    }

    override fun kompisSttClose() {
        sttListener = null
    }

    override fun kompisConversationOpen(listener: SttListener) = kompisSttOpen(listener)

    override fun kompisConversationClose() = kompisSttClose()

    // Later som setningene spilles av med en gang.
    private var speechListener: SpeechListener? = null

    override fun speechStart(sampleRate: Int, listener: SpeechListener) {
        speechListener = listener
    }

    override fun speechEnqueue(id: String, apiKey: String, bodyJson: String) {
        speechListener?.onFirstAudio(id, 200)
        speechListener?.onItemStarted(id)
        speechListener?.onItemFinished(id)
    }

    override fun speechFinish() {
        speechListener?.onFinished()
    }

    override fun speechStop() = Unit

    override fun openRouterWarmUp() = Unit

    // Later som svaret strømmer inn ord for ord.
    override fun openRouterChat(apiKey: String, bodyJson: String, listener: ChatListener) {
        val words = "Hei! Dette er et svar fra forhåndsvisningen. Det kommer ord for ord.".split(" ")
        words.forEachIndexed { i, word ->
            val text = if (i == 0) word else " $word"
            listener.onEvent("""{"provider":"Forhåndsvisning","choices":[{"delta":{"content":"$text"}}]}""", 240L + i * 20)
        }
        listener.onEvent("""{"choices":[{"delta":{"content":""}}],"usage":{"prompt_tokens":1200,"completion_tokens":20,"cost":0.00002}}""", 500)
        listener.onDone(500)
    }

    override fun openRouterCancelChat() = Unit

    // Later som lyden kom med en gang, og at den er spilt av.
    override fun ttsSpeak(id: String, apiKey: String, bodyJson: String, sampleRate: Int, callAudio: Boolean, listener: TtsListener) {
        listener.onFirstAudio(id, 310)
        listener.onReceived(id, 900, 3.2, 153_600, "pcm 24 kHz")
    }

    override fun ttsReplay(id: String, callAudio: Boolean, listener: TtsListener) = Unit

    override fun ttsStop() = Unit

    override fun ttsClose() = Unit

    override fun openRouterGet(path: String, apiKey: String, onResult: (body: String?, error: String?) -> Unit) = onResult(
        """{"data":[
            {"id":"openai/whisper-1","name":"OpenAI: Whisper","created":1700000000},
            {"id":"mistralai/voxtral-mini-transcribe-3-0","name":"Mistral: Voxtral Mini Transcribe 3","created":1792540800},
            {"id":"elevenlabs/scribe-v2","name":"ElevenLabs: Scribe v2","created":1791331200}
        ]}""",
        null,
    )

    // Ekko: later som lyden er hentet med en gang, med en tilfeldig lydbølge.

    override fun ekkoImportYouTube(clipId: String, videoId: String, listener: EkkoImportListener) =
        fakeImport("YouTube Shorts $videoId", listener)

    override fun ekkoImportWeb(clipId: String, url: String, listener: EkkoImportListener) = fakeImport("Spotlight", listener)

    override fun ekkoImportFile(clipId: String, fromPhotos: Boolean, listener: EkkoImportListener) =
        fakeImport(if (fromPhotos) "Video" else "Lydfil", listener)

    private fun fakeImport(title: String, listener: EkkoImportListener) {
        val waveform = List(120) { i -> (0.25 + 0.6 * kotlin.math.abs(kotlin.math.sin(i * 0.37)) * kotlin.random.Random.nextDouble(0.5, 1.0)).toFloat() }
        val wave = waveform.joinToString(",")
        listener.onImported("""{"title":"$title","durationSeconds":24.0,"waveform":[$wave]}""")
    }

    override fun ekkoCancelImport() = Unit

    override fun ekkoDeleteAudio(clipId: String) = Unit

    override fun ekkoPasteboardText(): String? = null

    override fun ekkoPlay(
        clipId: String,
        startSeconds: Double,
        endSeconds: Double,
        fromSeconds: Double,
        title: String,
        artist: String,
        lockScreen: Boolean,
        listener: EkkoPlayerListener,
    ) = println("Forhåndsvisning: spiller $title fra $fromSeconds til $endSeconds")

    override fun ekkoPause() = Unit

    override fun ekkoResume() = Unit

    override fun ekkoSeek(seconds: Double) = Unit

    override fun ekkoStop() = Unit

    override fun saveWidgetData(name: String, json: String) = Unit

    override fun haptic(kind: String) = Unit

    override fun openUrl(url: String) = println("Forhåndsvisning: åpner $url")

    override fun bremsNotify(noticesJson: String) = println("Forhåndsvisning: Brems-varsler $noticesJson")
}
