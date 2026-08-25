package com.offhand.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ValidatorTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 25, 10, 0)
    private val dateResolver = DateResolver { now }
    private val validator = Validator(
        ContactResolver(listOf(Contact("Priya Sharma", "priya@example.com"))),
        dateResolver,
    )

    @Test fun `path hint is sanitised against traversal`() {
        val draft = ActionDraft(
            ActionType.FETCH_LAPTOP_FILE,
            Slots(pathHint = "../../etc/passwd"),
            Confidence.HIGH,
            "get the file ../../etc/passwd from my laptop",
        )
        val v = validator.validate(draft)
        assertEquals(FieldStatus.OK, v.pathHint.status)
        assertFalse(v.pathHint.value!!.contains(".."))
        assertFalse(v.pathHint.value!!.contains("/"))
        assertFalse(v.pathHint.value!!.contains("\\"))
    }

    @Test fun `datetime is re-resolved from transcript independently`() {
        // Parser claims a wrong datetime; the validator's own resolution wins.
        val draft = ActionDraft(
            ActionType.SET_REMINDER,
            Slots(body = "call home", datetime = "1999-01-01T00:00"),
            Confidence.HIGH,
            "remind me to call home tomorrow morning",
        )
        val v = validator.validate(draft)
        assertEquals("2026-08-26T09:00", v.datetime.value)
    }

    @Test fun `email subject and body stay optional`() {
        val draft = ActionDraft(
            ActionType.SEND_EMAIL,
            Slots(recipient = "priya"),
            Confidence.HIGH,
            "email priya",
        )
        val v = validator.validate(draft)
        assertTrue(v.ready)
        assertEquals("priya@example.com", v.recipient.value)
        assertEquals(FieldStatus.OK, v.subject.status)
    }

    @Test fun `low parser confidence propagates`() {
        val draft = ActionDraft(
            ActionType.CAPTURE_NOTE,
            Slots(body = "something unclear"),
            Confidence.LOW,
            "something unclear",
        )
        assertEquals(Confidence.LOW, validator.validate(draft).confidence)
    }

    @Test fun `clipboard needs nothing`() {
        val draft = ActionDraft(
            ActionType.GET_LAPTOP_CLIPBOARD, Slots(), Confidence.HIGH, "get my clipboard",
        )
        assertTrue(validator.validate(draft).ready)
    }
}
