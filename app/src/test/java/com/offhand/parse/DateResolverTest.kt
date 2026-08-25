package com.offhand.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DateResolverTest {

    // Tuesday 2026-08-25, 10:00.
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 25, 10, 0)
    private val resolver = DateResolver { now }

    private fun resolved(text: String): String? =
        resolver.resolve(text)?.dateTime?.toString()

    @Test fun tomorrow() = assertEquals("2026-08-26T09:00", resolved("tomorrow"))
    @Test fun `tomorrow evening`() = assertEquals("2026-08-26T18:00", resolved("tomorrow evening"))
    @Test fun `day after tomorrow`() = assertEquals("2026-08-27T09:00", resolved("day after tomorrow"))
    @Test fun `weekday name`() = assertEquals("2026-08-27T09:00", resolved("on thursday"))
    @Test fun `next weekday is upcoming occurrence`() =
        assertEquals("2026-08-31T09:00", resolved("next monday"))
    @Test fun `same weekday as today goes to next week`() =
        assertEquals("2026-09-01T09:00", resolved("on tuesday"))
    @Test fun `explicit pm time`() = assertEquals("2026-08-25T17:00", resolved("at 5 pm"))
    @Test fun `bare hour already past rolls forward`() =
        assertEquals("2026-08-25T20:00", resolved("at 8"))
    @Test fun `bare hour upcoming stays`() = assertEquals("2026-08-25T11:00", resolved("at 11"))
    @Test fun `minutes offset`() = assertEquals("2026-08-25T10:30", resolved("in 30 minutes"))
    @Test fun `hours offset`() = assertEquals("2026-08-25T12:00", resolved("in 2 hours"))
    @Test fun `today at noon`() = assertEquals("2026-08-25T12:00", resolved("today at noon"))
    @Test fun tonight() = assertEquals("2026-08-25T20:00", resolved("tonight"))
    @Test fun `midnight rolls to next day`() = assertEquals("2026-08-26T00:00", resolved("at midnight"))
    @Test fun `half past`() = assertEquals("2026-08-25T17:30", resolved("at 5:30 pm"))
    @Test fun `no datetime returns null`() = assertNull(resolved("hello world"))

    @Test fun `strip removes the time phrase`() {
        val text = "call amma at 6 pm"
        val res = resolver.resolve(text)
        assertEquals("call amma", resolver.stripMatches(text, res))
    }
}
