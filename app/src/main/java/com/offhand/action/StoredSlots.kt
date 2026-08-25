package com.offhand.action

import com.offhand.parse.ValidatedDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What gets persisted in ActionEntity.slotsJson: the *resolved* values the
 * user confirmed, not the raw spoken hints. Strict JSON — unknown keys reject.
 */
@Serializable
data class StoredSlots(
    val recipientEmail: String? = null,
    val recipientName: String? = null,
    val subject: String? = null,
    val body: String? = null,
    /** ISO-8601 LocalDateTime, resolved in code. */
    val datetime: String? = null,
    val pathHint: String? = null,
    /** Local path of a bridge-fetched file to attach (M5). */
    val attachmentPath: String? = null,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = false }

        fun from(v: ValidatedDraft): StoredSlots = StoredSlots(
            recipientEmail = if (v.recipient.status == com.offhand.parse.FieldStatus.OK) v.recipient.value else null,
            recipientName = v.recipientDisplay,
            subject = v.subject.value,
            body = v.body.value,
            datetime = v.datetime.value,
            pathHint = v.pathHint.value,
        )

        fun decode(s: String): StoredSlots = json.decodeFromString(serializer(), s)
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}
