package no.jodal.salat.apps.kompis

/** Et tekstfelt i et JSON-objekt som kanskje ikke er skrevet ferdig. */
internal class PartialField(val value: String, val complete: Boolean)

/**
 * Leser tekstfeltene i et JSON-objekt mens det skrives, f.eks. argumentene til et verktøykall som
 * strømmer inn. Det som er skrevet så langt av et felt, gis med en gang. Felt som ikke er tekst hoppes over.
 */
internal object PartialJson {
    fun strings(json: String): Map<String, PartialField> {
        val fields = linkedMapOf<String, PartialField>()
        var i = json.indexOf('{').takeIf { it >= 0 }?.plus(1) ?: return fields
        while (i < json.length) {
            i = skipSeparators(json, i)
            if (i >= json.length || json[i] == '}') break
            if (json[i] != '"') break
            val key = readString(json, i + 1) ?: break
            if (!key.complete) break
            i = skipWhitespace(json, key.end)
            if (i >= json.length || json[i] != ':') break
            i = skipWhitespace(json, i + 1)
            if (i >= json.length) break
            if (json[i] == '"') {
                val value = readString(json, i + 1) ?: break
                fields[key.value] = PartialField(value.value, value.complete)
                if (!value.complete) break
                i = value.end
            } else {
                i = skipValue(json, i)
            }
        }
        return fields
    }

    private class Read(val value: String, val complete: Boolean, val end: Int)

    /** Leser en tekst som begynner etter anførselstegnet i [start]. Stopper før en escape som ikke er ferdig. */
    private fun readString(json: String, start: Int): Read? {
        val out = StringBuilder()
        var i = start
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> return Read(out.toString(), true, i + 1)
                c != '\\' -> {
                    out.append(c)
                    i++
                }
                i + 1 >= json.length -> return Read(out.toString(), false, i)
                else -> {
                    when (val escaped = json[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            if (i + 6 > json.length) return Read(out.toString(), false, i)
                            val code = json.substring(i + 2, i + 6).toIntOrNull(16) ?: return Read(out.toString(), false, i)
                            out.append(code.toChar())
                            i += 4
                        }
                        else -> out.append(escaped)
                    }
                    i += 2
                }
            }
        }
        return Read(out.toString(), false, i)
    }

    private fun skipWhitespace(json: String, from: Int): Int {
        var i = from
        while (i < json.length && json[i].isWhitespace()) i++
        return i
    }

    private fun skipSeparators(json: String, from: Int): Int {
        var i = from
        while (i < json.length && (json[i].isWhitespace() || json[i] == ',')) i++
        return i
    }

    /** Hopper over en verdi som ikke er tekst, til neste komma på samme nivå. */
    private fun skipValue(json: String, from: Int): Int {
        var depth = 0
        var i = from
        var inString = false
        while (i < json.length) {
            val c = json[i]
            when {
                inString -> if (c == '\\') i++ else if (c == '"') inString = false
                c == '"' -> inString = true
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> if (depth == 0) return i else depth--
                c == ',' && depth == 0 -> return i
            }
            i++
        }
        return json.length
    }
}

/**
 * Svaret fra språkmodellen mens det strømmer inn. Modellen svarer med verktøyet `say`, der den først
 * skriver hva den tror brukeren sa (`understood`), så språket og til slutt det som skal sies
 * (`text`). Svarer den med vanlig tekst i stedet, brukes den som den er.
 */
internal class SayStream {
    private val arguments = StringBuilder()
    private val calls = ToolCallAssembler()
    private var sayIndex: Int? = null

    /** Modellen svarte med verktøyet. */
    var usedTool = false
        private set

    /** Hva modellen tror brukeren sa, med feilhørte ord rettet. `null` til feltet er skrevet ferdig. */
    var understood: String? = null
        private set

    /** Språket modellen svarer på (`no`, `en` eller `es`), når det er skrevet ferdig. */
    var language: String? = null
        private set

    /** Det som skal sies, så langt. */
    var text = ""
        private set

    /** Legger til en hendelse. Gir den nye teksten som skal sies, eller tom tekst. */
    fun add(chunk: ChatChunk): String {
        calls.add(chunk.toolCalls)
        if (sayIndex == null) {
            sayIndex = chunk.toolCalls.firstOrNull { calls.at(it.index)?.name == "say" }?.index
        }
        val say = sayIndex?.let { calls.at(it) }
        if (say != null) {
            usedTool = true
            arguments.clear()
            arguments.append(say.arguments)
            return readTool()
        }
        if (chunk.toolCalls.isNotEmpty()) return ""
        // Vanlig tekst, for modeller som ikke bruker verktøy. Kommer verktøyet, brukes bare det.
        if (chunk.content.isEmpty() || usedTool) return ""
        text += chunk.content
        return chunk.content
    }

    private fun readTool(): String {
        val fields = PartialJson.strings(arguments.toString())
        fields["understood"]?.takeIf { it.complete }?.let { understood = it.value.trim().ifEmpty { null } }
        fields["language"]?.takeIf { it.complete }?.let { language = OpenRouter.normalizeLanguage(it.value).ifEmpty { null } }
        val spoken = fields["text"]?.value.orEmpty()
        if (!spoken.startsWith(text)) return ""
        val added = spoken.substring(text.length)
        text = spoken
        return added
    }
}
