package no.jodal.salat.apps.kompis.tools

import kotlinx.serialization.json.*

/** Terminal tool: the conversation loop handles speech instead of executing it. */
object SayTool : KompisTool {
    private val definition = buildJsonObject { sayDefinition() }["function"]!!.jsonObject
    override val name = "say"
    override val description = definition["description"]!!.jsonPrimitive.content
    override val parameters = definition["parameters"]!!.jsonObject
    override val promptHint = "Avslutt turen med say for å svare høyt til brukeren."
    override suspend fun execute(args: JsonObject): ToolResult =
        error("say must be handled by the conversation loop")
}

private fun JsonObjectBuilder.sayDefinition() {
    put("type", "function")
    putJsonObject("function") {
        put("name", "say")
        put("description", "Si noe høyt til brukeren. Dette er den eneste måten du svarer på.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("understood") {
                    put("type", "string")
                    put(
                        "description",
                        "Hva brukeren mest sannsynlig sa, skrevet riktig. Talegjenkjenningen kan ha hørt norsk som svensk eller dansk " +
                            "eller hørt ord feil. Rett det opp. Bare det brukeren mente, uten kommentarer.",
                    )
                }
                putJsonObject("language") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add("no")
                        add("en")
                        add("es")
                    }
                    put("description", "Språket du svarer på.")
                }
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "Det som skal sies høyt, som ren tekst slik det skal høres.")
                }
            }
            putJsonArray("required") {
                add("understood")
                add("language")
                add("text")
            }
        }
    }
}
