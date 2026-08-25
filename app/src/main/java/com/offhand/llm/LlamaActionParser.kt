package com.offhand.llm

import com.offhand.parse.ActionDraft
import com.offhand.parse.ActionParser
import com.offhand.parse.ActionType
import com.offhand.parse.Confidence
import com.offhand.parse.Slots
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Raised when the LLM path can't produce a valid draft; caller falls back. */
class LlmParseException(message: String) : Exception(message)

@Serializable
private data class WireSlots(
    val recipient: String?,
    val subject: String?,
    val body: String?,
    val datetime: String?,
    @SerialName("path_hint") val pathHint: String?,
)

@Serializable
private data class WireDraft(
    val action: String,
    val slots: WireSlots,
    val confidence: String,
)

/**
 * The LLM parser: grammar-constrained generation, then a STRICT schema parse
 * (unknown keys reject). The grammar guarantees shape; this parse is the
 * independent check that it really happened. Returns drafts only — the
 * validator and confirmation card still stand between this and execution.
 */
class LlamaActionParser(private val engine: LlamaEngine) : ActionParser {

    private val json = Json { ignoreUnknownKeys = false }

    override fun parse(transcript: String): ActionDraft {
        val raw = engine.generate(transcript)
            ?: throw LlmParseException("generation failed")
        val wire = try {
            json.decodeFromString(WireDraft.serializer(), raw)
        } catch (e: Exception) {
            throw LlmParseException("schema-invalid output")
        }
        val type = ActionType.entries.firstOrNull { it.wireName == wire.action }
            ?: throw LlmParseException("unknown action ${wire.action}")
        val confidence = when (wire.confidence) {
            "high" -> Confidence.HIGH
            "low" -> Confidence.LOW
            else -> throw LlmParseException("unknown confidence")
        }
        return ActionDraft(
            type = type,
            slots = Slots(
                recipient = wire.slots.recipient?.takeIf { it.isNotBlank() },
                subject = wire.slots.subject?.takeIf { it.isNotBlank() },
                body = wire.slots.body?.takeIf { it.isNotBlank() },
                datetime = wire.slots.datetime?.takeIf { it.isNotBlank() },
                pathHint = wire.slots.pathHint?.takeIf { it.isNotBlank() },
            ),
            confidence = confidence,
            transcript = transcript,
        )
    }
}
