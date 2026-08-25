package com.offhand.parse

/**
 * The production parser. Keyword/regex intent detection plus slot extraction.
 * Deterministic by design: same transcript, same draft, every time.
 *
 * Dispatch order (first hit wins):
 *   1. clipboard      — "clipboard" is unambiguous
 *   2. reminder       — leading "remind …" / "set a reminder"
 *   3. laptop file    — file noun + laptop context, or fetch verb + file noun
 *   4. email          — email/mail keywords or leading send/write-to
 *   5. event          — meeting/event/appointment/calendar keywords
 *   6. explicit note  — leading note/jot/write-down
 *   7. fallback       — capture_note with the transcript as body (safe default)
 *
 * Anything not confidently matched degrades toward the safe default; the
 * validator and the confirmation card catch the rest. This parser never
 * executes anything.
 */
class DeterministicActionParser(
    private val dateResolver: DateResolver,
) : ActionParser {

    private val fileNoun =
        Regex("""\b(file|files|document|documents|doc|pdf|presentation|ppt|pptx|spreadsheet|sheet|slides|report|photo|image)\b""")
    private val laptopWord = Regex("""\b(laptop|computer|pc|desktop)\b""")
    private val recipientStop = setOf(
        "about", "regarding", "saying", "that", "subject", "and", "telling",
        "asking", "with", "for", "on", "at", "by", "tomorrow", "today", "the",
    )

    override fun parse(transcript: String): ActionDraft {
        val text = transcript.trim()
        val lower = text.lowercase()

        if (lower.isBlank()) {
            return ActionDraft(ActionType.CAPTURE_NOTE, Slots(body = ""), Confidence.LOW, text)
        }

        // 1. clipboard
        if (lower.contains("clipboard")) {
            return ActionDraft(ActionType.GET_LAPTOP_CLIPBOARD, Slots(), Confidence.HIGH, text)
        }

        // 2. reminder (strong forms)
        if (Regex("""^(please\s+)?remind\b""").containsMatchIn(lower) ||
            lower.contains("set a reminder") || lower.contains("set reminder")
        ) {
            return reminderDraft(text, lower, Confidence.HIGH)
        }

        // 3. laptop file fetch
        val hasFileNoun = fileNoun.containsMatchIn(lower)
        val hasLaptop = laptopWord.containsMatchIn(lower)
        val leadingFetch = Regex("""^(please\s+)?(fetch|get|grab|pull|bring)\b""").containsMatchIn(lower)
        if ((hasFileNoun && hasLaptop) || (leadingFetch && hasFileNoun)) {
            return fetchDraft(text, lower, if (hasLaptop) Confidence.HIGH else Confidence.LOW)
        }

        // 4. email
        val hasEmailWord = Regex("""\b(email|e-mail|mail)\b""").containsMatchIn(lower)
        val leadingSend = Regex("""^(please\s+)?(send|email|mail|write to)\b""").containsMatchIn(lower)
        if (hasEmailWord || leadingSend) {
            val confidence = if (leadingSend || lower.startsWith("email")) Confidence.HIGH else Confidence.LOW
            return emailDraft(text, lower, confidence)
        }

        // 5. event
        val hasEventWord = Regex("""\b(meeting|event|appointment|calendar)\b""").containsMatchIn(lower)
        val leadingSchedule = Regex("""^(please\s+)?(schedule|book)\b""").containsMatchIn(lower)
        if (hasEventWord || leadingSchedule) {
            val confidence = if (leadingSchedule ||
                Regex("""^(please\s+)?(create|add|set up|put)\b""").containsMatchIn(lower)
            ) Confidence.HIGH else Confidence.LOW
            return eventDraft(text, lower, confidence)
        }

        // 6. explicit note
        val noteLead = Regex("""^(please\s+)?(take a note|note down|note that|note|jot down|jot|write down)\b""")
        noteLead.find(lower)?.let { m ->
            val body = text.drop(m.range.last + 1).trim().trimStart(':', ',', ' ').trim()
            return ActionDraft(
                ActionType.CAPTURE_NOTE,
                Slots(body = body.ifBlank { text }),
                Confidence.HIGH,
                text,
            )
        }

        // 7. weak reminder anywhere
        if (lower.contains("remind")) return reminderDraft(text, lower, Confidence.LOW)

        // 8. safe default: capture everything as a note, flagged for review
        return ActionDraft(ActionType.CAPTURE_NOTE, Slots(body = text), Confidence.LOW, text)
    }

    // ---- per-action slot extraction ------------------------------------

    private fun reminderDraft(text: String, lower: String, confidence: Confidence): ActionDraft {
        val resolution = dateResolver.resolve(text)
        val bodyMatch = Regex(
            """\bremind(?:er)?(?:\s+me)?\s+(?:to|about|that|for)?\s*""",
        ).find(lower)
        var body = if (bodyMatch != null) text.drop(bodyMatch.range.last + 1) else text
        body = dateResolver.stripMatches(body, shiftRanges(resolution, bodyMatch?.range?.last?.plus(1) ?: 0))
        return ActionDraft(
            ActionType.SET_REMINDER,
            Slots(
                body = body.trim().ifBlank { null },
                datetime = resolution?.dateTime?.toString(),
            ),
            confidence,
            text,
        )
    }

    private fun fetchDraft(text: String, lower: String, confidence: Confidence): ActionDraft {
        // "called X" / "named X" wins; otherwise the words between the fetch
        // verb and the laptop phrase, minus filler.
        var hint: String? = Regex("""\b(?:called|named)\s+(.+?)(?:\s+from\b.*)?$""")
            .find(lower)?.groupValues?.get(1)
        if (hint == null) {
            var candidate = lower
                .replace(Regex("""^(please\s+)?(fetch|get|grab|pull|bring)\s+(me\s+)?"""), "")
                .replace(Regex("""\bfrom\b.*$"""), "")
                .replace(Regex("""\b(the|my|a|an|that|this|latest)\b"""), " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            if (candidate.isNotBlank()) hint = candidate
        }
        return ActionDraft(
            ActionType.FETCH_LAPTOP_FILE,
            Slots(pathHint = hint?.trim()?.ifBlank { null }),
            confidence,
            text,
        )
    }

    private fun emailDraft(text: String, lower: String, confidence: Confidence): ActionDraft {
        // Recipient: "to <name>" preferred; else the word straight after email/mail.
        var recipient: String? = null
        Regex("""\bto\s+([a-z]+(?:\s+[a-z]+)?)""").find(lower)?.let { m ->
            recipient = m.groupValues[1].split(" ")
                .takeWhile { it !in recipientStop }
                .joinToString(" ")
                .ifBlank { null }
        }
        if (recipient == null) {
            Regex("""\b(?:email|e-mail|mail)\s+([a-z]+)""").find(lower)?.let { m ->
                val word = m.groupValues[1]
                if (word !in recipientStop && word !in setOf("me", "it", "them", "him", "her", "a", "an")) {
                    recipient = word
                }
            }
        }

        // Subject: after about/regarding/subject, up to a body marker.
        val subject = Regex(
            """\b(?:about|regarding|subject(?:\s+is)?)\s+(.+?)(?=\s+(?:saying|body|and say|tell)\b|$)""",
        ).find(lower)?.groupValues?.get(1)?.trim()

        // Body: after an explicit body marker.
        val body = Regex(
            """\b(?:saying|say that|body is|that says|tell (?:him|her|them)(?: that)?)\s+(.+)$""",
        ).find(lower)?.groupValues?.get(1)?.trim()

        return ActionDraft(
            ActionType.SEND_EMAIL,
            Slots(recipient = recipient, subject = subject, body = body),
            confidence,
            text,
        )
    }

    private fun eventDraft(text: String, lower: String, confidence: Confidence): ActionDraft {
        val resolution = dateResolver.resolve(text)
        var title = dateResolver.stripMatches(lower, resolution)
        title = title
            .replace(Regex("""^(please\s+)?(schedule|book|create|add|set up|put)\s+"""), "")
            .replace(Regex("""\b(on|in|to)\s+(my\s+)?calendar\b"""), " ")
            .replace(Regex("""^\s*(a|an|the)\s+"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim().trimEnd('.', ',')
        return ActionDraft(
            ActionType.CREATE_EVENT,
            Slots(
                subject = title.ifBlank { null },
                datetime = resolution?.dateTime?.toString(),
            ),
            confidence,
            text,
        )
    }

    /** Shift date-match ranges after a prefix of `offset` chars was dropped. */
    private fun shiftRanges(res: DateResolver.Resolution?, offset: Int): DateResolver.Resolution? {
        if (res == null || offset == 0) return res
        val shifted = res.matchedRanges
            .filter { it.first >= offset }
            .map { (it.first - offset)..(it.last - offset) }
        return DateResolver.Resolution(res.dateTime, shifted)
    }
}
