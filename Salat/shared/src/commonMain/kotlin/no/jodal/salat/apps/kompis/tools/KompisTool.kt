package no.jodal.salat.apps.kompis.tools

import kotlinx.serialization.json.JsonObject

interface KompisTool {
    val name: String
    val description: String
    val parameters: JsonObject
    val promptHint: String
    suspend fun execute(args: JsonObject): ToolResult
}

@kotlinx.serialization.Serializable
data class ToolCard(val title: String, val detail: String? = null)
data class ToolResult(val content: String, val isError: Boolean = false, val card: ToolCard? = null)

class KompisTools(val all: List<KompisTool> = listOf(SayTool)) {
    init { require(all.map { it.name }.distinct().size == all.size) { "Duplicate tool names" } }
    operator fun get(name: String): KompisTool? = all.firstOrNull { it.name == name }
}
