package com.offhand.parse

/**
 * Business-rule validation, independent of the parser. The parser's output is
 * a hint; this validator's verdict is what the UI trusts. Every unresolvable
 * field becomes NEEDS_INPUT and is flagged on the confirmation card — never
 * guessed, never silently dropped.
 */
enum class FieldStatus { OK, NEEDS_INPUT }

data class ValidatedField(
    val value: String?,
    val status: FieldStatus,
    val hint: String? = null,
)

data class ValidatedDraft(
    val type: ActionType,
    /** Resolved email address when OK; the spoken name is in recipientDisplay. */
    val recipient: ValidatedField,
    val recipientDisplay: String?,
    val subject: ValidatedField,
    val body: ValidatedField,
    /** ISO-8601 LocalDateTime when OK. */
    val datetime: ValidatedField,
    val pathHint: ValidatedField,
    val confidence: Confidence,
    val transcript: String,
    val recipientCandidates: List<Contact> = emptyList(),
) {
    val ready: Boolean
        get() = listOf(recipient, subject, body, datetime, pathHint)
            .none { it.status == FieldStatus.NEEDS_INPUT }
}

class Validator(
    private val contactResolver: ContactResolver,
    private val dateResolver: DateResolver,
) {

    fun validate(draft: ActionDraft): ValidatedDraft {
        val ok = ValidatedField(null, FieldStatus.OK)

        var recipient = ok
        var recipientDisplay: String? = null
        var candidates: List<Contact> = emptyList()
        var subject = ok
        var body = ok
        var datetime = ok
        var pathHint = ok

        when (draft.type) {
            ActionType.SEND_EMAIL -> {
                when (val r = contactResolver.resolve(draft.slots.recipient)) {
                    is RecipientResolution.Match -> {
                        recipient = ValidatedField(r.contact.email, FieldStatus.OK)
                        recipientDisplay = r.contact.name
                    }
                    is RecipientResolution.Ambiguous -> {
                        recipient = ValidatedField(
                            draft.slots.recipient, FieldStatus.NEEDS_INPUT,
                            "Several contacts match — pick one",
                        )
                        candidates = r.candidates
                    }
                    RecipientResolution.NoMatch -> {
                        recipient = ValidatedField(
                            draft.slots.recipient, FieldStatus.NEEDS_INPUT,
                            "No contact matches — choose a recipient",
                        )
                    }
                }
                // Subject and body are optional: "(no subject)" fallback at send
                // time, and an empty body is legal. Both stay editable.
                subject = ValidatedField(draft.slots.subject, FieldStatus.OK)
                body = ValidatedField(draft.slots.body, FieldStatus.OK)
            }

            ActionType.CREATE_EVENT -> {
                subject = requireValue(draft.slots.subject, "Event needs a title")
                datetime = resolveDatetime(draft)
            }

            ActionType.SET_REMINDER -> {
                body = requireValue(draft.slots.body, "What should the reminder say?")
                datetime = resolveDatetime(draft)
            }

            ActionType.FETCH_LAPTOP_FILE -> {
                val sanitized = sanitizePathHint(draft.slots.pathHint)
                pathHint = if (sanitized.isNullOrBlank()) {
                    ValidatedField(null, FieldStatus.NEEDS_INPUT, "Which file?")
                } else {
                    ValidatedField(sanitized, FieldStatus.OK)
                }
            }

            ActionType.GET_LAPTOP_CLIPBOARD -> Unit // no required slots

            ActionType.CAPTURE_NOTE -> {
                body = requireValue(draft.slots.body, "Note is empty")
            }
        }

        val anyNeedsInput = listOf(recipient, subject, body, datetime, pathHint)
            .any { it.status == FieldStatus.NEEDS_INPUT }
        val confidence =
            if (draft.confidence == Confidence.LOW || anyNeedsInput) Confidence.LOW
            else Confidence.HIGH

        return ValidatedDraft(
            type = draft.type,
            recipient = recipient,
            recipientDisplay = recipientDisplay,
            subject = subject,
            body = body,
            datetime = datetime,
            pathHint = pathHint,
            confidence = confidence,
            transcript = draft.transcript,
            recipientCandidates = candidates,
        )
    }

    private fun requireValue(value: String?, hint: String): ValidatedField =
        if (value.isNullOrBlank()) ValidatedField(null, FieldStatus.NEEDS_INPUT, hint)
        else ValidatedField(value.trim(), FieldStatus.OK)

    /**
     * The parser's datetime is a hint; re-resolve from the transcript in code.
     * Prefer the independent resolution, fall back to the parser's value.
     */
    private fun resolveDatetime(draft: ActionDraft): ValidatedField {
        val independent = dateResolver.resolve(draft.transcript)?.dateTime?.toString()
        val value = independent ?: draft.slots.datetime
        return if (value == null) {
            ValidatedField(null, FieldStatus.NEEDS_INPUT, "When?")
        } else {
            ValidatedField(value, FieldStatus.OK)
        }
    }

    /** No traversal, no separators, no drive letters — hints only. */
    private fun sanitizePathHint(hint: String?): String? =
        hint?.replace("..", " ")
            ?.replace(Regex("""[\\/:*?"<>|]"""), " ")
            ?.replace(Regex("""\s{2,}"""), " ")
            ?.trim()
}
