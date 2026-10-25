package no.jodal.salat

/** En bro som later som den er iOS, for testene. Det finnes bare én, siden butikkene i appene er singletons. */
internal object FakeBridge : NativeBridge {
    private val storage = mutableMapOf<String, String>()
    var startedWith: String? = null
    var rolledWith: String? = null
    var sttSentWith: String? = null
    val openRouterGets = mutableListOf<String>()

    /** Svaret [openRouterGet] gir, eller `null` for å aldri svare. */
    var openRouterResult: Pair<String?, String?>? = null

    /** Det Ekko har bedt Swift om, f.eks. `play clip1 from 2.0` eller `pause`. */
    val ekkoCalls = mutableListOf<String>()

    override fun signingExpiresAtMillis(): Long? = null
    override fun scheduleExpiryReminder() = Unit
    override fun sendTestNotification(delaySeconds: Int) = Unit
    override fun scheduleTestAlarm(delaySeconds: Int, snoozeMinutes: Int, onResult: (String?) -> Unit) = Unit
    override fun loadText(key: String): String? = storage[key]
    override fun saveText(key: String, value: String) {
        storage[key] = value
    }
    override fun loadSecret(key: String): String? = storage["secret.$key"]
    override fun saveSecret(key: String, value: String) {
        storage["secret.$key"] = value
    }
    override fun applyRiseSchedule(planJson: String, onResult: (String?) -> Unit) = Unit
    override fun previewSound(sound: String) = Unit
    override fun stopSoundPreview() = Unit
    override fun startLiveSession(configJson: String, listener: LiveListener) {
        startedWith = configJson
    }
    override fun stopLiveSession() = Unit
    override fun setLiveMuted(muted: Boolean) = Unit
    override fun rollLiveSession(historyJson: String) {
        rolledWith = historyJson
    }
    override fun kompisSttOpen(listener: SttListener) = Unit
    override fun kompisSttStartRecording() = Unit
    override fun kompisSttStopRecording(configJson: String) {
        sttSentWith = configJson
    }
    override fun kompisSttClose() = Unit
    override fun openRouterGet(path: String, apiKey: String, onResult: (body: String?, error: String?) -> Unit) {
        openRouterGets += path
        openRouterResult?.let { (body, error) -> onResult(body, error) }
    }

    override fun ekkoImportYouTube(clipId: String, videoId: String, listener: EkkoImportListener) {
        ekkoCalls += "youtube $videoId"
    }
    override fun ekkoImportWeb(clipId: String, url: String, listener: EkkoImportListener) {
        ekkoCalls += "web $url"
    }
    override fun ekkoImportFile(clipId: String, fromPhotos: Boolean, listener: EkkoImportListener) {
        ekkoCalls += if (fromPhotos) "photos" else "files"
    }
    override fun ekkoCancelImport() {
        ekkoCalls += "cancel"
    }
    override fun ekkoDeleteAudio(clipId: String) {
        ekkoCalls += "delete $clipId"
    }
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
    ) {
        ekkoCalls += "play $clipId from $fromSeconds"
    }
    override fun ekkoPause() {
        ekkoCalls += "pause"
    }
    override fun ekkoResume() {
        ekkoCalls += "resume"
    }
    override fun ekkoSeek(seconds: Double) {
        ekkoCalls += "seek $seconds"
    }
    override fun ekkoStop() {
        ekkoCalls += "stop"
    }

    /** Det widgetene sist fikk, per navn. */
    val widgetData = mutableMapOf<String, String>()

    override fun saveWidgetData(name: String, json: String) {
        widgetData[name] = json
    }

    override fun haptic(kind: String) = Unit

    val openedUrls = mutableListOf<String>()

    override fun openUrl(url: String) {
        openedUrls += url
    }

    var bremsNotices: String? = null

    override fun bremsNotify(noticesJson: String) {
        bremsNotices = noticesJson
    }
}
