package no.jodal.salat.apps.kompis

import kotlinx.serialization.Serializable

internal data class ToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val argumentsDelta: String = "",
)

@Serializable
data class ToolCall(val id: String, val name: String, val arguments: String)

/** Retains arguments even when they arrive before the name. One instance per response. */
internal class ToolCallAssembler {
    private val calls = mutableMapOf<Int, ToolCall>()
    fun add(deltas: List<ToolCallDelta>) {
        deltas.forEach { delta ->
            val previous = calls[delta.index] ?: ToolCall("", "", "")
            calls[delta.index] = ToolCall(
                delta.id ?: previous.id,
                delta.name ?: previous.name,
                previous.arguments + delta.argumentsDelta,
            )
        }
    }
    val all: List<ToolCall> get() = calls.entries.sortedBy { it.key }.map { it.value }
    fun at(index: Int): ToolCall? = calls[index]
}
