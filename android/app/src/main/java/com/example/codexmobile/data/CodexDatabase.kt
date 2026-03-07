package com.example.codexmobile.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, PatchEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CodexDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
