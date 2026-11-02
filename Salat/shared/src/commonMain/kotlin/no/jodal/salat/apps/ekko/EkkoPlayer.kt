package no.jodal.salat.apps.ekko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import no.jodal.salat.EkkoPlayerListener
import no.jodal.salat.NativeBridge

/**
 * Avspillingen i Ekko. Køen styres her, mens Swift spiller av én lyd om gangen. Den fortsetter når
 * man går ut av Ekko og når skjermen er låst.
 */
class EkkoPlayer private constructor(
    private val store: EkkoStore,
    private val bridge: NativeBridge,
    private val random: Random,
) : EkkoPlayerListener {
    var queue by mutableStateOf<PlayQueue?>(null)
        private set

    /** Hvor køen kommer fra: alle lydene eller en spilleliste. */
    var source by mutableStateOf<QueueSource?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    /** Hvor langt man har kommet i lyden, i sekunder fra starten av utsnittet. */
    var position by mutableStateOf(0.0)
        private set

    /** Utsnittet man hører på mens man klipper, eller `null`. */
    var preview by mutableStateOf<Preview?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Hvor køen kommer fra. [id] er id-en til spillelisten, eller [AllId] for alle lydene. */
    data class QueueSource(val id: String, val name: String) {
        companion object {
            const val AllId = "all"
            val All = QueueSource(AllId, "Alle lyder")
        }
    }

    data class Preview(val clipId: String, val startSeconds: Double, val endSeconds: Double, val positionSeconds: Double)

    /** Lyden i køen som Swift har lastet, så man vet om den kan fortsette der den slapp. */
    private var loadedClipId: String? = null

    val current: Clip? get() = queue?.current?.let(store::clip)

    /** Spiller [clips] fra [source], fra [startAt] eller fra starten. */
    fun play(clips: List<Clip>, source: QueueSource, startAt: Clip? = null) {
        queue = PlayQueue.start(clips.map { it.id }, startAt?.id, store.data.shuffle, random) ?: return
        this.source = source
        playCurrent()
    }

    /** Spiller eller pauser [source], eller starter den hvis det er noe annet som spilles. */
    fun playOrPause(clips: List<Clip>, source: QueueSource) =
        if (this.source == source && queue != null) toggle() else play(clips, source)

    fun toggle() = if (isPlaying) pause() else resume()

    fun pause() {
        if (!isPlaying) return
        bridge.ekkoPause()
        isPlaying = false
    }

    fun resume() {
        val clip = current ?: return
        stopPreview()
        if (loadedClipId == clip.id) {
            bridge.ekkoResume()
            isPlaying = true
        } else {
            load(clip, from = clip.startSeconds + position)
        }
    }

    /** Neste lyd. På slutten av køen begynner den på nytt. */
    fun next() {
        val q = queue ?: return
        queue = q.next(Repeat.All, store.data.shuffle, random)
        playCurrent()
    }

    /** Til starten av lyden, eller til forrige lyd hvis man er helt i starten. */
    fun previous() {
        val q = queue ?: return
        if (position > RestartThreshold || q.index == 0) {
            seekTo(0.0)
            if (!isPlaying) resume()
        } else {
            queue = q.previous()
            playCurrent()
        }
    }

    /** Hopper til [seconds] fra starten av utsnittet. */
    fun seekTo(seconds: Double) {
        val clip = current ?: return
        position = seconds.coerceIn(0.0, clip.lengthSeconds)
        if (loadedClipId == clip.id) bridge.ekkoSeek(clip.startSeconds + position)
    }

    /** Setter volumet i Ekko, oppå volumet på telefonen. */
    fun setVolume(volume: Float) {
        store.setVolume(volume)
        bridge.ekkoSetVolume(store.data.volume)
    }

    fun toggleShuffle() {
        val shuffle = !store.data.shuffle
        store.setShuffle(shuffle)
        queue = queue?.withShuffle(shuffle, random)
    }

    fun cycleRepeat() = store.setRepeat(store.data.repeat.next())

    /** Spiller [clip] fra [source]. Trykker man på lyden som alt spilles, settes den på pause. */
    fun playClip(clips: List<Clip>, clip: Clip, source: QueueSource) {
        if (current?.id == clip.id && this.source == source) toggle() else play(clips, source, startAt = clip)
    }

    /** Om lydene fra [source] er de som spilles nå. */
    fun isFrom(source: QueueSource): Boolean = this.source == source && queue != null

    /** Fjerner en slettet lyd fra køen. */
    fun forget(clipId: String) {
        val q = queue ?: return
        val wasCurrent = q.current == clipId
        queue = q.without(clipId)
        if (queue == null) {
            stop()
        } else if (wasCurrent) {
            loadedClipId = null
            position = 0.0
            if (isPlaying) playCurrent() else bridge.ekkoStop()
        }
    }

    /** Stopper og tømmer køen. */
    fun stop() {
        bridge.ekkoStop()
        queue = null
        source = null
        isPlaying = false
        position = 0.0
        loadedClipId = null
    }

    /** Spiller av et utsnitt mens man klipper. Det som spilles i køen, settes på pause. */
    fun startPreview(clipId: String, startSeconds: Double, endSeconds: Double) {
        isPlaying = false
        loadedClipId = null
        preview = Preview(clipId, startSeconds, endSeconds, startSeconds)
        bridge.ekkoSetVolume(store.data.volume)
        bridge.ekkoPlay(clipId, startSeconds, endSeconds, startSeconds, "", "", false, this)
    }

    fun stopPreview() {
        if (preview == null) return
        bridge.ekkoStop()
        preview = null
    }

    fun dismissError() {
        error = null
    }

    private fun playCurrent() {
        val clip = current ?: return stop()
        preview = null
        position = 0.0
        load(clip, from = clip.startSeconds)
    }

    private fun load(clip: Clip, from: Double) {
        error = null
        loadedClipId = clip.id
        isPlaying = true
        bridge.ekkoSetVolume(store.data.volume)
        bridge.ekkoPlay(clip.id, clip.startSeconds, clip.endSeconds, from, clip.title, clip.artist, true, this)
    }

    // EkkoPlayerListener, kalles fra Swift.

    override fun onPlaying(playing: Boolean) {
        if (preview != null) {
            if (!playing) preview = null
        } else {
            isPlaying = playing
        }
    }

    override fun onPosition(seconds: Double) {
        val p = preview
        if (p != null) {
            preview = p.copy(positionSeconds = seconds)
        } else {
            current?.let { position = (seconds - it.startSeconds).coerceAtLeast(0.0) }
        }
    }

    override fun onFinished() {
        if (preview != null) {
            preview = null
            return
        }
        val q = queue ?: return
        if (store.data.repeat == Repeat.One) return playCurrent()
        val next = q.next(store.data.repeat, store.data.shuffle, random)
        if (next != null) {
            queue = next
            playCurrent()
        } else {
            // Ferdig: tilbake til starten av køen, på pause.
            queue = q.copy(index = 0)
            isPlaying = false
            position = 0.0
            loadedClipId = null
        }
    }

    override fun onNext() {
        if (preview == null) next()
    }

    override fun onPrevious() {
        if (preview == null) previous()
    }

    override fun onError(message: String) {
        isPlaying = false
        preview = null
        loadedClipId = null
        error = message
    }

    companion object {
        /** Etter så mange sekunder går «forrige» til starten av lyden i stedet for til forrige lyd. */
        private const val RestartThreshold = 2.0

        private var instance: EkkoPlayer? = null

        fun get(store: EkkoStore, bridge: NativeBridge, random: Random = Random): EkkoPlayer =
            instance ?: EkkoPlayer(store, bridge, random).also { instance = it }
    }
}
