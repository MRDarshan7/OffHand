package com.offhand.action

import com.offhand.data.ActionDao
import com.offhand.data.ActionEntity
import com.offhand.data.ActionState.CANCELLED
import com.offhand.data.ActionState.CONFIRMED
import com.offhand.data.ActionState.DONE
import com.offhand.data.ActionState.DRAFT
import com.offhand.data.ActionState.FAILED
import com.offhand.data.ActionState.QUEUED
import com.offhand.data.ActionState.SENDING
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTest {

    private object NoopDao : ActionDao {
        override suspend fun upsert(action: ActionEntity) = Unit
        override suspend fun getById(id: String): ActionEntity? = null
        override fun observeAll(): Flow<List<ActionEntity>> = flowOf(emptyList())
        override suspend fun getByState(state: String): List<ActionEntity> = emptyList()
    }

    private val repo = ActionRepository(NoopDao)

    @Test fun `happy path is legal`() {
        assertTrue(repo.isLegal(DRAFT, CONFIRMED))
        assertTrue(repo.isLegal(CONFIRMED, QUEUED))
        assertTrue(repo.isLegal(QUEUED, SENDING))
        assertTrue(repo.isLegal(SENDING, DONE))
    }

    @Test fun `offline immediate execution is legal`() {
        assertTrue(repo.isLegal(CONFIRMED, SENDING))
    }

    @Test fun `cancellation windows`() {
        assertTrue(repo.isLegal(DRAFT, CANCELLED))
        assertTrue(repo.isLegal(CONFIRMED, CANCELLED))
        assertTrue(repo.isLegal(QUEUED, CANCELLED))
        assertFalse(repo.isLegal(SENDING, CANCELLED))
        assertFalse(repo.isLegal(DONE, CANCELLED))
    }

    @Test fun `retry loop`() {
        assertTrue(repo.isLegal(SENDING, FAILED))
        assertTrue(repo.isLegal(FAILED, QUEUED))
    }

    @Test fun `terminal states go nowhere`() {
        for (target in listOf(DRAFT, CONFIRMED, QUEUED, SENDING, FAILED)) {
            assertFalse(repo.isLegal(DONE, target))
            assertFalse(repo.isLegal(CANCELLED, target))
        }
    }

    @Test fun `no skipping the queue`() {
        assertFalse(repo.isLegal(DRAFT, QUEUED))
        assertFalse(repo.isLegal(DRAFT, SENDING))
        assertFalse(repo.isLegal(DRAFT, DONE))
        assertFalse(repo.isLegal(QUEUED, DONE))
    }

    @Test fun `stored slots round trip`() {
        val slots = StoredSlots(
            recipientEmail = "priya@example.com",
            recipientName = "Priya Sharma",
            subject = "hello",
            body = "line one\nline two",
            datetime = "2026-08-26T09:00",
        )
        assertEquals(slots, StoredSlots.decode(slots.encode()))
    }
}
