package com.example.codexmobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeById(sessionId: String): Flow<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Query("UPDATE sessions SET selectedModel = :model WHERE id = :sessionId")
    suspend fun updateModel(sessionId: String, model: String)

    @Query("UPDATE sessions SET state = :state WHERE id = :sessionId")
    suspend fun updateState(sessionId: String, state: String)
}
