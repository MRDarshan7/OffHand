package com.offhand.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The parser eval harness (master spec §10). Every parser or validator tweak
 * runs against this table. Transcript -> expected action + key slots.
 * Hard rule proven here: every output is a safe draft or NEEDS_INPUT —
 * zero invalid or hallucinated actions.
 */
class ParserEvalHarnessTest {

    // Fixed clock: Tuesday 2026-08-25, 10:00.
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 25, 10, 0)
    private val dateResolver = DateResolver { now }
    private val parser = DeterministicActionParser(dateResolver)
    private val contacts = listOf(
        Contact("Priya Sharma", "priya@example.com"),
        Contact("Rahul Verma", "rahul@example.com"),
        Contact("Sneha Rao", "sneha@example.com"),
        Contact("Karan Mehta", "karan@example.com"),
        Contact("Kiran Joshi", "kiran@example.com"),
        Contact("Professor Kumar", "kumar@example.com"),
    )
    private val validator = Validator(ContactResolver(contacts), dateResolver)

    private fun run(transcript: String): ValidatedDraft =
        validator.validate(parser.parse(transcript))

    // ---- send_email ----------------------------------------------------

    @Test fun `email with recipient and subject`() {
        val v = run("email priya about the assignment deadline")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals("priya@example.com", v.recipient.value)
        assertEquals(FieldStatus.OK, v.recipient.status)
        assertEquals("the assignment deadline", v.subject.value)
    }

    @Test fun `email with subject and body markers`() {
        val v = run("send an email to rahul subject project update saying the demo is ready")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals("rahul@example.com", v.recipient.value)
        assertEquals("project update", v.subject.value)
        assertEquals("the demo is ready", v.body.value)
        assertTrue(v.ready)
    }

    @Test fun `mail verb with possessive in subject`() {
        val v = run("mail sneha about tomorrow's class")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals("sneha@example.com", v.recipient.value)
    }

    @Test fun `write to phrasing is email`() {
        val v = run("write to rahul that the meeting moved")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals("rahul@example.com", v.recipient.value)
    }

    @Test fun `ambiguous recipient needs input`() {
        val v = run("email keran about the fees")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals(FieldStatus.NEEDS_INPUT, v.recipient.status)
        assertEquals(2, v.recipientCandidates.size)
        assertEquals(Confidence.LOW, v.confidence)
        assertFalse(v.ready)
    }

    @Test fun `unknown recipient needs input`() {
        val v = run("email daniel about the trip")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals(FieldStatus.NEEDS_INPUT, v.recipient.status)
        assertFalse(v.ready)
    }

    @Test fun `multi word contact resolves`() {
        val v = run("send email to professor kumar about the seminar")
        assertEquals(ActionType.SEND_EMAIL, v.type)
        assertEquals("kumar@example.com", v.recipient.value)
    }

    // ---- set_reminder --------------------------------------------------

    @Test fun `reminder with relative day and day part`() {
        val v = run("remind me to submit the lab record tomorrow morning")
        assertEquals(ActionType.SET_REMINDER, v.type)
        assertEquals("2026-08-26T09:00", v.datetime.value)
        assertEquals("submit the lab record", v.body.value)
        assertTrue(v.ready)
    }

    @Test fun `reminder with clock time`() {
        val v = run("remind me to call amma at 6 pm")
        assertEquals(ActionType.SET_REMINDER, v.type)
        assertEquals("2026-08-25T18:00", v.datetime.value)
        assertEquals("call amma", v.body.value)
    }

    @Test fun `reminder with weekday afternoon`() {
        val v = run("set a reminder for the viva on thursday afternoon")
        assertEquals(ActionType.SET_REMINDER, v.type)
        assertEquals("2026-08-27T14:00", v.datetime.value)
    }

    @Test fun `reminder without time needs input`() {
        val v = run("remind me to water the plants")
        assertEquals(ActionType.SET_REMINDER, v.type)
        assertEquals("water the plants", v.body.value)
        assertEquals(FieldStatus.NEEDS_INPUT, v.datetime.status)
        assertFalse(v.ready)
    }

    // ---- create_event --------------------------------------------------

    @Test fun `event with time and title`() {
        val v = run("schedule a meeting with the project team tomorrow at 3 pm")
        assertEquals(ActionType.CREATE_EVENT, v.type)
        assertEquals("2026-08-26T15:00", v.datetime.value)
        assertEquals("meeting with the project team", v.subject.value)
        assertTrue(v.ready)
    }

    @Test fun `event with weekday morning`() {
        val v = run("add dentist appointment on friday morning")
        assertEquals(ActionType.CREATE_EVENT, v.type)
        assertEquals("2026-08-28T09:00", v.datetime.value)
        assertEquals("dentist appointment", v.subject.value)
    }

    @Test fun `event without time needs input`() {
        val v = run("schedule sync meeting")
        assertEquals(ActionType.CREATE_EVENT, v.type)
        assertEquals(FieldStatus.NEEDS_INPUT, v.datetime.status)
        assertFalse(v.ready)
    }

    // ---- fetch_laptop_file --------------------------------------------

    @Test fun `fetch file with description`() {
        val v = run("get the quarterly report file from my laptop")
        assertEquals(ActionType.FETCH_LAPTOP_FILE, v.type)
        assertTrue(v.pathHint.value!!.contains("quarterly report"))
        assertTrue(v.ready)
    }

    @Test fun `fetch file with called name`() {
        val v = run("fetch the presentation called demo day slides from the laptop")
        assertEquals(ActionType.FETCH_LAPTOP_FILE, v.type)
        assertEquals("demo day slides", v.pathHint.value)
    }

    // ---- get_laptop_clipboard -----------------------------------------

    @Test fun `clipboard beats file wording`() {
        val v = run("get the clipboard from my laptop")
        assertEquals(ActionType.GET_LAPTOP_CLIPBOARD, v.type)
        assertTrue(v.ready)
    }

    @Test fun `clipboard plain`() {
        val v = run("copy whatever is on my clipboard")
        assertEquals(ActionType.GET_LAPTOP_CLIPBOARD, v.type)
    }

    // ---- capture_note --------------------------------------------------

    @Test fun `note down`() {
        val v = run("note down buy milk and eggs")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals("buy milk and eggs", v.body.value)
        assertTrue(v.ready)
    }

    @Test fun `take a note`() {
        val v = run("take a note the wifi password is offhand123")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals("the wifi password is offhand123", v.body.value)
    }

    // ---- out-of-scope and junk: the safe default ----------------------

    @Test fun `out of scope becomes note flagged low`() {
        val v = run("what's the weather like today")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals("what's the weather like today", v.body.value)
        assertEquals(Confidence.LOW, v.confidence)
    }

    @Test fun `gibberish becomes note`() {
        val v = run("blah blah gibberish input")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals(Confidence.LOW, v.confidence)
    }

    // Real ASR degradations observed with Vosk small-en on synthetic speech:
    // the parser must degrade SAFELY — fallback note, never a wrong action.

    @Test fun `asr-mangled clipboard phrase degrades to note, not a wrong action`() {
        val v = run("copy whatever is unlikely board")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals(Confidence.LOW, v.confidence)
    }

    @Test fun `asr-mangled note phrase still captures the content`() {
        val v = run("no down by milk and eggs")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals("no down by milk and eggs", v.body.value)
    }

    @Test fun `blank input is a note needing input`() {
        val v = run("")
        assertEquals(ActionType.CAPTURE_NOTE, v.type)
        assertEquals(FieldStatus.NEEDS_INPUT, v.body.status)
        assertFalse(v.ready)
    }

    // ---- global invariants --------------------------------------------

    @Test fun `every case yields a legal action and never throws`() {
        val transcripts = listOf(
            "email priya about the assignment deadline",
            "send an email to rahul subject project update saying the demo is ready",
            "mail sneha about tomorrow's class",
            "write to rahul that the meeting moved",
            "email keran about the fees",
            "email daniel about the trip",
            "send email to professor kumar about the seminar",
            "remind me to submit the lab record tomorrow morning",
            "remind me to call amma at 6 pm",
            "set a reminder for the viva on thursday afternoon",
            "remind me to water the plants",
            "schedule a meeting with the project team tomorrow at 3 pm",
            "add dentist appointment on friday morning",
            "schedule sync meeting",
            "get the quarterly report file from my laptop",
            "fetch the presentation called demo day slides from the laptop",
            "get the clipboard from my laptop",
            "copy whatever is on my clipboard",
            "note down buy milk and eggs",
            "take a note the wifi password is offhand123",
            "what's the weather like today",
            "blah blah gibberish input",
            "",
            "asdf qwer zxcv",
            "play some music please",
        )
        for (t in transcripts) {
            val v = run(t)
            // Type is by construction one of the six enum values; a draft that
            // is not ready must carry at least one NEEDS_INPUT flag.
            if (!v.ready) {
                assertTrue(
                    "not-ready draft for \"$t\" must flag a field",
                    listOf(v.recipient, v.subject, v.body, v.datetime, v.pathHint)
                        .any { it.status == FieldStatus.NEEDS_INPUT },
                )
            }
        }
    }
}
