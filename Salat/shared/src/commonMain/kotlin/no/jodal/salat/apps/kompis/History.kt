package no.jodal.salat.apps.kompis

/** Alle søkeord må finnes i tittelen eller meldingene. Søket er lokalt på telefonen. */
class HistorySearch(query: String) {
    private val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(conversation: Conversation): Boolean = words.all { word ->
        conversation.title.contains(word, ignoreCase = true) ||
            conversation.messages.any { it.text.contains(word, ignoreCase = true) || it.card?.title?.contains(word, ignoreCase = true) == true }
    }

    /** Vis en melding med treff, også når det man leter etter er langt tilbake i samtalen. */
    fun preview(conversation: Conversation): String? {
        val match = conversation.messages.firstOrNull { message -> words.any { message.text.contains(it, ignoreCase = true) } }
            ?: return conversation.messages.lastOrNull { it.text.isNotBlank() }?.text
        val index = words.map { match.text.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.min()
        val start = (index - 40).coerceAtLeast(0)
        return (if (start > 0) "…" else "") + match.text.substring(start)
    }
}
