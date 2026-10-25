package no.jodal.salat.apps.kompis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Alt Kompis lagrer, bortsett fra API-nøkkelen, som ligger i nøkkelringen. */
@Serializable
data class KompisData(
    val conversations: List<Conversation> = emptyList(),
    val settings: KompisSettings = KompisSettings(),
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val messages: List<Message> = emptyList(),
    /** Hvor lenge man har snakket til sammen, i sekunder. */
    val durationSeconds: Int = 0,
)

@Serializable
data class Message(
    val role: Role,
    val text: String,
    val atMillis: Long,
)

@Serializable
enum class Role {
    @SerialName("user") User,
    @SerialName("kompis") Kompis,
}

@Serializable
data class KompisSettings(
    val voice: String = KompisVoice.Kore.id,
    val personality: String = DefaultPersonality,
    /** Modellen og språket i testen av tale til tekst. Tomt språk betyr at modellen finner det selv. */
    val sttModel: String = DefaultSttModel,
    val sttLanguage: String = "no",
    /** Språkmodellen og leverandørene i rekkefølge. Ingen leverandører lar OpenRouter velge. */
    val llmModel: String = DefaultLlmModel,
    val llmProviders: List<String> = DefaultLlmProviders,
)

const val DefaultPersonality =
    "Du er Kompis, en vennlig og nysgjerrig samtalepartner med en lun humor. Du er ærlig, også når du er uenig."

/** Personligheten før den ble kortere. Har man ikke endret den, brukes den nye i stedet. */
internal const val OldDefaultPersonality =
    "Du er Kompis, en vennlig og nysgjerrig samtalepartner. Du snakker norsk, svarer kort og naturlig " +
        "som i en muntlig samtale, og stiller gjerne et oppfølgingsspørsmål."

/** Stemmene i Gemini Live. [id] er navnet Gemini bruker. */
enum class KompisVoice(val id: String, val description: String) {
    Kore("Kore", "Bestemt og tydelig"),
    Puck("Puck", "Opplagt og glad"),
    Charon("Charon", "Rolig og informativ"),
    Aoede("Aoede", "Luftig og avslappet"),
    Fenrir("Fenrir", "Energisk"),
    Zephyr("Zephyr", "Lys og vennlig"),
    ;

    companion object {
        fun byId(id: String): KompisVoice = entries.firstOrNull { it.id == id } ?: Kore
    }
}
