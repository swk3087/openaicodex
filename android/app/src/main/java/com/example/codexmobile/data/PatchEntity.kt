package com.example.codexmobile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patches",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class PatchEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val diffText: String,
    val applyStatus: String,
    val createdAtEpochMs: Long
)
