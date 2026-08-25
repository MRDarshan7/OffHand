package com.offhand.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Natural-language date/time resolution, in code. The transcript text is a
 * hint; this resolver's output is the truth the app acts on.
 *
 * Conventions (documented product behaviour):
 *   morning = 09:00 · afternoon = 14:00 · evening = 18:00 · night/tonight = 20:00
 *   noon = 12:00 · midnight = 00:00 next day
 *   A date with no time defaults to 09:00.
 *   A bare time earlier than now rolls to tomorrow.
 */
class DateResolver(private val now: () -> LocalDateTime) {

    data class Resolution(
        val dateTime: LocalDateTime,
        /** Character ranges of the transcript that expressed the date/time. */
        val matchedRanges: List<IntRange>,
    )

    private data class Match(val range: IntRange)

    fun resolve(text: String): Resolution? {
        val lower = text.lowercase()
        val current = now()

        var date: LocalDate? = null
        var time: LocalTime? = null
        var dayRolled = false
        val ranges = mutableListOf<IntRange>()

        // --- relative offsets: "in 10 minutes", "in 2 hours", "in 3 days"
        Regex("""\bin\s+(\d+|a|an)\s+(minute|minutes|hour|hours|day|days)\b""")
            .find(lower)?.let { m ->
                val amount = when (m.groupValues[1]) {
                    "a", "an" -> 1L
                    else -> m.groupValues[1].toLong()
                }
                val result = when {
                    m.groupValues[2].startsWith("minute") -> current.plusMinutes(amount)
                    m.groupValues[2].startsWith("hour") -> current.plusHours(amount)
                    else -> current.plusDays(amount).withHour(9).withMinute(0)
                }
                return Resolution(result.withSecond(0).withNano(0), listOf(m.range))
            }

        // --- explicit dates
        Regex("""\bday after tomorrow\b""").find(lower)?.let {
            date = current.toLocalDate().plusDays(2); ranges += it.range
        }
        if (date == null) Regex("""\btomorrow\b""").find(lower)?.let {
            date = current.toLocalDate().plusDays(1); ranges += it.range
        }
        if (date == null) Regex("""\btoday\b""").find(lower)?.let {
            date = current.toLocalDate(); ranges += it.range
        }
        if (date == null) Regex("""\btonight\b""").find(lower)?.let {
            date = current.toLocalDate(); time = LocalTime.of(20, 0); ranges += it.range
        }

        // --- weekday names, with optional "next"
        if (date == null) {
            val weekdays = mapOf(
                "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
                "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
                "sunday" to DayOfWeek.SUNDAY,
            )
            Regex("""\b(next\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")
                .find(lower)?.let { m ->
                    // Convention: "thursday" and "next thursday" both mean the
                    // upcoming Thursday. The confirmation card shows the
                    // resolved date, so a wrong guess is one edit away.
                    val target = weekdays.getValue(m.groupValues[2])
                    var d = current.toLocalDate().plusDays(1)
                    while (d.dayOfWeek != target) d = d.plusDays(1)
                    date = d; ranges += m.range
                }
        }

        // --- day parts
        if (time == null) {
            Regex("""\b(morning|afternoon|evening|night|noon|midnight)\b""").find(lower)?.let { m ->
                time = when (m.groupValues[1]) {
                    "morning" -> LocalTime.of(9, 0)
                    "afternoon" -> LocalTime.of(14, 0)
                    "evening" -> LocalTime.of(18, 0)
                    "night" -> LocalTime.of(20, 0)
                    "noon" -> LocalTime.of(12, 0)
                    else -> { dayRolled = true; LocalTime.MIDNIGHT } // midnight -> next day
                }
                ranges += m.range
            }
        }

        // --- explicit clock times: "at 5", "at 5 pm", "at 5:30", "at 17:45"
        if (time == null) {
            Regex("""\b(?:at|by)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?\b""")
                .find(lower)?.let { m ->
                    var hour = m.groupValues[1].toInt()
                    val minute = m.groupValues[2].ifBlank { "0" }.toInt()
                    val meridiem = m.groupValues[3].replace(".", "")
                    if (meridiem == "pm" && hour < 12) hour += 12
                    if (meridiem == "am" && hour == 12) hour = 0
                    // "at 5" with no meridiem and no date: prefer the upcoming instance.
                    if (meridiem.isBlank() && hour in 1..11 && date == null) {
                        val asIs = LocalTime.of(hour, minute)
                        if (asIs <= current.toLocalTime() &&
                            LocalTime.of(hour + 12, minute).isAfter(current.toLocalTime())
                        ) hour += 12
                    }
                    if (hour in 0..23 && minute in 0..59) {
                        time = LocalTime.of(hour, minute)
                        ranges += m.range
                    }
                }
        }

        if (date == null && time == null) return null

        var resolvedDate = date ?: current.toLocalDate()
        val resolvedTime = time ?: LocalTime.of(9, 0)
        if (dayRolled) resolvedDate = resolvedDate.plusDays(1)
        var result = LocalDateTime.of(resolvedDate, resolvedTime)
        // A bare time (no date words) already in the past rolls to tomorrow.
        if (date == null && result <= current) result = result.plusDays(1)
        return Resolution(result.withSecond(0).withNano(0), ranges)
    }

    /** Strip the matched date/time phrase out of a string (for clean titles/bodies). */
    fun stripMatches(text: String, resolution: Resolution?): String {
        if (resolution == null) return text.trim()
        val sb = StringBuilder(text)
        resolution.matchedRanges.sortedByDescending { it.first }.forEach { r ->
            if (r.last < sb.length) sb.delete(r.first, r.last + 1)
        }
        return sb.toString()
            .replace(Regex("""\s{2,}"""), " ")
            .replace(Regex("""\s+(at|on|by|for)\s*$"""), "")
            .trim()
    }
}
