package com.offhand.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(action: ActionEntity)

    @Query("SELECT * FROM actions WHERE id = :id")
    suspend fun getById(id: String): ActionEntity?

    @Query("SELECT * FROM actions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE state = :state ORDER BY createdAt ASC")
    suspend fun getByState(state: String): List<ActionEntity>
}
