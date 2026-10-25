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

    /** Hvor lang en setning uten punktum kan bli før den deles ved et komma, så stemmen kan begynne. */
    const val MaxClause = 140

    /**
     * Er [rest] (det som er igjen etter de hele setningene) blitt lang, deles den ved siste komma,
     * semikolon eller kolon, så stemmen slipper å vente på punktum. Gir delen å lese opp og det som er igjen.
     */
    fun cutLongClause(rest: String): Pair<String, String>? {
        if (rest.length < MaxClause) return null
        val cut = listOf(", ", "; ", ": ").maxOf { rest.lastIndexOf(it) }
        if (cut < 40) return null
        return rest.substring(0, cut + 1).trim() to rest.substring(cut + 2)
    }

    /**
     * [text] slik den skal leses opp: uten markdown, lister og emojier, som stemmen ellers ville lest
     * høyt eller snublet i. Tom hvis det ikke er noe å lese.
     */
    fun speakable(text: String): String {
        val kept = text.filter { c ->
            c != '*' && c != '#' && c != '`' && c != '~' && c != '\uFE0F' && c != '\u200D' &&
                !c.isSurrogate() && c !in '\u2600'..'\u27BF'
        }
        return kept.lines()
            .map { it.trim().removePrefix("- ").removePrefix("• ").removePrefix("> ") }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
