package no.jodal.salat.apps.kompis

/**
 * Deler tekst som strømmer inn i hele setninger, så hver setning kan leses opp så snart den er
 * ferdig. En setning er ferdig når den slutter på punktum, spørsmålstegn, utropstegn eller
 * ellipse, og noe kommer etter, så «2.5» og en setning som ikke er skrevet ferdig ikke deles.
 */
object Sentences {
    private val end = Regex("""[.!?…]+["»”')]*\s+""")

    /** De hele setningene i [text], og det som er igjen etter dem. */
    fun split(text: String): Pair<List<String>, String> {
        val sentences = mutableListOf<String>()
        var start = 0
        for (match in end.findAll(text)) {
            val sentence = text.substring(start, match.range.last + 1).trim()
            if (sentence.isNotEmpty()) sentences += sentence
            start = match.range.last + 1
        }
        return sentences to text.substring(start)
    }

    /** Om [text] har minst én hel setning. */
    fun hasComplete(text: String): Boolean = end.containsMatchIn(text)
}
