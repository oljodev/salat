package no.jodal.salat.apps.ekko

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Alt Ekko lagrer. Selve lyden ligger i filer på telefonen, én per lyd, med [Clip.id] som navn. */
@Serializable
data class EkkoData(
    val clips: List<Clip> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.Off,
    /** Lydene på lydbrettet i widgeten, i rekkefølge. */
    val soundboard: List<String> = emptyList(),
    /** Volumet i Ekko fra 0 til 1, oppå volumet på telefonen. */
    val volume: Float = 1f,
)

/** En lyd: et utsnitt av lyden fra en video. */
@Serializable
data class Clip(
    val id: String,
    val title: String,
    /** Hvem som sier det, eller tom tekst. */
    val artist: String = "",
    /** Lenken lyden ble hentet fra, eller `null` hvis den kom fra en fil. */
    val sourceUrl: String? = null,
    /** Hvor lang hele lyden fra videoen er. */
    val sourceSeconds: Double,
    val startSeconds: Double = 0.0,
    val endSeconds: Double,
    /** Hvor høy lyden er gjennom hele videoen, fra 0 til 1, for å tegne lydbølgen. */
    val waveform: List<Float> = emptyList(),
    val addedAtMillis: Long,
) {
    val lengthSeconds: Double get() = endSeconds - startSeconds

    /** Lydbølgen for bare utsnittet. */
    fun trimmedWaveform(): List<Float> {
        if (waveform.isEmpty() || sourceSeconds <= 0) return waveform
        val from = (startSeconds / sourceSeconds * waveform.size).toInt().coerceIn(0, waveform.size - 1)
        val to = (endSeconds / sourceSeconds * waveform.size).toInt().coerceIn(from + 1, waveform.size)
        return waveform.subList(from, to)
    }
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val clipIds: List<String> = emptyList(),
    val createdAtMillis: Long,
)

@Serializable
enum class Repeat {
    @SerialName("off") Off,
    /** Starter på nytt når spillelisten er ferdig. */
    @SerialName("all") All,
    /** Spiller samme lyd om og om igjen. */
    @SerialName("one") One,
    ;

    fun next(): Repeat = entries[(ordinal + 1) % entries.size]
}

/** Det Swift sender tilbake når lyden fra en video er hentet. */
@Serializable
data class ImportedAudio(
    val title: String = "",
    val durationSeconds: Double,
    val waveform: List<Float> = emptyList(),
)

internal fun newId(): String = (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
