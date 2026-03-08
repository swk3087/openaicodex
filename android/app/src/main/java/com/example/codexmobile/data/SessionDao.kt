package com.example.codexmobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeById(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun findById(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Query(
        """
        INSERT OR IGNORE INTO sessions(
            id,
            selectedModel,
            runtimeVersion,
            state,
            workspacePath,
            metadata,
            lastErrorCode,
            lastErrorMessage,
            updatedAt
        ) VALUES (
            :sessionId,
            :model,
            '-',
            'IDLE',
            :workspaceUri,
            '',
            NULL,
            NULL,
            :updatedAt
        )
        """
    )
    suspend fun createIfAbsent(
        sessionId: String,
        model: String,
        workspaceUri: String,
        updatedAt: Long
    )

    @Query("UPDATE sessions SET selectedModel = :model WHERE id = :sessionId")
    suspend fun updateModel(sessionId: String, model: String)

    @Query("UPDATE sessions SET state = :state WHERE id = :sessionId")
    suspend fun updateState(sessionId: String, state: String)

    @Query("UPDATE sessions SET runtimeVersion = :runtime, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateRuntimeVersion(sessionId: String, runtime: String, updatedAt: Long)

    @Query(
        """
        UPDATE sessions
        SET lastErrorCode = :code,
            lastErrorMessage = :message,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updateError(sessionId: String, code: String?, message: String?, updatedAt: Long)

    @Query("UPDATE sessions SET metadata = :metadata WHERE id = :sessionId")
    suspend fun updateMetadata(sessionId: String, metadata: String)
}
