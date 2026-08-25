package com.offhand.action

import com.offhand.bridge.LaptopBridge
import com.offhand.data.ActionDao
import com.offhand.data.ActionEntity
import com.offhand.data.NoteDao
import com.offhand.data.NoteEntity
import java.io.File
import java.util.Locale

/**
 * Pulls the best-matching file from the laptop's whitelisted folder into
 * local storage (the email-attachment path). The stored slots gain
 * `attachmentPath` so the Outbox shows where it landed.
 */
class FetchFileExecutor(
    private val bridge: LaptopBridge,
    private val actionDao: ActionDao,
    private val destDir: File,
) : ActionExecutor {

    override suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome {
        val hint = slots.pathHint?.takeIf { it.isNotBlank() }
            ?: return ExecOutcome.Failure("No file specified")

        val files = bridge.listFiles()
        if (files.isEmpty()) {
            return ExecOutcome.Failure("Laptop unreachable or shared folder empty")
        }

        val best = bestMatch(hint, files)
            ?: return ExecOutcome.Failure("No file on the laptop matches “$hint”")

        val fetched = bridge.fetchFile(best, destDir)
            ?: return ExecOutcome.Failure("Download of “$best” failed")

        actionDao.upsert(
            entity.copy(
                slotsJson = slots.copy(attachmentPath = fetched.absolutePath).encode(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return ExecOutcome.Success
    }

    /** Token containment scoring; ties broken by shorter name. Deterministic. */
    private fun bestMatch(hint: String, files: List<String>): String? {
        val hintTokens = hint.lowercase(Locale.ROOT).split(Regex("""[\s._-]+"""))
            .filter { it.isNotBlank() && it !in setOf("file", "document", "doc", "the") }
        if (hintTokens.isEmpty()) return null
        val scored = files.map { name ->
            val base = name.lowercase(Locale.ROOT)
            val score = hintTokens.count { base.contains(it) }.toDouble() / hintTokens.size
            Triple(name, score, name.length)
        }.sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
        val top = scored.first()
        return if (top.second >= 0.5) top.first else null
    }
}

/** Laptop clipboard -> a saved note (visible immediately, works offline after). */
class ClipboardExecutor(
    private val bridge: LaptopBridge,
    private val noteDao: NoteDao,
) : ActionExecutor {

    override suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome {
        val text = bridge.clipboard()
            ?: return ExecOutcome.Failure("Laptop unreachable")
        if (text.isBlank()) return ExecOutcome.Failure("Laptop clipboard is empty")
        noteDao.insert(
            NoteEntity(body = text, createdAt = System.currentTimeMillis()),
        )
        return ExecOutcome.Success
    }
}
