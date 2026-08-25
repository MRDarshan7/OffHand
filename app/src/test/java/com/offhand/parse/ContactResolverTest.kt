package com.offhand.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactResolverTest {

    private val resolver = ContactResolver(
        listOf(
            Contact("Priya Sharma", "priya@example.com"),
            Contact("Rahul Verma", "rahul@example.com"),
            Contact("Karan Mehta", "karan@example.com"),
            Contact("Kiran Joshi", "kiran@example.com"),
            Contact("Professor Kumar", "kumar@example.com"),
        ),
    )

    @Test fun `exact first name`() {
        val r = resolver.resolve("priya") as RecipientResolution.Match
        assertEquals("priya@example.com", r.contact.email)
    }

    @Test fun `exact full name`() {
        val r = resolver.resolve("professor kumar") as RecipientResolution.Match
        assertEquals("kumar@example.com", r.contact.email)
    }

    @Test fun `fuzzy one edit`() {
        val r = resolver.resolve("pria") as RecipientResolution.Match
        assertEquals("priya@example.com", r.contact.email)
    }

    @Test fun `ambiguous between two close names`() {
        val r = resolver.resolve("keran")
        assertTrue(r is RecipientResolution.Ambiguous)
        assertEquals(2, (r as RecipientResolution.Ambiguous).candidates.size)
    }

    @Test fun miss() {
        assertTrue(resolver.resolve("daniel") is RecipientResolution.NoMatch)
    }

    @Test fun `null and blank`() {
        assertTrue(resolver.resolve(null) is RecipientResolution.NoMatch)
        assertTrue(resolver.resolve("  ") is RecipientResolution.NoMatch)
    }
}
