package no.jodal.salat.apps.ekko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import no.jodal.salat.EkkoImportListener
import no.jodal.salat.NativeBridge

/** Henter lyden til en ny lyd, fra en lenke eller en fil, før den klippes og lagres. */
class EkkoImporter private constructor(private val store: EkkoStore, private val bridge: NativeBridge) : EkkoImportListener {
    sealed interface State {
        data object Idle : State

        /** Henter lyd fra [source]. [progress] er `null` når man ikke vet hvor langt det har kommet. */
        data class Fetching(val source: String, val progress: Float?) : State

        data class Failed(val message: String) : State

        /** Lyden er hentet og kan klippes. */
        data class Ready(val draft: Draft) : State
    }

    /** En lyd som er hentet, men ikke lagret ennå. */
    data class Draft(val clipId: String, val sourceUrl: String?, val audio: ImportedAudio)

    var state by mutableStateOf<State>(State.Idle)
        private set

    private var clipId: String? = null
    private var sourceUrl: String? = null

    /** Henter lyd fra lenken i [text]. */
    fun fromText(text: String) {
        val link = EkkoLink.parse(text)
        if (link == null) {
            state = State.Failed("Fant ingen lenke. Lim inn en lenke fra YouTube, X eller Snapchat.")
            return
        }
        val source = when {
            link is EkkoLink.YouTube -> "YouTube"
            link is EkkoLink.X -> "X"
            link is EkkoLink.Web && link.isSnapchat -> "Snapchat"
            else -> "nettsiden"
        }
        val id = begin(link.url, source)
        when (link) {
            is EkkoLink.YouTube -> bridge.ekkoImportYouTube(id, link.videoId, this)
            is EkkoLink.X -> bridge.ekkoImportX(id, link.apiUrl, this)
            is EkkoLink.Web -> bridge.ekkoImportWeb(id, link.url, this)
        }
    }

    fun fromPhotos() = bridge.ekkoImportFile(begin(null, "Bilder"), fromPhotos = true, this)

    fun fromFiles() = bridge.ekkoImportFile(begin(null, "Filer"), fromPhotos = false, this)

    /** Avbryter, og sletter lyden som er hentet men ikke lagret. */
    fun cancel() {
        when (val s = state) {
            is State.Fetching -> bridge.ekkoCancelImport()
            is State.Ready -> bridge.ekkoDeleteAudio(s.draft.clipId)
            else -> Unit
        }
        clipId = null
        state = State.Idle
    }

    /** Lagrer den hentede lyden som en ny lyd. */
    fun save(draft: Draft, title: String, artist: String, startSeconds: Double, endSeconds: Double): Clip {
        val clip = Clip(
            id = draft.clipId,
            title = title.trim().ifEmpty { "Uten navn" },
            artist = artist.trim(),
            sourceUrl = draft.sourceUrl,
            sourceSeconds = draft.audio.durationSeconds,
            startSeconds = startSeconds,
            endSeconds = endSeconds,
            waveform = draft.audio.waveform,
            addedAtMillis = store.nowMillis(),
        )
        store.saveClip(clip)
        clipId = null
        state = State.Idle
        return clip
    }

    private fun begin(url: String?, source: String): String {
        val id = newId()
        clipId = id
        sourceUrl = url
        state = State.Fetching(source, progress = null)
        return id
    }

    // EkkoImportListener, kalles fra Swift.

    override fun onProgress(fraction: Float) {
        val s = state as? State.Fetching ?: return
        state = s.copy(progress = fraction.coerceIn(0f, 1f))
    }

    override fun onImported(resultJson: String) {
        val id = clipId ?: return
        val audio = runCatching { json.decodeFromString(ImportedAudio.serializer(), resultJson) }.getOrNull()
        state = if (audio == null || audio.durationSeconds <= 0) {
            bridge.ekkoDeleteAudio(id)
            State.Failed("Fant ingen lyd i videoen.")
        } else {
            State.Ready(Draft(id, sourceUrl, audio))
        }
    }

    override fun onFailed(message: String) {
        clipId?.let(bridge::ekkoDeleteAudio)
        clipId = null
        state = if (message.isEmpty()) State.Idle else State.Failed(message)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private var instance: EkkoImporter? = null

        fun get(store: EkkoStore, bridge: NativeBridge): EkkoImporter =
            instance ?: EkkoImporter(store, bridge).also { instance = it }
    }
}
