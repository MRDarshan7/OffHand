package com.offhand.parse

/** The six actions. There is no seventh. */
enum class ActionType {
    SEND_EMAIL,
    CREATE_EVENT,
    SET_REMINDER,
    FETCH_LAPTOP_FILE,
    GET_LAPTOP_CLIPBOARD,
    CAPTURE_NOTE;

    val wireName: String
        get() = name.lowercase()
}

enum class Confidence { HIGH, LOW }

/** Raw slots as extracted from the transcript. All optional at parse time. */
data class Slots(
    val recipient: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val datetime: String? = null, // ISO-8601 after resolution
    val pathHint: String? = null,
)

/** Parser output: always a draft, never an execution. */
data class ActionDraft(
    val type: ActionType,
    val slots: Slots,
    val confidence: Confidence,
    val transcript: String,
)

/** One parser interface so implementations are swappable (an LLM could slot in later). */
interface ActionParser {
    fun parse(transcript: String): ActionDraft
}
