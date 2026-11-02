package no.jodal.salat.apps.ekko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge

/** Holder på lydene og spillelistene i Ekko, og lagrer dem på telefonen. */
class EkkoStore private constructor(private val bridge: NativeBridge) {
    var data by mutableStateOf(load())
        private set

    /** Alle lydene, nyeste først. */
    val library: List<Clip> get() = data.clips.sortedByDescending { it.addedAtMillis }

    fun update(transform: (EkkoData) -> EkkoData) {
        data = transform(data)
        bridge.saveText(StorageKey, json.encodeToString(EkkoData.serializer(), data))
        publishWidget()
    }

    /** Gir widgetene lydene og lydbrettet. Sendes bare når noe widgetene viser, er endret. */
    fun publishWidget() {
        val widget = widgetJson(this)
        if (widget == publishedWidget) return
        publishedWidget = widget
        bridge.saveWidgetData("ekko", widget)
    }

    private var publishedWidget: String? = null

    fun clip(id: String): Clip? = data.clips.firstOrNull { it.id == id }

    fun playlist(id: String): Playlist? = data.playlists.firstOrNull { it.id == id }

    fun clipsIn(playlist: Playlist): List<Clip> = playlist.clipIds.mapNotNull(::clip)

    /** Legger til [clip], eller erstatter den med samme id. */
    fun saveClip(clip: Clip) = update { data ->
        val exists = data.clips.any { it.id == clip.id }
        data.copy(clips = if (exists) data.clips.map { if (it.id == clip.id) clip else it } else data.clips + clip)
    }

    /** Sletter lyden, også fra spillelistene, og lydfilen. */
    fun deleteClip(id: String) {
        update { data ->
            data.copy(
                clips = data.clips.filter { it.id != id },
                playlists = data.playlists.map { it.copy(clipIds = it.clipIds - id) },
                soundboard = data.soundboard - id,
            )
        }
        bridge.ekkoDeleteAudio(id)
    }

    fun createPlaylist(name: String, clipIds: List<String> = emptyList()): Playlist {
        val playlist = Playlist(newId(), name.trim(), clipIds.distinct(), nowMillis())
        update { it.copy(playlists = it.playlists + playlist) }
        return playlist
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylist(id) { it.copy(name = name.trim()) }

    fun deletePlaylist(id: String) = update { data -> data.copy(playlists = data.playlists.filter { it.id != id }) }

    /** Legger [clipIds] til sist i spillelisten. Lyder som alt er der, legges ikke til på nytt. */
    fun addToPlaylist(id: String, clipIds: List<String>) = updatePlaylist(id) { playlist ->
        playlist.copy(clipIds = playlist.clipIds + clipIds.filter { it !in playlist.clipIds }.distinct())
    }

    fun removeFromPlaylist(id: String, clipId: String) = updatePlaylist(id) { it.copy(clipIds = it.clipIds - clipId) }

    fun isOnSoundboard(id: String): Boolean = id in data.soundboard

    /** Legger lyden på lydbrettet, eller tar den av. Nye lyder legges sist, og det er plass til [SoundboardSize]. */
    fun toggleSoundboard(id: String) = update { data ->
        data.copy(soundboard = if (id in data.soundboard) data.soundboard - id else (data.soundboard + id).takeLast(SoundboardSize))
    }

    fun setShuffle(shuffle: Boolean) = update { it.copy(shuffle = shuffle) }

    fun setVolume(volume: Float) = update { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setRepeat(repeat: Repeat) = update { it.copy(repeat = repeat) }

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun updatePlaylist(id: String, transform: (Playlist) -> Playlist) = update { data ->
        data.copy(playlists = data.playlists.map { if (it.id == id) transform(it) else it })
    }

    private fun load(): EkkoData = bridge.loadText(StorageKey)
        ?.let { runCatching { json.decodeFromString(EkkoData.serializer(), it) }.getOrNull() }
        ?: EkkoData()

    companion object {
        private const val StorageKey = "ekko.data"

        /** Så mange lyder får plass i den største widgeten. */
        const val SoundboardSize = 12
        private val json = Json { ignoreUnknownKeys = true }
        private var instance: EkkoStore? = null

        fun get(bridge: NativeBridge): EkkoStore = instance ?: EkkoStore(bridge).also { instance = it }
    }
}
