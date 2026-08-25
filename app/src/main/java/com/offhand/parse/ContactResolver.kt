package com.offhand.parse

import java.util.Locale

data class Contact(val name: String, val email: String)

sealed interface RecipientResolution {
    data class Match(val contact: Contact) : RecipientResolution
    data class Ambiguous(val candidates: List<Contact>) : RecipientResolution
    data object NoMatch : RecipientResolution
}

/**
 * Resolves a spoken name against the in-app contact list.
 * Tiers, first non-empty wins: exact full name -> exact first word ->
 * prefix -> fuzzy (edit distance <= 1 for short names, <= 2 otherwise).
 */
class ContactResolver(private val contacts: List<Contact>) {

    fun resolve(spoken: String?): RecipientResolution {
        val query = spoken?.trim()?.lowercase(Locale.ROOT) ?: return RecipientResolution.NoMatch
        if (query.isBlank()) return RecipientResolution.NoMatch

        val exactFull = contacts.filter { it.name.lowercase(Locale.ROOT) == query }
        val exactWord = contacts.filter { c ->
            c.name.lowercase(Locale.ROOT).split(" ").any { it == query }
        }
        val prefix = contacts.filter { c ->
            val n = c.name.lowercase(Locale.ROOT)
            n.startsWith(query) || n.split(" ").any { it.startsWith(query) && query.length >= 3 }
        }
        val fuzzy = contacts.filter { c ->
            val threshold = if (query.length <= 4) 1 else 2
            c.name.lowercase(Locale.ROOT).split(" ").any { word ->
                levenshtein(query, word) <= threshold
            }
        }

        val tier = listOf(exactFull, exactWord, prefix, fuzzy).firstOrNull { it.isNotEmpty() }
            ?: return RecipientResolution.NoMatch
        return if (tier.size == 1) RecipientResolution.Match(tier.first())
        else RecipientResolution.Ambiguous(tier.distinct())
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
