package com.offhand.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "actions", indices = [Index("state")])
data class ActionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val slotsJson: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val transcript: String = "",
    val idempotencyKey: String = id,
)
